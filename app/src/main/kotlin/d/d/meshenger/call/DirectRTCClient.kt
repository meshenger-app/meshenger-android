package d.d.meshenger.call

import android.util.Log
import d.d.meshenger.utils.AddressUtils
import d.d.meshenger.model.Contact
import d.d.meshenger.utils.Crypto
import d.d.meshenger.service.MainService
import d.d.meshenger.call.AppRTCClient.SignalingEvents
import d.d.meshenger.call.AppRTCClient.SignalingParameters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.ThreadUtils.ThreadChecker
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DirectRTCClient constructor (private var socket: SocketWrapper?, val contact: Contact, val callDirection: CallDirection)
    : Thread(), AppRTCClient   {

    companion object {

        private const val TAG = "DirectRTCClient"

        private var currentCall: DirectRTCClient? = null
        private val currentCallLock = Any()

        // numerical presentation of how high-level an error is
        private fun getExceptionLevel(e: Exception): Int {
            when(e) {
                is ConnectException -> 3
                is SocketTimeoutException -> 2
                is IOException -> 1
            }
            return 0
        }

        // Put a |key|->|value| mapping in |json|.
        private fun jsonPut(json: JSONObject, key: String, value: Any) {
            try {
                json.put(key, value)
            } catch (e: JSONException) {
                throw RuntimeException(e)
            }
        }

        // Converts a Java candidate to a JSONObject.
        private fun toJsonCandidate(candidate: IceCandidate): JSONObject? {
            val json = JSONObject()
            jsonPut(json, "label", candidate.sdpMLineIndex)
            jsonPut(json, "id", candidate.sdpMid)
            jsonPut(json, "candidate", candidate.sdp)
            return json
        }

        // Converts a JSON candidate to a Java object.
        @Throws(JSONException::class)
        private fun toJavaCandidate(json: JSONObject): IceCandidate? {
            return IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate")
            )
        }

        fun getCurrentCall(): DirectRTCClient? {
            synchronized(currentCallLock) { return currentCall }
        }

        fun setCurrentCall(client: DirectRTCClient?) {
            synchronized(currentCallLock) {
                currentCall = client
            }
        }

        fun createOutgoingCall(contact: Contact?): Boolean {
            Log.d(TAG, "createOutgoingCall")
            if (contact == null) {
                return false
            }
            synchronized(currentCallLock) {
                if (currentCall != null) {
                    Log.w(
                        TAG,
                        "Cannot handle outgoing call. Call in progress!"
                    )
                    return false
                }
                currentCall = DirectRTCClient(
                    null,
                    contact,
                    DirectRTCClient.CallDirection.OUTGOING
                )
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
                val ownSecretKey = MainService.instance!!.getSettings()?.secretKey
                val ownPublicKey = MainService.instance!!.getSettings()?.publicKey
                val socket = SocketWrapper(rawSocket)
                Log.d(TAG, "readMessage")
                val request = socket.readMessage()
                if (request == null) {
                    Log.d(TAG, "request is null")
                    // invalid or timed out packet
                    return false
                }
                Log.d(TAG, "decrypt message")
                // receive public key of contact
                val decrypted =
                    Crypto.decryptMessage(request, clientPublicKeyOut, ownPublicKey, ownSecretKey)
                if (decrypted == null) {
                    Log.d(TAG, "decryption failed")
                    return false
                }
                Log.d(TAG, "decrypted message: $decrypted")
                var contact =
                    MainService.instance!!.getContacts()?.getContactByPublicKey(clientPublicKeyOut)
                if (contact == null) {
                    Log.d(TAG, "unknown contact")
                    if (MainService.instance!!.getSettings()?.blockUnknown!!) {
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
                        Log.w(
                            TAG,
                            "Cannot handle incoming call. Call in progress!"
                        )
                        return false
                    }
                    Log.d(TAG, "create DirectRTCClient")
                    currentCall = DirectRTCClient(
                        socket,
                        contact,
                        DirectRTCClient.CallDirection.INCOMING
                    )
                }
                true
            } catch (e: IOException) {
                if (rawSocket != null) {
                    try {
                        rawSocket.close()
                    } catch (_e: IOException) {
                        // ignore
                    }
                }
                Log.e(TAG, "exception in createIncomingCall")
                false
            }
        }

    }

    // call context for events
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var executorThreadCheck: ThreadChecker = ThreadChecker()
    var signalingEvents: SignalingEvents? = null
    private var socketLock = Any()
    private var ownSecretKey = MainService.instance!!.getSettings()?.secretKey
    private var ownPublicKey = MainService.instance!!.getSettings()?.publicKey

    private enum class ConnectionState {
        NEW, CONNECTED, CLOSED, ERROR
    }

    enum class CallDirection {
        INCOMING, OUTGOING
    }

    // only set in executor (or ctor)
    private var connectionState = ConnectionState.NEW


    init {
        executorThreadCheck.detachThread()
    }

    // numerical presentation of how high-level an error is
    private fun getExceptionLevel(e: java.lang.Exception?): Int {
        if (e is ConnectException) {
            // connection went wrong
            return 3
        }
        if (e is SocketTimeoutException) {
            // no connection at all
            return 2
        }
        return if (e is IOException) {
            // internal error while gettting streams?
            1
        } else 0
    }

    private fun establishConnection(
        addresses: Array<InetSocketAddress>,
        timeout: Int
    ): SocketWrapper? {
        var errorException: java.lang.Exception? = null
        //InetSocketAddress errorAddress = null;
        var socket: SocketWrapper? = null
        for (address in addresses) {
            Log.d(TAG, "try address: '" + address.address + "', port: " + address.port)
            val rawSocket = Socket()
            try {
                rawSocket.connect(address, timeout)
                socket = SocketWrapper(rawSocket)
                errorException = null
                //errorAddress = null;
                // successful connection - abort
                break
            } catch (e: java.lang.Exception) {
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
                errorException.message?.let { reportError(it) }
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
*/if (signalingEvents == null) {
            disconnectSocket()
            Log.e(TAG, "No listener found!")
            return
        }
        if (callDirection == CallDirection.OUTGOING) {
            assert(contact != null)
            assert(socket == null)

            // contact is only set for outgoing call
            if (contact.addresses.isEmpty()) {
                reportError("No addresses set for contact.")
                return
            }
            Log.d(TAG, "Create outgoing socket")
            assert(contact != null)
            assert(socket == null)
            val addresses = AddressUtils.getAllSocketAddresses(
                contact.addresses, contact.last_working_address, MainService.serverPort
            )
            socket = establishConnection(addresses, 2000)
            if (socket == null) {
                disconnectSocket()
                return
            }

            // send initial packet
            executor.execute { sendMessage("{\"type\":\"call\"}") }
        } else {
            Log.v(TAG, "Execute onConnectedToRoom")
            executor.execute {
                connectionState = ConnectionState.CONNECTED
                val parameters =
                    SignalingParameters( // Ice servers are not needed for direct connections.
                        java.util.ArrayList(),
                        callDirection == CallDirection.INCOMING,  // Server side acts as the initiator on direct connections.
                        null,  // clientId
                        null,  // wssUrl
                        null,  // wwsPostUrl
                        null,  // offerSdp
                        null // iceCandidates
                    )
                // call to CallActivity
                signalingEvents!!.onConnectedToRoom(parameters)
            }
        }
        val remote_address = socket!!.getRemoteSocketAddress() as InetSocketAddress

        // remember last good address (the outgoing port is random and not the server port)
        contact.last_working_address = remote_address.address

        // read data
        while (true) {
            val message: ByteArray? = try {
                socket!!.readMessage()
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
            val decrypted =
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
            socket?.close()
            socket = null
            executor.execute { signalingEvents!!.onChannelClose() }
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
        this.connectionState = ConnectionState.NEW

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
        if (callDirection != CallDirection.INCOMING) {
            Log.e(TAG, "we send offer as client?")
        }
        executor.execute {
            if (connectionState != ConnectionState.CONNECTED) {
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
            if (connectionState != ConnectionState.CONNECTED) {
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
            if (connectionState != ConnectionState.CONNECTED) {
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
            if (type == "call") {
                if (callDirection != CallDirection.INCOMING) {
                    Log.e(TAG, "Dang, we are the client but got the packet of an outoing call?")
                    reportError("Unexpected answer: $msg")
                } else {
                    // ignore - first packet of an incoming call
                }
            } else if (type == "candidate") {
                toJavaCandidate(json)?.let { signalingEvents!!.onRemoteIceCandidate(it) }
            } else if (type == "remove-candidates") {
                val candidateArray = json.getJSONArray("candidates")
                val candidates = arrayOfNulls<IceCandidate>(candidateArray.length())
                var i = 0
                while (i < candidateArray.length()) {
                    candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i))
                    i += 1
                }
                signalingEvents!!.onRemoteIceCandidatesRemoved(candidates)
            } else if (type == "answer") {
                if (callDirection != CallDirection.INCOMING) {
                    Log.e(TAG, "Dang, we are the client but got an answer?")
                    reportError("Unexpected answer: $msg")
                } else {
                    val sdp = SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp")
                    )
                    signalingEvents!!.onRemoteDescription(sdp)
                }
            } else if (type == "offer") {
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp")
                )
                if (callDirection != CallDirection.OUTGOING) {
                    Log.e(TAG, "Dang, we are the server but got an offer?")
                    reportError("Unexpected offer: $msg")
                } else {
                    val parameters =
                        SignalingParameters( // Ice servers are not needed for direct connections.
                            java.util.ArrayList(),
                            false,  // This code will only be run on the client side. So, we are not the initiator.
                            null,  // clientId
                            null,  // wssUrl
                            null,  // wssPostUrl
                            sdp,  // offerSdp
                            null // iceCandidates
                        )
                    connectionState = ConnectionState.CONNECTED
                    // call to CallActivity
                    signalingEvents!!.onConnectedToRoom(parameters)
                }
            } else {
                reportError("Unexpected message: $msg")
            }
        } catch (e: JSONException) {
            reportError("TCP message JSON parsing error: $e")
        }
    }

    private fun reportError(errorMessage: String) {
        Log.e(TAG, errorMessage + " (" + connectionState.name + ")")
        try {
            executor.execute {
                if (connectionState != ConnectionState.ERROR) {
                    connectionState = ConnectionState.ERROR
                    signalingEvents!!.onChannelError(errorMessage)
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, e.toString())
        }
    }

    private fun sendMessage(message: String) {
        executorThreadCheck.checkIsOnValidThread()
        val encrypted =
            ownPublicKey?.let {
                Crypto.encryptMessage(message, contact.publicKey,
                    it, ownSecretKey)
            }
        synchronized(socketLock) {
            if (socket == null) {
                reportError("Sending data on closed socket.")
                return
            }
            try {
                if (encrypted != null) {
                    socket!!.writeMessage(encrypted)
                }
            } catch (e: IOException) {
                reportError("Failed to write message: $e")
            }
        }
    }
}