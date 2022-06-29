package d.d.meshenger.activity

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.model.Event.CallType
import d.d.meshenger.service.MainService
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.R
import d.d.meshenger.call.*
import d.d.meshenger.utils.Utils
import d.d.meshenger.call.AppRTCAudioManager.AudioManagerEvents
import d.d.meshenger.call.AppRTCClient.SignalingParameters
import d.d.meshenger.call.DirectRTCClient.CallDirection
import d.d.meshenger.fragment.CallFragment
import d.d.meshenger.fragment.HudFragment
import org.webrtc.*
import org.webrtc.RendererCommon.ScalingType

class CallActivity: MeshengerActivity(), AppRTCClient.SignalingEvents,
    PeerConnectionClient.PeerConnectionEvents,
    CallFragment.OnCallEvents {

        companion object {

            private const val TAG = "CallActivity"

            private const val INTERNET_PERMISSION_REQUEST_CODE = 2
            private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 3
            private const val MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE = 4
            private const val CAMERA_PERMISSION_REQUEST_CODE = 5

            // List of mandatory application permissions.
            private val MANDATORY_PERMISSIONS = arrayOf(
                "android.permission.MODIFY_AUDIO_SETTINGS",
                "android.permission.RECORD_AUDIO", "android.permission.INTERNET"
            ) //, "android.permission.CAMERA"};


            // Peer connection statistics callback period in ms.
            private const val STAT_CALLBACK_PERIOD = 1000

            private class ProxyVideoSink : VideoSink {
                private var target: VideoSink? = null

                @Synchronized
                override fun onFrame(frame: VideoFrame) {
                    if (target == null) {
                        Logging.d(
                            TAG,
                            "Dropping frame in proxy because target is null."
                        )
                        return
                    }
                    target!!.onFrame(frame)
                }

                @Synchronized
                fun setTarget(target: VideoSink?) {
                    this.target = target
                }
            }

            private fun getSystemUiVisibility(): Int {
                var flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                }
                return flags
            }

        }


    private val remoteProxyRenderer = ProxyVideoSink()
    private val localProxyVideoSink = ProxyVideoSink()
    private var peerConnectionClient: PeerConnectionClient? = null
    private var appRtcClient: DirectRTCClient? = null
    private var signalingParameters: SignalingParameters? = null
    private var audioManager: AppRTCAudioManager? = null
    private var pipRenderer: SurfaceViewRenderer? = null
    private var fullscreenRenderer: SurfaceViewRenderer? = null
    private var videoFileRenderer: VideoFileRenderer? = null
    private val remoteSinks = ArrayList<VideoSink>()
    private var logToast: Toast? = null
    private var activityRunning = false// needed?
    private lateinit var peerConnectionParameters: PeerConnectionClient.Companion.PeerConnectionParameters
    private var connected = false
    private var isError = false
    private var callControlFragmentVisible = true
    private var callStartedTimeMs: Long = 0
    private var micEnabled = true

    // True if local view is in the fullscreen renderer.
    private var isSwappedFeeds = false

    // Controls
    private lateinit var callFragment: CallFragment
    private lateinit var hudFragment: HudFragment
    private lateinit var eglBase: EglBase //temporary

    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

    private var callType = CallType.UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(UnhandledExceptionHandler(this))

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        window.decorView.systemUiVisibility = getSystemUiVisibility()
        setContentView(R.layout.activity_call)
        connected = false
        signalingParameters = null

        // Create UI controls.
        pipRenderer = findViewById(R.id.pip_video_view)
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
        //fullscreenRenderer.setBackgroundColor(Color.parseColor("#00aacc"));
        callFragment = CallFragment()
        hudFragment = HudFragment()

        // Show/hide call control fragment on view click.
        fullscreenRenderer?.setOnClickListener(View.OnClickListener { view: View? -> toggleCallControlFragmentVisibility() })

        // Swap feeds on pip view click.
        pipRenderer?.setOnClickListener(View.OnClickListener { view: View? ->
            setSwappedFeeds(
                !isSwappedFeeds
            )
        })
        remoteSinks.add(remoteProxyRenderer)
        val intent = intent
        //final EglBase eglBase = EglBase.create();
        eglBase = EglBase.create()

        // Create video renderers.
        pipRenderer?.init(eglBase.getEglBaseContext(), null)
        pipRenderer?.setScalingType(ScalingType.SCALE_ASPECT_FIT)
        fullscreenRenderer?.init(eglBase.getEglBaseContext(), null)
        fullscreenRenderer?.setScalingType(ScalingType.SCALE_ASPECT_FILL)
        pipRenderer?.setZOrderMediaOverlay(true)
        pipRenderer?.setEnableHardwareScaler(true /* enabled */)
        fullscreenRenderer?.setEnableHardwareScaler(false /* enabled */)
        run {
            fullscreenRenderer!!.visibility = View.INVISIBLE
            pipRenderer!!.visibility = View.INVISIBLE
        }
        var dataChannelParameters: PeerConnectionClient.Companion.DataChannelParameters? = null
        if (intent.getBooleanExtra("EXTRA_DATA_CHANNEL_ENABLED", true)) {
            dataChannelParameters = PeerConnectionClient.Companion.DataChannelParameters(
                true,  // ORDERED
                -1,  // MAX_RETRANSMITS_MS
                -1,  // MAX_RETRANSMITS
                "",  //PROTOCOL
                false,  //NEGOTIATED
                -1 // ID
            )
            dataChannelParameters.debug()
        }
        val settings = MainService.instance!!.getSettings()
        peerConnectionParameters = PeerConnectionClient.Companion.PeerConnectionParameters(
            true,  //settings.getPlayVideo(),
            true,  //settings.getRecordVideo(),
            true,  //settings.getPlayAudio(),
            true,  //settings.getRecordAudio(),
            true,  // VIDEO_CALL // TODO: remove
            0,  // VIDEO_WIDTH
            0,  // VIDEO_HEIGHT
            0,  // VIDEO_FPS
            0,  // VIDEO_BITRATE
            settings!!.videoCodec,
            true,  // HWCODEC_ENABLED
            false,  // FLEXFEC_ENABLED
            0,  // AUDIO_BITRATE
            settings.audioCodec,
            settings.audioProcessing,  // NOAUDIOPROCESSING_ENABLED
            false,  // OPENSLES_ENABLED
            false,  // DISABLE_BUILT_IN_AEC
            false,  // DISABLE_BUILT_IN_AGC
            false,  // DISABLE_BUILT_IN_NS
            false,  // DISABLE_WEBRTC_AGC_AND_HPF
            dataChannelParameters!!
        )
        peerConnectionParameters.debug()

        // TODO: move into startCall!
        // so we can check permission over and over...
        checkPermissions(peerConnectionParameters)

/*
   // Check for mandatory permissions.
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }
*/

        // Activate call and HUD fragments and start the call.
        val ft = supportFragmentManager.beginTransaction()
        ft.add(R.id.call_fragment_container, callFragment)
        ft.add(R.id.hud_fragment_container, hudFragment)
        ft.commit()
        appRtcClient = DirectRTCClient.getCurrentCall()
        if (appRtcClient == null) {
            disconnectWithErrorMessage("No connection expected!")
            return
        }
        appRtcClient?.signalingEvents = this

        //callFragment.update(false, peerConnectionParameters);
        callType = CallType.MISSED
        when (appRtcClient?.callDirection) {
            CallDirection.INCOMING -> {
                callFragment.setCallStatus(resources.getString(R.string.call_connecting))
                Log.d(TAG, "Incoming call")
                if (MainService.instance!!.getSettings()?.autoAcceptCall!!) {
                    startCall()
                } else {
                    // start ringing and wait for connect call
                    Log.d(TAG, "start ringing")
                    startRinging()
                    //fullscreenRenderer.setBackgroundColor(Color.parseColor("#00aacc"));
                }
            }
            CallDirection.OUTGOING -> {
                callFragment.setCallStatus(resources.getString(R.string.call_ringing))
                Log.d(TAG, "Outgoing call")
                if (MainService.instance!!.getSettings()?.autoConnectCall!!) {
                    startCall()
                } else {
                    Log.d(TAG, "wait for explicit button call to start call")
                }
            }
            else -> {
                reportError("Invalid call direction!")
                return
            }
        }
    }

    private fun checkPermissions(peerConnectionParameters: PeerConnectionClient.Companion.PeerConnectionParameters) {
        Log.d(TAG, "checkPermissions")
        if (peerConnectionParameters.recordAudio) {
            if (!Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                Log.d(TAG, "Ask for RECORD_AUDIO permissions")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
                return
            }
        }
        if (peerConnectionParameters.playAudio) {
            if (!Utils.hasPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
                Log.d(TAG, "Ask for MODIFY_AUDIO_SETTINGS permissions")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.MODIFY_AUDIO_SETTINGS),
                    MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE
                )
                return
            }
        }
        if (peerConnectionParameters.recordVideo) {
            if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
                Log.d(TAG, "Ask for CAMERA permissions")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
                return
            }
        }

        // required!
        if (!Utils.hasPermission(this, Manifest.permission.INTERNET)) {
            Log.d(TAG, "Ask for INTERNET permissions")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.INTERNET),
                INTERNET_PERMISSION_REQUEST_CODE
            )
            return
        }
    }

    private fun startRinging() {
        Log.d(TAG, "startRinging")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> Log.d(TAG, "ringerMode: RINGER_MODE_NORMAL")
            AudioManager.RINGER_MODE_SILENT -> Log.d(TAG, "ringerMode: RINGER_MODE_SILENT")
            AudioManager.RINGER_MODE_VIBRATE -> Log.d(TAG, "ringerMode: RINGER_MODE_VIBRATE")
            else -> Log.d(TAG, "ringerMode: unknown")
        }
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            // alarm disabled
            return
        }
        if (vibrator == null) {
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(1500, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibe = VibrationEffect.createWaveform(pattern, 0)
            vibrator!!.vibrate(vibe)
        } else {
            vibrator!!.vibrate(pattern, 0)
        }
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            // only vibrating
            return
        }
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(
                applicationContext, RingtoneManager.getActualDefaultRingtoneUri(
                    applicationContext, RingtoneManager.TYPE_RINGTONE
                )
            )
        }

        // start ringing
        ringtone!!.play()
    }

    private fun stopRinging() {
        Log.d(TAG, "stopRinging")
        vibrator?.cancel()
        vibrator = null

        ringtone?.stop()
        ringtone = null
    }

    //onPause is called before, onResume afterwards
    // TODO: need to apply changed permissions
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size == 0) {
            // permission request was aborted / interrupted
            return
        }
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone disabled", Toast.LENGTH_LONG).show()
                    peerConnectionParameters!!.recordAudio = false
                }
                startCall()
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Audio disabled", Toast.LENGTH_LONG).show()
                    peerConnectionParameters!!.playAudio = false
                }
                startCall()
            }
            MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Audio disabled", Toast.LENGTH_LONG).show()
                    peerConnectionParameters!!.playAudio = false
                }
                startCall()
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Camera disabled", Toast.LENGTH_LONG).show()
                    peerConnectionParameters!!.recordVideo = false
                }
                startCall()
            }
            INTERNET_PERMISSION_REQUEST_CODE -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Internet access required", Toast.LENGTH_LONG).show()
                isError = true
                disconnect()
            }
            else -> reportError("Unknown permission request code: $requestCode")
        }
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this) && intent.getBooleanExtra("EXTRA_CAMERA2", true)
    }

    private fun captureToTexture(): Boolean {
        return true //getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    override fun onStop() {
        super.onStop()
        activityRunning = false
        peerConnectionClient?.stopVideoSource()
    }

    override fun onStart() {
        super.onStart()
        activityRunning = true
        peerConnectionClient?.startVideoSource()
    }

    override fun onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null)
        disconnect()
        logToast?.cancel()
        activityRunning = false
        super.onDestroy()
    }

    // CallFragment.OnCallEvents interface implementation.
    override fun onCallHangUp() {
        Log.d(TAG, "onCallHangUp")
        if (callType != CallType.ACCEPTED) {
            callType = CallType.DECLINED
        }
        disconnect()
    }

    override fun onCallAccept() {
        Log.d(TAG, "onCallAccept")
        callType = CallType.ACCEPTED
        startCall()
    }


    override fun onCameraSwitch() {
        peerConnectionClient?.switchCamera()
    }

    // added by myself
    override fun onVideoMirrorSwitch(mirror: Boolean) {
        fullscreenRenderer!!.setMirror(mirror) //!fullscreenRenderer.getMirror());
    }

    override fun onVideoScalingSwitch(scalingType: ScalingType) {
        fullscreenRenderer!!.setScalingType(scalingType)
    }

    override fun onCaptureFormatChange(width: Int, height: Int, framerate: Int) {
        peerConnectionClient?.changeCaptureFormat(width, height, framerate)

    }

    override fun onToggleMic(): Boolean {
        peerConnectionClient?.let{
            // TODO: set in peerConnectionParameters
            micEnabled = !micEnabled
            it.setAudioEnabled(micEnabled)
        }
        return micEnabled
    }

    // Helper functions.
    private fun toggleCallControlFragmentVisibility() {
        Log.d(TAG, "toggleCallControlFragmentVisibility")
        if (!connected || !callFragment.isAdded) {
            return
        }

        // Show/hide call control fragment
        callControlFragmentVisible = !callControlFragmentVisible
        val ft = supportFragmentManager.beginTransaction()
        if (callControlFragmentVisible) {
            ft.show(callFragment)
            ft.show(hudFragment)
        } else {
            ft.hide(callFragment)
            ft.hide(hudFragment)
        }
        ft.setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        ft.commit()
    }

    private fun startCall() {
        if (peerConnectionClient != null) {
            logAndToast("Call already started")
            return
        }
        if (appRtcClient == null) {
            Log.e(TAG, "AppRTC client is not allocated for a call.")
            return
        }
        callType = CallType.ACCEPTED
        //callFragment.update(true, peerConnectionParameters);

        // Start with local feed in fullscreen and swap it to the pip when the call is connected.
        setSwappedFeeds(true /* isSwappedFeeds */)
        fullscreenRenderer!!.visibility = View.VISIBLE
        pipRenderer!!.visibility = View.VISIBLE
        run {

            // Create peer connection client.
            peerConnectionClient = PeerConnectionClient(
                applicationContext, eglBase, peerConnectionParameters, this@CallActivity
            )
            val options = PeerConnectionFactory.Options()
            //options.disableNetworkMonitor = true; // does not work! from email by dante carvalho to fix connection in case of tethering
            peerConnectionClient!!.createPeerConnectionFactory(options)
        }
        callStartedTimeMs = System.currentTimeMillis()

        // Start room connection.
        //logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
        logAndToast("appRtcClient.connectToRoom")
        appRtcClient!!.connectToRoom() //this.contact_address, this.contact_port);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(
            applicationContext,
            MainService.instance!!.getSettings()?.speakerphone
        )
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d(TAG, "Starting the audio manager...")
        audioManager!!.start(object : AudioManagerEvents {
            // This method will be called each time the number of available audio
            // devices has changed.
            override fun onAudioDeviceChanged(
                selectedAudioDevice: AppRTCAudioManager.AudioDevice,
                availableAudioDevices: HashSet<AppRTCAudioManager.AudioDevice>
            ) {
                onAudioManagerDevicesChanged(selectedAudioDevice, availableAudioDevices)
            }
        })
    }

    private fun callConnected() {
        stopRinging()
        val delta = System.currentTimeMillis() - callStartedTimeMs
        Log.i(TAG, "Call connected: delay=" + delta + "ms")
        if (peerConnectionClient == null || isError) {
            Log.w(TAG, "Call is connected in closed or error state")
            return
        }
        callFragment.setCallStatus(resources.getString(R.string.call_connected))

        // Enable statistics callback.
        peerConnectionClient!!.enableStatsEvents(true, STAT_CALLBACK_PERIOD)
        setSwappedFeeds(false /* isSwappedFeeds */)
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private fun onAudioManagerDevicesChanged(
        device: AppRTCAudioManager.AudioDevice,
        availableDevices: Set<AppRTCAudioManager.AudioDevice>
    ) {
        Log.d(
            TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
                    + "selected: " + device
        )
        // TODO(henrika): add callback handler.
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    private fun disconnect() {
        // read -1; message is null causes:
        // called from  onChannelClose, onDestroy
        Log.d(
            TAG,
            "disconnect() from thread " + Thread.currentThread().name + " and print stacktrace now: "
        )
        // print stack trace as help:
        Thread.dumpStack()
        stopRinging()
        if (isError) {
            callType = CallType.ERROR
        }
        if (appRtcClient != null) {
            Log.d(TAG, "add event: " + callType.name)
            MainService.instance!!.getEvents()?.addEvent(
                appRtcClient!!.contact,
                appRtcClient!!.callDirection,
                callType
            )
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("events_changed"))
        }
        activityRunning = false
        remoteProxyRenderer.setTarget(null)
        localProxyVideoSink.setTarget(null)
        if (appRtcClient != null) {
            logAndToast("appRtcClient.disconnectFromRoom")
            appRtcClient!!.disconnectFromRoom()
            appRtcClient = null
            DirectRTCClient.setCurrentCall(null)
        }
        if (pipRenderer != null) {
            pipRenderer!!.release()
            pipRenderer = null
        }
        videoFileRenderer?.release()
        videoFileRenderer = null

        if (fullscreenRenderer != null) {
            fullscreenRenderer!!.release()
            fullscreenRenderer = null
        }
        if (peerConnectionClient != null) {
            peerConnectionClient!!.close()
            peerConnectionClient = null
        }
        if (audioManager != null) {
            audioManager!!.stop()
            audioManager = null
        }
        if (connected && !isError) {
            setResult(RESULT_OK)
        } else {
            setResult(RESULT_CANCELED)
        }
        Log.d(TAG, "finish")
        finish()
    }

    private fun disconnectWithErrorMessage(errorMessage: String) {
        if (!activityRunning) {
            Log.e(TAG, "Critical error: $errorMessage")
            disconnect()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Connection error")
                .setMessage(errorMessage)
                .setCancelable(false)
                .setNeutralButton(R.string.ok,
                    DialogInterface.OnClickListener { dialog, id ->
                        dialog.cancel()
                        disconnect()
                    })
                .create()
                .show()
        }
    }

    private fun logAndToast(msg: String) {
        Log.d(TAG, msg)
        logToast?.cancel()

        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        logToast?.show()
    }

    private fun reportError(description: String) {
        runOnUiThread {
            if (!isError) {
                isError = true
                disconnectWithErrorMessage(description)
            }
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
            if (!captureToTexture()) {
                reportError("Camera2 only supports capturing to texture. Either disable Camera2 or enable capturing to texture in the options.")
                return null
            }
            Logging.d(TAG, "Creating capturer using camera2 API.")
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            Logging.d(TAG, "Creating capturer using camera1 API.")
            createCameraCapturer(Camera1Enumerator(captureToTexture()))
        }
        if (videoCapturer == null) {
            reportError("Failed to open camera")
            return null
        }
        return videoCapturer
    }

    private fun setSwappedFeeds(isSwappedFeeds: Boolean) {
        Logging.d(TAG, "setSwappedFeeds: $isSwappedFeeds")
        this.isSwappedFeeds = isSwappedFeeds
        localProxyVideoSink.setTarget(if (isSwappedFeeds) fullscreenRenderer else pipRenderer)
        remoteProxyRenderer.setTarget(if (isSwappedFeeds) pipRenderer else fullscreenRenderer)
        fullscreenRenderer!!.setMirror(isSwappedFeeds)
        pipRenderer!!.setMirror(!isSwappedFeeds)
    }

    // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
    // All callbacks are invoked from websocket signaling looper thread and
    // are routed to UI thread.
    private fun onConnectedToRoomInternal(params: SignalingParameters) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        stopRinging()
        if (appRtcClient!!.contact != null) {
            val contact = appRtcClient!!.contact
            if (!contact.name.isEmpty()) {
                callFragment.setContactName(contact.name)
            } else {
                val unknown_caller =
                    Utils.getUnknownCallerName(this.applicationContext, contact.publicKey)
                callFragment.setContactName(unknown_caller)
            }
            callFragment.setCallStatus(resources.getString(R.string.call_connecting))
        }
        signalingParameters = params
        logAndToast("Creating peer connection, delay=" + delta + "ms")
        var videoCapturer: VideoCapturer? = null
        if (peerConnectionParameters!!.videoCallEnabled) {
            videoCapturer = createVideoCapturer()
        }
        peerConnectionClient!!.createPeerConnection2(
            localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters!!.iceServers
        )
        if (signalingParameters!!.initiator) { // INCOMING call
            logAndToast("Creating OFFER...")
            // Create offer. Offer SDP will be sent to answering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient!!.createOffer()
        } else {
            if (params.offerSdp != null) {
                peerConnectionClient!!.setRemoteDescription(params.offerSdp)
                logAndToast("Creating ANSWER...")
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient!!.createAnswer()
            }
            if (params.iceCandidates != null) {
                // Add remote ICE candidates from room.
                for (iceCandidate in params.iceCandidates) {
                    peerConnectionClient!!.addRemoteIceCandidate(iceCandidate)
                }
            }
        }
    }

    // called from DirectRTCClient.onTCPConnected (if we are server)
    // and DirectRTCClient.onTCPMessage (with sdp from offer)
    override fun onConnectedToRoom(params: SignalingParameters) {
        runOnUiThread { onConnectedToRoomInternal(params) }
    }

    // called from DirectRTCClient.onTCPMessage
    override fun onRemoteDescription(desc: SessionDescription) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread {
            if (peerConnectionClient == null) {
                Log.e(
                    TAG,
                    "Received remote SDP for non-initilized peer connection."
                )
                return@runOnUiThread
            }
            logAndToast("Received remote " + desc.type + ", delay=" + delta + "ms")
            peerConnectionClient!!.setRemoteDescription(desc)
            if (!signalingParameters!!.initiator) {
                logAndToast("Creating ANSWER...")
                // Create answer. Answer SDP will be sent to offering client in
                // PeerConnectionEvents.onLocalDescription event.
                peerConnectionClient!!.createAnswer()
            }
        }
    }

    override fun onRemoteIceCandidate(candidate: IceCandidate) {
        runOnUiThread {
            if (peerConnectionClient == null) {
                Log.e(
                    TAG,
                    "Received ICE candidate for a non-initialized peer connection."
                )
                return@runOnUiThread
            }
            peerConnectionClient!!.addRemoteIceCandidate(candidate)
        }
    }

    override fun onRemoteIceCandidatesRemoved(candidates: Array<IceCandidate?>) {
        runOnUiThread {
            if (peerConnectionClient == null) {
                Log.e(
                    TAG,
                    "Received ICE candidate removals for a non-initialized peer connection."
                )
                return@runOnUiThread
            }
            peerConnectionClient!!.removeRemoteIceCandidates(candidates)
        }
    }

    override fun onChannelClose() {
        runOnUiThread {
            logAndToast("Remote end hung up; dropping PeerConnection")
            disconnect()
        }
    }

    override fun onChannelError(description: String) {
        reportError(description!!)
    }

    // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
    // Send local peer connection SDP and ICE candidates to remote party.
    // All callbacks are invoked from peer connection client looper thread and
    // are routed to UI thread.
    override fun onLocalDescription(desc: SessionDescription) {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread {
            if (appRtcClient != null) {
                logAndToast("Sending " + desc.type + ", delay=" + delta + "ms")
                if (signalingParameters!!.initiator) {
                    logAndToast("appRtcClient.sendOfferSdp")
                    appRtcClient!!.sendOfferSdp(desc)
                } else {
                    logAndToast("appRtcClient.sendAnswerSdp")
                    appRtcClient!!.sendAnswerSdp(desc)
                }
            }
            if (peerConnectionParameters!!.videoMaxBitrate > 0) {
                Log.d(
                    TAG,
                    "Set video maximum bitrate: " + peerConnectionParameters!!.videoMaxBitrate
                )
                peerConnectionClient!!.setVideoMaxBitrate(peerConnectionParameters!!.videoMaxBitrate)
            }
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "appRtcClient.sendLocalIceCandidate")
        runOnUiThread {
            if (appRtcClient != null) {
                appRtcClient!!.sendLocalIceCandidate(candidate)
            }
        }
    }

    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
        Log.d(TAG, "appRtcClient.sendLocalIceCandidateRemovals")
        runOnUiThread {
            if (appRtcClient != null) {
                appRtcClient!!.sendLocalIceCandidateRemovals(candidates)
            }
        }
    }

    override fun onIceConnected() {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread { logAndToast("ICE connected, delay=" + delta + "ms") }
    }

    override fun onIceDisconnected() {
        runOnUiThread { logAndToast("ICE disconnected") }
    }

    // not called?
    override fun onConnected() {
        val delta = System.currentTimeMillis() - callStartedTimeMs
        runOnUiThread {
            logAndToast("DTLS connected, delay=" + delta + "ms")
            connected = true
            callConnected()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            logAndToast("DTLS disconnected")
            connected = false
            disconnect()
        }
    }

    override fun onPeerConnectionClosed() {}

    override fun onPeerConnectionStatsReady(reports: Array<StatsReport>) {
        runOnUiThread {
            if (!isError && connected) {
                hudFragment.updateEncoderStatistics(reports)
            }
        }
    }

    override fun onPeerConnectionError(description: String) {
        reportError(description!!)
    }
}