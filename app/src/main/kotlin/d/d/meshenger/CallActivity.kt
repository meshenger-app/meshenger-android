package d.d.meshenger

import android.Manifest
import android.content.*
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.os.PowerManager.WakeLock
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import d.d.meshenger.call.*
import d.d.meshenger.call.RTCPeerConnection.CallState
import org.webrtc.*
import java.net.InetSocketAddress
import java.util.*

class CallActivity : BaseActivity(), RTCCall.CallContext {
    private var binder: MainService.MainBinder? = null
    private lateinit var callStatus: TextView
    private lateinit var callStats: TextView
    private lateinit var callAddress: TextView
    private lateinit var nameTextView: TextView
    private lateinit var connection: ServiceConnection
    private lateinit var currentCall: RTCCall
    private lateinit var contact: Contact
    private lateinit var eglBase: EglBase
    private lateinit var proximitySensor: RTCProximitySensor
    private lateinit var rtcAudioManager: RTCAudioManager

    private var proximityScreenLock: WakeLock? = null
    private var proximityCameraWasOn = false

    private var polledStartInit = true
    private var activityActive = true
    private var callEventType = Event.Type.UNKNOWN
    private lateinit var vibrator: Vibrator
    private lateinit var ringtone: Ringtone

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
    private lateinit var speakerphoneButton: ImageButton
    private lateinit var captureFormatSlider: SeekBar
    private lateinit var captureFormatText: TextView

    // set by CallActivity
    private var debugOutputEnabled = false // small window for video/audio statistics and other debug data
    private var swappedVideoFeeds = false // swapped fullscreen and pip video content
    private var showPipEnabled = true // enable PIP window
    private var callWasStarted = false

    // set by RTCall
    private var isLocalVideoAvailable = false // own camera is on/off
    private var isRemoteVideoAvailable = false // we receive a video feed

    class InitialSettings {
        var cameraEnabled = false
        var micEnabled = true
        var frontCameraEnabled = false
    }

    private val statsCollector = object : RTCStatsCollectorCallback {
        var statsReportUtil = StatsReportUtil()

        override fun onStatsDelivered(rtcStatsReport: RTCStatsReport) {
            val stats = statsReportUtil.getStatsReport(rtcStatsReport)
            runOnUiThread {
                callStats.text = stats
            }
        }
    }

    override fun getContext(): Context {
        return this.applicationContext
    }

    override fun onStateChange(state: CallState) {
        runOnUiThread {
            Log.d(this, "onStateChange: $state")
            val isIncoming = (intent.action == "ACTION_INCOMING_CALL")

            val handleError = { messageId: Int ->
                callStatus.text = getString(messageId)
                callEventType = if (isIncoming) {
                    Event.Type.INCOMING_ERROR
                } else {
                    Event.Type.OUTGOING_ERROR
                }
                finishDelayed()
            }

            val handleExit = { messageId: Int ->
                callStatus.text = getString(messageId)
                callEventType = if (callWasStarted) {
                    if (isIncoming) {
                        Event.Type.INCOMING_ACCEPTED
                    } else {
                        Event.Type.OUTGOING_ACCEPTED
                    }
                } else {
                    if (isIncoming) {
                        Event.Type.INCOMING_MISSED
                    } else {
                        Event.Type.OUTGOING_MISSED
                    }
                }
                finishDelayed()
            }

            val setContactState = { state: Contact.State ->
                val b = binder
                if (b != null) {
                    val storedContact = b.getContacts().getContactByPublicKey(contact.publicKey)
                    if (storedContact != null) {
                        storedContact.state = state
                    } else {
                        contact.state = state
                    }
                } else {
                    Log.w(this, "setContactState() binder is null")
                }
            }

            when (state) {
                CallState.WAITING -> {
                    callStatus.text = getString(R.string.call_waiting)
                }
                CallState.CONNECTING -> {
                    callStatus.text = getString(R.string.call_connecting)
                }
                CallState.RINGING -> {
                    callStatus.text = getString(R.string.call_ringing)
                    setContactState(Contact.State.CONTACT_ONLINE)
                }
                CallState.CONNECTED -> {
                    // call started
                    acceptButton.visibility = View.GONE
                    declineButton.visibility = View.VISIBLE
                    callStatus.text = getString(R.string.call_connected)
                    updateCameraButtons()
                    callWasStarted = true
                    setContactState(Contact.State.CONTACT_ONLINE)
                }
                CallState.DISMISSED -> {
                    // call did not start
                    handleExit(R.string.call_denied)
                    setContactState(Contact.State.CONTACT_ONLINE)
                }
                CallState.ENDED -> {
                    // normal call end
                    handleExit(R.string.call_ended)
                    setContactState(Contact.State.CONTACT_ONLINE)
                }
                CallState.ERROR_NO_CONNECTION -> {
                    handleError(R.string.call_connection_failed)
                    setContactState(Contact.State.CONTACT_OFFLINE)
                }
                CallState.ERROR_AUTHENTICATION -> {
                    handleError(R.string.call_authentication_failed)
                    setContactState(Contact.State.AUTHENTICATION_FAILED)
                }
                CallState.ERROR_DECRYPTION -> {
                    handleError(R.string.call_error)
                }
                CallState.ERROR_CONNECT_PORT -> {
                    handleError(R.string.call_error_not_listening)
                    setContactState(Contact.State.APP_NOT_RUNNING)
                }
                CallState.ERROR_NO_ADDRESSES -> {
                    handleError(R.string.call_error_no_address)
                    setContactState(Contact.State.CONTACT_OFFLINE)
                }
                CallState.ERROR_UNKNOWN_HOST -> {
                    handleError(R.string.call_error_unresolved_hostname)
                    setContactState(Contact.State.CONTACT_OFFLINE)
                }
                CallState.ERROR_COMMUNICATION -> {
                    handleError(R.string.call_error)
                    setContactState(Contact.State.COMMUNICATION_FAILED)
                }
                CallState.ERROR_NO_NETWORK -> {
                    handleError(R.string.call_no_network)
                    setContactState(Contact.State.CONTACT_OFFLINE)
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

            // video available for pip
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

    private fun updateCameraButtons() {
        val cameraEnabled = currentCall.getCameraEnabled()

        Log.d(this, "updateCameraButtons: cameraEnabled=$cameraEnabled")

        if (cameraEnabled) {
            toggleFrontCameraButton.visibility = View.VISIBLE
            toggleCameraButton.setImageResource(R.drawable.ic_camera_off)
        } else {
            toggleFrontCameraButton.visibility = View.GONE
            toggleCameraButton.setImageResource(R.drawable.ic_camera_on)
        }
    }

    private fun updateDebugDisplay() {
        val cameraEnabled = currentCall.getCameraEnabled()

        Log.d(this, "updateDebugDisplay: cameraEnabled=$cameraEnabled")

        if (debugOutputEnabled) {
            currentCall.setStatsCollector(statsCollector)
            callStats.visibility = View.VISIBLE
            callAddress.visibility = View.VISIBLE
        } else {
            currentCall.setStatsCollector(null)
            callStats.visibility = View.GONE
            callAddress.visibility = View.GONE
        }

        if (debugOutputEnabled && cameraEnabled) {
            captureFormatSlider.setOnSeekBarChangeListener(CaptureQualityController(captureFormatText, this))
            captureFormatText.visibility = View.VISIBLE
            captureFormatSlider.visibility = View.VISIBLE
        } else {
            captureFormatSlider.setOnSeekBarChangeListener(null)
            captureFormatText.visibility = View.GONE
            captureFormatSlider.visibility = View.GONE
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
            updateCameraButtons()
            updateDebugDisplay()
        }
    }

    override fun onLocalVideoEnabled(enabled: Boolean) {
        Log.d(this, "onLocalVideoEnabled: $enabled")
        runOnUiThread {
            isLocalVideoAvailable = enabled
            updateVideoDisplay()
            updateCameraButtons()
            updateDebugDisplay()
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

    // set debug output
    override fun onRemoteAddressChange(address: InetSocketAddress, isConnected: Boolean) {
        runOnUiThread {
            val addressString = address.toString().replace("/", "")
            if (isConnected) {
                callAddress.text = String.format(getString(R.string.connected_to_address), addressString)
            } else {
                callAddress.text = String.format(getString(R.string.connecting_to_address), addressString)
            }
        }
    }

    override fun showTextMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate")

        super.onCreate(savedInstanceState)

        // keep screen on during the call
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_call)

        // keep screen on during the call
        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        callStatus = findViewById(R.id.callStatus)
        callStats = findViewById(R.id.callStats)
        callAddress = findViewById(R.id.callAddress)
        nameTextView = findViewById(R.id.callName)
        pipRenderer = findViewById(R.id.pip_video_view)
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
        togglePipButton = findViewById(R.id.toggle_pip_window)
        toggleCameraButton = findViewById(R.id.toggleCameraButton)
        toggleMicButton = findViewById(R.id.toggleMicButton)
        acceptButton = findViewById(R.id.acceptButton)
        declineButton = findViewById(R.id.declineButton)
        toggleFrontCameraButton = findViewById(R.id.frontFacingSwitch)
        speakerphoneButton = findViewById(R.id.speakerphoneButton)
        captureFormatSlider = findViewById(R.id.captureFormatSlider)
        captureFormatText = findViewById(R.id.captureFormatText)

        contact = intent.extras!!["EXTRA_CONTACT"] as Contact

        eglBase = EglBase.create()
        proximitySensor = RTCProximitySensor(applicationContext)
        rtcAudioManager = RTCAudioManager(applicationContext)

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

        initRinging()

        if (contact.name.isEmpty()) {
            nameTextView.text = resources.getString(R.string.unknown_caller)
        } else {
            nameTextView.text = contact.name
        }

        Log.d(this, "intent: ${intent.action}, state: ${this.lifecycle.currentState}")

        when (intent.action) {
            "ACTION_OUTGOING_CALL" -> initOutgoingCall()
            "ACTION_INCOMING_CALL" -> initIncomingCall()
            else -> {
                Log.e(this, "invalid action: ${intent.action}, this should never happen")
                finish()
            }
        }
    }

    private fun initOutgoingCall() {
        connection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                Log.d(this@CallActivity, "onServiceConnected")
                binder = iBinder as MainService.MainBinder
                currentCall = RTCCall(binder!!, contact)
                binder!!.setCurrentCall(currentCall)

                updateVideoDisplay()

                if (!binder!!.getSettings().promptOutgoingCalls) {
                    // start outgoing call immediately
                    acceptButton.performClick()
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                // nothing to do
            }
        }
        bindService(Intent(this, MainService::class.java), connection, 0)

        val declineListener = View.OnClickListener {
            Log.d(this, "decline call...")

            if (callWasStarted) {
                currentCall.hangup()
            } else {
                currentCall.decline()
            }
        }

        val startCallListener = View.OnClickListener {
            Log.d(this, "start call...")
            if (binder!!.getCurrentCall() == null) {
                Log.d(this, "currentCall not set")
                return@OnClickListener
            }

            currentCall.setRemoteRenderer(remoteProxyVideoSink)
            currentCall.setLocalRenderer(localProxyVideoSink)
            currentCall.setCallContext(this@CallActivity)
            currentCall.setEglBase(eglBase)

            currentCall.initVideo()
            currentCall.initOutgoing()

            initCall()

            acceptButton.visibility = View.GONE
            declineButton.visibility = View.VISIBLE
        }

        acceptButton.visibility = View.VISIBLE
        declineButton.visibility = View.VISIBLE

        acceptButton.setOnClickListener(startCallListener)
        declineButton.setOnClickListener(declineListener)
    }

    private fun initIncomingCall() {
        connection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                Log.d(this@CallActivity, "onServiceConnected")
                binder = iBinder as MainService.MainBinder
                currentCall = binder!!.getCurrentCall()!!

                currentCall.setRemoteRenderer(remoteProxyVideoSink)
                currentCall.setLocalRenderer(localProxyVideoSink)
                currentCall.setCallContext(this@CallActivity)
                currentCall.setEglBase(eglBase)

                Thread {
                    currentCall.continueOnIncomingSocket()
                }.start()

                updateVideoDisplay()

                startRinging()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                binder = null
            }
        }

        bindService(Intent(this, MainService::class.java), connection, 0)

        // decline before call starts
        val declineListener = View.OnClickListener {
            Log.d(this, "decline call...")
            stopRinging()

            if (callWasStarted) {
                currentCall.hangup()
            } else {
                currentCall.decline()
            }
        }

        // accept call
        val acceptListener = View.OnClickListener {
            Log.d(this, "accept call...")
            if (binder!!.getCurrentCall() == null) {
                Log.d(this, "currentCall not set")
                return@OnClickListener
            }

            stopRinging()

            acceptButton.visibility = View.GONE
            declineButton.visibility = View.VISIBLE

            currentCall.initVideo()
            currentCall.initIncoming()

            initCall()
        }

        acceptButton.setOnClickListener(acceptListener)
        declineButton.setOnClickListener(declineListener)

        acceptButton.visibility = View.VISIBLE
        declineButton.visibility = View.VISIBLE
    }

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

        Log.d(this, "continueCallSetup"
            + " init.action: ${intent.action}"
            + ", lifecycle.currentState: ${this.lifecycle.currentState}"
            + ", audio permissions: ${Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)}"
            + ", video permissions: ${Utils.hasPermission(this, Manifest.permission.CAMERA)}"
        )

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

        findViewById<ImageButton>(R.id.toggle_debug_output).setOnClickListener {
            debugOutputEnabled = !debugOutputEnabled
            updateDebugDisplay()
        }

        toggleMicButton.setOnClickListener { switchMicEnabled() }
        toggleCameraButton.setOnClickListener { switchCameraEnabled() }
        speakerphoneButton.setOnClickListener { switchSpeakerphoneMode() }

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

        Log.d(this, "initCall"
            + " mic ${settings.micEnabled} => ${currentCall.getMicrophoneEnabled()}"
            + ", cam ${settings.cameraEnabled} =>  ${currentCall.getCameraEnabled()}"
            + ", front cam ${settings.frontCameraEnabled} => ${currentCall.getFrontCameraEnabled()}")

        if (settings.micEnabled != currentCall.getMicrophoneEnabled()) {
            switchMicEnabled()
        }

        if (settings.cameraEnabled != currentCall.getCameraEnabled()) {
            switchCameraEnabled()
        }

        if (settings.frontCameraEnabled != currentCall.getFrontCameraEnabled()) {
            currentCall.setFrontCameraEnabled(settings.frontCameraEnabled)
        }

        rtcAudioManager.setEventListener(object : RTCAudioManager.AudioManagerEvents {
            private fun getAudioDeviceName(device: RTCAudioManager.AudioDevice): String {
                val id = when (device) {
                    RTCAudioManager.AudioDevice.SPEAKER_PHONE -> R.string.audio_device_speakerphone
                    RTCAudioManager.AudioDevice.WIRED_HEADSET -> R.string.audio_device_wired_headset
                    RTCAudioManager.AudioDevice.EARPIECE -> R.string.audio_device_earpiece
                    RTCAudioManager.AudioDevice.BLUETOOTH -> R.string.audio_device_bluetooth
                }
                return getString(id)
            }

            override fun onBluetoothConnectPermissionRequired() {
                Log.d(this, "onBluetoothConnectPermissionRequired()")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    allowBluetoothConnectForResult.launch(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    allowBluetoothConnectForResult.launch(Manifest.permission.BLUETOOTH)
                }
            }

            override fun onAudioDeviceChanged(
                        oldDevice: RTCAudioManager.AudioDevice,
                        newDevice: RTCAudioManager.AudioDevice) {
                val nameOld = getAudioDeviceName(oldDevice)
                val nameNew = getAudioDeviceName(newDevice)
                if (rtcAudioManager.getSpeakerphoneMode() == RTCAudioManager.SpeakerphoneMode.AUTO) {
                    showTextMessage("$nameOld => $nameNew (auto)")
                } else {
                    showTextMessage("$nameOld => $nameNew (fixed)")
                }
                updateSpeakerphoneIcon()
            }
        })

        val speakerphoneMode = when (binder!!.getSettings().speakerphoneMode) {
            "auto" -> RTCAudioManager.SpeakerphoneMode.AUTO
            "on" -> RTCAudioManager.SpeakerphoneMode.ON
            "off" -> RTCAudioManager.SpeakerphoneMode.OFF
            else -> RTCAudioManager.SpeakerphoneMode.AUTO
        }

        // set initial speakerphone mode
        rtcAudioManager.setSpeakerphoneMode(speakerphoneMode)
        updateSpeakerphoneIcon()
        rtcAudioManager.start()

        setProximitySensorEnabled(!binder!!.getSettings().disableProximitySensor)

        toggleMicButton.visibility = View.VISIBLE
        toggleCameraButton.visibility = View.VISIBLE
        toggleFrontCameraButton.visibility = View.GONE
    }

    private fun setProximitySensorEnabled(enabled: Boolean) {
        if (enabled) {
            proximitySensor.addListener(rtcAudioManager::onProximitySensorChangedState)
            proximitySensor.addListener(::onProximitySensorToggleScreen)
            proximitySensor.addListener(::onProximitySensorToggleCamera)
            proximitySensor.start()
        } else {
            proximitySensor.stop()
        }
    }

    override fun onFrontFacingCamera(enabled: Boolean) {
        runOnUiThread {
            updateVideoDisplay()
        }
    }

    private fun initRinging() {
        Log.d(this, "initRinging")

        // init ringtone
        ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            )
        )

        // init vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun startRinging() {
        Log.d(this, "startRinging")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        val pattern = longArrayOf(1500, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibe = VibrationEffect.createWaveform(pattern, 1)
            vibrator.vibrate(vibe)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 1)
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return
        }

        ringtone.play()
    }

    private fun stopRinging() {
        Log.d(this, "stopRinging")
        vibrator.cancel()
        ringtone.stop()
    }

    fun onCaptureFormatChange(width: Int, height: Int, framerate: Int) {
        currentCall.changeCaptureFormat(width, height, framerate)
    }

    private fun updateSpeakerphoneIcon() {
        Log.d(this, "updateSpeakerphoneIcon()")

        val mode = rtcAudioManager.getSpeakerphoneMode()
        val device = rtcAudioManager.getAudioDevice()

        // get matching button icon
        val icon = when (mode) {
            RTCAudioManager.SpeakerphoneMode.AUTO -> R.drawable.ic_audio_device_bluetooth // preferred device
            RTCAudioManager.SpeakerphoneMode.ON -> R.drawable.ic_audio_device_speakerphone // enforced setting
            RTCAudioManager.SpeakerphoneMode.OFF -> R.drawable.ic_audio_device_phone // enforced setting
        }

         Log.d(this, "updateSpeakerphoneIcon() mode=$mode, device=$device")
        speakerphoneButton.setImageResource(icon)
    }

    private fun switchSpeakerphoneMode() {
        val oldMode = rtcAudioManager.getSpeakerphoneMode()

        // switch to the next speakerphone mode
        val newMode = when (oldMode) {
            RTCAudioManager.SpeakerphoneMode.AUTO -> RTCAudioManager.SpeakerphoneMode.OFF
            RTCAudioManager.SpeakerphoneMode.OFF -> RTCAudioManager.SpeakerphoneMode.ON
            RTCAudioManager.SpeakerphoneMode.ON -> RTCAudioManager.SpeakerphoneMode.AUTO
        }

        Log.d(this, "switchSpeakerphoneMode: $oldMode => $newMode")
        rtcAudioManager.setSpeakerphoneMode(newMode)
        updateSpeakerphoneIcon()
    }

    private val allowBluetoothConnectForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            rtcAudioManager.startBluetooth()
        } else {
            // do not turn on microphone
            showTextMessage(getString(R.string.missing_bluetooth_permission))
        }
        Log.d(this, "allowBluetoothConnectForResult() isGranted=$isGranted")
    }

    private val enabledMicrophoneForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            switchMicEnabled()
        } else {
            // do not turn on microphone
            showTextMessage(getString(R.string.missing_microphone_permission))
        }
    }

    private fun switchMicEnabled() {
        if (currentCall.getMicrophoneEnabled()) {
            // turn microphone off
            currentCall.setMicrophoneEnabled(false)
        } else {
            // check permission
            if (!Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                enabledMicrophoneForResult.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            // check and request permission
            currentCall.setMicrophoneEnabled(true)
        }
    }

    private val enabledCameraForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            switchCameraEnabled()
        } else {
            // do not turn on camera
            showTextMessage(getString(R.string.missing_camera_permission))
        }
    }

    private fun switchCameraEnabled() {
        Log.d(this, "switchCameraEnabled")

        if (currentCall.getCameraEnabled()) {
            // turn camera off
            currentCall.setCameraEnabled(false)
        } else {
            // check and request permission
            if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
                enabledCameraForResult.launch(Manifest.permission.CAMERA)
                return
            }
            // turn camera on
            currentCall.setCameraEnabled(true)
        }
    }

    override fun onDestroy() {
        Log.d(this, "onDestroy")

        currentCall.setCallContext(null)

        proximitySensor.stop()

        stopRinging()

        binder!!.setCurrentCall(null)

        currentCall.cleanup()

        if (callEventType != Event.Type.UNKNOWN) {
            val event = Event(contact.publicKey, contact.lastWorkingAddress, callEventType, Date())
            binder!!.addEvent(event)
        }

        unbindService(connection)

        proximityScreenLock?.release()

        rtcAudioManager.stop()

        remoteProxyVideoSink.setTarget(null)
        localProxyVideoSink.setTarget(null)

        pipRenderer.release()
        fullscreenRenderer.release()

        currentCall.releaseCamera()

        eglBase.release()

        super.onDestroy()
    }

    private fun finishDelayed() {
        if (activityActive) {
            stopRinging() // do not wait
            activityActive = false
            Handler(mainLooper).postDelayed({ finish() }, 2000)
        }
    }

    // turn off/on the camera while the proximity sensor is triggered
    private fun onProximitySensorToggleCamera(isNear: Boolean) {
        Log.d(this, "onProximitySensorToggleCamera: $isNear")

        if (isNear) {
            if (currentCall.getCameraEnabled() && !currentCall.getFrontCameraEnabled()) {
                currentCall.setCameraEnabled(false)
                proximityCameraWasOn = true
            }
        } else {
            if (proximityCameraWasOn) {
                currentCall.setCameraEnabled(true)
                proximityCameraWasOn = false
            }
        }
    }

    // turn off/on the screen while the proximity sensor is triggered
    private fun onProximitySensorToggleScreen(isProximityNear: Boolean) {
        Log.d(this, "onProximitySensorToggleScreen: $isProximityNear")

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        proximityScreenLock?.release()

        if (isProximityNear) {
            // turn screen off
            proximityScreenLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "meshenger:tag"
            )
            proximityScreenLock?.acquire(10*60*1000L) // 10 minutes
        } else {
            // turn screen on
            proximityScreenLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "meshenger:tag"
            )
            proximityScreenLock?.acquire(10*60*1000L) // 10 minutes
        }
    }
}
