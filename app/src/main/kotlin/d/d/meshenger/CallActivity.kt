package d.d.meshenger

import android.Manifest
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.os.PowerManager.WakeLock
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.call.RTCCall
import d.d.meshenger.call.RTCCall.CallState
import d.d.meshenger.call.RTCCall.OnStateChangeListener
import d.d.meshenger.call.StatsReportUtil
import org.webrtc.*
import java.io.IOException

class CallActivity : BaseActivity(), RTCCall.CallContext, SensorEventListener {
    private var binder: MainService.MainBinder? = null
    private lateinit var statusTextView: TextView
    private lateinit var callStats: TextView
    private lateinit var nameTextView: TextView
    private lateinit var connection: ServiceConnection
    private lateinit var currentCall: RTCCall
    private lateinit var contact: Contact
    private lateinit var eglBase: EglBase

    private var powerManager: PowerManager? = null
    private var wakeLock: WakeLock? = null
    private lateinit var passiveWakeLock: WakeLock

    private var callEventType = Event.Type.UNKNOWN
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

    private val remoteProxyVideoSink = RTCCall.ProxyVideoSink()
    private val localProxyVideoSink = RTCCall.ProxyVideoSink()

    private lateinit var pipRenderer: SurfaceViewRenderer
    private lateinit var fullscreenRenderer: SurfaceViewRenderer
    private lateinit var acceptButton: ImageButton
    private lateinit var declineButton: ImageButton
    private lateinit var togglePipButton: ImageButton
    private lateinit var toggleCameraButton: ImageButton
    private lateinit var toggleMicButton: ImageButton
    private lateinit var toggleFrontCameraButton: ImageButton
    private lateinit var speakerModeButton: ImageButton

    // set by CallActivity
    private var callStatsEnabled = false // small window for video/audio statistics
    private var swappedVideoFeeds = false // swapped fullscreen and pip video content
    private var showPipEnabled = true // enable PIP window

    // set by RTCall
    private var isLocalVideoAvailable = false // own camera is on/off
    private var isRemoteVideoAvailable = false // we receive a video feed

    private var speakerEnabled = false

    class InitialSettings {
        var cameraEnabled = false
        var micEnabled = true
        var frontCameraEnabled = false
    }

    private val statsCollector: RTCStatsCollectorCallback = object : RTCStatsCollectorCallback {
        var statsReportUtil = StatsReportUtil()

        override fun onStatsDelivered(rtcStatsReport: RTCStatsReport) {
            val stats = statsReportUtil.getStatsReport(rtcStatsReport)
            runOnUiThread {
                callStats.text = stats
            }
        }
    }

    private fun setCallStats(enabled: Boolean) {
        Log.d(this, "show call stats: $enabled")
        if (enabled) {
            currentCall.setStatsCollector(statsCollector)
            callStats.visibility = View.VISIBLE
        } else {
            callStats.visibility = View.GONE
        }
    }

    private val stateChangeCallback = OnStateChangeListener { state: CallState ->
        runOnUiThread {
            when (state) {
                CallState.CONNECTING -> {
                    Log.d(this, "stateChangeCallback: CONNECTING")
                    setStatusText(getString(R.string.call_connecting))
                }
                CallState.RINGING -> {
                    Log.d(this, "stateChangeCallback: RINGING")
                    setStatusText(getString(R.string.call_ringing))
                }
                CallState.CONNECTED -> {
                    Log.d(this, "stateChangeCallback: CONNECTED")
                    setStatusText(getString(R.string.call_connected))
                    onCameraEnabled()
                }
                CallState.DISMISSED -> {
                    Log.d(this, "stateChangeCallback: DISMISSED")
                    stopDelayed(getString(R.string.call_denied))
                }
                CallState.ENDED -> {
                    Log.d(this, "stateChangeCallback: ENDED")
                    stopDelayed(getString(R.string.call_ended))
                }
                CallState.ERROR_CONN -> {
                    Log.d(this, "stateChangeCallback: ERROR_CONN")
                    stopDelayed(getString(R.string.call_connection_failed))
                }
                CallState.ERROR_AUTH -> {
                    Log.d(this, "stateChangeCallback: ERROR_AUTH")
                    stopDelayed(getString(R.string.call_authentication_failed))
                }
                CallState.ERROR_CRYPTO, CallState.ERROR_OTHER -> {
                    Log.d(this, "passiveCallback: ERROR")
                    stopDelayed(getString(R.string.call_error))
                }
            }
        }
    }

    private fun updateVideoDisplay() {
        val frontCameraEnabled = currentCall.getFrontCameraEnabled()

        Log.d(this, "updateVideoDisplay: swappedVideoFeeds=$swappedVideoFeeds, frontCameraEnabled=$frontCameraEnabled")

        if (swappedVideoFeeds) {
            localProxyVideoSink.setTarget(fullscreenRenderer)
            remoteProxyVideoSink.setTarget(pipRenderer)

            pipRenderer.setMirror(false)
            fullscreenRenderer.setMirror(!frontCameraEnabled)

            showPipView(isRemoteVideoAvailable && showPipEnabled)
            showFullscreenView(isLocalVideoAvailable)

            // video availabe for pip
            setPipButtonEnabled(isRemoteVideoAvailable)
        } else {
            // default (local video in pip, remote video in fullscreen)
            localProxyVideoSink.setTarget(pipRenderer)
            remoteProxyVideoSink.setTarget(fullscreenRenderer)

            pipRenderer.setMirror(!frontCameraEnabled)
            fullscreenRenderer.setMirror(false)

            showPipView(isLocalVideoAvailable && showPipEnabled)
            showFullscreenView(isRemoteVideoAvailable)

            // video availabe for pip
            setPipButtonEnabled(isLocalVideoAvailable)
        }
    }

    private fun setPipButtonEnabled(enable: Boolean) {
        if (enable) {
            Log.d(this, "show pip button")
            togglePipButton.visibility = View.VISIBLE
        } else {
            Log.d(this, "hide pip button")
            togglePipButton.visibility = View.INVISIBLE
        }
    }

    private fun showPipView(enable: Boolean) {
        if (enable) {
            Log.d(this, "show pip video")
            pipRenderer.visibility = View.VISIBLE
        } else {
            Log.d(this, "hide pip video")
            pipRenderer.visibility = View.INVISIBLE
        }
    }

    private fun showFullscreenView(enable: Boolean) {
        if (enable) {
            Log.d(this, "show fullscreen video")
            fullscreenRenderer.visibility = View.VISIBLE
        } else {
            Log.d(this, "hide fullscreen video")
            fullscreenRenderer.visibility = View.INVISIBLE
        }
    }

    override fun onCameraEnabled() {
        runOnUiThread {
            updateCameraButtons(currentCall.getCameraEnabled())
        }
    }

    override fun onLocalVideoEnabled(enabled: Boolean) {
        Log.d(this, "onLocalVideoEnabled: $enabled")
        runOnUiThread {
            isLocalVideoAvailable = enabled
            updateVideoDisplay()
            updateCameraButtons(currentCall.getCameraEnabled())
        }
    }

    override fun onRemoteVideoEnabled(enabled: Boolean) {
        Log.d(this, "onRemoteVideoEnabled: $enabled")
        runOnUiThread {
            isRemoteVideoAvailable = enabled
            updateVideoDisplay()
        }
    }

    override fun onMicrophoneEnabled(enabled: Boolean) {
        Log.d(this, "onMicrophoneEnabled: $enabled")
        runOnUiThread {
            if (currentCall.getMicrophoneEnabled()) {
                toggleMicButton.setImageResource(R.drawable.ic_mic_off)
            } else {
                toggleMicButton.setImageResource(R.drawable.ic_mic_on)
            }
        }
    }

    override fun showTextMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val passiveCallback = OnStateChangeListener { state: CallState ->
        runOnUiThread {
            when (state) {
                CallState.CONNECTING -> {
                    // nothing to do?
                }
                CallState.DISMISSED -> {
                    // nothing to do?
                }
                CallState.CONNECTED -> {
                    Log.d(this, "passiveCallback: CONNECTED")
                    setStatusText(getString(R.string.call_connected))
                    acceptButton.visibility = View.GONE
                    declineButton.visibility = View.VISIBLE
                    onCameraEnabled()
                }
                CallState.RINGING -> {
                    Log.d(this, "passiveCallback: RINGING")
                    setStatusText(getString(R.string.call_ringing))
                }
                CallState.ENDED -> {
                    Log.d(this, "passiveCallback: ENDED")
                    stopDelayed(getString(R.string.call_ended))
                }
                CallState.ERROR_CONN -> {
                    Log.d(this, "stateChangeCallback: ERROR_CONN")
                    stopDelayed(getString(R.string.call_connection_failed))
                }
                CallState.ERROR_AUTH -> {
                    Log.d(this, "stateChangeCallback: ERROR_AUTH")
                    stopDelayed(getString(R.string.call_authentication_failed))
                }
                CallState.ERROR_CRYPTO, CallState.ERROR_OTHER -> {
                    Log.d(this, "passiveCallback: ERROR")
                    stopDelayed(getString(R.string.call_error))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate")

        super.onCreate(savedInstanceState)

        // keep screen on during the call
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_call)

        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            setTheme(R.style.AppTheme_Dark)
        } else {
            setTheme(R.style.AppTheme_Light)
        }

        // keep screen on during the call
        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        statusTextView = findViewById(R.id.callStatus)
        callStats = findViewById(R.id.callStats)
        nameTextView = findViewById(R.id.callName)
        pipRenderer = findViewById(R.id.pip_video_view)
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
        togglePipButton = findViewById(R.id.toggle_pip_window)
        toggleCameraButton = findViewById(R.id.toggleCameraButton)
        toggleMicButton = findViewById(R.id.toggleMicButton)
        acceptButton = findViewById(R.id.acceptButton)
        declineButton = findViewById(R.id.declineButton)
        speakerModeButton = findViewById(R.id.speakerMode)
        toggleFrontCameraButton = findViewById(R.id.frontFacingSwitch)
        contact = intent.extras!!["EXTRA_CONTACT"] as Contact

        eglBase = EglBase.create()

        pipRenderer.init(eglBase.eglBaseContext, null)
        pipRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

        fullscreenRenderer.init(eglBase.eglBaseContext, null)
        fullscreenRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

        pipRenderer.setZOrderMediaOverlay(true)
        pipRenderer.setEnableHardwareScaler(true)
        fullscreenRenderer.setEnableHardwareScaler(false)

        // make both invisible
        showPipView(false)
        showFullscreenView(false)

        acceptButton.visibility = View.GONE
        declineButton.visibility = View.GONE
        toggleMicButton.visibility = View.GONE
        toggleCameraButton.visibility = View.GONE
        toggleFrontCameraButton.visibility = View.GONE

        if (contact.name.isEmpty()) {
            nameTextView.text = resources.getString(R.string.unknown_caller)
        } else {
            nameTextView.text = contact.name
        }

        Log.d(this, "intent: ${intent.action}")

        if ("ACTION_OUTGOING_CALL" == intent.action) {
            callEventType = Event.Type.OUTGOING_UNKNOWN
            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    Log.d(this@CallActivity, "onServiceConnected")
                    binder = iBinder as MainService.MainBinder
                    currentCall = RTCCall(
                        this@CallActivity,
                        binder!!,
                        contact,
                        stateChangeCallback
                    )
                    currentCall.setRemoteRenderer(remoteProxyVideoSink)
                    currentCall.setLocalRenderer(localProxyVideoSink)
                    currentCall.setCallContext(this@CallActivity)
                    currentCall.setEglBase(eglBase)

                    updateVideoDisplay()
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    // nothing to do
                }
            }
            bindService(Intent(this, MainService::class.java), connection, 0)

            val declineListener = View.OnClickListener {
                // end call
                currentCall.hangUp()
                callEventType = Event.Type.OUTGOING_DECLINED
                finish()
            }

            acceptButton.visibility = View.GONE
            declineButton.visibility = View.VISIBLE
            declineButton.setOnClickListener(declineListener)
            startSensor()
        } else if ("ACTION_INCOMING_CALL" == intent.action) {
            callEventType = Event.Type.INCOMING_UNKNOWN
            passiveWakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.PARTIAL_WAKE_LOCK,
                "meshenger:wakeup"
            )
            passiveWakeLock.acquire(10000)
            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    Log.d(this@CallActivity, "onServiceConnected")
                    binder = iBinder as MainService.MainBinder
                    currentCall = binder!!.getCurrentCall()!!

                    updateVideoDisplay()
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    binder = null
                }
            }
            bindService(Intent(this, MainService::class.java), connection, 0)

            startRinging()

            // decline call
            val declineListener = View.OnClickListener {
                stopRinging()
                Log.d(this, "declining call...")
                currentCall.decline()

                if (passiveWakeLock.isHeld) {
                    passiveWakeLock.release()
                }
                callEventType = Event.Type.INCOMING_DECLINED
                finish()
            }

            // hangup call
            val hangupListener = View.OnClickListener {
                Log.d(this, "hangup call...")
                stopRinging() // make sure ringing has stopped ;-)

                currentCall.decline()

                if (passiveWakeLock.isHeld) {
                    passiveWakeLock.release()
                }
                callEventType = Event.Type.INCOMING_ACCEPTED

                finish()
            }

            val acceptListener = View.OnClickListener {
                Log.d(this, "accepted call...")
                stopRinging()

                try {
                    currentCall.setRemoteRenderer(remoteProxyVideoSink)
                    currentCall.setLocalRenderer(localProxyVideoSink)
                    currentCall.setOnStateChangeListener(passiveCallback)
                    currentCall.setCallContext(this@CallActivity)
                    currentCall.setEglBase(eglBase)
                    currentCall.initIncoming()

                    if (passiveWakeLock.isHeld) {
                        passiveWakeLock.release()
                    }

                    declineButton.setOnClickListener(hangupListener)
                    acceptButton.visibility = View.GONE
                    declineButton.visibility = View.VISIBLE

                    initCall()

                    startSensor()
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopDelayed("Error accepting call")
                    acceptButton.visibility = View.GONE
                    declineButton.visibility = View.GONE
                    callEventType = Event.Type.INCOMING_ERROR
                }
            }

            acceptButton.setOnClickListener(acceptListener)
            declineButton.setOnClickListener(declineListener)

            acceptButton.visibility = View.VISIBLE
            declineButton.visibility = View.VISIBLE
        } else {
            Log.d(this, "missing action, should never happen")
            finish()
        }

        Log.d(this, "state: ${this.lifecycle.currentState}")
    }

    var polledStartInit = true

    override fun onResume() {
        super.onResume()
        if (polledStartInit) {
            polledStartInit = false
            polledStart()
        }
    }

    // hack since we need access to binder _and_ activity in state STARTED (for permissions launcher)
    private fun polledStart(counter: Int = 0) {
        Log.d(this, "polledStart")

        val waitMS = 500L
        Handler(mainLooper).postDelayed({
            if (this@CallActivity.binder != null) {
                continueCallSetup()
            } else if ((counter * waitMS) > 4000) {
                Log.d(this, "give up")
                // give up
                finish()
            } else {
                polledStart(counter + 1)
            }
        }, waitMS)
    }

    private fun continueCallSetup() {
        Log.d(this, "continueCallSetup")

        Log.d(this, "has record audio permissions: " + Utils.hasRecordAudioPermission(this))
        Log.d(this, "has record video permissions: " + Utils.hasCameraPermission(this))
        Log.d(this, "init.action: ${intent.action}, state: ${this.lifecycle.currentState}")

        currentCall.initVideo()

        if (intent.action == "ACTION_OUTGOING_CALL") {
            currentCall.initOutgoing()
            initCall()
        }

        // swap pip and fullscreen content
        pipRenderer.setOnClickListener {
            Log.d(this, "pipRenderer.setOnClickListener")
            showPipEnabled = true
            swappedVideoFeeds = !swappedVideoFeeds
            updateVideoDisplay()
        }

        // swap pip and fullscreen content
        fullscreenRenderer.setOnClickListener {
            Log.d(this, "fullscreenRenderer.setOnClickListener")
            swappedVideoFeeds = !swappedVideoFeeds
            showPipEnabled = true
            updateVideoDisplay()
        }

        togglePipButton.setOnClickListener {
            Log.d(this, "togglePipButton.setOnClickListener")
            showPipEnabled = !showPipEnabled
            updateVideoDisplay()
        }

        findViewById<ImageButton>(R.id.toggle_call_stats).setOnClickListener {
            callStatsEnabled = !callStatsEnabled
            if (callStatsEnabled) {
                currentCall.setStatsCollector(statsCollector)
                callStats.visibility = View.VISIBLE
            } else {
                currentCall.setStatsCollector(null)
                callStats.visibility = View.GONE
            }
        }

        toggleMicButton.setOnClickListener { switchMicEnabled() }
        toggleCameraButton.setOnClickListener { switchCameraEnabled() }
        speakerModeButton.setOnClickListener { chooseVoiceMode() }

        toggleFrontCameraButton.setOnClickListener {
            Log.d(this, "frontFacingSwitch: swappedVideoFeeds: $swappedVideoFeeds, frontCameraEnabled: ${currentCall.getFrontCameraEnabled()}}")
            currentCall.setFrontCameraEnabled(
                !currentCall.getFrontCameraEnabled()
            )
        }
    }

    private fun initCall() {
        Log.d(this, "initCall")

        val settings = InitialSettings()

        Log.d(this, "mic ${settings.micEnabled} ${currentCall.getMicrophoneEnabled()}")

        if (settings.micEnabled != currentCall.getMicrophoneEnabled()) {
            switchMicEnabled()
        }

        Log.d(this, "cam ${settings.cameraEnabled} ${currentCall.getCameraEnabled()}")

        if (settings.cameraEnabled != currentCall.getCameraEnabled()) {
            switchCameraEnabled()
        }

        Log.d(this, "frontCameraEnabled ${settings.frontCameraEnabled} ${currentCall.getFrontCameraEnabled()}")

        if (settings.frontCameraEnabled != currentCall.getFrontCameraEnabled()) {
            currentCall.setFrontCameraEnabled(settings.frontCameraEnabled)
        }

        toggleMicButton.visibility = View.VISIBLE
        toggleCameraButton.visibility = View.VISIBLE
        toggleFrontCameraButton.visibility = View.GONE
    }

    override fun onFrontFacingCamera(enabled: Boolean) {
        runOnUiThread {
            updateVideoDisplay()
        }
    }

    private fun startRinging() {
        Log.d(this, "startRinging")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(1500, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibe = VibrationEffect.createWaveform(pattern, 1)
            vibrator!!.vibrate(vibe)
        } else {
            @Suppress("DEPRECATION")
            vibrator!!.vibrate(pattern, 1)
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return
        }

        ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            )
        )
        ringtone!!.play()
    }

    private fun stopRinging() {
        Log.d(this, "stopRinging")
        if (vibrator != null) {
            vibrator!!.cancel()
            vibrator = null
        }
        ringtone?.stop()
    }

    private fun chooseVoiceMode() {
        val audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager

        val button = speakerModeButton
        speakerEnabled = !speakerEnabled
        Log.d(this, "chooseVoiceMode: $speakerEnabled")

        if (speakerEnabled) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            button.alpha = 1.0f
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            audioManager.isBluetoothScoOn = false
            button.alpha = 0.6f
        }
    }

    private val enabledMicrophoneForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            switchMicEnabled()
        } else {
            // do not turn on microphone
            showTextMessage(getString(R.string.missing_microphone_permissions))
        }
    }

    private fun switchMicEnabled() {
        if (currentCall.getMicrophoneEnabled()) {
            // turn microphone off
            currentCall.setMicrophoneEnabled(false)
        } else {
            // check permission
            if (!Utils.hasRecordAudioPermission(this)) {
                enabledMicrophoneForResult.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            // turn microphone on
            currentCall.setMicrophoneEnabled(true)
        }
    }

    private val enabledCameraForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            switchCameraEnabled()
        } else {
            // do not turn on camera
            showTextMessage(getString(R.string.missing_camera_permissions))
        }
    }

    private fun switchCameraEnabled() {
        Log.d(this, "switchCameraEnabled")

        if (currentCall.getCameraEnabled()) {
            // turn camera off
            currentCall.setCameraEnabled(false)
        } else {
            // check permission
            if (!Utils.hasCameraPermission(this)) {
                enabledCameraForResult.launch(Manifest.permission.CAMERA)
                return
            }
            // turn camera on
            currentCall.setCameraEnabled(true)
        }
    }

    private fun updateCameraButtons(enabled: Boolean) {
        val animation = ScaleAnimation(
            1.0f,
            0.0f,
            1.0f,
            1.0f,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        )

        animation.duration = BUTTON_ANIMATION_DURATION / 2
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                // nothing to do
            }

            override fun onAnimationEnd(animation: Animation) {
                if (enabled) {
                    toggleCameraButton.setImageResource(R.drawable.ic_camera_off)
                } else {
                    toggleCameraButton.setImageResource(R.drawable.ic_camera_on)
                }

                val a: Animation = ScaleAnimation(
                    0.0f,
                    1.0f,
                    1.0f,
                    1.0f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f
                )
                a.duration = BUTTON_ANIMATION_DURATION / 2
                toggleCameraButton.startAnimation(a)
            }

            override fun onAnimationRepeat(animation: Animation) {
                // nothing to do
            }
        })

        if (enabled) {
            toggleFrontCameraButton.visibility = View.VISIBLE
            val scale: Animation = ScaleAnimation(
                0f,
                1f,
                0f,
                1f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            scale.duration = BUTTON_ANIMATION_DURATION
            toggleFrontCameraButton.startAnimation(scale)
        } else {
            val scale: Animation = ScaleAnimation(
                1f,
                0f,
                1f,
                0f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            scale.duration = BUTTON_ANIMATION_DURATION
            scale.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    // nothing to do
                }

                override fun onAnimationEnd(animation: Animation) {
                    toggleFrontCameraButton.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animation) {
                    // nothing to do
                }
            })
            toggleFrontCameraButton.startAnimation(scale)
        }

        toggleCameraButton.startAnimation(animation)
    }

    private fun startSensor() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager!!.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "meshenger:proximity"
        )
        wakeLock?.acquire()
    }

    override fun onDestroy() {
        Log.d(this, "onDestroy")
        super.onDestroy()

        stopRinging()

        binder!!.setCurrentCall(null)
        currentCall.cleanup()

        if (currentCall.state == CallState.CONNECTED) {
            currentCall.decline()
        }
        //currentCall.cleanup()
        binder!!.getEvents().addEvent(contact, callEventType)

        unbindService(connection)

        wakeLock?.release()
        if (currentCall.commSocket != null && currentCall.commSocket!!.isConnected && !currentCall.commSocket!!.isClosed) {
            try {
                currentCall.commSocket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        remoteProxyVideoSink.setTarget(null)
        localProxyVideoSink.setTarget(null)

        pipRenderer.release()
        fullscreenRenderer.release()

        currentCall.releaseCamera()

        eglBase.release()
    }

    private fun setStatusText(text: String) {
        Handler(mainLooper).post { statusTextView.text = text }
    }

    private fun stopDelayed(message: String) {
        Handler(mainLooper).post {
            statusTextView.text = message
            Handler(mainLooper).postDelayed({ finish() }, 2000)
        }
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        Log.d(this, "sensor changed: " + sensorEvent.values[0])
        if (sensorEvent.values[0] == 0.0f) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "meshenger:tag"
                )
            wakeLock?.acquire()
        } else {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "meshenger:tag"
                )
            wakeLock?.acquire()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {
        // nothing to do
    }

    companion object {
        private const val BUTTON_ANIMATION_DURATION = 400L
    }
}