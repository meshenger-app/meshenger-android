package d.d.meshenger

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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.Utils.readInternalFile
import d.d.meshenger.Utils.writeInternalFile
import d.d.meshenger.call.CallActivity
import d.d.meshenger.call.DirectRTCClient
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.*

//TODO(IoDevBlue): Mock is active here
class MainService: Service(), Runnable {

    var database: Database? = null
    private var firstStart = false
    private var databasePath = ""
    var databasePassword = ""
    private var server: ServerSocket? = null
    @Volatile
    private var run = true
    private val mBinder: IBinder = LocalBinder()
    private var notificationBuilder: NotificationCompat.Builder? = null

    companion object{
        private const val TAG = "MainService"
        const val serverPort = 10001
        private const val NOTIFICATON_ID = 42
        const val START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION"
        const val STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION"

        private val currentCall: DirectRTCClient? = null
        private val currentCallLock: Any = Any()

        var instance: MainService? = null


        fun start(ctx: Context) {
            val startIntent = Intent(ctx, MainService::class.java)
            startIntent.action = START_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx, startIntent)
        }

        fun stop(ctx: Context) {
            val stopIntent = Intent(ctx, MainService::class.java)
            stopIntent.action = STOP_FOREGROUND_ACTION
            ContextCompat.startForegroundService(ctx, stopIntent)
        }
    }

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private val mChangingConfiguration = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        instance = this
        databasePath = this.filesDir.toString() + "/database.bin"
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(eventsChangedReceiver, IntentFilter("events_changed"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(contactsChangedReceiver, IntentFilter("contacts_changed"))

        // handle incoming connections
        Thread(this).start()
    }

    fun loadDatabase() {
        try {
            if (File(databasePath).exists()) {
                // open existing database
                val data = readInternalFile(databasePath)
                database = Database.fromData(data, databasePassword)
                firstStart = false
            } else {
                // create new database
                database = Database()
                firstStart = true
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun replaceDatabase(database: Database?) {

        database?.let {
            if (this.database == null) {
                this.database = database
            } else {
                this.database = database
                saveDatabase()
            }
        }
    }

    fun isFirstStart(): Boolean = firstStart

    fun getSettings(): Settings = database?.settings!!

    fun getContacts(): Contacts = database?.contacts!!

    fun getEvents(): Events = database?.events!!

    fun saveDatabase() {
        try {
            val data: ByteArray? = database?.let { Database.toData(it, databasePassword) }
            writeInternalFile(databasePath, data)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        run = false
        LocalBroadcastManager.getInstance(this).unregisterReceiver(eventsChangedReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsChangedReceiver)

        database?.let{
            // The database might be null here if no correct
            // database password was supplied to open it.
            try {
                val data: ByteArray = Database.toData(it, databasePassword)
                writeInternalFile(databasePath, data)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            // shutdown listening socket and say goodbye
            server?.apply {
                if (this.isBound && !this.isClosed) {
                    try {
                        server!!.close()
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // zero keys from memory
            it.onDestroy()
        }

    }

    private fun showNotification() {
        val channelId = "meshenger_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "Meshenger Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            chan.apply {
                this.lightColor = Color.RED
                this.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }

        // start MainActivity
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE) //TODO(IODevBlue): 0? Lint error
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
        startForeground(NOTIFICATON_ID, notificationBuilder!!.build())


        // update notification, since we want do display an updated "xxx ago"
        val t = Timer()
        val tt: TimerTask = object : TimerTask() {
            override fun run() {
                Log.d(TAG, "trigger notification update")
                updateMissedCallsNotification()
            }
        }
        t.scheduleAtFixedRate(tt, 1000, 10 * 1000) // every 10s
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // ignore
        } else if (intent.action == START_FOREGROUND_ACTION) {
            Log.d(TAG, "Received Start Foreground Intent")
            showNotification()
        } else if (intent.action == STOP_FOREGROUND_ACTION) {
            Log.d(TAG, "Received Stop Foreground Intent")
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATON_ID)
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // runs in a thread
    override fun run() {
        try {
            // wait until database is ready
            while (this.database == null && this.run) {
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    break
                }
            }
            server = ServerSocket(serverPort)
//            val binder: LocalBinder? = onBind(null) as LocalBinder? //unused variable
            while (this.run) {
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
        } catch (e: Exception) {
            e.printStackTrace()
            Handler(mainLooper).post { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            stopSelf()
            return
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
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




    private val eventsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "received events_changed")
            updateMissedCallsNotification()
            saveDatabase()
        }
    }

    private val contactsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "received contacts_changed")
            saveDatabase()
        }
    }

    private fun updateMissedCallsNotification() {
        if (notificationBuilder == null || database == null) {
            return
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val missedCalls: List<Event> = getEvents().getMissedCalls()
        if (missedCalls.isEmpty()) {
            notificationBuilder!!.setContentText(
                resources.getText(R.string.listen_for_incoming_calls)
            )
        } else {
            val lastCall: Event = missedCalls[missedCalls.size - 1]
            notificationBuilder!!.setContentText(
                missedCalls.size.toString() + " missed calls"
                        + " - "
                        + DateUtils.getRelativeTimeSpanString(
                    lastCall.date.time,
                    System.currentTimeMillis(),
                    0L,
                    DateUtils.FORMAT_ABBREV_ALL
                )
            )
        }
        manager.notify(NOTIFICATON_ID, notificationBuilder!!.build())
    }
}