package d.d.meshenger.call

import android.content.Context
import android.media.MediaCodecInfo
import d.d.meshenger.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import org.webrtc.PeerConnection.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback
import java.net.*
import java.nio.ByteBuffer
import java.util.*

class RTCCall : RTCPeerConnection {
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
    private var useFrontFacingCamera = true

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
                        // start with hard default settings
                        videoCapturer!!.startCapture(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FRAMERATE)
                        callActivity!!.onLocalVideoEnabled(true)
                        callActivity!!.onCameraChanged()
                    } else {
                        callActivity!!.onLocalVideoEnabled(false)
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

    fun changeCaptureFormat(degradation: String, width: Int, height: Int, framerate: Int) {
        Log.d(this, "changeCaptureFormat() degradation=$degradation format=${width}x${height}@${framerate}")

        execute {
            if (!getCameraEnabled() || peerConnection == null || videoCapturer == null || videoSource == null) {
                Log.w(this, "changeCaptureFormat() cannot change capture format")
            } else {
                val sender = peerConnection!!.senders.first { it.track()?.kind() == "video" }
                val degradationPreference = convertDegradationPreference(degradation)
                if (sender != null && degradationPreference != null) {
                    // set on RTPSender
                    sender.parameters.degradationPreference = degradationPreference
                } else {
                    Log.w(this, "changeCaptureFormat() cannot set degradation preference")
                }

                if (width > 0 && height > 0 && framerate > 0) {
                    Log.w(this, "changeCaptureFormat() apply")
                    videoSource!!.adaptOutputFormat(width, height, framerate)
                } else {
                    Log.w(this, "changeCaptureFormat() invalid format")
                }
            }
        }
    }

    val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(l: Long) {
            // nothing to do
        }

        override fun onStateChange() {
            val channel = dataChannel
            if (channel == null) {
                Log.d(this, "onStateChange dataChannel: is null")
            } else {
                Log.d(this, "onStateChange dataChannel: ${channel.state()}")
                if (channel.state() == DataChannel.State.OPEN) {
                    callActivity?.onDataChannelReady()
                }
            }
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
                        HANGUP_MESSAGE -> reportStateChange(CallState.ENDED)
                        else -> {}
                    }
                } else {
                    Log.d(this, "onMessage() unknown message: $s")
                }

            } catch (e: JSONException) {
                e.printStackTrace()
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
        val settings = binder.getSettings()

        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        val enable = if (settings.disableAudioProcessing) {
            "false"
        } else {
            "true"
        }

        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", enable))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", enable))
    }

    fun initOutgoing() {
        Log.d(this, "initOutgoing()")
        Utils.checkIsOnMainThread()

        execute {
            Log.d(this, "initOutgoing() executor start")
            val rtcConfig = RTCConfiguration(emptyList())
            val settings = binder.getSettings()
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE
            rtcConfig.enableCpuOveruseDetection = !settings.disableCpuOveruseDetection // true by default

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
            dataChannel!!.registerObserver(dataChannelObserver)

            addTracks()

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

    private fun addTracks() {
        try {
            val rtpSenderAudio = peerConnection!!.addTrack(createAudioTrack(), listOf("stream1"))
            val rtpSenderVideo = peerConnection!!.addTrack(createVideoTrack(), listOf("stream1"))

            // needed to have a high resolution/framerate
            for (encoding in rtpSenderVideo.parameters.encodings) {
                encoding.maxBitrateBps = DEFAULT_MAX_BITRATE_BPS
                encoding.scaleResolutionDownBy = 2.0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFrontCameraEnabled(): Boolean {
        return useFrontFacingCamera
    }

    fun switchCamera(isFrontFacing: Boolean) {
        Log.d(this, "switchCamera() isFrontFacing=$isFrontFacing")
        Utils.checkIsOnMainThread()
        if (videoCapturer != null && isFrontFacing != useFrontFacingCamera) {
            val enumerator = Camera1Enumerator()

            val deviceName = enumerator.deviceNames.find { isFrontFacing == enumerator.isFrontFacing(it) }
            if (deviceName != null) {
                // switch to camera
                (videoCapturer as CameraVideoCapturer).switchCamera(object :
                    CameraVideoCapturer.CameraSwitchHandler {
                    override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                        useFrontFacingCamera = isFrontCamera
                        val formats = enumerator.getSupportedFormats(deviceName)
                        // tell CaptureQualityController that the camera changed
                        callActivity!!.onCameraChange(deviceName, true, formats)
                        callActivity!!.onCameraChanged()
                    }

                    override fun onCameraSwitchError(errorDescription: String) {
                        callActivity?.showTextMessage(errorDescription)
                    }
                }, deviceName)
            }
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        videoCapturer = null

        val enumerator = Camera1Enumerator()
        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) }
        if (deviceName != null) {
            // select front facing by default
            videoCapturer = enumerator.createCapturer(deviceName, null)
            val formats = enumerator.getSupportedFormats(deviceName)
            // for debug/settings menu
            callActivity!!.onCameraChange(deviceName, true, formats)
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

    private fun createJavaAudioDevice(): AudioDeviceModule? {
        // Set audio record error callbacks
        val audioRecordErrorCallback: AudioRecordErrorCallback = object : AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                Log.e(this, "onWebRtcAudioRecordInitError: $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }

            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String
            ) {
                Log.e(this, "onWebRtcAudioRecordStartError: $errorCode. $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }

            override fun onWebRtcAudioRecordError(errorMessage: String) {
                Log.e(this, "onWebRtcAudioRecordError: $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }
        }
        val audioTrackErrorCallback: AudioTrackErrorCallback = object : AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                Log.e(this, "onWebRtcAudioTrackInitError: $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }

            override fun onWebRtcAudioTrackStartError(
                errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String
            ) {
                Log.e(this, "onWebRtcAudioTrackStartError: $errorCode. $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }

            override fun onWebRtcAudioTrackError(errorMessage: String) {
                Log.e(this, "onWebRtcAudioTrackError: $errorMessage")
                callActivity!!.showTextMessage(errorMessage)
            }
        }

        // Set audio record state callbacks
        val audioRecordStateCallback: AudioRecordStateCallback = object : AudioRecordStateCallback {
            override fun onWebRtcAudioRecordStart() {
                Log.i(this, "Audio recording starts")
            }

            override fun onWebRtcAudioRecordStop() {
                Log.i(this, "Audio recording stops")
            }
        }

        // Set audio track state callbacks
        val audioTrackStateCallback: AudioTrackStateCallback = object : AudioTrackStateCallback {
            override fun onWebRtcAudioTrackStart() {
                Log.i(this, "Audio playout starts")
            }

            override fun onWebRtcAudioTrackStop() {
                Log.i(this, "Audio playout stops")
            }
        }

        return JavaAudioDeviceModule.builder(callActivity!!.getContext())
            //.setSamplesReadyCallback(saveRecordedAudioToFile)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioRecordStateCallback(audioRecordStateCallback)
            .setAudioTrackStateCallback(audioTrackStateCallback)
            .createAudioDeviceModule()
    }

    fun initVideo() {
        Log.d(this, "initVideo()")
        Utils.checkIsOnMainThread()

        val settings = binder.getSettings()
        reportStateChange(CallState.WAITING)

        // must be created in Main/GUI Thread!
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(callActivity!!.getContext())
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory

        Log.d(this, "initVideo() video acceleration: ${settings.videoHardwareAcceleration}")

        if (binder.getSettings().videoHardwareAcceleration) {
            val enableIntelVp8Encoder = true
            val enableH264HighProfile = true
            encoderFactory = CustomHardwareVideoEncoderFactory(eglBase.eglBaseContext, enableIntelVp8Encoder, enableH264HighProfile, object :
                CustomHardwareVideoEncoderFactory.VideoEncoderSupportedCallback {
                override fun isSupportedVp8(info: MediaCodecInfo): Boolean {return false }
                override fun isSupportedVp9(info: MediaCodecInfo): Boolean {return false }
                override fun isSupportedH264(info: MediaCodecInfo): Boolean { return true }
            })
            decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }

        val adm = if (settings.disableAudioProcessing) {
            null
        } else {
            createJavaAudioDevice()
        }

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
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
        Log.d(this, "reportStateChange() state=$state")

        this.state = state
        callActivity!!.onStateChange(state)
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
            val settings = binder.getSettings()
            val remoteAddress = commSocket!!.remoteSocketAddress as InetSocketAddress
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE
            rtcConfig.enableCpuOveruseDetection = !settings.disableCpuOveruseDetection // true by default

            peerConnection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    Log.d(this, "onIceGatheringChange() $iceGatheringState")
                    super.onIceGatheringChange(iceGatheringState)

                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        try {
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
                                // connected state will be reported by WebRTC onIceConnectionChange()
                                //reportStateChange(CallState.CONNECTED)
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
                    this@RTCCall.dataChannel!!.registerObserver(dataChannelObserver)
                }
            })!!

            addTracks()

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

    // send over data channel, when the call is established
    private fun hangupInternal() {
        Log.d(this, "hangupInternal")

        // send hangup over WebRTC channel
        val o = JSONObject()
        o.put(STATE_CHANGE_MESSAGE, HANGUP_MESSAGE)

        if (sendOnDataChannel(o.toString())) {
            reportStateChange(CallState.ENDED)
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

        Log.d(this, "cleanup() executor start")
        setCallContext(null)
        setStatsCollector(null)

        execute {
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
        fun getContext(): Context
        fun onStateChange(state: CallState)
        fun onLocalVideoEnabled(enabled: Boolean)
        fun onRemoteVideoEnabled(enabled: Boolean)
        fun onMicrophoneEnabled(enabled: Boolean)
        fun onCameraChange(name: String, isFrontFacing: Boolean, formats: List<CaptureFormat>)
        fun onCameraChanged()
        fun onDataChannelReady()
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
        private const val HANGUP_MESSAGE = "Hangup"
        private const val AUDIO_TRACK_ID = "audio1"
        private const val VIDEO_TRACK_ID = "video1"
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
        const val DEFAULT_FRAMERATE = 25
        const val DEFAULT_MAX_BITRATE_BPS = 10000 * 1000 * 8 // 10 MB/s - just a high value

        private fun convertDegradationPreference(degradationPreferenceString: String): RtpParameters.DegradationPreference? {
            return when (degradationPreferenceString) {
                "maintain_resolution" -> RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                "maintain_framerate" -> RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE
                "balanced" -> RtpParameters.DegradationPreference.BALANCED
                "disabled" -> RtpParameters.DegradationPreference.DISABLED
                else -> {
                    Log.e(this, "convertDegradationPreference() Unknown degradationPreferenceString: $degradationPreferenceString")
                    null
                }
            }
        }
    }
}
