package org.rivchain.cuplink.call

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.rivchain.cuplink.CallActivity
import org.rivchain.cuplink.CallService
import org.rivchain.cuplink.Crypto
import org.rivchain.cuplink.MainService
import org.rivchain.cuplink.R
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.util.NetworkUtils
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.Utils
import java.io.IOException
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

abstract class RTCPeerConnection(
    var service: MainService,
    protected var contact: Contact,
    protected var commSocket: Socket?
) {
    protected var state = CallState.WAITING
    protected var callActivity: RTCCall.CallContext? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var mediaPlayer: MediaPlayer? = null

    fun playTone(state: CallState) {
        stopTone() // Stop any currently playing tone

        val toneResId: Int = when (state) {
            CallState.WAITING -> R.raw.waiting
            CallState.CONNECTING -> R.raw.waiting
            CallState.RINGING -> R.raw.ringing
            CallState.DISMISSED -> R.raw.stop
            CallState.ENDED -> R.raw.ended
            CallState.CONNECTED -> R.raw.connected
            CallState.ERROR_COMMUNICATION -> R.raw.stop
            CallState.ERROR_AUTHENTICATION -> R.raw.stop
            CallState.ERROR_DECRYPTION -> R.raw.stop
            CallState.ERROR_CONNECT_PORT -> R.raw.stop
            CallState.ERROR_UNKNOWN_HOST -> R.raw.stop
            CallState.ERROR_NO_CONNECTION -> R.raw.stop
            CallState.ERROR_NO_ADDRESSES -> R.raw.stop
            CallState.ERROR_NO_NETWORK -> R.raw.stop
        }

        mediaPlayer = MediaPlayer.create(service, toneResId)
        mediaPlayer?.isLooping = state == CallState.RINGING
        mediaPlayer?.setVolume(0.5f,0.5f)
        mediaPlayer?.start()
    }

    private fun stopTone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    abstract fun reportStateChange(state: CallState)
    abstract fun handleAnswer(remoteDesc: String)

    protected fun cleanupRTCPeerConnection() {
        execute {
            Log.d(this, "cleanup() executor start")
            try {
                Log.d(this, "cleanup() close socket")
                commSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Log.d(this, "cleanup() executor end")
            stopTone()
        }

        // wait for tasks to finish
        executor.shutdown()
        executor.awaitTermination(4L, TimeUnit.SECONDS)
    }

    fun setCallContext(activity: RTCCall.CallContext?) {
        this.callActivity = activity
    }

    private fun sendOnSocket(message: String): Boolean {
        Log.d(this, "sendOnSocket() message=$message")

        Utils.checkIsNotOnMainThread()

        val socket = commSocket
        if (socket == null || socket.isClosed) {
            return false
        }

        //val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = service.getSettings()
        val ownSecretKey = settings.secretKey
        val ownPublicKey = settings.publicKey

        val encrypted = Crypto.encryptMessage(
            message,
            contact.publicKey,
            ownPublicKey,
            ownSecretKey
        )

        if (encrypted == null) {
            reportStateChange(CallState.ERROR_COMMUNICATION)
            return false
        }

        val pw = PacketWriter(socket)
        pw.writeMessage(encrypted)

        return true
    }

    protected fun createOutgoingCall(contact: Contact, offer: String) {
        Log.d(this, "createOutgoingCall()")
        Thread {
            try {
                createOutgoingCallInternal(contact, offer)
            } catch (e: Exception) {
                e.printStackTrace()
                reportStateChange(CallState.ERROR_COMMUNICATION)
            }
        }.start()
    }

    private fun createOutgoingCallInternal(contact: Contact, offer: String) {
        Log.d(this, "createOutgoingCallInternal()")

        val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = service.getSettings()
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey

        val socket = createCommSocket(contact) ?: return

        callActivity?.onRemoteAddressChange(socket.remoteSocketAddress as InetSocketAddress, true)
        commSocket = socket

        val remoteAddress = socket.remoteSocketAddress as InetSocketAddress

        Log.d(this, "createOutgoingCallInternal() outgoing call from remote address: $remoteAddress")

        val pr = PacketReader(socket)
        reportStateChange(CallState.CONNECTING)
        run {
            Log.d(this, "createOutgoingCallInternal() outgoing call: send call")
            val obj = JSONObject()
            obj.put("action", "call")
            obj.put("offer", offer) // WebRTC offer!
            val encrypted = Crypto.encryptMessage(
                obj.toString(),
                contact.publicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (encrypted == null) {
                reportStateChange(CallState.ERROR_COMMUNICATION)
                return
            }

            val pw = PacketWriter(socket)
            pw.writeMessage(encrypted)
        }

        run {
            Log.d(this, "createOutgoingCallInternal() outgoing call: expect ringing")
            val response = pr.readMessage()
            if (response == null) {
                reportStateChange(CallState.ERROR_COMMUNICATION)
                return
            }

            val decrypted = Crypto.decryptMessage(
                response,
                otherPublicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (decrypted == null) {
                reportStateChange(CallState.ERROR_DECRYPTION)
                return
            }

            if (!contact.publicKey.contentEquals(otherPublicKey)) {
                reportStateChange(CallState.ERROR_AUTHENTICATION)
                return
            }

            val obj = JSONObject(decrypted)
            val action = obj.optString("action")
            if (action == "ringing") {
                Log.d(this, "createOutgoingCallInternal() got ringing")
                reportStateChange(CallState.RINGING)
            } else if (action == "dismissed") {
                Log.d(this, "createOutgoingCallInternal() got dismissed")
                reportStateChange(CallState.DISMISSED)
                return
            } else {
                Log.d(this, "createOutgoingCallInternal() unexpected action: $action")
                reportStateChange(CallState.ERROR_COMMUNICATION)
                return
            }
        }

        run {
            // remember latest working address and set state
            val workingAddress = InetSocketAddress(remoteAddress.address, MainService.serverPort)
            val storedContact = service.getContacts().getContactByPublicKey(contact.publicKey)
            if (storedContact != null) {
                storedContact.lastWorkingAddress = workingAddress
            } else {
                contact.lastWorkingAddress = workingAddress
            }
        }

        var lastKeepAlive = System.currentTimeMillis()

        // send keep alive to detect a broken connection
        val writeExecutor = Executors.newSingleThreadExecutor()
        writeExecutor?.execute {
            val pw = PacketWriter(socket)

            Log.d(this, "createOutgoingCallInternal() start to send keep_alive")
            while (!socket.isClosed) {
                try {
                    val obj = JSONObject()
                    obj.put("action", "keep_alive")
                    val encrypted = Crypto.encryptMessage(
                        obj.toString(),
                        contact.publicKey,
                        ownPublicKey,
                        ownSecretKey
                    ) ?: break
                    pw.writeMessage(encrypted)
                    Thread.sleep(SOCKET_TIMEOUT_MS / 2)
                    if ((System.currentTimeMillis() - lastKeepAlive) > SOCKET_TIMEOUT_MS) {
                        Log.w(this, "createOutgoingCallInternal() keep_alive timeout => close socket")
                        closeSocket(socket)
                    }

                } catch (e: Exception) {
                    Log.w(this, "createOutgoingCallInternal() got $e => close socket")
                    closeSocket(socket)
                    break
                }
            }
            Log.d(this, "createOutgoingCallInternal() stop to send keep_alive")
        }

        while (!socket.isClosed) {
            Log.d(this, "createOutgoingCallInternal() expect connected/dismissed")
            val response = pr.readMessage()
            if (response == null) {
                Thread.sleep(SOCKET_TIMEOUT_MS / 10)
                Log.d(this, "createOutgoingCallInternal() response is null")
                continue
            }

            val decrypted = Crypto.decryptMessage(
                response,
                otherPublicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (decrypted == null) {
                reportStateChange(CallState.ERROR_DECRYPTION)
                return
            }

            if (!contact.publicKey.contentEquals(otherPublicKey)) {
                reportStateChange(CallState.ERROR_AUTHENTICATION)
                return
            }

            val obj = JSONObject(decrypted)
            val action = obj.getString("action")
            if (action == "connected") {
                Log.d(this, "createOutgoingCallInternal() connected")
                reportStateChange(CallState.CONNECTED)
                val answer = obj.optString("answer")
                if (answer.isNotEmpty()) {
                    handleAnswer(answer)
                } else {
                    reportStateChange(CallState.ERROR_COMMUNICATION)
                }
                break
            } else if (action == "dismissed") {
                Log.d(this, "createOutgoingCallInternal() dismissed")
                reportStateChange(CallState.DISMISSED)
                break
            } else if (action == "keep_alive") {
                Log.d(this, "createOutgoingCallInternal() keep_alive")
                lastKeepAlive = System.currentTimeMillis()
                continue
            } else {
                Log.e(this, "createOutgoingCallInternal() unknown action reply $action")
                reportStateChange(CallState.ERROR_COMMUNICATION)
                break
            }
        }

        Log.d(this, "createOutgoingCallInternal() close socket")
        closeSocket(socket)
        //Log.d(this, "createOutgoingCallInternal() dataChannel is null: ${dataChannel == null}")

        Log.d(this, "createOutgoingCallInternal() wait for writeExecutor")
        writeExecutor.shutdown()
        writeExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)

        // detect broken initial connection
        if (isCallInit(state) && socket.isClosed) {
            Log.e(this, "createOutgoingCallInternal() call (state=$state) is not connected and socket is closed")
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }

        Log.d(this, "createOutgoingCallInternal() finished")
    }

    // Continue listening for socket message.
    // Must run on separate thread!
    fun continueOnIncomingSocket() {
        Log.d(this, "continueOnIncomingSocket()")
        Utils.checkIsNotOnMainThread()

        val socket = commSocket ?: throw IllegalStateException("commSocket not expected to be null")

        val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = service.getSettings()
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey

        if (!socket.isClosed) {
            Log.d(this, "continueOnIncomingSocket() expected dismissed/keep_alive")
            val pr = PacketReader(socket)

            var lastKeepAlive = System.currentTimeMillis()

            // send keep alive to detect a broken connection
            val writeExecutor = Executors.newSingleThreadExecutor()
            writeExecutor?.execute {
                val pw = PacketWriter(socket)

                Log.d(this, "continueOnIncomingSocket() start to send keep_alive")
                while (!socket.isClosed) {
                    try {
                        val obj = JSONObject()
                        obj.put("action", "keep_alive")
                        val encrypted = Crypto.encryptMessage(
                            obj.toString(),
                            contact.publicKey,
                            ownPublicKey,
                            ownSecretKey
                        ) ?: break
                        pw.writeMessage(encrypted)
                        Thread.sleep(SOCKET_TIMEOUT_MS / 2)
                        if ((System.currentTimeMillis() - lastKeepAlive) > SOCKET_TIMEOUT_MS) {
                            Log.w(this, "continueOnIncomingSocket() keep_alive timeout => close socket")
                            closeSocket(socket)
                        }
                    } catch (e: Exception) {
                        Log.w(this, "continueOnIncomingSocket() got $e => close socket")
                        closeSocket(socket)
                        break
                    }
                }
            }

            while (!socket.isClosed) {
                val response = pr.readMessage()
                if (response == null) {
                    Thread.sleep(SOCKET_TIMEOUT_MS / 10)
                    Log.d(this, "continueOnIncomingSocket() response is null")
                    continue
                }

                val decrypted = Crypto.decryptMessage(
                    response,
                    otherPublicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (decrypted == null) {
                    reportStateChange(CallState.ERROR_DECRYPTION)
                    break
                }

                val obj = JSONObject(decrypted)
                val action = obj.optString("action")
                if (action == "dismissed") {
                    Log.d(this, "continueOnIncomingSocket() received dismissed")
                    reportStateChange(CallState.DISMISSED)
                    declineOwnCall()
                    break
                } else if (action == "keep_alive") {
                    // ignore, keeps the socket alive
                    lastKeepAlive = System.currentTimeMillis()
                } else {
                    Log.e(this, "continueOnIncomingSocket() received unknown action reply: $action")
                    reportStateChange(CallState.ERROR_COMMUNICATION)
                    break
                }
            }


            Log.d(this, "continueOnIncomingSocket() wait for writeExecutor")
            writeExecutor.shutdown()
            writeExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS)

            //Log.d(this, "continueOnIncomingSocket() dataChannel is null: ${dataChannel == null}")
            closeSocket(socket)
        }

        // detect broken initial connection
        if (isCallInit(state) && socket.isClosed) {
            Log.e(this, "continueOnIncomingSocket() call (state=$state) is not connected and socket is closed")
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }

        Log.d(this, "continueOnIncomingSocket() finished")
    }

    protected fun declineOwnCall(){
        // decline own call. call session has been started yet.
        if(!CallActivity.isCallInProgress) {
            Log.d(this, "decline() send broadcast to receiver")
            PendingIntent.getBroadcast(
                this.service,
                0,
                Intent().apply {
                    setAction(CallService.DECLINE_CALL_ACTION)
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    PendingIntent.FLAG_IMMUTABLE
                else
                    0
            ).send()
        }
    }

    protected fun execute(r: Runnable) {
        try {
            executor.execute(r)
        } catch (e: RejectedExecutionException) {
            e.printStackTrace()
            // can happen when the executor has shut down
            Log.w(this, "execute() catched RejectedExecutionException")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.w(this, "execute() catched $e")
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }
    }

    // send over initial socket when the call is not yet established
    private fun declineInternal() {
        Log.d(this, "declineInternal()")
        // send decline over initial socket
        val socket = commSocket
        if (socket != null && !socket.isClosed) {
            val pw = PacketWriter(socket)
            val settings = service.getSettings()
            val ownPublicKey = settings.publicKey
            val ownSecretKey = settings.secretKey

            val encrypted = Crypto.encryptMessage(
                "{\"action\":\"dismissed\"}",
                contact.publicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (encrypted == null) {
                reportStateChange(CallState.ERROR_COMMUNICATION)
            } else {
                try {
                    Log.d(this, "declineInternal() write dismissed message to socket")
                    pw.writeMessage(encrypted)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                reportStateChange(CallState.DISMISSED)
            }
        } else {
            reportStateChange(CallState.DISMISSED)
        }
    }

    fun decline() {
        Log.d(this, "decline()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "decline() executor start")
            declineInternal()
            Log.d(this, "decline() executor end")
        }
    }

    private fun createCommSocket(contact: Contact): Socket? {
        Log.d(this, "createCommSocket()")

        Utils.checkIsNotOnMainThread()

        val settings = service.getSettings()
        val useNeighborTable = settings.useNeighborTable
        val connectTimeout = settings.connectTimeout
        val connectRetries = settings.connectRetries

        var unknownHostException = false
        var connectException = false
        var socketTimeoutException = false
        var exception = false

        val allGeneratedAddresses = NetworkUtils.getAllSocketAddresses(contact, useNeighborTable)
        Log.d(this, "createCommSocket() contact.addresses: ${contact.addresses}, allGeneratedAddresses: $allGeneratedAddresses")

        for (iteration in 0..max(0, min(connectRetries, 4))) {
            Log.d(this, "createCommSocket() loop number $iteration")

            for (address in allGeneratedAddresses) {
                callActivity?.onRemoteAddressChange(address, false)
                Log.d(this, "try address: $address")

                val socket = Socket()

                try {
                    socket.connect(address, connectTimeout)
                    reportStateChange(CallState.CONNECTING)
                    return socket
                } catch (e: SocketTimeoutException) {
                    // no connection
                    Log.d(this, "createCommSocket() socket has thrown SocketTimeoutException")
                    socketTimeoutException = true
                } catch (e: ConnectException) {
                    // device is online, but does not listen on the given port
                    Log.d(this, "createCommSocket() socket has thrown ConnectException")
                    connectException = true
                } catch (e: UnknownHostException) {
                    // hostname did not resolve
                    Log.d(this, "createCommSocket() socket has thrown UnknownHostException")
                    unknownHostException = true
                } catch (e: Exception) {
                    Log.d(this, "createCommSocket() socket has thrown Exception")
                    exception = true
                }

                closeSocket(socket)
            }
        }

        if (connectException) {
            reportStateChange(CallState.ERROR_CONNECT_PORT)
        } else if (unknownHostException) {
            reportStateChange(CallState.ERROR_UNKNOWN_HOST)
        } else if (exception) {
            reportStateChange(CallState.ERROR_COMMUNICATION)
        } else if (socketTimeoutException) {
            reportStateChange(CallState.ERROR_NO_CONNECTION)
        } else if (contact.addresses.isEmpty()) {
            reportStateChange(CallState.ERROR_NO_ADDRESSES)
        } else {
            // No addresses were generated.
            // This happens if MAC addresses were
            // used and no network is available.
            reportStateChange(CallState.ERROR_NO_NETWORK)
        }

        return null
    }

    enum class CallState {
        WAITING,
        CONNECTING,
        RINGING,
        CONNECTED,
        DISMISSED,
        ENDED,
        ERROR_AUTHENTICATION,
        ERROR_DECRYPTION,
        ERROR_CONNECT_PORT,
        ERROR_UNKNOWN_HOST,
        ERROR_COMMUNICATION,
        ERROR_NO_CONNECTION,
        ERROR_NO_ADDRESSES,
        ERROR_NO_NETWORK
    }

    // Not an error but also not established (CONNECTED)
    private fun isCallInit(state: CallState): Boolean {
        return when (state) {
            CallState.WAITING,
            CallState.CONNECTING,
            CallState.RINGING -> true
            else -> false
        }
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 25000L

        // used to pass incoming RTCCall to CallActiviy
        public var incomingRTCCall: RTCCall? = null

/*
        // for debug purposes
        private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

        private fun debugPacket(label: String, msg: ByteArray?) {
            if (msg != null) {
                Log.d(this, "$label: ${msg.size}, ${msg.toHex()}")
            } else {
                Log.d(this, "$label: message is null!")
            }
        }
*/

        fun closeSocket(socket: Socket?) {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createIncomingCall(service: MainService, socket: Socket) {
            Thread {
                try {
                    createIncomingCallInternal(service, socket)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //decline()
                }
            }.start()
        }

        private fun createIncomingCallInternal(service: MainService, socket: Socket) {
            Log.d(this, "createIncomingCallInternal()")

            val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
            val settings = service.getSettings()
            val blockUnknown = settings.blockUnknown
            val ownSecretKey = settings.secretKey
            val ownPublicKey = settings.publicKey

            val decline = {
                Log.d(this, "createIncomingCallInternal() declining...")

                try {
                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        otherPublicKey,
                        ownPublicKey,
                        ownSecretKey
                    )

                    if (encrypted != null) {
                        val pw = PacketWriter(socket)
                        pw.writeMessage(encrypted)
                    }

                    socket.close()
                } catch (e: Exception) {
                    closeSocket(socket)
                }
            }

            val remoteAddress = socket.remoteSocketAddress as InetSocketAddress
            val pw = PacketWriter(socket)
            val pr = PacketReader(socket)

            Log.d(this, "createIncomingCallInternal() incoming peerConnection from $remoteAddress")

            val request = pr.readMessage()
            if (request == null) {
                Log.d(this, "createIncomingCallInternal() connection closed")
                socket.close()
                return
            }

            //Log.d(this, "request: ${request.toHex()}")

            val decrypted = Crypto.decryptMessage(request, otherPublicKey, ownPublicKey, ownSecretKey)
            if (decrypted == null) {
                Log.d(this, "createIncomingCallInternal() decryption failed")
                // cause: the caller might use the wrong key
                socket.close()
                return
            }

            Log.d(this, "createIncomingCallInternal() request: $decrypted")

            var contact = service.getContacts().getContactByPublicKey(otherPublicKey)
            if (contact == null && blockUnknown) {
                Log.d(this, "createIncomingCallInternal() block unknown contact => decline")
                decline()
                return
            }

            if (contact != null && contact.blocked) {
                Log.d(this, "createIncomingCallInternal() blocked contact => decline")
                decline()
                return
            }

            if (contact == null) {
                // unknown caller
                contact = Contact("", otherPublicKey.clone(), listOf(remoteAddress.address.hostAddress!!))
            }

            // suspicious change of identity in during peerConnection...
            if (!contact.publicKey.contentEquals(otherPublicKey)) {
                Log.d(this, "createIncomingCallInternal() suspicious change of key")
                decline()
                return
            }

            // remember latest working address and set state
            contact.lastWorkingAddress = InetSocketAddress(remoteAddress.address, MainService.serverPort)

            val obj = JSONObject(decrypted)
            val action = obj.optString("action", "")
            Log.d(this, "createIncomingCallInternal() action: $action")
            when (action) {
                "call" -> {
                    contact.state = Contact.State.CONTACT_ONLINE
                    MainService.refreshContacts(service)

                    if (CallActivity.isCallInProgress) {
                        Log.d(this, "createIncomingCallInternal() call in progress => decline")
                        decline() // TODO: send busy
                        return
                    }

                    Log.d(this, "createIncomingCallInternal() got WebRTC offer")

                    // someone calls us
                    val offer = obj.optString("offer")
                    if (offer.isEmpty()) {
                        Log.d(this, "createIncomingCallInternal() missing offer")
                        decline()
                        return
                    }

                    // respond that we accept the call (our phone is ringing)
                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"ringing\"}",
                        contact.publicKey,
                        ownPublicKey,
                        ownSecretKey
                    )

                    if (encrypted == null) {
                        Log.d(this, "createIncomingCallInternal() encryption failed")
                        decline()
                        return
                    }

                    pw.writeMessage(encrypted)

                    incomingRTCCall?.cleanup() // just in case
                    incomingRTCCall = RTCCall(service, contact, socket, offer)
                    try {
                        // CallActivity accepts calls by default
                        // CallActivity is being opened from a foreground notification below
                        if (service.getSettings().autoAcceptCalls) {
                                Log.d(
                                    this,
                                    "createIncomingCallInternal() start incoming call from Service"
                                )
                                val intent = Intent(service, CallActivity::class.java)
                                intent.action = "ACTION_INCOMING_CALL"
                                intent.putExtra("EXTRA_CONTACT", contact)
                                intent.flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                service.startActivity(intent)
                        } else {
                            val intent = Intent(service, CallService::class.java)
                                .putExtra(CallService.SERVICE_CONTACT_KEY,
                                    contact)
                            ContextCompat.startForegroundService(service, intent)
                        }
                    } catch (e: Exception) {
                        incomingRTCCall?.cleanup()
                        incomingRTCCall = null
                        e.printStackTrace()
                    }
                }
                "ping" -> {
                    Log.d(this, "createIncomingCallInternal() ping...")
                    // someone wants to know if we are online
                    contact.state = Contact.State.CONTACT_ONLINE
                    MainService.refreshContacts(service)

                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"pong\"}",
                        contact.publicKey,
                        ownPublicKey,
                        ownSecretKey
                    )

                    if (encrypted == null) {
                        Log.d(this, "createIncomingCallInternal() encryption failed")
                        decline()
                        return
                    }

                    pw.writeMessage(encrypted)
                }
                "status_change" -> {
                    val status = obj.getString("status")
                    if (status == "offline") {
                        contact.state = Contact.State.CONTACT_OFFLINE
                        MainService.refreshContacts(service)
                    } else {
                        Log.d(this, "createIncomingCallInternal() received unknown status_change: $status")
                    }
                }
            }
        }
    }
}
