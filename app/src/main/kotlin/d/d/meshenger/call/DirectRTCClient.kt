package d.d.meshenger.call

import android.util.Log
import d.d.meshenger.AddressUtils.getAllSocketAddresses
import d.d.meshenger.Contact
import d.d.meshenger.Crypto
import d.d.meshenger.MainService
import d.d.meshenger.call.AppRTCClient.SignalingEvents
import d.d.meshenger.call.AppRTCClient.SignalingParameters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.ThreadUtils
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class DirectRTCClient(var socket: SocketWrapper?, val contact: Contact, val callDirection: CallDirection): Thread(), AppRTCClient {

    companion object{
        const val TAG = "DirectRTCClient"

        var currentCall: DirectRTCClient? = null
            get() {
                synchronized(currentCallLock) { return field }
            }
            set(client) {
                synchronized(currentCallLock) { field = client }
        }


        private val currentCallLock: Any = Any()


        // Put a |key|->|value| mapping in |json|.
        private fun jsonPut(json: JSONObject, key: String, value: Any) {
            try {
                json.put(key, value)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        // Converts a Java candidate to a JSONObject.
        private fun toJsonCandidate(candidate: IceCandidate): JSONObject {
            return JSONObject().apply {
                jsonPut(this, "label", candidate.sdpMLineIndex)
                jsonPut(this, "id", candidate.sdpMid)
                jsonPut(this, "candidate", candidate.sdp)
            }
        }

        // Converts a JSON candidate to a Java object.
        @Throws(JSONException::class)
        private fun toJavaCandidate(json: JSONObject): IceCandidate {
            return IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate")
            )
        }



        fun createOutgoingCall(contact: Contact?): Boolean {
            Log.d(TAG, "createOutgoingCall")
            if (contact == null) {
                return false
            }
            synchronized(currentCallLock) {
                if (currentCall != null) {
                    Log.w(TAG, "Cannot handle outgoing call. Call in progress!")
                    return false
                }
                currentCall = DirectRTCClient(null, contact, CallDirection.OUTGOING)
                return true
            }
        }

        fun createIncomingCall(rawSocket: Socket?): Boolean {
            Log.d(TAG, "createIncomingCall")
            return if (rawSocket == null) {
                false
            } else try {
                // search for contact identity
                val clientPublicKeyOut = ByteArray(Sodium.crypto_sign_publickeybytes())

                val ownSecretKey: ByteArray = MainService.instance?.getSettings()?.secretKey!!
                val ownPublicKey: ByteArray = MainService.instance?.getSettings()?.publicKey!!
                val socket = SocketWrapper(rawSocket)
                Log.d(TAG, "readMessage")
                val request: ByteArray? = socket.readMessage()
                if (request == null) {
                    Log.d(TAG, "request is null")
                    // invalid or timed out packet
                    return false
                }
                Log.d(TAG, "decrypt message")
                // receive public key of contact
                val decrypted: String? =
                    Crypto.decryptMessage(request, clientPublicKeyOut, ownPublicKey, ownSecretKey)
                if (decrypted == null) {
                    Log.d(TAG, "decryption failed")
                    return false
                }
                Log.d(TAG, "decrypted message: $decrypted")
                var contact: Contact? =
                    MainService.instance!!.getContacts().getContactByPublicKey(clientPublicKeyOut)
                if (contact == null) {
                    Log.d(TAG, "unknown contact")
                    if (MainService.instance?.getSettings()?.blockUnknown!!) {
                        Log.d(TAG, "block unknown contact => decline")
                        return false
                    }

                    // unknown caller
                    contact = Contact(
                        "" /* unknown caller */,
                        clientPublicKeyOut.clone(),
                        ArrayList(),
                        false
                    )
                } else {
                    if (contact.blocked) {
                        Log.d(TAG, "blocked contact => decline")
                        return false
                    }
                }
                synchronized(currentCallLock) {
                    if (currentCall != null) {
                        Log.w(TAG, "Cannot handle incoming call. Call in progress!")
                        return false
                    }
                    Log.d(TAG, "create DirectRTCClient")
                    currentCall =
                        DirectRTCClient(socket, contact, CallDirection.INCOMING)
                }
                true
            } catch (e: IOException) {
                try {
                    rawSocket.close()
                } catch (_e: IOException) {
                    // ignore
                }
                Log.e(TAG, "exception in createIncomingCall")
                false
            }
        }

        // numerical presentation of how high-level an error is
        private fun getExceptionLevel(e: Exception?): Int {

            return when(e) {
                is ConnectException -> 3
                is SocketTimeoutException -> 2
                is IOException -> 1
                else -> 0
            }

        }

    }

    // call context for events
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var executorThreadCheck: ThreadUtils.ThreadChecker
    private var signalingEvents: SignalingEvents? = null
    private var connectionState: ConnectionState
    private var socketLock: Any = Any()
    private lateinit var ownSecretKey: ByteArray
    private lateinit var ownPublicKey: ByteArray


    private enum class ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    enum class CallDirection {
        INCOMING, OUTGOING
    }

    // only set in executor (or ctor)

    init {
        connectionState = ConnectionState.NEW
        MainService.instance?.getSettings()?.let{
            ownSecretKey = it.secretKey!!
            ownPublicKey = it.publicKey!!
        }

        executorThreadCheck = ThreadUtils.ThreadChecker()
        executorThreadCheck.detachThread()
    }


    fun setEventListener(signalingEvents: SignalingEvents?) {
        this.signalingEvents = signalingEvents!!
    }

    //TODO: Timeout value is always 2000
    private fun establishConnection(addresses: Array<InetSocketAddress>, timeout: Int): SocketWrapper? {
        var errorException: Exception? = null
        //InetSocketAddress errorAddress = null;
        var socket: SocketWrapper? = null
        for (address in addresses) {
            Log.d(
                TAG,
                "try address: ' ${address.address} ', port:0 ${address.port}"
            )
            val rawSocket = Socket()
            try {
                rawSocket.connect(address, timeout)
                socket = SocketWrapper(rawSocket)
                errorException = null
                //errorAddress = null;
                // successful connection - abort
                break
            } catch (e: Exception) {
                if (getExceptionLevel(e) >= getExceptionLevel(errorException)) {
                    errorException = e
                    //errorAddress = address;
                }
                try {
                    rawSocket.close()
                } catch (ioe: IOException) {
                    // ignore
                }
            }
        }
        if (socket == null) {
            if (errorException != null) {
                reportError(errorException.message)
            } else {
                reportError("Connection failed.")
            }
        }
        return socket
    }

    override fun run() {
        Log.d(TAG, "Listening thread started...")
        /*
        // wait for listner to be attached
        try {
            for (int i = 0; i < 50 && listener == null; i += 1) {
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Wait for listener interrupted: " + e);
            return;
        }
*/      if (signalingEvents == null) {
            disconnectSocket()
            Log.e(TAG, "No listener found!")
            return
        }
        if (callDirection === CallDirection.OUTGOING) {
            assert(socket == null)

            // contact is only set for outgoing call
            if (contact.addresses.isEmpty()) {
                reportError("No addresses set for contact.")
                return
            }
            Log.d(TAG, "Create outgoing socket")
            assert(socket == null)
            val addresses: Array<InetSocketAddress> = getAllSocketAddresses(
                contact.addresses, contact.lastWorkingAddress, MainService.serverPort
            )
            socket = establishConnection(addresses, 2000)
            if (socket == null) {
                disconnectSocket()
                return
            }

            // send initial packet
            executor.execute { sendMessage("{\"type\":\"call\"}") }
        } else {
            assert(callDirection === CallDirection.INCOMING)
            assert(socket != null)
            Log.v(TAG, "Execute onConnectedToRoom")
            executor.execute {
                connectionState = ConnectionState.CONNECTED
                val parameters =
                    SignalingParameters( // Ice servers are not needed for direct connections.
                        ArrayList(),
                        callDirection === CallDirection.INCOMING,  // Server side acts as the initiator on direct connections.
                        null,  // clientId
                        null,  // wssUrl
                        null,  // wwsPostUrl
                        null,  // offerSdp
                        null // iceCandidates
                    )
                // call to CallActivity
                signalingEvents?.onConnectedToRoom(parameters)
            }
        }
        val remoteAddress: InetSocketAddress = socket?.getRemoteSocketAddress() as InetSocketAddress

        // remember last good address (the outgoing port is random and not the server port)
        contact.lastWorkingAddress = remoteAddress.address

        // read data
        while (true) {
            val message: ByteArray? = try {
                socket?.readMessage()
            } catch (e: IOException) {
                reportError("Connection failed: " + e.message) // called when connection is closed by either end?
                // using reportError will cause the executor.shutdown() and also assignment of a task afterwards
                Log.d(TAG, "Failed to read from rawSocket: " + e.message)
                break
            }

            // No data received, rawSocket probably closed.
            if (message == null) {
                Log.d(TAG, "message is null")
                // hm, call hangup?
                break
            }
            val decrypted: String? =
                Crypto.decryptMessage(message, contact.publicKey, ownPublicKey, ownSecretKey)
            if (decrypted == null) {
                reportError("decryption failed")
                break
            }

            //Log.d(TAG, "decrypted: " + decrypted);
            executor.execute { onTCPMessage(decrypted) }
        }
        Log.d(TAG, "Receiving thread exiting...")

        // Close the rawSocket if it is still open.
        disconnectSocket()
    }

    private fun disconnectSocket() {
        synchronized(socketLock) {
            socket.let{
                it?.close()
                socket = null
                executor.execute { signalingEvents?.onChannelClose() }
            }

        }
    }

    override fun connectToRoom() {
        executor.execute { connectToRoomInternal() }
    }

    override fun disconnectFromRoom() {
        executor.execute { disconnectFromRoomInternal() }
    }

    private fun connectToRoomInternal() {
        executorThreadCheck.checkIsOnValidThread()
        connectionState = ConnectionState.NEW

        // start thread and run()
        if (!this.isAlive) {
            start()
        } else {
            Log.w(TAG, "Thread is already running!")
        }
    }

    private fun disconnectFromRoomInternal() {
        executorThreadCheck.checkIsOnValidThread()
        connectionState = ConnectionState.CLOSED
        disconnectSocket()
        Log.d(TAG, "shutdown executor")
        executor.shutdown()
    }

    override fun sendOfferSdp(sdp: SessionDescription) {
        if (callDirection !== CallDirection.INCOMING) {
            Log.e(TAG, "we send offer as client?")
        }
        executor.execute {
            if (connectionState !== ConnectionState.CONNECTED) {
                reportError("Sending offer SDP in non connected state.")
                return@execute
            }
            val json = JSONObject()
            jsonPut(json, "sdp", sdp.description)
            jsonPut(json, "type", "offer")
            sendMessage(json.toString())
        }
    }

    override fun sendAnswerSdp(sdp: SessionDescription) {
        executor.execute {
            val json = JSONObject()
            jsonPut(json, "sdp", sdp.description)
            jsonPut(json, "type", "answer")
            sendMessage(json.toString())
        }
    }

    override fun sendLocalIceCandidate(candidate: IceCandidate) {
        executor.execute {
            val json = JSONObject()
            jsonPut(json, "type", "candidate")
            jsonPut(json, "label", candidate.sdpMLineIndex)
            jsonPut(json, "id", candidate.sdpMid)
            jsonPut(json, "candidate", candidate.sdp)
            if (connectionState !== ConnectionState.CONNECTED) {
                reportError("Sending ICE candidate in non connected state.")
                return@execute
            }
            sendMessage(json.toString())
        }
    }

    /** Send removed Ice candidates to the other participant.  */
    override fun sendLocalIceCandidateRemovals(candidates: Array<IceCandidate>) {
        executor.execute {
            val json = JSONObject()
            jsonPut(json, "type", "remove-candidates")
            val jsonArray = JSONArray()
            for (candidate in candidates) {
                jsonArray.put(toJsonCandidate(candidate))
            }
            jsonPut(json, "candidates", jsonArray)
            if (connectionState !== ConnectionState.CONNECTED) {
                reportError("Sending ICE candidate removals in non connected state.")
                return@execute
            }
            sendMessage(json.toString())
        }
    }

    private fun onTCPMessage(msg: String) {
        //Log.d(TAG, "onTCPMessage: " + msg);
        try {
            val json = JSONObject(msg)
            val type = json.optString("type")

            // need to record for a call, so we can close the socket
            Log.e(TAG, "onTCPMessage: ($type): $msg")

            when(type){

                "call" -> {
                    if (callDirection !== CallDirection.INCOMING) {
                        Log.e(TAG, "Dang, we are the client but got the packet of an outoing call?")
                        reportError("Unexpected answer: $msg")
                    } else {
                        // ignore - first packet of an incoming call
                    }
                }

                "candidate" -> {
                    signalingEvents?.onRemoteIceCandidate(toJavaCandidate(json))
                }

                "remove-candidates" -> {
                    val candidateArray = json.getJSONArray("candidates")
                    val candidates: Array<IceCandidate?> =
                        arrayOfNulls(candidateArray.length())
                    var i = 0
                    while (i < candidateArray.length()) {
                        candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i))
                        i += 1
                    }
                    signalingEvents?.onRemoteIceCandidatesRemoved(candidates)

                }

                "answer" -> {
                    if (callDirection !== CallDirection.INCOMING) {
                        Log.e(TAG, "Dang, we are the client but got an answer?")
                        reportError("Unexpected answer: $msg")
                    } else {
                        val sdp = SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp")
                        )
                        signalingEvents?.onRemoteDescription(sdp)
                    }

                }

                "offer" -> {
                    val sdp = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp")
                    )
                    if (callDirection !== CallDirection.OUTGOING) {
                        Log.e(TAG, "Dang, we are the server but got an offer?")
                        reportError("Unexpected offer: $msg")
                    } else {
                        val parameters =
                            SignalingParameters( // Ice servers are not needed for direct connections.
                                ArrayList(),
                                false,  // This code will only be run on the client side. So, we are not the initiator.
                                null,  // clientId
                                null,  // wssUrl
                                null,  // wssPostUrl
                                sdp,  // offerSdp
                                null // iceCandidates
                            )
                        connectionState = ConnectionState.CONNECTED
                        // call to CallActivity
                        signalingEvents?.onConnectedToRoom(parameters)
                    }
                }

                else -> reportError("Unexpected message: $msg")

            }


        } catch (e: JSONException) {
            reportError("TCP message JSON parsing error: $e")
        }
    }

    private fun reportError(errorMessage: String?) {
        Log.e(TAG, errorMessage + " (" + connectionState.name + ")")
        try {
            executor.execute {
                if (connectionState !== ConnectionState.ERROR) {
                    connectionState = ConnectionState.ERROR
                    signalingEvents?.onChannelError(errorMessage!!)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private fun sendMessage(message: String) {
        executorThreadCheck.checkIsOnValidThread()
        val encrypted: ByteArray? =
            Crypto.encryptMessage(message, contact.publicKey, ownPublicKey, ownSecretKey)
        synchronized(socketLock) {
            try {
                socket?.let{
                    reportError("Sending data on closed socket.")
                    it.writeMessage(encrypted!!)
                    return
                }
            } catch (e: IOException) {
                reportError("Failed to write message: $e")
            }
        }
    }

}