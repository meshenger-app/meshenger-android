package org.rivchain.cuplink

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class MainService: Service(), Runnable {

    var isFirstStart = false
        get() = field
        private set
    private var database_path = ""
    var databasePassword = ""
        get() = field
        set(password) {
            databasePassword = password
        }
    private var server: ServerSocket? = null

    @Volatile
    private var run = true
    private var events: ArrayList<CallEvent>? = null
    override fun onCreate() {
        super.onCreate()
        database_path = this.filesDir.toString() + "/database.bin"

        // handle incoming connections
        Thread(this).start()
        events = ArrayList()
    }

    private fun loadDatabase() {
        try {
            if (File(database_path).exists()) {
                // open existing database
                database = Database.load(database_path, databasePassword)
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
            Database.store(database_path, database!!, databasePassword)
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
                Database.store(database_path, database!!, databasePassword)
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
                    val encrypted = Crypto.encryptMessage(message, contact.publicKey, ownPublicKey, ownSecretKey)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun handleClient(client: Socket) {
        val ownSecretKey = database!!.settings.secretKey
        val ownPublicKey = database!!.settings.publicKey
        try {
            val pw = PacketWriter(client)
            val pr = PacketReader(client)
            var contact: Contact? = null
            val remote_address = client.remoteSocketAddress as InetSocketAddress
            val clientPublicKey = remote_address.address.address
            log("incoming connection from $remote_address")
            while (true) {
                val request = pr.readMessage() ?: break
                val decrypted = Crypto.decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey)
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
                        val encrypted = Crypto.encryptMessage("{\"action\":\"ringing\"}", contact.publicKey, ownPublicKey, ownSecretKey)
                        pw.writeMessage(encrypted)
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
                        val encrypted = Crypto.encryptMessage("{\"action\":\"pong\"}", contact.publicKey, ownPublicKey, ownSecretKey)
                        pw.writeMessage(encrypted)
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

    override fun onBind(intent: Intent): IBinder? {
        return MainBinder()
    }

    private fun log(data: String) {
        Log.d(this, data)
    }

    /*
     * Allows communication between MainService and other objects
     */
    inner class MainBinder : Binder() {

        fun getDatabase(): Database? {
            return database
        }

        fun getCurrentCall(): RTCCall? {
            return currentCall
        }

        fun getDatabasePassword(): String? {
            return this@MainService.databasePassword
        }

        fun setDatabasePassword(databasePassword: String) {
            this@MainService.databasePassword = databasePassword
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
                if (contact.name == name) {
                    return contact
                }
            }
            return null
        }

        fun addContact(contact: Contact?) {
            database!!.addContact(contact!!)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService).sendBroadcast(Intent("refresh_contact_list"))
        }

        fun deleteContact(pubKey: ByteArray?) {
            database!!.deleteContact(pubKey)
            saveDatabase()
            LocalBroadcastManager.getInstance(this@MainService).sendBroadcast(Intent("refresh_contact_list"))
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
            Thread(PingRunnable(
                    this@MainService,
                    contactsCopy,
                    settings.publicKey!!,
                    settings.secretKey!!)
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

        fun addCallEvent(contact: Contact, type: CallEvent.Type?) {
            events!!.add(CallEvent(
                contact.publicKey!!,
                contact.allSocketAddresses[0].address!!,
                type!!))
            LocalBroadcastManager.getInstance(this@MainService).sendBroadcast(Intent("refresh_event_list"))
        }

        // return a cloned list
        val eventsCopy: List<CallEvent>
            get() = ArrayList(events)

        fun clearEvents() {
            events!!.clear()
            LocalBroadcastManager.getInstance(this@MainService).sendBroadcast(Intent("refresh_event_list"))
        }

        fun isFirstStart(): Boolean {
            return this@MainService.isFirstStart
        }
    }

    internal inner class PingRunnable(var context: Context, private val contacts: List<Contact>, var ownPublicKey: ByteArray, var ownSecretKey: ByteArray) : Runnable {
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
                    log("send ping to " + contact.name)
                    val encrypted = Crypto.encryptMessage("{\"action\":\"ping\"}", publicKey, ownPublicKey, ownSecretKey)
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
                    val decrypted = Crypto.decryptMessage(request, publicKey, ownPublicKey, ownSecretKey)
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

    companion object {
        const val serverPort = 10001

        var currentCall: RTCCall? = null

        var database: Database? = null
            get() = field
            private set
    }
}