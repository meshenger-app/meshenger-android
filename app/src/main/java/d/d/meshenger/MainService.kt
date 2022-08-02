package d.d.meshenger

import android.app.*
import android.content.Context
import d.d.meshenger.Database.Companion.load
import d.d.meshenger.Database.Companion.store
import d.d.meshenger.Crypto.encryptMessage
import d.d.meshenger.Crypto.decryptMessage
import d.d.meshenger.Log.d
import d.d.meshenger.Database
import kotlin.jvm.Volatile
import d.d.meshenger.RTCCall
import d.d.meshenger.CallEvent
import d.d.meshenger.Contact
import d.d.meshenger.Crypto
import d.d.meshenger.PacketWriter
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.Handler
import org.libsodium.jni.Sodium
import d.d.meshenger.PacketReader
import d.d.meshenger.MainService
import org.json.JSONObject
import d.d.meshenger.MainService.MainBinder
import d.d.meshenger.CallActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.widget.Toast
import d.d.meshenger.MainService.PingRunnable
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class MainService : Service(), Runnable {
    var database: Database? = null
    var isFirstStart = false
    private var database_path = ""
    var databasePassword = ""

    @Volatile
    private var run = true
    private var currentCall: RTCCall? = null
    private var events: ArrayList<CallEvent>? = null
    override fun onCreate() {
        super.onCreate()
        database_path = this.filesDir.toString() + "/database.bin"

        // handle incoming connections
        Thread(this).start()
        events = ArrayList()
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
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
    private fun loadDatabase() {
        try {
            if (File(database_path).exists()) {
                // open existing database
                database = load(database_path, databasePassword)
                isFirstStart = false
            } else {
                // create new database
                database = Database()
                isFirstStart = true
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun saveDatabase() {
        try {
            store(database_path, database!!, databasePassword)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        run = false

        // The database might be null here if no correct
        // database password was supplied to open it.
        if (database != null) {
            try {
                store(database_path, database!!, databasePassword)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // shutdown listening socket and say goodbye
        if (database != null && server != null && server!!.isBound && !server!!.isClosed) {
            try {
                val ownPublicKey = database!!.settings.publicKey
                val ownSecretKey = database!!.settings.secretKey
                val message = "{\"action\": \"status_change\", \"status\", \"offline\"}"
                for (contact in database!!.contacts) {
                    if (contact.state === Contact.State.OFFLINE) {
                        continue
                    }
                    val encrypted =
                        encryptMessage(message, contact.publicKey, ownPublicKey!!, ownSecretKey)
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
                server!!.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (database != null) {
            // zero keys from memory
            database!!.onDestroy()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // ignore
        } else if (intent.action == START_FOREGROUND_ACTION) {
            log("Received Start Foreground Intent")
            showNotification()
        } else if (intent.action == STOP_FOREGROUND_ACTION) {
            log("Received Stop Foreground Intent")
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
            log("incoming connection from $remote_address")
            while (true) {
                val request = pr.readMessage() ?: break
                val decrypted = decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey)
                if (decrypted == null) {
                    log("decryption failed")
                    break
                }
                if (contact == null) {
                    for (c in database!!.contacts) {
                        if (Arrays.equals(c.publicKey, clientPublicKey)) {
                            contact = c
                        }
                    }
                    if (contact == null && database!!.settings.blockUnknown) {
                        if (currentCall != null) {
                            log("block unknown contact => decline")
                            currentCall!!.decline()
                        }
                        break
                    }
                    if (contact != null && contact.getBlocked()) {
                        if (currentCall != null) {
                            log("blocked contact => decline")
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
                if (!Arrays.equals(contact.publicKey, clientPublicKey)) {
                    log("suspicious change of key")
                    continue
                }

                // remember last good address (the outgoing port is random and not the server port)
                contact.setLastWorkingAddress(
                    InetSocketAddress(remote_address.address, serverPort)
                )
                val obj = JSONObject(decrypted)
                val action = obj.optString("action", "")
                when (action) {
                    "call" -> {

                        // someone calls us
                        log("call...")
                        val offer = obj.getString("offer")
                        currentCall = RTCCall(this, MainBinder(), contact, client, offer)

                        // respond that we accept the call
                        val encrypted = encryptMessage(
                            "{\"action\":\"ringing\"}",
                            contact.publicKey,
                            ownPublicKey!!,
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
                        log("ping...")
                        // someone wants to know if we are online
                        setClientState(contact, Contact.State.ONLINE)
                        val encrypted = encryptMessage(
                            "{\"action\":\"pong\"}",
                            contact.publicKey,
                            ownPublicKey!!,
                            ownSecretKey
                        )
                        pw.writeMessage(encrypted!!)
                    }
                    "status_change" -> {
                        if (obj.optString("status", "") == "offline") {
                            setClientState(contact, Contact.State.OFFLINE)
                        } else {
                            log("Received unknown status_change: " + obj.getString("status"))
                        }
                    }
                }
            }
            log("client disconnected")
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("call_declined"))
        } catch (e: Exception) {
            e.printStackTrace()
            log("client disconnected (exception)")
            if (currentCall != null) {
                currentCall!!.decline()
            }
        }

        // zero out key
        Arrays.fill(clientPublicKey, 0.toByte())
    }

    private fun setClientState(contact: Contact, state: Contact.State) {
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
        fun getService():MainService=this@MainService
        fun getCurrentCall(): RTCCall? {
            return currentCall
        }

        fun getContactByPublicKey(pubKey: ByteArray?): Contact? {
            for (contact in database!!.contacts) {
                if (Arrays.equals(contact.publicKey, pubKey)) {
                    return contact
                }
            }
            return null
        }

        fun getContactByName(name: String): Contact? {
            for (contact in database!!.contacts) {
                if (contact.getName() == name) {
                    return contact
                }
            }
            return null
        }

        fun addContact(contact: Contact?) {
            database!!.addContact(contact!!)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_contact_list"))
        }

        fun deleteContact(pubKey: ByteArray?) {
            database!!.deleteContact(pubKey)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_contact_list"))
        }

        fun shutdown() {
            this@MainService.stopSelf()
        }

        fun loadDatabase() {
            this@MainService.loadDatabase()
        }

        fun replaceDatabase(db: Database?) {
            if (db != null) {
                if (database == null) {
                    database = db
                } else {
                    database = db
                    saveDatabase()
                }
            }
        }

        fun pingContacts() {
            Thread(
                PingRunnable(
                    this@MainService,
                    contactsCopy,
                    settings.publicKey,
                    settings.secretKey
                )
            ).start()
        }

        fun saveDatabase() {
            this@MainService.saveDatabase()
        }

        val settings: Settings
            get() = database!!.settings

        // return a cloned list
        val contactsCopy: List<Contact>
            get() = ArrayList(database!!.contacts)

        internal fun addCallEvent(contact: Contact, type: CallEvent.Type?) {
            val last_working = contact.lastWorkingAddress
            events!!.add(
                CallEvent(
                    contact.publicKey!!,
                    last_working?.address!!,
                    type!!
                )
            )
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }

        // return a cloned list
        internal val eventsCopy: List<CallEvent>
            get() = ArrayList(events)

        fun clearEvents() {
            events!!.clear()
            LocalBroadcastManager.getInstance(this@MainService)
                .sendBroadcast(Intent("refresh_event_list"))
        }
    }

    internal inner class PingRunnable(
        var context: Context,
        private val contacts: List<Contact>,
        var ownPublicKey: ByteArray?,
        var ownSecretKey: ByteArray?
    ) : Runnable {
        var binder: MainBinder
        private fun setState(publicKey: ByteArray?, state: Contact.State) {
            val contact = binder.getContactByPublicKey(publicKey)
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
                    log("send ping to " + contact.getName())
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
                        log("decryption failed")
                        socket.close()
                        continue
                    }
                    val obj = JSONObject(decrypted)
                    val action = obj.optString("action", "")
                    if (action == "pong") {
                        log("got pong")
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
            log("send refresh_contact_list")
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("refresh_contact_list"))
        }

        init {
            binder = MainBinder()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return MainBinder()
    }

    private fun log(data: String) {
        d(this, data)
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