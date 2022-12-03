package d.d.meshenger.call

import android.content.Context
import android.content.Intent
import d.d.meshenger.*
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors

class RTCCall : DataChannel.Observer {
    var state: CallState? = null
    var commSocket: Socket?
    private lateinit var factory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    private var dataChannel: DataChannel? = null
    private var offer: String? = null

    private var remoteVideoSink: ProxyVideoSink? = null
    private var localVideoSink: ProxyVideoSink? = null

    private var videoCapturer: VideoCapturer? = null
    private var appContext: Context
    private var contact: Contact
    private var ownPublicKey: ByteArray
    private var ownSecretKey: ByteArray
    private var iceServers = mutableListOf<IceServer>()
    private var onStateChangeListener: OnStateChangeListener?
    private lateinit var callActivity: CallContext
    private lateinit var eglBase: EglBase
    private var binder: MainService.MainBinder
    private var statsTimer = Timer()
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private val executor = Executors.newSingleThreadExecutor()

    private val audioConstraints = MediaConstraints()
    private val sdpMediaConstraints = MediaConstraints()

    private var isCameraEnabled = false
    private var isMicrophoneEnabled = false
    private var useFrontFacingCamera = false

    fun getMicrophoneEnabled(): Boolean {
        return isMicrophoneEnabled
    }

    fun getCameraEnabled(): Boolean {
        return isCameraEnabled
    }

    fun setCameraEnabled(enabled: Boolean) {
        executor.execute {
            Log.d(this, "setCameraEnabled() executor start")
            if (videoCapturer == null) {
                Log.w(this, "setCameraEnabled no ready to be called => ignore")
                return@execute
            }

            if (this.isCameraEnabled == enabled) {
                Log.w(this, "setCameraEnabled already $enabled => ignore")
                return@execute
            }

            if (dataChannel == null) {
                Log.w(this, "setCameraEnabled dataChannel not set => ignore")
                return@execute
            }

            if (dataChannel!!.state() != DataChannel.State.OPEN) {
                Log.w(this, "setCameraEnabled dataChannel not ready => ignore")
                return@execute
            }

            Log.d(this, "setVideoEnabled: $enabled")
            try {
                // send own camera state over data channel
                val o = JSONObject()
                if (enabled) {
                    o.put(STATE_CHANGE_MESSAGE, CAMERA_ENABLE_MESSAGE)
                } else {
                    o.put(STATE_CHANGE_MESSAGE, CAMERA_DISABLE_MESSAGE)
                }

                if (sendOnDataChannel(o)) {
                    if (enabled) {
                        videoCapturer!!.startCapture(1280, 720, 25)
                        callActivity.onLocalVideoEnabled(true)
                    } else {
                        callActivity.onLocalVideoEnabled(false)
                        videoCapturer!!.stopCapture()
                    }

                    this.isCameraEnabled = enabled
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            Log.d(this, "setCameraEnabled() executor end")
        }
    }

    private fun sendOnDataChannel(obj: JSONObject): Boolean {
        Log.d(this, "send on datachannel")

        if (dataChannel == null) {
            Log.w(this, "setCameraEnabled dataChannel not set => ignore")
            return false
        }

        if (dataChannel!!.state() != DataChannel.State.OPEN) {
            Log.w(this, "setCameraEnabled dataChannel not ready => ignore")
            return false
        }

        dataChannel!!.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(
                    obj.toString().toByteArray()
                ), false
            )
        )

        return true
    }

    // called for incoming calls
    constructor(
        appContext: Context,
        binder: MainService.MainBinder,
        contact: Contact,
        commSocket: Socket?,
        offer: String?
    ) {
        Log.d(this, "RTCCall created for incoming calls")

        this.appContext = appContext
        this.contact = contact
        this.commSocket = commSocket
        this.onStateChangeListener = null
        this.binder = binder
        this.ownPublicKey = binder.getSettings().publicKey
        this.ownSecretKey = binder.getSettings().secretKey
        this.offer = offer

        // usually empty
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }

        createMediaConstraints()
    }

    // called for outgoing calls
    constructor(
        appContext: Context,
        binder: MainService.MainBinder,
        contact: Contact,
        listener: OnStateChangeListener
    ) {
        Log.d(this, "RTCCall created for outgoing calls")

        this.appContext = appContext
        this.contact = contact
        this.commSocket = null
        this.onStateChangeListener = listener
        this.binder = binder
        this.ownPublicKey = binder.getSettings().publicKey
        this.ownSecretKey = binder.getSettings().secretKey

        // usually empty
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }

        createMediaConstraints()
    }

    private fun createMediaConstraints() {
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    private fun createOutgoingCall(contact: Contact, offer: String) {
        try {
            val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
            val socket = createCommSocket(contact)
            if (socket == null) {
                Log.d(this, "cannot establish peerConnection")
                reportStateChange(CallState.ERROR_CONN)
                return
            } else {
                commSocket = socket
            }
            val remote_address = socket.remoteSocketAddress as InetSocketAddress

            Log.d(this, "outgoing call from remote address: $remote_address")

            // remember latest working address
            contact.lastWorkingAddress = InetSocketAddress(remote_address.address, MainService.serverPort)

            val pr = PacketReader(socket)
            reportStateChange(CallState.CONNECTING)
            run {
                Log.d(this, "outgoing call: send call")
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
                    closeCommSocket()
                    reportStateChange(CallState.ERROR_CRYPTO)
                    return
                }

                val pw = PacketWriter(socket)
                pw.writeMessage(encrypted)
            }
            run {
                Log.d(this, "outgoing call: got ringing")
                val response = pr.readMessage()
                val decrypted = Crypto.decryptMessage(
                    response,
                    otherPublicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (!contact.publicKey.contentEquals(otherPublicKey)) {
                    closeCommSocket()
                    reportStateChange(CallState.ERROR_AUTH)
                    return
                }

                val obj = JSONObject(decrypted!!)
                if (obj.optString("action", "") != "ringing") {
                    Log.d(this, "action not equals ringing")
                    closeCommSocket()
                    reportStateChange(CallState.ERROR_OTHER)
                    return
                }
                reportStateChange(CallState.RINGING)
            }
            run {
                val response = pr.readMessage()
                val decrypted = Crypto.decryptMessage(
                    response,
                    otherPublicKey,
                    ownPublicKey,
                    ownSecretKey
                )

                if (decrypted == null) {
                    closeCommSocket()
                    reportStateChange(CallState.ERROR_CRYPTO)
                    return
                }

                if (!contact.publicKey.contentEquals(otherPublicKey)) {
                    closeCommSocket()
                    reportStateChange(CallState.ERROR_AUTH)
                    return
                }

                val obj = JSONObject(decrypted)
                when (val action = obj.getString("action")) {
                    "connected" -> {
                        Log.d(this, "outgoing call: connected")
                        reportStateChange(CallState.CONNECTED)
                        handleAnswer(obj.getString("answer"))
                        // contact accepted receiving call
                    }
                    "dismissed" -> {
                        Log.d(this, "outgoing call:dismissed")
                        closeCommSocket()
                        reportStateChange(CallState.DISMISSED)
                    }
                    else -> {
                        Log.d(this, "outgoing call: unknown action reply $action")
                        closeCommSocket()
                        reportStateChange(CallState.ERROR_OTHER)
                    }
                }
            }

        } catch (e: Exception) {
            closeCommSocket()
            e.printStackTrace()
            reportStateChange(CallState.ERROR_OTHER)
        }
    }

    fun initOutgoing() {
        executor.execute {
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            peerConnection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {

                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState)
                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        Log.d(this, "outgoing call: send offer")
                        createOutgoingCall(contact, peerConnection.localDescription.description)
                    }
                }

                override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                    Log.d(this, "onIceConnectionChange " + iceConnectionState.name)
                    super.onIceConnectionChange(iceConnectionState)
                    if (iceConnectionState == IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

            })!!

            val init = DataChannel.Init()
            init.ordered = true
            dataChannel = peerConnection.createDataChannel("data", init)
            dataChannel!!.registerObserver(this)

            callActivity.onCameraEnabled()

            createPeerConnection()

            peerConnection.createOffer(object : DefaultSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    peerConnection.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                }
            }, sdpMediaConstraints)
        }
    }

    private fun createCommSocket(contact: Contact): Socket? {
        Log.d(this, "createCommSocket")

        val useSystemTable = binder.getSettings().useSystemTable
        val addresses = AddressUtils.getAllSocketAddresses(contact, useSystemTable)
        for (address in addresses) {
            callActivity.onRemoteAddressChange(address, false)
            Log.d(this, "try address: $address")

            val socket = AddressUtils.establishConnection(address)
            if (socket != null) {
                callActivity.onRemoteAddressChange(socket.remoteSocketAddress as InetSocketAddress, true)
                return socket
            }
        }
        return null
    }

    private fun closeCommSocket() {
        Log.d(this, "closeCommSocket")

        try {
            commSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        commSocket = null
    }

    fun setRemoteRenderer(remoteVideoSink: ProxyVideoSink?) {
        this.remoteVideoSink = remoteVideoSink
    }

    fun setLocalRenderer(localVideoSink: ProxyVideoSink?) {
        this.localVideoSink = localVideoSink
    }

    override fun onBufferedAmountChange(l: Long) {
        // nothing to do
    }

    override fun onStateChange() {
        // nothing to do
    }

    override fun onMessage(buffer: DataChannel.Buffer) {
        val data = ByteArray(buffer.data.remaining())
        buffer.data.get(data)
        val s = String(data)
        try {
            Log.d(this, "onMessage: $s")
            val o = JSONObject(s)
            if (o.has(STATE_CHANGE_MESSAGE)) {
                when (o.getString(STATE_CHANGE_MESSAGE)) {
                    CAMERA_ENABLE_MESSAGE -> callActivity.onRemoteVideoEnabled(true)
                    CAMERA_DISABLE_MESSAGE -> callActivity.onRemoteVideoEnabled(false)
                    else -> {}
                }
            } else {
                Log.d(this, "unknown message: $s")
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun releaseCamera() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun handleMediaStream(stream: MediaStream) {
        Log.d(this, "handleMediaStream")

        executor.execute {
            Log.d(this, "handleMediaStream() executor start")
            if (remoteVideoSink == null || stream.videoTracks.size == 0) {
                return@execute
            }
            stream.videoTracks[0].addSink(remoteVideoSink)
            Log.d(this, "handleMediaStream() executor end")
        }
    }

    private fun createPeerConnection() {
        try {
            peerConnection.addTrack(createAudioTrack(), listOf("stream1"))
            peerConnection.addTrack(createVideoTrack(), listOf("stream1"))
        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    fun getFrontCameraEnabled(): Boolean {
        return useFrontFacingCamera
    }

    fun setFrontCameraEnabled(enabled: Boolean) {
        Log.d(this, "setFrontCameraEnabled: $enabled")
        if (videoCapturer != null) {
            if (enabled != useFrontFacingCamera) {
                (videoCapturer as CameraVideoCapturer).switchCamera(null)
                useFrontFacingCamera = enabled
                callActivity.onFrontFacingCamera(enabled)
            }
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        videoCapturer = null
        val enumerator = Camera1Enumerator()
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                videoCapturer = enumerator.createCapturer(name, null)
                break
            }
        }

        if (videoCapturer != null) {
            val surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)

            val localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            localVideoTrack.addSink(localVideoSink)
            localVideoTrack.setEnabled(true)
            return localVideoTrack
        }
        return null
    }

    private fun createAudioTrack(): AudioTrack? {
        Log.d(this, "createAudioTrack")
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(isMicrophoneEnabled)
        return localAudioTrack
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        Log.d(this, "setMicrophoneEnabled: $enabled")
        executor.execute {
            isMicrophoneEnabled = enabled
            localAudioTrack?.setEnabled(enabled)
            callActivity.onMicrophoneEnabled(enabled)
        }
    }

    fun initVideo() {
        // must be created in Main/GUI Thread!
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val videoCodecHwAcceleration = false
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory

        if (videoCodecHwAcceleration) {
            encoderFactory = HWVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            decoderFactory = HWVideoDecoderFactory(eglBase.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun handleAnswer(remoteDesc: String) {
        peerConnection.setRemoteDescription(object : DefaultSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(this, "onSetSuccess")
            }

            override fun onSetFailure(s: String) {
                super.onSetFailure(s)
                Log.d(this, "onSetFailure: $s")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, remoteDesc))
    }

    private fun reportStateChange(state: CallState) {
        Log.d(this, "reportStateChange: $state")

        this.state = state
        onStateChangeListener!!.onStateChange(state)
    }

    fun setStatsCollector(statsCollector: RTCStatsCollectorCallback?) {
        if (statsCollector == null) {
            statsTimer.cancel()
            statsTimer.purge()
        } else {
            statsTimer = Timer()
            statsTimer.schedule(object : TimerTask() {
                override fun run() {
                    executor.execute {
                        Log.d(this, "setStatsCollector() executor start")
                        try {
                            peerConnection.getStats(statsCollector)
                        } catch (e: Exception) {
                            Log.e(this, "Cannot schedule statistics timer $e")
                        }
                        Log.d(this, "setStatsCollector() executor end")
                    }
                }
            }, 0L, StatsReportUtil.STATS_INTERVAL_MS)
        }
    }

    fun setEglBase(eglBase: EglBase) {
        this.eglBase = eglBase
    }

    fun setCallContext(activity: CallContext) {
        this.callActivity = activity
    }

    fun setOnStateChangeListener(listener: OnStateChangeListener?) {
        this.onStateChangeListener = listener
    }

    fun initIncoming() {
        Log.d(this, "initIncoming")

        executor.execute {
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            peerConnection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState)

                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        Log.d(this, "onIceGatheringChange")
                        try {
                            val pw = PacketWriter(commSocket!!)
                            val obj = JSONObject()
                            obj.put("action", "connected")
                            obj.put("answer", peerConnection.localDescription.description)
                            val encrypted = Crypto.encryptMessage(
                                obj.toString(),
                                contact.publicKey,
                                ownPublicKey,
                                ownSecretKey
                            )
                            if (encrypted != null) {
                                pw.writeMessage(encrypted)
                                reportStateChange(CallState.CONNECTED)
                            } else {
                                reportStateChange(CallState.ERROR_CRYPTO)
                            }
                            //new Thread(new SpeakerRunnable(commSocket)).start()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            reportStateChange(CallState.ERROR_OTHER)
                        }
                    }
                }

                override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                    Log.d(this, "onIceConnectionChange")
                    super.onIceConnectionChange(iceConnectionState)
                    if (iceConnectionState == IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(this, "onAddStream")
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d(this, "onDataChannel")
                    super.onDataChannel(dataChannel)
                    this@RTCCall.dataChannel = dataChannel
                    this@RTCCall.dataChannel!!.registerObserver(this@RTCCall)
                    callActivity.onCameraEnabled()
                }
            })!!

            createPeerConnection()

            Log.d(this, "setting remote description")
            peerConnection.setRemoteDescription(object : DefaultSdpObserver() {
                override fun onSetSuccess() {
                    super.onSetSuccess()
                    Log.d(this, "creating answer...")
                    peerConnection.createAnswer(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription) {
                            Log.d(this, "onCreateSuccess")
                            super.onCreateSuccess(sessionDescription)
                            peerConnection.setLocalDescription(
                                DefaultSdpObserver(),
                                sessionDescription
                            )
                        }

                        override fun onCreateFailure(s: String) {
                            super.onCreateFailure(s)
                            Log.d(this, "onCreateFailure: $s")
                        }
                    }, sdpMediaConstraints)
                }
            }, SessionDescription(SessionDescription.Type.OFFER, offer))
        }
    }

    fun decline() {
        executor.execute {
            try {
                Log.d(this, "declining...")
                val socket = commSocket
                if (socket != null && !socket.isClosed) {
                    val pw = PacketWriter(socket)
                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        contact.publicKey,
                        ownPublicKey,
                        ownSecretKey
                    )

                    pw.writeMessage(encrypted!!)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun cleanup() {
        closeCommSocket()

        try {
            peerConnection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        statsTimer.cancel()
    }

    fun hangUp() {
        executor.execute {
            try {
                if (commSocket != null) {
                    val pw = PacketWriter(commSocket!!)
                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        contact.publicKey,
                        ownPublicKey,
                        ownSecretKey
                    )
                    if (encrypted != null) {
                        pw.writeMessage(encrypted)
                    }
                }
                closeCommSocket()
                statsTimer.cancel()
                statsTimer.purge()
                reportStateChange(CallState.ENDED)
            } catch (e: IOException) {
                e.printStackTrace()
                reportStateChange(CallState.ERROR_OTHER)
            }
        }
    }

    enum class CallState {
        CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR_CONN, ERROR_CRYPTO, ERROR_AUTH, ERROR_OTHER
    }

    fun interface OnStateChangeListener {
        fun onStateChange(state: CallState)
    }

    interface CallContext {
        fun onLocalVideoEnabled(enabled: Boolean)
        fun onRemoteVideoEnabled(enabled: Boolean)
        fun onFrontFacingCamera(enabled: Boolean)
        fun onMicrophoneEnabled(enabled: Boolean)
        fun onCameraEnabled()
        fun onRemoteAddressChange(address: InetSocketAddress, isConnected: Boolean)
        fun showTextMessage(message: String)
    }

    class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            val target = this.target

            if (target == null) {
                Log.d(this, "Dropping frame in proxy because target is null.")
            } else {
                target.onFrame(frame)
            }
        }

        @Synchronized
        fun setTarget(target: VideoSink?) {
            this.target = target
        }
    }

    companion object {
        private const val STATE_CHANGE_MESSAGE = "StateChange"
        private const val CAMERA_DISABLE_MESSAGE = "CameraDisabled"
        private const val CAMERA_ENABLE_MESSAGE = "CameraEnabled"
        private const val AUDIO_TRACK_ID = "audio1"
        private const val VIDEO_TRACK_ID = "video1"

        fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

        fun debugPacket(label: String, msg: ByteArray?) {
            if (msg != null) {
                Log.d(this, "$label: ${msg.size}, ${msg.toHex()}")
            } else {
                Log.d(this, "$label: message is null!")
            }
        }

        fun createIncomingCall(binder: MainService.MainBinder, socket: Socket) {
            Log.d(this, "createIncomingCall")

            val clientPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
            val ownSecretKey = binder.getDatabase().settings.secretKey
            val ownPublicKey = binder.getDatabase().settings.publicKey
            val decline = {
                Log.d(this, "declining...")

                try {
                    val encrypted = Crypto.encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        clientPublicKey,
                        ownPublicKey,
                        ownSecretKey
                    )

                    if (encrypted != null) {
                        val pw = PacketWriter(socket)
                        pw.writeMessage(encrypted)
                    }

                    socket.close()
                } catch (e: Exception) {
                    try {
                        socket.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            try {
                val remote_address = socket.remoteSocketAddress as InetSocketAddress
                val pw = PacketWriter(socket)
                val pr = PacketReader(socket)

                Log.d(this, "incoming peerConnection from $remote_address")

                val request = pr.readMessage()
                if (request == null) {
                    Log.d(this, "connection closed")
                    socket.close()
                    return
                }

                //Log.d(this, "request: ${request.toHex()}")

                val decrypted = Crypto.decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey)
                if (decrypted == null) {
                    Log.d(this, "decryption failed")
                    // cause: the caller might use the wrong key
                    socket.close()
                    return
                }

                if (binder.getCurrentCall() != null) {
                    Log.d(this, "call in progress => decline")
                    decline()
                    return
                }

                var contact = binder.getDatabase().contacts.getContactByPublicKey(clientPublicKey)
                if (contact == null && binder.getDatabase().settings.blockUnknown) {
                    Log.d(this, "block unknown contact => decline")
                    decline()
                    return
                }

                if (contact != null && contact.blocked) {
                    Log.d(this, "blocked contact => decline")
                    decline()
                    return
                }

                if (contact == null) {
                    // unknown caller
                    contact = Contact("", clientPublicKey.clone(), ArrayList())
                }

                // suspicious change of identity in during peerConnection...
                if (!contact.publicKey.contentEquals(clientPublicKey)) {
                    Log.d(this, "suspicious change of key")
                    decline()
                    return
                }

                // remember last good address (the outgoing port is random and not the server port)
                contact.lastWorkingAddress = InetSocketAddress(remote_address.address, MainService.serverPort)

                val obj = JSONObject(decrypted)
                val action = obj.optString("action", "")
                Log.d(this, "action: $action")
                when (action) {
                    "call" -> {
                        // someone calls us
                        val offer = obj.getString("offer")

                        // respond that we accept the call (our phone is ringing)
                        val encrypted = Crypto.encryptMessage(
                            "{\"action\":\"ringing\"}",
                            contact.publicKey,
                            ownPublicKey,
                            ownSecretKey
                        )

                        if (encrypted == null) {
                            Log.d(this, "encryption failed")
                            decline()
                            return
                        }

                        debugPacket("send ringing message:", encrypted)
                        pw.writeMessage(encrypted)

                        // TODO: keep ringing to keep socket open until resolved
                        val currentCall = RTCCall(binder.getService(), binder, contact, socket, offer)
                        binder.setCurrentCall(currentCall)
                        val intent = Intent(binder.getService(), CallActivity::class.java)
                        //intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        intent.action = "ACTION_INCOMING_CALL"
                        intent.putExtra("EXTRA_CONTACT", contact)
                        binder.getService().startActivity(intent)
                        return
                    }
                    "ping" -> {
                        Log.d(this, "ping...")
                        // someone wants to know if we are online
                        contact.state = Contact.State.ONLINE
                        val encrypted = Crypto.encryptMessage(
                            "{\"action\":\"pong\"}",
                            contact.publicKey,
                            ownPublicKey,
                            ownSecretKey
                        )

                        if (encrypted == null) {
                            Log.d(this, "encryption failed")
                            decline()
                            return
                        }

                        pw.writeMessage(encrypted)
                    }
                    "status_change" -> {
                        if (obj.optString("status", "") == "offline") {
                            contact.state = Contact.State.ONLINE
                        } else {
                            Log.d(this, "Received unknown status_change: " + obj.getString("status"))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                decline()
            }
        }
    }
}