package d.d.meshenger

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.Crypto.decryptMessage
import d.d.meshenger.Crypto.encryptMessage
import d.d.meshenger.Utils.readInternalFile
import d.d.meshenger.Utils.writeInternalFile
import org.json.JSONObject
import org.libsodium.jni.Sodium
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class MainService : Service(), Runnable {
    private var database: Database? = null
    var first_start = false
    private var database_path = ""
    var database_password = ""

    @Volatile
    private var run = true
    private var currentCall: RTCCall? = null

    override fun onCreate() {
        super.onCreate()
        database_path = this.filesDir.toString() + "/database.bin"
        // handle incoming connections
        Thread(this).start()
    }

    private val NOTIFICATION = 42
    private fun showNotification() {
        val channelId = "meshenger_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Meshenger Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            chan.lightColor = Color.RED
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)
        }
        // start MainActivity
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingNotificationIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
        val mActivity = applicationContext
        val notification = NotificationCompat.Builder(mActivity, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_logo)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.logo_small))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentText(resources.getText(R.string.listen_for_incoming_calls))
            .setContentIntent(pendingNotificationIntent)
            .build()
        startForeground(NOTIFICATION, notification)
    }

    fun loadDatabase() {
        try {
            if (File(database_path).exists()) {
                // open existing database
                val data = readInternalFile(database_path)
                database = Database.fromData(data, database_password)
                first_start = false
            } else {
                // create new database
                database = Database()
                first_start = true
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun mergeDatabase(new_db: Database) {
        val old_database = database!!

        old_database.settings = new_db.settings

        for (contact in new_db.contacts.contactList) {
            old_database.contacts.addContact(contact)
        }

        for (event in new_db.events.eventList) {
            old_database.events.addEvent(event)
        }
    }

    fun saveDatabase() {
        try {
            val db = database
            if (db != null) {
                val data = Database.toData(db, database_password)
                if (data != null) {
                    writeInternalFile(database_path, data)
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        run = false

        // say goodbye
        val database = this.database
        if (database != null && server != null && server!!.isBound && !server!!.isClosed) {
            try {
                val ownPublicKey = database.settings.publicKey
                val ownSecretKey = database.settings.secretKey
                val message = "{\"action\": \"status_change\", \"status\", \"offline\"}"
                for (contact in database.contacts.contactList) {
                    if (contact.state === Contact.State.OFFLINE) {
                        continue
                    }
                    val encrypted =
                        encryptMessage(message, contact.publicKey, ownPublicKey, ownSecretKey)
                            ?: continue
                    var socket: Socket? = null
                    try {
                        socket = contact.createSocket()
                        if (socket == null) {
                            continue
                        }
                        val pw = PacketWriter(socket)
                        pw.writeMessage(encrypted)
                        socket.close()
                    } catch (e: Exception) {
                        if (socket != null) {
                            try {
                                socket.close()
                            } catch (ee: Exception) {
                                // ignore
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // The database might be null here if no correct
        // database password was supplied to open it.
        if (database != null) {
            try {
                val data = Database.toData(database, database_password)
                if (data != null) {
                    writeInternalFile(database_path, data)
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        // shutdown listening socket
        if (database != null && server != null && server!!.isBound && !server!!.isClosed) {
            try {
                server!!.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        database?.destroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == null) {
            // ignore
        } else if (intent.action == START_FOREGROUND_ACTION) {
            Log.d(this, "Received Start Foreground Intent")
            showNotification()
        } else if (intent.action == STOP_FOREGROUND_ACTION) {
            Log.d(this, "Received Stop Foreground Intent")
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION)
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleClient(client: Socket) {
        val clientPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val ownSecretKey = database!!.settings.secretKey
        val ownPublicKey = database!!.settings.publicKey
        try {
            val pw = PacketWriter(client)
            val pr = PacketReader(client)
            var contact: Contact? = null
            val remote_address = client.remoteSocketAddress as InetSocketAddress
            Log.d(this, "incoming connection from $remote_address")
            while (true) {
                val request = pr.readMessage() ?: break
                val decrypted = decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey)
                if (decrypted == null) {
                    Log.d(this, "decryption failed")
                    break
                }
                if (contact == null) {
                    for (c in database!!.contacts.contactList) {
                        if (Arrays.equals(c.publicKey, clientPublicKey)) {
                            contact = c
                        }
                    }
                    if (contact == null && database!!.settings.blockUnknown) {
                        if (currentCall != null) {
                            Log.d(this, "block unknown contact => decline")
                            currentCall!!.decline()
                        }
                        break
                    }

                    if (contact != null && contact.blocked) {
                        if (currentCall != null) {
                            Log.d(this, "blocked contact => decline")
                            currentCall!!.decline()
                        }
                        break
                    }
                    if (contact == null) {
                        // unknown caller
                        contact = Contact("", clientPublicKey.clone(), ArrayList())
                    }
                }
                // suspicious change of identity in during connection...
                if (!contact.publicKey.contentEquals(clientPublicKey)) {
                    Log.d(this, "suspicious change of key")
                    continue
                }
                // remember last good address (the outgoing port is random and not the server port)
                contact.lastWorkingAddress = InetSocketAddress(remote_address.address, serverPort)

                val obj = JSONObject(decrypted)
                val action = obj.optString("action", "")
                when (action) {
                    "call" -> {
                        // someone calls us
                        Log.d(this, "call...")
                        val offer = obj.getString("offer")
                        currentCall = RTCCall(this, MainBinder(), contact, client, offer)
                        // respond that we accept the call
                        val encrypted = encryptMessage(
                            "{\"action\":\"ringing\"}",
                            contact.publicKey,
                            ownPublicKey,
                            ownSecretKey
                        )
                        pw.writeMessage(encrypted!!)
                        val intent = Intent(this, CallActivity::class.java)
                        intent.action = "ACTION_INCOMING_CALL"
                        intent.putExtra("EXTRA_CONTACT", contact)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        return
                    }
                    "ping" -> {
                        Log.d(this, "ping...")
                        // someone wants to know if we are online
                        setClientState(contact)
                        val encrypted = encryptMessage(
                            "{\"action\":\"pong\"}",
                            contact.publicKey,
                            ownPublicKey,
                            ownSecretKey
                        )
                        pw.writeMessage(encrypted!!)
                    }
                    "status_change" -> {
                        if (obj.optString("status", "") == "offline") {
                            setClientState(contact)
                        } else {
                            Log.d(this, "Received unknown status_change: " + obj.getString("status"))
                        }
                    }
                }
            }
            Log.d(this, "client disconnected")
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("call_declined"))
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(this, "client disconnected (exception)")
            if (currentCall != null) {
                currentCall!!.decline()
            }
        }
        // zero out key
        Arrays.fill(clientPublicKey, 0.toByte())
    }

    private fun setClientState(contact: Contact) {
        contact.state = Contact.State.ONLINE
    }

    override fun run() {
        try {
            // wait until database is ready
            while (database == null && run) {
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    break
                }
            }
            server = ServerSocket(serverPort)
            while (run) {
                try {
                    val socket = server!!.accept()
                    Thread { handleClient(socket) }.start()
                } catch (e: IOException) {
                    // ignore
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Handler(mainLooper).post { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            stopSelf()
            return
        }
    }

    /*
    * Allows communication between MainService and other objects
    */
    inner class MainBinder : Binder() {
        fun getService(): MainService {
            return this@MainService
        }

        fun getDatabase(): Database? {
            return this@MainService.database
        }

        fun getSettings(): Settings {
            return this@MainService.database!!.settings
        }

        fun getContacts(): Contacts {
            return this@MainService.database!!.contacts
        }

        fun getEvents(): Events {
            return this@MainService.database!!.events
        }

        fun getCurrentCall(): RTCCall? {
            return currentCall
        }

        fun addContact(contact: Contact) {
            database!!.contacts.addContact(contact)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_contact_list"))
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }

        fun deleteContact(pubKey: ByteArray) {
            database!!.contacts.deleteContact(pubKey)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_contact_list"))
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }

        fun shutdown() {
            this@MainService.stopSelf()
        }

        fun pingContacts() {
            val settings = getSettings()
            val contactList = getContacts().contactList
            Thread(
                PingRunnable(
                    this@MainService,
                    contactList,
                    settings.publicKey,
                    settings.secretKey
                )
            ).start()
        }

        fun saveDatabase() {
            this@MainService.saveDatabase()
        }

        internal fun addEvent(contact: Contact, type: Event.Type) {
            getEvents().addEvent(contact, type)
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }

        fun clearEvents() {
            getEvents().clearEvents()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }
    }

    internal inner class PingRunnable(
        var context: Context,
        private val contacts: List<Contact>,
        var ownPublicKey: ByteArray?,
        var ownSecretKey: ByteArray?,
    ) : Runnable {
        val binder = MainBinder()
        private fun setState(publicKey: ByteArray, state: Contact.State) {
            val contact = binder.getContacts().getContactByPublicKey(publicKey)
            if (contact != null) {
                contact.state = state
            }
        }

        override fun run() {
            for (contact in contacts) {
                var socket: Socket? = null
                val publicKey = contact.publicKey
                try {
                    socket = contact.createSocket()
                    if (socket == null) {
                        setState(publicKey, Contact.State.OFFLINE)
                        continue
                    }
                    val pw = PacketWriter(socket)
                    val pr = PacketReader(socket)
                    Log.d(this, "send ping to ${contact.name}")
                    val encrypted = encryptMessage(
                        "{\"action\":\"ping\"}",
                        publicKey,
                        ownPublicKey!!,
                        ownSecretKey
                    )
                    if (encrypted == null) {
                        socket.close()
                        continue
                    }
                    pw.writeMessage(encrypted)
                    val request = pr.readMessage()
                    if (request == null) {
                        socket.close()
                        continue
                    }
                    val decrypted = decryptMessage(request, publicKey, ownPublicKey, ownSecretKey)
                    if (decrypted == null) {
                        Log.d(this, "decryption failed")
                        socket.close()
                        continue
                    }
                    val obj = JSONObject(decrypted)
                    val action = obj.optString("action", "")
                    if (action == "pong") {
                        Log.d(this, "got pong")
                        setState(publicKey, Contact.State.ONLINE)
                    }
                    socket.close()
                } catch (e: Exception) {
                    setState(publicKey, Contact.State.OFFLINE)
                    if (socket != null) {
                        try {
                            socket.close()
                        } catch (ee: Exception) {
                            // ignore
                        }
                    }
                    e.printStackTrace()
                }
            }

            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("refresh_contact_list"))
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("refresh_event_list"))
        }

    }

    override fun onBind(intent: Intent): IBinder? {
        return MainBinder()
    }

    companion object {
        const val serverPort = 10001
        const val START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION"
        const val STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION"
        private var server: ServerSocket? = null
        fun start(ctx: Context?) {
            val startIntent = Intent(ctx, MainService::class.java)
            startIntent.action = START_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx!!, startIntent)
        }

        fun stop(ctx: Context) {
            val stopIntent = Intent(ctx, MainService::class.java)
            stopIntent.action = STOP_FOREGROUND_ACTION
            ctx.startService(stopIntent)
        }
    }
}