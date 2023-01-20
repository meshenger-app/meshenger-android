package d.d.meshenger.call

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import d.d.meshenger.*
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.*

class RTCCall : DataChannel.Observer {
    var state = CallState.WAITING
    var commSocket: Socket?
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var offer: String? = null

    private var remoteVideoSink: ProxyVideoSink? = null
    private var localVideoSink: ProxyVideoSink? = null

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null

    private var contact: Contact
    private var callActivity: CallContext? = null
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
        Log.d(this, "setCameraEnabled()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "setCameraEnabled() executor start")
            if (videoCapturer == null) {
                Log.w(this, "setCameraEnabled() no ready to be called => ignore")
                return@execute
            }

            if (this.isCameraEnabled == enabled) {
                Log.w(this, "setCameraEnabled() already $enabled => ignore")
                return@execute
            }

            if (dataChannel == null) {
                Log.w(this, "setCameraEnabled() dataChannel not set => ignore")
                return@execute
            }

            if (dataChannel!!.state() != DataChannel.State.OPEN) {
                Log.w(this, "setCameraEnabled() dataChannel not ready => ignore")
                return@execute
            }

            Log.d(this, "setVideoEnabled() enabled=$enabled")
            try {
                // send own camera state over data channel
                val obj = JSONObject()
                if (enabled) {
                    obj.put(STATE_CHANGE_MESSAGE, CAMERA_ENABLE_MESSAGE)
                } else {
                    obj.put(STATE_CHANGE_MESSAGE, CAMERA_DISABLE_MESSAGE)
                }

                if (sendOnDataChannel(obj.toString())) {
                    if (enabled) {
                        // start with default settings
                        videoCapturer!!.startCapture(1280, 720, 25)
                        callActivity?.onLocalVideoEnabled(true)
                    } else {
                        callActivity?.onLocalVideoEnabled(false)
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

    fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        execute {
            if (!getCameraEnabled() || videoCapturer == null || videoSource == null) {
                Log.e(this, "changeCaptureFormat() Failed to change capture format. Video: ${getCameraEnabled()}.")
            } else {
                Log.d(this, "changeCaptureFormat() ${width}x${height}@${framerate}")
                videoSource?.adaptOutputFormat(width, height, framerate)
            }
        }
  }

    private fun execute(r: Runnable) {
        try {
            executor.execute(r)
        } catch (e: RejectedExecutionException) {
            // can happen when the executor has shut down
            Log.w(this, "execute() catched RejectedExecutionException")
        }
    }

    private fun sendOnSocket(message: String): Boolean {
        Log.d(this, "sendOnSocket() message=$message")

        Utils.checkIsNotOnMainThread()

        val socket = commSocket
        if (socket == null || socket.isClosed) {
            return false
        }

        //val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = binder.getSettings()
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

    private fun sendOnDataChannel(message: String): Boolean {
        Log.d(this, "sendOnDataChannel() message=$message")

        val channel = dataChannel
        if (channel == null) {
            Log.w(this, "setCameraEnabled() dataChannel not set => ignore")
            return false
        }

        if (channel.state() != DataChannel.State.OPEN) {
            Log.w(this, "setCameraEnabled() dataChannel not ready => ignore")
            return false
        }

        try {
            channel.send(
                DataChannel.Buffer(
                    ByteBuffer.wrap(
                        message.toByteArray()
                    ), false
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        return true
    }

    // called for incoming calls
    constructor(
        binder: MainService.MainBinder,
        contact: Contact,
        commSocket: Socket,
        offer: String
    ) {
        Log.d(this, "RTCCall() created for incoming calls")

        this.contact = contact
        this.commSocket = commSocket
        this.binder = binder
        this.offer = offer

        createMediaConstraints()
    }

    // called for outgoing calls
    constructor(
        binder: MainService.MainBinder,
        contact: Contact
    ) {
        Log.d(this, "RTCCall() created for outgoing calls")

        this.contact = contact
        this.commSocket = null
        this.binder = binder

        createMediaConstraints()
    }

    private fun createMediaConstraints() {
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        if (binder.getSettings().disableAudioProcessing) {
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"))
        } else {
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"))
        }
    }

    private fun createOutgoingCall(contact: Contact, offer: String) {
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
        val settings = binder.getSettings()
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey

        val socket = createCommSocket(contact)
        if (socket == null) {
            return
        }

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
            val storedContact = binder.getContacts().getContactByPublicKey(contact.publicKey)
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
                val answer = obj.getString("answer")
                if (answer != null) {
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
                Log.d(this, "createOutgoingCallInternal() unknown action reply $action")
                reportStateChange(CallState.ERROR_COMMUNICATION)
                break
            }
        }

        Log.d(this, "createOutgoingCallInternal() close socket")
        closeSocket(socket)
        Log.d(this, "createOutgoingCallInternal() dataChannel is null: ${dataChannel == null}")

        Log.d(this, "createOutgoingCallInternal() wait for writeExecutor")
        writeExecutor.shutdown()
        writeExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)

        if (isCallInit(state) && socket.isClosed) {
            Log.d(this, "createOutgoingCallInternal() call (state=$state) is not connected and socket is closed")
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }

        Log.d(this, "createOutgoingCallInternal() finished")
    }

    // Continue listening for socket message.
    // Must run on separate thread!
    fun continueOnIncomingSocket() {
        Log.d(this, "continueOnIncomingSocket()")
        Utils.checkIsNotOnMainThread()

        val socket = commSocket
        if (socket == null) {
            throw IllegalStateException("commSocket not expected to be null")
        }

        val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = binder.getSettings()
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey
        val pr = PacketReader(socket)

        Log.d(this, "continueOnIncomingSocket() expected dismissed/keep_alive")

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
            val action = obj.getString("action")
            if (action == "dismissed") {
                Log.d(this, "continueOnIncomingSocket() received dismissed")
                reportStateChange(CallState.DISMISSED)
                break
            } else if (action == "keep_alive") {
                // ignore, keeps the socket alive
                lastKeepAlive = System.currentTimeMillis()
            } else {
                Log.d(this, "continueOnIncomingSocket() received unknown action reply: $action")
                reportStateChange(CallState.ERROR_COMMUNICATION)
                break
            }
        }

        Log.d(this, "continueOnIncomingSocket() wait for writeExecutor")
        writeExecutor.shutdown()
        writeExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS)

        Log.d(this, "continueOnIncomingSocket() dataChannel is null: ${dataChannel == null}")
        closeSocket(socket)

        if (isCallInit(state) && socket.isClosed) {
            Log.d(this, "continueOnIncomingSocket() call (state=$state) is not connected and socket is closed")
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }

        Log.d(this, "continueOnIncomingSocket() finished")
    }

    fun initOutgoing() {
        Log.d(this, "initOutgoing()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "initOutgoing() executor start")
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            peerConnection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    Log.d(this, "onIceGatheringChange() state=$iceGatheringState")
                    super.onIceGatheringChange(iceGatheringState)
                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        Log.d(this, "initOutgoing() outgoing call: send offer")
                        createOutgoingCall(contact, peerConnection!!.localDescription.description)
                    }
                }

                override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                    Log.d(this, "onIceConnectionChange() state=$iceConnectionState")
                    super.onIceConnectionChange(iceConnectionState)
                    when (iceConnectionState) {
                        IceConnectionState.DISCONNECTED -> reportStateChange(CallState.ENDED)
                        IceConnectionState.FAILED -> reportStateChange(CallState.ERROR_COMMUNICATION)
                        IceConnectionState.CONNECTED -> reportStateChange(CallState.CONNECTED)
                        else -> return
                    }
                    closeSocket(commSocket!!)
                }

                override fun onConnectionChange(state: PeerConnectionState) {
                    Log.d(this, "onConnectionChange() state=$state")
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

            })!!

            val init = DataChannel.Init()
            init.ordered = true
            dataChannel = peerConnection!!.createDataChannel("data", init)
            dataChannel!!.registerObserver(this)

            callActivity?.onCameraEnabled()

            createPeerConnection()

            peerConnection!!.createOffer(object : DefaultSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    peerConnection!!.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                }
            }, sdpMediaConstraints)

            Log.d(this, "initOutgoing() executor end")
        }
    }

    private fun createCommSocket(contact: Contact): Socket? {
        Log.d(this, "createCommSocket()")

        Utils.checkIsNotOnMainThread()

        val settings = binder.getSettings()
        val useNeighborTable = settings.useNeighborTable
        val connectTimeout = settings.connectTimeout

        var unknownHostException = false
        var connectException = false
        var socketTimeoutException = false
        var exception = false

        Log.d(this, "createCommSocket() contact.addresses: ${contact.addresses}")

        for (address in AddressUtils.getAllSocketAddresses(contact, useNeighborTable)) {
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
            Log.d(this, "onMessage() s=$s")
            val o = JSONObject(s)
            if (o.has(STATE_CHANGE_MESSAGE)) {
                when (o.getString(STATE_CHANGE_MESSAGE)) {
                    CAMERA_ENABLE_MESSAGE -> callActivity?.onRemoteVideoEnabled(true)
                    CAMERA_DISABLE_MESSAGE -> callActivity?.onRemoteVideoEnabled(false)
                    HANGUP_MESSAGE -> reportStateChange(CallState.DISMISSED)
                    else -> {}
                }
            } else {
                Log.d(this, "onMessage() unknown message: $s")
            }

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun releaseCamera() {
        Log.d(this, "releaseCamera()")
        Utils.checkIsOnMainThread()

        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun handleMediaStream(stream: MediaStream) {
        Log.d(this, "handleMediaStream()")

        execute {
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
            peerConnection!!.addTrack(createAudioTrack(), listOf("stream1"))
            peerConnection!!.addTrack(createVideoTrack(), listOf("stream1"))
        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    fun getFrontCameraEnabled(): Boolean {
        return useFrontFacingCamera
    }

    fun setFrontCameraEnabled(enabled: Boolean) {
        Log.d(this, "setFrontCameraEnabled() enabled=$enabled")
        Utils.checkIsOnMainThread()
        if (videoCapturer != null) {
            if (enabled != useFrontFacingCamera) {
                (videoCapturer as CameraVideoCapturer).switchCamera(null)
                useFrontFacingCamera = enabled
                callActivity?.onFrontFacingCamera(enabled)
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
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            val localVideoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, callActivity!!.getContext(), localVideoSource.capturerObserver)

            val localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource)
            localVideoTrack.addSink(localVideoSink)
            localVideoTrack.setEnabled(true)

            videoSource = localVideoSource

            return localVideoTrack
        }

        return null
    }

    private fun createAudioTrack(): AudioTrack? {
        Log.d(this, "createAudioTrack()")
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(isMicrophoneEnabled)
        return localAudioTrack
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        Log.d(this, "setMicrophoneEnabled() enabled=$enabled")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "setMicrophoneEnabled() executor start")
            isMicrophoneEnabled = enabled
            localAudioTrack?.setEnabled(enabled)
            callActivity?.onMicrophoneEnabled(enabled)
            Log.d(this, "setMicrophoneEnabled() executor end")
        }
    }

    fun initVideo() {
        Log.d(this, "initVideo()")
        Utils.checkIsOnMainThread()
        reportStateChange(CallState.WAITING)

        // must be created in Main/GUI Thread!
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(callActivity!!.getContext())
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory

        Log.d(this, "initVideo() video acceleration: ${binder.getSettings().videoHardwareAcceleration}")

        if (binder.getSettings().videoHardwareAcceleration) {
            val enableIntelVp8Encoder = true
            val enableH264HighProfile = true
            encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, enableIntelVp8Encoder, enableH264HighProfile)
            decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
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
        execute {
            Log.d(this, "handleAnswer() executor start")
            peerConnection!!.setRemoteDescription(object : DefaultSdpObserver() {
                override fun onSetSuccess() {
                    super.onSetSuccess()
                    Log.d(this, "onSetSuccess()")
                }

                override fun onSetFailure(s: String) {
                    super.onSetFailure(s)
                    Log.d(this, "onSetFailure() s=$s")
                }
            }, SessionDescription(SessionDescription.Type.ANSWER, remoteDesc))
            Log.d(this, "handleAnswer() executor end")
        }
    }

    private fun reportStateChange(state: CallState) {
        Log.d(this, "reportStateChange() $state")

        this.state = state
        callActivity?.onStateChange(state)
    }

    fun setStatsCollector(statsCollector: RTCStatsCollectorCallback?) {
        Log.d(this, "setStatsCollector()")
        if (statsCollector == null) {
            Log.d(this, "setStatsCollector() stop")
            statsTimer.cancel()
            statsTimer.purge()
        } else {
            Log.d(this, "setStatsCollector() start")
            statsTimer = Timer()
            statsTimer.schedule(object : TimerTask() {
                override fun run() {
                    execute {
                        Log.d(this, "setStatsCollector() executor start")
                        try {
                            peerConnection!!.getStats(statsCollector)
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

    fun setCallContext(activity: CallContext?) {
        this.callActivity = activity
    }

    // called when call accepted
    fun initIncoming() {
        Log.d(this, "initIncoming()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "initIncoming() executor start")
            val remoteAddress = commSocket!!.remoteSocketAddress as InetSocketAddress
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            peerConnection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    Log.d(this, "onIceGatheringChange() $iceGatheringState")
                    super.onIceGatheringChange(iceGatheringState)

                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        try {
                            val settings = binder.getSettings()
                            val ownPublicKey = settings.publicKey
                            val ownSecretKey = settings.secretKey
                            val pw = PacketWriter(commSocket!!)
                            val obj = JSONObject()
                            obj.put("action", "connected")
                            obj.put("answer", peerConnection!!.localDescription.description)
                            val encrypted = Crypto.encryptMessage(
                                obj.toString(),
                                contact.publicKey,
                                ownPublicKey,
                                ownSecretKey
                            )
                            if (encrypted != null) {
                                Log.d(this, "onIceGatheringChange() send connected")
                                pw.writeMessage(encrypted)
                                callActivity?.onRemoteAddressChange(remoteAddress, true)
                                reportStateChange(CallState.CONNECTED)
                            } else {
                                Log.d(this, "onIceGatheringChange() encryption failed")
                                reportStateChange(CallState.ERROR_COMMUNICATION)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            reportStateChange(CallState.ERROR_COMMUNICATION)
                        }
                        closeSocket(commSocket!!)
                    }
                }

                override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                    Log.d(this, "onIceConnectionChange() $iceConnectionState")
                    super.onIceConnectionChange(iceConnectionState)
                    when (iceConnectionState) {
                        IceConnectionState.DISCONNECTED -> reportStateChange(CallState.ENDED)
                        IceConnectionState.FAILED -> reportStateChange(CallState.ERROR_COMMUNICATION)
                        IceConnectionState.CONNECTED -> reportStateChange(CallState.CONNECTED)
                        else -> return
                    }
                    closeSocket(commSocket!!)
                }

                override fun onConnectionChange(state: PeerConnectionState) {
                    Log.d(this, "onConnectionChange() state=$state")
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(this, "onAddStream()")
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d(this, "onDataChannel()")
                    super.onDataChannel(dataChannel)
                    this@RTCCall.dataChannel = dataChannel
                    this@RTCCall.dataChannel!!.registerObserver(this@RTCCall)
                    callActivity?.onCameraEnabled()
                }
            })!!

            createPeerConnection()

            Log.d(this, "setting remote description")
            peerConnection!!.setRemoteDescription(object : DefaultSdpObserver() {
                override fun onSetSuccess() {
                    super.onSetSuccess()
                    Log.d(this, "creating answer...")
                    peerConnection!!.createAnswer(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription) {
                            Log.d(this, "onCreateSuccess")
                            super.onCreateSuccess(sessionDescription)
                            peerConnection!!.setLocalDescription(
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

            Log.d(this, "initIncoming() executor end")
        }
    }

    // send over data channel
    private fun hangupInternal() {
        Log.d(this, "hangupInternal")

        // send hangup over WebRTC channel
        val o = JSONObject()
        o.put(STATE_CHANGE_MESSAGE, HANGUP_MESSAGE)

        if (sendOnDataChannel(o.toString())) {
            reportStateChange(CallState.DISMISSED)
        } else {
            reportStateChange(CallState.ERROR_COMMUNICATION)
        }
    }

    fun hangup() {
        Log.d(this, "hangup")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "hangup() executor start")
            hangupInternal()
            Log.d(this, "hangup() executor end")
        }
    }

    // send over initial socket
    private fun declineInternal() {
        Log.d(this, "declineInternal()")
        // send decline over initial socket
        val socket = commSocket
        if (socket != null && !socket.isClosed) {
            val pw = PacketWriter(socket)
            val settings = binder.getSettings()
            val ownPublicKey = settings.publicKey
            val ownSecretKey = settings.secretKey

            val encrypted = Crypto.encryptMessage(
                "{\"action\":\"dismissed\"}",
                contact.publicKey,
                ownPublicKey,
                ownSecretKey
            )

            if (encrypted != null) {
                try {
                    Log.d(this, "declineInternal() write dismissed message to socket")
                    pw.writeMessage(encrypted)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                reportStateChange(CallState.DISMISSED)
            } else {
                reportStateChange(CallState.ERROR_COMMUNICATION)
            }
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

    fun cleanup() {
        Log.d(this, "cleanup()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "cleanup() executor start")
            setCallContext(null)
            setStatsCollector(null)

            try {
                Log.d(this, "cleanup() close socket")
                commSocket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                peerConnection?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Log.d(this, "cleanup() executor end")
        }

        // wait for tasks to finish
        executor.shutdown()
        executor.awaitTermination(4L, TimeUnit.SECONDS)
        Log.d(this, "cleanup() done")
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

    interface CallContext {
        fun onStateChange(state: CallState)
        fun onLocalVideoEnabled(enabled: Boolean)
        fun onRemoteVideoEnabled(enabled: Boolean)
        fun onFrontFacingCamera(enabled: Boolean)
        fun onMicrophoneEnabled(enabled: Boolean)
        fun onCameraEnabled()
        fun onRemoteAddressChange(address: InetSocketAddress, isConnected: Boolean)

        fun showTextMessage(message: String)
        fun getContext(): Context
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
        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"

        private const val STATE_CHANGE_MESSAGE = "StateChange"
        private const val CAMERA_DISABLE_MESSAGE = "CameraDisabled"
        private const val CAMERA_ENABLE_MESSAGE = "CameraEnabled"
        private const val HANGUP_MESSAGE = "Hangup"
        private const val AUDIO_TRACK_ID = "audio1"
        private const val VIDEO_TRACK_ID = "video1"

        private const val SOCKET_TIMEOUT_MS = 3000L

        private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

        private fun debugPacket(label: String, msg: ByteArray?) {
            if (msg != null) {
                Log.d(this, "$label: ${msg.size}, ${msg.toHex()}")
            } else {
                Log.d(this, "$label: message is null!")
            }
        }

        private fun closeSocket(socket: Socket?) {
            try {
                socket?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createIncomingCall(binder: MainService.MainBinder, socket: Socket) {
            Thread {
                try {
                    createIncomingCallInternal(binder, socket)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //decline()
                }
            }.start()
        }

        private fun createIncomingCallInternal(binder: MainService.MainBinder, socket: Socket) {
            Log.d(this, "createIncomingCallInternal()")

            val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
            val settings = binder.getSettings()
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

            var contact = binder.getContacts().getContactByPublicKey(otherPublicKey)
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
                contact = Contact("", otherPublicKey.clone(), ArrayList())
            }

            // suspicious change of identity in during peerConnection...
            if (!contact.publicKey.contentEquals(otherPublicKey)) {
                Log.d(this, "createIncomingCallInternal() suspicious change of key")
                decline()
                return
            }

            run {
                // remember latest working address and set state
                val workingAddress = InetSocketAddress(remoteAddress.address, MainService.serverPort)
                val storedContact = binder.getContacts().getContactByPublicKey(contact.publicKey)
                if (storedContact != null) {
                    storedContact.lastWorkingAddress = workingAddress
                } else {
                    contact.lastWorkingAddress = workingAddress
                }
            }

            val obj = JSONObject(decrypted)
            val action = obj.optString("action", "")
            Log.d(this, "createIncomingCallInternal() action: $action")
            when (action) {
                "call" -> {
                    if (binder.getCurrentCall() != null) {
                        Log.d(this, "createIncomingCallInternal() call in progress => decline")
                        decline() // TODO: send busy
                        return
                    }

                    Log.d(this, "createIncomingCallInternal() got WebRTC offer")

                    // someone calls us
                    val offer = obj.getString("offer")
                    if (offer == null) {
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

                    debugPacket("createIncomingCallInternal() send ringing message: ", encrypted)
                    pw.writeMessage(encrypted)

                    val currentCall = RTCCall(binder, contact, socket, offer)
                    binder.setCurrentCall(currentCall)
                    try {
                        val activity = MainActivity.instance
                        if (activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            Log.d(this, "createIncomingCallInternal() start incoming call from stored MainActivity")
                            val intent = Intent(activity, CallActivity::class.java)
                            intent.action = "ACTION_INCOMING_CALL"
                            intent.putExtra("EXTRA_CONTACT", contact)
                            activity.startActivity(intent)
                        } else {
                            Log.d(this, "createIncomingCallInternal() start incoming call from Service")
                            val service = binder.getService()
                            val intent = Intent(service, CallActivity::class.java)
                            intent.action = "ACTION_INCOMING_CALL"
                            intent.putExtra("EXTRA_CONTACT", contact)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            service.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        binder.setCurrentCall(null)
                        currentCall.cleanup()
                        e.printStackTrace()
                    }
                }
                "ping" -> {
                    Log.d(this, "createIncomingCallInternal() ping...")
                    // someone wants to know if we are online
                    contact.state = Contact.State.CONTACT_ONLINE
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
                    if (obj.optString("status", "") == "offline") {
                        contact.state = Contact.State.CONTACT_ONLINE
                    } else {
                        Log.d(this, "createIncomingCallInternal() received unknown status_change: ${obj.getString("status")}")
                    }
                }
            }
        }
    }
}
