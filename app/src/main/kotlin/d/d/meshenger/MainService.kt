/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.Utils.readInternalFile
import d.d.meshenger.Utils.writeInternalFile
import d.d.meshenger.call.PacketWriter
import d.d.meshenger.call.Pinger
import d.d.meshenger.call.RTCPeerConnection
import java.io.File
import java.io.IOException
import java.net.*
import java.util.*

class MainService : Service(), Runnable {
    private val binder = MainBinder()
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isServerSocketRunning = true

    override fun onCreate() {
        super.onCreate()
        // handle incoming connections
        Thread(this).start()
    }

    private fun createNotification(text: String, showSinceWhen: Boolean): Notification {
        Log.d(this, "createNotification() text=$text setShowWhen=$showSinceWhen")
        val channelId = "meshenger_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Meshenger Call Listener",
                NotificationManager.IMPORTANCE_LOW // display notification as collapsed by default
            )
            chan.lightColor = Color.RED
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }

        // start MainActivity
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingNotificationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(applicationContext, channelId)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(showSinceWhen)
            .setUsesChronometer(showSinceWhen)
            .setSmallIcon(R.drawable.ic_logo)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentText(text)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentIntent(pendingNotificationIntent)
            .build()
    }

    override fun onDestroy() {
        Log.d(this, "onDestroy()")
        isServerSocketRunning = false

        // say goodbye
        if (Database.isDatabaseLoaded() && serverSocket != null && serverSocket!!.isBound && !serverSocket!!.isClosed) {
            try {
                val settings = Database.getSettings()
                val ownPublicKey = settings.publicKey
                val ownSecretKey = settings.secretKey
                val message = "{\"action\": \"status_change\", \"status\", \"offline\"}"
                for (contact in Database.getContacts().contactList) {
                    if (contact.state != Contact.State.CONTACT_ONLINE) {
                        continue
                    }
                    val encrypted = Crypto.encryptMessage(message, contact.publicKey, ownPublicKey, ownSecretKey) ?: continue
                    var socket: Socket? = null
                    try {
                        val connector = Connector(
                            settings.connectTimeout,
                            1, // only try once, this is low priority
                            settings.guessEUI64Address,
                            settings.useNeighborTable
                        )
                        socket = connector.connect(contact)
                        if (socket == null) {
                            continue
                        }
                        val pw = PacketWriter(socket)
                        pw.writeMessage(encrypted)
                    } catch (_: Exception) {
                        // ignore
                    } finally {
                        AddressUtils.closeSocket(socket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }

        // save database on exit
        Database.saveDatabase()
        Database.shutdownDatabase()

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(this, "onStartCommand()")

        if (intent == null || intent.action == null) {
            Log.d(this, "onStartCommand() Received invalid intent")
        } else if (intent.action == START_FOREGROUND_ACTION) {
            Log.d(this, "onStartCommand() Received Start Foreground Intent")
            val message = resources.getText(R.string.listen_for_incoming_calls).toString()
            val notification = createNotification(message, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
        } else if (intent.action == STOP_FOREGROUND_ACTION) {
            Log.d(this, "onStartCommand() Received Stop Foreground Intent")
            shutdown()
        }
        return START_NOT_STICKY
    }

    override fun run() {
        try {
            // wait until database is ready
            while (!Database.isDatabaseLoaded() && isServerSocketRunning) {
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    break
                }
            }
            serverSocket = ServerSocket(SERVER_PORT)
            while (isServerSocketRunning) {
                try {
                    val socket = serverSocket!!.accept()
                    Log.d(this, "run() new incoming connection")
                    RTCPeerConnection.createIncomingCall(binder, socket)
                } catch (e: IOException) {
                    // ignore
                }
            }
        } catch (e: IOException) {
            Log.e(this, "run() e=$e")
            e.printStackTrace()
            Handler(mainLooper).post { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            shutdown()
        }
    }

    private fun updateNotification() {
        Log.d(this, "updateNotification()")

        val events = Database.getEvents()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val message = if (events.eventList.isEmpty() || events.eventsMissed == 0) {
            // default message
            resources.getText(R.string.listen_for_incoming_calls).toString()
        } else {
            // missed calls
            val publicKey = events.eventList.last().publicKey
            val contact = Database.getContacts().getContactByPublicKey(publicKey)
            val name = contact?.name ?: getString(R.string.unknown_caller)
            String.format(getString(R.string.missed_call_from), name, events.eventsMissed)
        }
        val notification = createNotification(message, false)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /*
    * Allows communication between MainService and other objects
    */
    inner class MainBinder : Binder() {
        fun getService(): MainService {
            return this@MainService
        }

        fun getContactOrOwn(otherPublicKey: ByteArray): Contact? {
            if (Database.getSettings().publicKey.contentEquals(otherPublicKey)) {
                return Database.getSettings().getOwnContact()
            } else {
                return Database.getContacts().getContactByPublicKey(otherPublicKey)
            }
        }

        fun updateNotification() {
            this@MainService.updateNotification()
        }

        fun addContact(contact: Contact) {
            Database.getContacts().addContact(contact)
            Database.saveDatabase()

            pingContacts(listOf(contact))

            MainService.refreshContacts(this@MainService)
            MainService.refreshEvents(this@MainService)
        }

        fun deleteContact(publicKey: ByteArray) {
            Database.getContacts().deleteContact(publicKey)
            Database.saveDatabase()

            MainService.refreshContacts(this@MainService)
            MainService.refreshEvents(this@MainService)
        }

        fun deleteEvents(eventDates: List<Date>) {
            Database.getEvents().deleteEvents(eventDates)
            Database.saveDatabase()

            MainService.refreshContacts(this@MainService)
            MainService.refreshEvents(this@MainService)
        }

/*
        fun shutdown() {
            this@MainService.shutdown()
        }
*/
        fun pingContacts(contactList: List<Contact>) {
            Log.d(this, "pingContacts()")
            Thread(
                Pinger(binder, contactList)
            ).start()
        }

        fun addEvent(event: Event) {
            Log.d(this, "addEvent() event.type=${event.type}")

            // update notification
            if (event.type == Event.Type.INCOMING_MISSED) {
                Database.getEvents().eventsMissed += 1
                updateNotification()
            }

            if (!Database.getSettings().disableCallHistory) {
                Database.getEvents().addEvent(event)
                Database.saveDatabase()
                MainService.refreshEvents(this@MainService)
            }
        }

        fun clearEvents() {
            Database.getEvents().clearEvents()
            MainService.refreshEvents(this@MainService)
        }
    }

    private fun shutdown() {
        Log.i(this, "shutdown()")
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {
        const val SERVER_PORT = 10001
        private const val START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION"
        private const val STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION"
        private const val NOTIFICATION_ID = 42

        fun start(ctx: Context) {
            val startIntent = Intent(ctx, MainService::class.java)
            startIntent.action = START_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx, startIntent)
        }

        fun stop(ctx: Context) {
            val stopIntent = Intent(ctx, MainService::class.java)
            stopIntent.action = STOP_FOREGROUND_ACTION
            ctx.startService(stopIntent)
        }

        fun refreshContacts(ctx: Context) {
            LocalBroadcastManager.getInstance(ctx)
                .sendBroadcast(Intent("refresh_contact_list"))
        }

        fun refreshEvents(ctx: Context) {
            LocalBroadcastManager.getInstance(ctx)
                .sendBroadcast(Intent("refresh_event_list"))
        }
    }
}
