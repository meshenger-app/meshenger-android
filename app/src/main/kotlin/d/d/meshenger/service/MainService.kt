package d.d.meshenger.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.text.format.DateUtils
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.R
import d.d.meshenger.model.Settings
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Utils.readInternalFile
import d.d.meshenger.utils.Utils.writeInternalFile
import d.d.meshenger.activity.MainActivity
import d.d.meshenger.activity.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.model.Contacts
import d.d.meshenger.model.Database
import d.d.meshenger.model.Events
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class MainService: Service(), Runnable {

    companion object {
        private const val TAG = "MainService"
        private val currentCall: DirectRTCClient? = null
        private val currentCallLock = Any()
        var instance: MainService? = null

        const val serverPort = 10001
        private const val NOTIFICATION_ID = 42
        const val START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION"
        const val STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION"

        fun start(ctx: Context?) {
            val startIntent = Intent(ctx, MainService::class.java)
            startIntent.action = START_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx!!, startIntent)
        }

        fun stop(ctx: Context?) {
            val stopIntent = Intent(ctx, MainService::class.java)
            stopIntent.action = STOP_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx!!, stopIntent)
        }

    }

    var database: Database? = null
    var first_start = false
    private var database_path = ""
    var database_password = ""
    private var server: ServerSocket? = null

    @Volatile
    private var run = true

    private val mBinder: IBinder = LocalBinder()
    private var notificationBuilder: NotificationCompat.Builder? = null

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private val mChangingConfiguration = false


    override fun onCreate() {
        super.onCreate()
        d(TAG, "onCreate")
        instance = this
        database_path = this.filesDir.toString() + "/database.bin"
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(eventsChangedReceiver, IntentFilter("events_changed"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(contactsChangedReceiver, IntentFilter("contacts_changed"))

        // handle incoming connections
        Thread(this).start()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun replaceDatabase(database: Database?) {
        if (database != null) {
            if (this.database == null) {
                this.database = database
            } else {
                this.database = database
                saveDatabase()
            }
        }
    }


    fun getSettings(): Settings? {
        return database!!.settings
    }

    fun getContacts(): Contacts? {
        return database!!.contacts
    }

    fun getEvents(): Events? {
        return database!!.events
    }

    fun saveDatabase() {
        try {
            val data: ByteArray? = database?.let { Database.toData(it, database_password) }
            writeInternalFile(database_path, data)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        run = false
        LocalBroadcastManager.getInstance(this).unregisterReceiver(eventsChangedReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsChangedReceiver)

        // The database might be null here if no correct
        // database password was supplied to open it.
        if (database != null) {
            try {
                val data: ByteArray? = Database.toData(database!!, database_password)
                writeInternalFile(database_path, data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        // shutdown listening socket and say goodbye
        if (database != null && server != null && server!!.isBound && !server!!.isClosed) {
            try {
                server!!.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        database?.onDestroy()
    }

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
        val pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val mActivity = applicationContext
        notificationBuilder = NotificationCompat.Builder(mActivity, channelId) //.setOngoing(true)
            .setOnlyAlertOnce(true) // keep notification update from turning on the screen
            .setSmallIcon(R.drawable.ic_logo)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.logo_small))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentText(resources.getText(R.string.listen_for_incoming_calls))
            .setContentIntent(pendingNotificationIntent)
        //.setVisibility(VISIBILITY_PUBLIC);
        startForeground(NOTIFICATION_ID, notificationBuilder!!.build())


        // update notification, since we want do display an updated "xxx ago"
        val t = Timer()
        val tt: TimerTask = object : TimerTask() {
            override fun run() {
                d(TAG, "trigger notification update")
                updateMissedCallsNotification()
            }
        }
        t.scheduleAtFixedRate(tt, 1000, (10 * 1000).toLong()) // every 10s
    }

    private val eventsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            d(TAG, "received events_changed")
            updateMissedCallsNotification()
            saveDatabase()
        }
    }

    private val contactsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            d(TAG, "received contacts_changed")
            saveDatabase()
        }
    }

    private fun updateMissedCallsNotification() {
        if (notificationBuilder == null || database == null) {
            return
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val missedCalls = getEvents()!!.getMissedCalls()
        if (missedCalls.size == 0) {
            notificationBuilder!!.setContentText(
                resources.getText(R.string.listen_for_incoming_calls)
            )
        } else {
            val lastCall = missedCalls[missedCalls.size - 1]
            notificationBuilder!!.setContentText(
                missedCalls.size.toString() + " missed calls"
                        + " - "
                        + DateUtils.getRelativeTimeSpanString(
                    lastCall.date.time, System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL
                )
            )
        }
        manager.notify(NOTIFICATION_ID, notificationBuilder!!.build())
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // ignore
        } else if (intent.action == START_FOREGROUND_ACTION) {
            d(TAG, "Received Start Foreground Intent")
            showNotification()
        } else if (intent.action == STOP_FOREGROUND_ACTION) {
            d(TAG, "Received Stop Foreground Intent")
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // runs in a thread
    override fun run() {
        try {
            // wait until database is ready
            while (database == null && run) {
                try {
                    Thread.sleep(1000)
                } catch (e: java.lang.Exception) {
                    break
                }
            }
            server = ServerSocket(serverPort)
            val binder = onBind(null) as LocalBinder?
            while (run) {
                var socket: Socket? = null
                try {
                    socket = server!!.accept()
                    if (DirectRTCClient.createIncomingCall(socket)) {
                        val intent = Intent(this, CallActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    Thread.sleep(50) // mitigate DDOS attack
                } catch (e: IOException) {
                    if (socket != null) {
                        try {
                            socket.close()
                        } catch (_e: IOException) {
                            // ignore
                        }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Handler(mainLooper).post {
                Toast.makeText(
                    this,
                    e.message,
                    Toast.LENGTH_LONG
                ).show()
            }
            stopSelf()
            return
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        d(TAG, "onBind")
        return mBinder
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: MainService
            get() = this@MainService
    }
}