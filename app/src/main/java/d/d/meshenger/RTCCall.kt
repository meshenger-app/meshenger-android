package d.d.meshenger

import android.content.Context
import d.d.meshenger.Crypto.encryptMessage
import d.d.meshenger.Crypto.decryptMessage
import d.d.meshenger.Log.d
import d.d.meshenger.Contact
import org.webrtc.PeerConnection.IceServer
import d.d.meshenger.RTCCall.OnStateChangeListener
import d.d.meshenger.MainService.MainBinder
import d.d.meshenger.RTCCall.CallState
import androidx.appcompat.app.AppCompatDelegate
import d.d.meshenger.R
import d.d.meshenger.DefaultObserver
import org.webrtc.PeerConnection.IceGatheringState
import org.libsodium.jni.Sodium
import d.d.meshenger.MainService
import d.d.meshenger.PacketReader
import org.json.JSONObject
import d.d.meshenger.Crypto
import d.d.meshenger.PacketWriter
import org.webrtc.PeerConnection.IceConnectionState
import d.d.meshenger.DefaultSdpObserver
import android.os.Looper
import android.util.TypedValue
import android.content.res.Resources.Theme
import android.graphics.Color
import android.os.Handler
import androidx.annotation.ColorInt
import d.d.meshenger.RTCCall
import org.json.JSONException
import org.webrtc.*
import java.io.IOException
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*

//import org.webrtc.VideoCapturer;
class RTCCall : DataChannel.Observer {
    enum class CallState {
        CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR
    }

    private val StateChangeMessage = "StateChange"
    private val CameraDisabledMessage = "CameraDisabled"
    private val CameraEnabledMessage = "CameraEnabled"
    private var factory: PeerConnectionFactory? = null
    private var connection: PeerConnection? = null
    private var constraints: MediaConstraints? = null
    private var offer: String? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val localRenderer: SurfaceViewRenderer? = null
    private val sharedContext: EglBase.Context? = null
    private var capturer: CameraVideoCapturer? = null
    private var upStream: MediaStream? = null
    private var dataChannel: DataChannel? = null
    var isVideoEnabled = false
        set(enabled) {
            field = enabled
            try {
                if (enabled) {
                    capturer!!.startCapture(500, 500, 30)
                } else {
                    capturer!!.stopCapture()
                }
                val obj = JSONObject()
                obj.put(
                    StateChangeMessage,
                    if (enabled) CameraEnabledMessage else CameraDisabledMessage
                )
                log("setVideoEnabled: $obj")
                dataChannel!!.send(
                    DataChannel.Buffer(
                        ByteBuffer.wrap(
                            obj.toString().toByteArray()
                        ), false
                    )
                )
            } catch (e: JSONException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    private var context: Context
    private var contact: Contact
    private var ownPublicKey: ByteArray?
    private var ownSecretKey: ByteArray?
    private var iceServers: MutableList<IceServer>
    private var listener: OnStateChangeListener?
    private var binder: MainBinder
    var state: CallState? = null
    var commSocket: Socket?

    // called for incoming calls
    constructor(
        context: Context,
        binder: MainBinder,
        contact: Contact,
        commSocket: Socket?,
        offer: String?
    ) {
        this.context = context
        this.contact = contact
        this.commSocket = commSocket
        listener = null
        this.binder = binder
        ownPublicKey = binder.getSettings().publicKey
        ownSecretKey = binder.getSettings().secretKey
        ownPublicKey = ownPublicKey
        ownSecretKey = ownSecretKey
        this.offer = offer

        // usually empty
        iceServers = ArrayList()
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }
        initRTC(context)
    }

    // called for outgoing calls
    private constructor(
        context: Context,
        binder: MainBinder,
        contact: Contact,
        listener: OnStateChangeListener
    ) {
        this.context = context
        this.contact = contact
        commSocket = null
        this.listener = listener
        this.binder = binder
        ownPublicKey = binder.getSettings().publicKey
        ownSecretKey = binder.getSettings().secretKey
        log("RTCCall created")

        // usually empty
        iceServers = ArrayList()
        for (server in binder.getSettings().iceServers) {
            iceServers.add(IceServer.builder(server).createIceServer())
        }
        initRTC(context)
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            context.setTheme(R.style.AppTheme_Dark)
        } else {
            context.setTheme(R.style.AppTheme_Light)
        }
        Thread( Runnable {
            connection = factory!!.createPeerConnection(emptyList(), object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState)
                    val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        log("transferring offer...")
                        try {
                            commSocket = contact.createSocket()
                            if (commSocket == null) {
                                log("cannot establish connection")
                                reportStateChange(CallState.ERROR)
                                //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                return
                            }
                            val remote_address =
                                commSocket!!.remoteSocketAddress as InetSocketAddress
                            log("outgoing call from remote address: $remote_address")

                            // remember latest working address
                            contact.lastWorkingAddress =
                                InetSocketAddress(remote_address.address, MainService.serverPort)
                            log("connect..")
                            val pr = PacketReader(commSocket!!)
                            reportStateChange(CallState.CONNECTING)
                            run {
                                val obj = JSONObject()
                                obj.put("action", "call")
                                obj.put("offer", connection!!.localDescription.description)
                                val encrypted = encryptMessage(
                                    obj.toString(),
                                    contact.publicKey,
                                    ownPublicKey!!,
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
                                val response = pr.readMessage()
                                val decrypted = decryptMessage(
                                    response,
                                    otherPublicKey,
                                    ownPublicKey,
                                    ownSecretKey
                                )
                                if (decrypted == null || !Arrays.equals(
                                        contact.publicKey,
                                        otherPublicKey
                                    )
                                ) {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return
                                }
                                val obj = JSONObject(decrypted)
                                if (obj.optString("action", "") != "ringing") {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return
                                }
                                log("ringing...")
                                reportStateChange(CallState.RINGING)
                            }
                            run {
                                val response = pr.readMessage()
                                val decrypted = decryptMessage(
                                    response,
                                    otherPublicKey,
                                    ownPublicKey,
                                    ownSecretKey
                                )
                                if (decrypted == null || !Arrays.equals(
                                        contact.publicKey,
                                        otherPublicKey
                                    )
                                ) {
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    return
                                }
                                val obj = JSONObject(decrypted)
                                val action = obj.getString("action")
                                if (action == "connected") {
                                    reportStateChange(CallState.CONNECTED)
                                    handleAnswer(obj.getString("answer"))
                                    // contact accepted receiving call
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ACCEPTED);
                                } else if (action == "dismissed") {
                                    closeCommSocket()
                                    reportStateChange(CallState.DISMISSED)
                                    // contact declined receiving call
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_DECLINED);
                                } else {
                                    log("unknown action reply: $action")
                                    closeCommSocket()
                                    reportStateChange(CallState.ERROR)
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
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
                    log("onIceConnectionChange " + iceConnectionState.name)
                    super.onIceConnectionChange(iceConnectionState)
                    if (iceConnectionState == IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    super.onDataChannel(dataChannel)
                    this@RTCCall.dataChannel = dataChannel
                    dataChannel.registerObserver(this@RTCCall)
                }
            })
            connection!!.addStream(createStream())
            dataChannel = connection!!.createDataChannel("data", DataChannel.Init())
            dataChannel?.registerObserver(this)
            connection!!.createOffer(object : DefaultSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription) {
                    super.onCreateSuccess(sessionDescription)
                    connection!!.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                }
            }, constraints)
        }).start()
    }

    private fun closeCommSocket() {
        log("closeCommSocket")
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
        log("closePeerConnection")
        if (connection != null) {
            try {
                connection!!.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            connection = null
        }
    }

    fun setRemoteRenderer(remoteRenderer: SurfaceViewRenderer?) {
        this.remoteRenderer = remoteRenderer
    }

    fun switchFrontFacing() {
        if (capturer != null) {
            capturer!!.switchCamera(null)
        }
    }

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
        var obj: JSONObject? = null
        try {
            log("onMessage: $s")
            obj = JSONObject(s)
            if (obj.has(StateChangeMessage)) {
                val state = obj.getString(StateChangeMessage)
                when (state) {
                    CameraEnabledMessage, CameraDisabledMessage -> {
                        setRemoteVideoEnabled(state == CameraEnabledMessage)
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun setRemoteVideoEnabled(enabled: Boolean) {
        Handler(Looper.getMainLooper()).post {
            if (enabled) {
                remoteRenderer!!.setBackgroundColor(Color.TRANSPARENT)
            } else {
                val typedValue = TypedValue()
                val theme = context.theme
                theme.resolveAttribute(R.attr.backgroundCardColor, typedValue, true)
                @ColorInt val color = typedValue.data
                remoteRenderer!!.setBackgroundColor(color)
            }
        }
    }

    /*private void initLocalRenderer() {
        if (this.localRenderer != null) {
            log("really initng " + (this.sharedContext == null));
            this.localRenderer.init(this.sharedContext, null);
            this.localCameraTrack.addSink(localRenderer);
            this.capturer.startCapture(500, 500, 30);
        }
    }*/
    /*private void initVideoTrack() {
        this.sharedContext = EglBase.create().getEglBaseContext();
        this.capturer = createCapturer(true);
        this.localCameraTrack = factory.createVideoTrack("video1", factory.createVideoSource(capturer));
    }*/
    private fun createCapturer(): CameraVideoCapturer? {
        val enumerator: CameraEnumerator = Camera1Enumerator()
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    fun releaseCamera() {
        if (capturer != null) {
            try {
                capturer!!.stopCapture()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
        if (remoteRenderer != null) {
            remoteRenderer!!.release()
        }
        if (localRenderer != null) {
            localRenderer.release()
        }
    }

    private fun handleMediaStream(stream: MediaStream) {
        log("handleMediaStream")
        if (remoteRenderer == null || stream.videoTracks.size == 0) {
            return
        }
        Handler(Looper.getMainLooper()).post {

            //remoteRenderer.setBackgroundColor(Color.TRANSPARENT);
            remoteRenderer!!.init(sharedContext, null)
            stream.videoTracks[0].addSink(remoteRenderer)
        }
    }

    private fun createStream(): MediaStream? {
        upStream = factory!!.createLocalMediaStream("stream1")
        val audio =
            factory!!.createAudioTrack("audio1", factory!!.createAudioSource(MediaConstraints()))
        upStream?.addTrack(audio)
        upStream?.addTrack(videoTrack)
        //this.capturer.startCapture(500, 500, 30);
        return upStream
    }

    private val videoTrack: VideoTrack
        private get() {
            capturer = createCapturer()
            return factory!!.createVideoTrack("video1", factory!!.createVideoSource(capturer))
        }

    private fun initRTC(c: Context) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(c).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        constraints = MediaConstraints()
        constraints!!.optional.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        constraints!!.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"))
        constraints!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        //initVideoTrack();
    }

    private fun handleAnswer(remoteDesc: String) {
        connection!!.setRemoteDescription(object : DefaultSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                log("onSetSuccess")
            }

            override fun onSetFailure(s: String) {
                super.onSetFailure(s)
                log("onSetFailure: $s")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, remoteDesc))
    }

    private fun reportStateChange(state: CallState) {
        this.state = state
        if (listener != null) {
            listener!!.OnStateChange(state)
        }
    }

    fun accept(listener: OnStateChangeListener?) {
        this.listener = listener
        Thread {
            connection = factory!!.createPeerConnection(iceServers, object : DefaultObserver() {
                override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState)
                    if (iceGatheringState == IceGatheringState.COMPLETE) {
                        log("onIceGatheringChange")
                        try {
                            val pw = PacketWriter(commSocket!!)
                            val obj = JSONObject()
                            obj.put("action", "connected")
                            obj.put("answer", connection!!.localDescription.description)
                            val encrypted = encryptMessage(
                                obj.toString(),
                                contact.publicKey,
                                ownPublicKey!!,
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
                    log("onIceConnectionChange")
                    super.onIceConnectionChange(iceConnectionState)
                    if (iceConnectionState == IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED)
                    }
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    log("onAddStream")
                    super.onAddStream(mediaStream)
                    handleMediaStream(mediaStream)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    super.onDataChannel(dataChannel)
                    this@RTCCall.dataChannel = dataChannel
                    dataChannel.registerObserver(this@RTCCall)
                }
            })
            connection!!.addStream(createStream())
            //this.dataChannel = connection.createDataChannel("data", new DataChannel.Init());
            log("setting remote description")
            connection!!.setRemoteDescription(object : DefaultSdpObserver() {
                override fun onSetSuccess() {
                    super.onSetSuccess()
                    log("creating answer...")
                    connection!!.createAnswer(object : DefaultSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription) {
                            log("onCreateSuccess")
                            super.onCreateSuccess(sessionDescription)
                            connection!!.setLocalDescription(
                                DefaultSdpObserver(),
                                sessionDescription
                            )
                        }

                        override fun onCreateFailure(s: String) {
                            super.onCreateFailure(s)
                            log("onCreateFailure: $s")
                        }
                    }, constraints)
                }
            }, SessionDescription(SessionDescription.Type.OFFER, offer))
        }.start()
    }

    fun decline() {
        Thread {
            try {
                log("declining...")
                if (commSocket != null) {
                    val pw = PacketWriter(commSocket!!)
                    val encrypted = encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        contact.publicKey,
                        ownPublicKey!!,
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
        if (upStream != null && state == CallState.CONNECTED) {
            /*for(AudioTrack track : this.upStream.audioTracks){
                track.setEnabled(false);
                track.dispose();
            }
            for(VideoTrack track : this.upStream.videoTracks) track.dispose();*/
            closePeerConnection()
            //factory.dispose();
        }
    }

    fun hangUp() {
        Thread {
            try {
                if (commSocket != null) {
                    val pw = PacketWriter(commSocket!!)
                    val encrypted = encryptMessage(
                        "{\"action\":\"dismissed\"}",
                        contact.publicKey,
                        ownPublicKey!!,
                        ownSecretKey
                    )
                    pw.writeMessage(encrypted!!)
                }
                closeCommSocket()
                closePeerConnection()
                reportStateChange(CallState.ENDED)
            } catch (e: IOException) {
                e.printStackTrace()
                reportStateChange(CallState.ERROR)
            }
        }.start()
    }

    interface OnStateChangeListener {
        fun OnStateChange(state: CallState?)
    }

    private fun log(s: String) {
        d(this, s)
    }

    companion object {
        fun startCall(
            context: Context,
            binder: MainBinder,
            contact: Contact,
            listener: OnStateChangeListener
        ): RTCCall {
            return RTCCall(context, binder, contact, listener)
        }
    }
}