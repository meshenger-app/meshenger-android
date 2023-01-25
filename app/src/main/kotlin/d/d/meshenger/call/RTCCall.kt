package d.d.meshenger.call

import android.content.Context
import d.d.meshenger.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import java.net.*
import java.nio.ByteBuffer
import java.util.*

class RTCCall : RTCPeerConnection, DataChannel.Observer {
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var offer: String? = null

    private var remoteVideoSink: ProxyVideoSink? = null
    private var localVideoSink: ProxyVideoSink? = null

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null

    private lateinit var eglBase: EglBase
    private var statsTimer = Timer()
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

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
    ) : super(binder, contact, commSocket) {
        Log.d(this, "RTCCall() created for incoming calls")

        this.offer = offer

        createMediaConstraints()
    }

    // called for outgoing calls
    constructor(
        binder: MainService.MainBinder,
        contact: Contact
    ) : super(binder, contact, null) {
        Log.d(this, "RTCCall() created for outgoing calls")

        createMediaConstraints()
    }

    private fun createMediaConstraints() {
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        val enable = if (binder.getSettings().disableAudioProcessing) {
            "false"
        } else {
            "true"
        }

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, enable))
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

    override fun handleAnswer(remoteDesc: String) {
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

    override fun reportStateChange(state: CallState) {
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

    fun cleanup() {
        Log.d(this, "cleanup()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "cleanup() executor start")
            setCallContext(null)
            setStatsCollector(null)

            try {
                peerConnection?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Log.d(this, "cleanup() executor end")
        }

        super.cleanupRTCPeerConnection()

        Log.d(this, "cleanup() done")
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
    }
}
