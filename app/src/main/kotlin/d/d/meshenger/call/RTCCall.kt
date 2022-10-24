package d.d.meshenger.call

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.MaterialColors
import d.d.meshenger.*
import d.d.meshenger.R
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
    private lateinit var connection: PeerConnection
    private var constraints: MediaConstraints? = null
    private var offer: String? = null

    private var remoteVideoSink: ProxyVideoSink? = null
    private var localVideoSink: ProxyVideoSink? = null

    private var capturer: CameraVideoCapturer? = null
    private var context: Context
    private var contact: Contact
    private var ownPublicKey: ByteArray
    private var ownSecretKey: ByteArray
    private var iceServers = mutableListOf<IceServer>()
    private var onStateChangeListener: OnStateChangeListener?
    private var callActivity: CallActivity?
    private var binder: MainService.MainBinder
    private val statsTimer = Timer()
    private val executor = Executors.newSingleThreadExecutor()

    //Migrated to Unified Plan
    //private var upStream: MediaStream? = null
    private lateinit var dataChannel: DataChannel
    var isSpeakerEnabled = false

    // local video
    var isVideoEnabled = false
        set(enabled) {
            Log.d(this, "setVideoEnabled: $enabled")
            field = enabled
            try {
                if (enabled) {
                    capturer!!.startCapture(1280, 720, 25)
                    callActivity!!.setLocalVideoEnabled(true)
                } else {
                    callActivity!!.setLocalVideoEnabled(false)
                    capturer!!.stopCapture()
                }
                val o = JSONObject()
                if (enabled) {
                    o.put(STATE_CHANGE_MESSAGE, CAMERA_ENABLE_MESSAGE)
                } else {
                    o.put(STATE_CHANGE_MESSAGE, CAMERA_DISABLE_MESSAGE)
                }
                Thread {
                    while (!::dataChannel.isInitialized) {
                        Thread.sleep(1000)
                    }
                    dataChannel.send(
                        DataChannel.Buffer(
                            ByteBuffer.wrap(
                                o.toString().toByteArray()
                            ), false
                        )
                    )
                }.start()
            } catch (e: JSONException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

    // called for incoming calls
    constructor(
        context: Context,
        binder: MainService.MainBinder,
        contact: Contact,
        commSocket: Socket?,
        offer: String?
    ) {
        Log.d(this, "RTCCall created")

        this.context = context
        this.contact = contact
        this.commSocket = commSocket
        this.onStateChangeListener = null
        this.callActivity = null
        this.binder = binder
        this.ownPublicKey = binder.getSettings().publicKey
        this.ownSecretKey = binder.getSettings().secretKey
        this.offer = offer

        // usually empty
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }

        initVideo(context)
    }

    // called for outgoing calls
    constructor(
        context: Context,
        binder: MainService.MainBinder,
        contact: Contact,
        listener: OnStateChangeListener
    ) {
        Log.d(this, "RTCCall created")

        this.context = context
        this.contact = contact
        this.commSocket = null
        this.onStateChangeListener = listener
        this.callActivity = null
        this.binder = binder
        this.ownPublicKey = binder.getSettings().publicKey
        this.ownSecretKey = binder.getSettings().secretKey

        // usually empty
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }

        initVideo(context)

        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            context.setTheme(R.style.AppTheme_Dark)
        } else {
            context.setTheme(R.style.AppTheme_Light)
        }

        Thread {
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            connection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {

                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {

                    super.onIceGatheringChange(iceGatheringState)
                    val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())

                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        Log.d(this, "transferring offer...")
                        try {
                            commSocket = createCommSocket(contact)
                            if (commSocket == null) {
                                Log.d(this, "cannot establish connection")
                                reportStateChange(CallState.ERROR)
                                //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                return
                            }
                            val remote_address =
                                commSocket!!.remoteSocketAddress as InetSocketAddress

                            Log.d(this, "outgoing call from remote address: $remote_address")

                            // remember latest working address
                            contact.lastWorkingAddress =
                                InetSocketAddress(remote_address.address, MainService.serverPort)

                            Log.d(this, "connect..")
                            val pr = PacketReader(commSocket!!)
                            reportStateChange(CallState.CONNECTING)
                            run {
                                Log.d(this, "send call message")
                                val obj = JSONObject()
                                obj.put("action", "call")
                                obj.put("offer", connection.localDescription.description)
                                val encrypted = Crypto.encryptMessage(
                                    obj.toString(),
                                    contact.publicKey,
                                    ownPublicKey,
                                    ownSecretKey
                                )
                                if (encrypted == null) {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return
                                }

                                val pw = PacketWriter(commSocket!!)
                                pw.writeMessage(encrypted)
                            }
                            run {
                                Log.d(this, "read ringing message")
                                val response = pr.readMessage()
                                val decrypted = Crypto.decryptMessage(
                                    response,
                                    otherPublicKey,
                                    ownPublicKey,
                                    ownSecretKey
                                )
                                if (decrypted == null || !contact.publicKey.contentEquals(otherPublicKey)) {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return
                                }
                                val obj = JSONObject(decrypted)
                                if (obj.optString("action", "") != "ringing") {
                                    Log.d(this, "action not equals ringing")
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return
                                }
                                Log.d(this, "ringing...")
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
                                if (decrypted == null || !contact.publicKey.contentEquals(otherPublicKey)) {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    return
                                }
                                val obj = JSONObject(decrypted)
                                when (val action = obj.getString("action")) {
                                    "connected" -> {
                                        reportStateChange(CallState.CONNECTED)
                                        handleAnswer(obj.getString("answer"))
                                        // contact accepted receiving call
                                        //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ACCEPTED);
                                    }
                                    "dismissed" -> {
                                        Log.d(this, "dismissed")
                                        closeCommSocket()
                                        reportStateChange(CallState.DISMISSED)
                                        // contact declined receiving call
                                        //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_DECLINED);
                                    }
                                    else -> {
                                        Log.d(this, "unknown action reply: $action")
                                        closeCommSocket()
                                        reportStateChange(CallState.ERROR)
                                        //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            closeCommSocket()
                            e.printStackTrace()
                            reportStateChange(CallState.ERROR)
                            //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                        }
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
            dataChannel = connection.createDataChannel("data", DataChannel.Init())
            dataChannel.registerObserver(this)

            callActivity!!.showVideoButton()

            //Migrated to Unified Plan
            //val config = RTCConfiguration(iceServers)
            //config.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE
            //connection.setConfiguration(config)

            addTrack()
            connection.createOffer(object : DefaultSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    connection.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                }
            }, constraints)
        }.start()
    }

    private fun createCommSocket(contact: Contact): Socket? {
        Log.d(this, "createCommSocket")

        val addresses = contact.getAllSocketAddresses()
        for (address in addresses) {
            Log.d(this, "try address: $address")
            //callActivity?.showTextMessage("call $address")

            val socket = AddressUtils.establishConnection(address)
            if (socket != null) {
                return socket
            }
        }
        return null
    }

    private fun closeCommSocket() {
        Log.d(this, "closeCommSocket")
        if (commSocket != null) {
            try {
                commSocket!!.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            commSocket = null
        }
    }

    private fun closePeerConnection() {
        Log.d(this, "closePeerConnection")
        try {
            connection.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setRemoteRenderer(remoteVideoSink: ProxyVideoSink?) {
        this.remoteVideoSink = remoteVideoSink
    }

    fun setLocalRenderer(localVideoSink: ProxyVideoSink?) {
        this.localVideoSink = localVideoSink
        //this.localVideoSink?.setMirror(!mIsCameraSwitched)
    }

    private var mIsCameraSwitched = false;

    override fun onBufferedAmountChange(l: Long) {
        // nothing to do
    }

    override fun onStateChange() {
        // nothing to do
    }

    override fun onMessage(buffer: DataChannel.Buffer) {
        val data = ByteArray(buffer.data.remaining())
        buffer.data[data]
        val s = String(data)
        try {
            Log.d(this, "onMessage: $s")
            val o = JSONObject(s)
            if (o.has(STATE_CHANGE_MESSAGE)) {
                when (o.getString(STATE_CHANGE_MESSAGE)) {
                    CAMERA_ENABLE_MESSAGE -> callActivity!!.setRemoteVideoEnabled(true)
                    CAMERA_DISABLE_MESSAGE -> callActivity!!.setRemoteVideoEnabled(false)
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
        if (capturer != null) {
            try {
                capturer!!.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    private fun handleMediaStream(stream: MediaStream) {
        Log.d(this, "handleMediaStream")
        if (remoteVideoSink == null || stream.videoTracks.size == 0) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            stream.videoTracks[0].addSink(remoteVideoSink)
        }
    }

    private fun addTrack() {
        //Migrated to Unified Plan
        //upStream = factory.createLocalMediaStream("stream1")
        try {
            connection.addTrack(getAudioTrack(), listOf("stream1"))
            connection.addTrack(getVideoTrack(), listOf("stream1"))
        } catch (e: Exception){
            e.printStackTrace()
        }
    }

    private fun createCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = Camera1Enumerator()
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    fun switchFrontFacing() {
        if (capturer != null) {
            capturer!!.switchCamera(null)
            mIsCameraSwitched = !mIsCameraSwitched
            //this.localVideoSink?.setMirror(!mIsCameraSwitched)
        }
    }

    private fun getVideoTrack(): VideoTrack? {
        capturer = createCapturer()
        if (capturer != null) {
            val surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", CallActivity.eglBaseContext)
            val videoSource = factory.createVideoSource(capturer!!.isScreencast)
            capturer!!.initialize(
                surfaceTextureHelper,
                this@RTCCall.context,
                videoSource.capturerObserver
            )

            val localVideoTrack = factory.createVideoTrack("video1", videoSource)
            localVideoTrack.addSink(localVideoSink)
            localVideoTrack.setEnabled(true)
            return localVideoTrack
        }
        return null
    }

    private fun getAudioTrack(): AudioTrack {
        return factory.createAudioTrack("audio1", factory.createAudioSource(MediaConstraints())
        )
    }

    private fun initVideo(c: Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(c)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val videoCodecHwAcceleration = true
        val encoderFactory: VideoEncoderFactory
        val decoderFactory: VideoDecoderFactory
        if (videoCodecHwAcceleration) {
            encoderFactory = HWVideoEncoderFactory(CallActivity.eglBaseContext, true, true)
            decoderFactory = HWVideoDecoderFactory(CallActivity.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        constraints = MediaConstraints()
        constraints!!.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        constraints!!.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        constraints!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        //initVideoTrack();
    }

    private fun handleAnswer(remoteDesc: String) {
        connection.setRemoteDescription(object : DefaultSdpObserver() {
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
        this.state = state
        if (onStateChangeListener != null) {
            onStateChangeListener!!.onStateChange(state)
        }
    }

    fun setStatsCollector(statsCollector: RTCStatsCollectorCallback?) {
        if (statsCollector != null) {
            try {
                statsTimer.schedule(object : TimerTask() {
                    override fun run() {
                        executor.execute { connection.getStats(statsCollector) }
                    }
                }, 0L, StatsReportUtil.STATS_INTERVAL_MS)
            } catch (e: Exception) {
                Log.e(this, "Can not schedule statistics timer $e");
            }
        } else {
            statsTimer.cancel()
        }
    }

    fun setCallActivity(activity: CallActivity?) {
        this.callActivity = activity
    }

    fun setOnStateChangeListener(listener: OnStateChangeListener?) {
        this.onStateChangeListener = listener
        Thread {
            val rtcConfig = RTCConfiguration(emptyList())
            rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
            rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE

            connection = factory.createPeerConnection(rtcConfig, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState)

                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        Log.d(this, "onIceGatheringChange")
                        try {
                            val pw = PacketWriter(commSocket!!)
                            val obj = JSONObject()
                            obj.put("action", "connected")
                            obj.put("answer", connection.localDescription.description)
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
                                reportStateChange(CallState.ERROR)
                            }
                            //new Thread(new SpeakerRunnable(commSocket)).start();
                        } catch (e: Exception) {
                            e.printStackTrace()
                            reportStateChange(CallState.ERROR)
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
                    this@RTCCall.dataChannel.registerObserver(this@RTCCall)
                    callActivity!!.showVideoButton()
                }
            })!!

            addTrack()

            Log.d(this, "setting remote description")
            connection.setRemoteDescription(object : DefaultSdpObserver() {
                override fun onSetSuccess() {
                    super.onSetSuccess()
                    Log.d(this, "creating answer...")
                    connection.createAnswer(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription) {
                            Log.d(this, "onCreateSuccess")
                            super.onCreateSuccess(sessionDescription)
                            connection.setLocalDescription(
                                DefaultSdpObserver(),
                                sessionDescription
                            )
                        }

                        override fun onCreateFailure(s: String) {
                            super.onCreateFailure(s)
                            Log.d(this, "onCreateFailure: $s")
                        }
                    }, constraints)
                }
            }, SessionDescription(SessionDescription.Type.OFFER, offer))
        }.start()
    }

    fun decline() {
        Thread {
            try {
                Log.d(this, "declining...")
                if (commSocket != null) {
                    val pw = PacketWriter(commSocket!!)
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
            } finally {
                cleanup()
            }
        }.start()
    }

    fun cleanup() {
        closeCommSocket()
        if (state == CallState.CONNECTED) {
            /*for(AudioTrack track : this.upStream.audioTracks){
                track.setEnabled(false);
                track.dispose();
            }
            for(VideoTrack track : this.upStream.videoTracks) track.dispose();*/
            closePeerConnection()
            //factory.dispose();
        }
        statsTimer.cancel()
    }

    fun hangUp() {
        Thread {
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
                closePeerConnection()
                statsTimer.cancel()
                reportStateChange(CallState.ENDED)
            } catch (e: IOException) {
                e.printStackTrace()
                reportStateChange(CallState.ERROR)
            }
        }.start()
    }

    enum class CallState {
        CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR
    }

    fun interface OnStateChangeListener {
        fun onStateChange(state: CallState)
    }

    class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            if (target == null) {
                Log.d(ContentValues.TAG, "Dropping frame in proxy because target is null.")
                return
            }
            target!!.onFrame(frame)
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
    }
}