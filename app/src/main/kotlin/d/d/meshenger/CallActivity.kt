/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.os.PowerManager.WakeLock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager.LayoutParams
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import d.d.meshenger.call.*
import d.d.meshenger.call.RTCPeerConnection.CallState
import org.webrtc.*
import java.net.InetSocketAddress
import java.util.*

class CallActivity : BaseActivity(), RTCCall.CallContext {
    private var binder: MainService.MainBinder? = null
    private lateinit var connection: ServiceConnection
    private lateinit var currentCall: RTCCall
    private lateinit var contact: Contact
    private lateinit var eglBase: EglBase
    private lateinit var proximitySensor: RTCProximitySensor
    private lateinit var rtcAudioManager: RTCAudioManager

    private var proximityScreenLock: WakeLock? = null
    private var proximityCameraWasOn = false

    private var activityActive = true
    private var callEventType = Event.Type.UNKNOWN
    private lateinit var vibrator: Vibrator
    private lateinit var ringtone: Ringtone

    private val remoteProxyVideoSink = RTCCall.ProxyVideoSink()
    private val localProxyVideoSink = RTCCall.ProxyVideoSink()

    private lateinit var pipRenderer: SurfaceViewRenderer
    private lateinit var fullscreenRenderer: SurfaceViewRenderer

    // call info texts
    private lateinit var callStatus: TextView
    private lateinit var callStats: TextView
    private lateinit var callAddress: TextView
    private lateinit var callName: TextView

    // control buttons
    private lateinit var acceptButton: ImageButton
    private lateinit var declineButton: ImageButton
    private lateinit var toggleCameraButton: ImageButton
    private lateinit var toggleMicButton: ImageButton
    private lateinit var toggleFrontCameraButton: ImageButton
    private lateinit var speakerphoneButton: ImageButton

    private lateinit var changePipButton: ImageButton // show/hide Picture-in-Picture window
    private lateinit var changeUiButton: ImageButton // show/hide different control
    private lateinit var controlPanel: View
    private lateinit var capturePanel: View
    private lateinit var captureQualityController: CaptureQualityController

    private var uiMode = 0

    // set by CallActivity
    private var swappedVideoFeeds = false // swapped fullscreen and pip video content
    private var showPipEnabled = true // enable PIP window
    private var callWasStarted = false

    // set by RTCall
    private var isLocalVideoAvailable = false // own camera is on/off
    private var isRemoteVideoAvailable = false // we receive a video feed

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
            Log.d(this, "onStateChange() state=$state")
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

        Log.d(this, "updateVideoDisplay() swappedVideoFeeds=$swappedVideoFeeds, frontCameraEnabled=$frontCameraEnabled")

        if (swappedVideoFeeds) {
            localProxyVideoSink.setTarget(fullscreenRenderer)
            remoteProxyVideoSink.setTarget(pipRenderer)

            pipRenderer.setMirror(false)
            fullscreenRenderer.setMirror(false)

            showPipView(isRemoteVideoAvailable && showPipEnabled)
            showFullscreenView(isLocalVideoAvailable)

            // video available for pip
            setPipButtonEnabled(isRemoteVideoAvailable)
        } else {
            // default (local video in pip, remote video in fullscreen)
            localProxyVideoSink.setTarget(pipRenderer)
            remoteProxyVideoSink.setTarget(fullscreenRenderer)

            pipRenderer.setMirror(false)
            fullscreenRenderer.setMirror(false)

            showPipView(isLocalVideoAvailable && showPipEnabled)
            showFullscreenView(isRemoteVideoAvailable)

            // video available for pip
            setPipButtonEnabled(isLocalVideoAvailable)
        }
    }

    private fun updateCameraButtons() {
        val cameraEnabled = currentCall.getCameraEnabled()

        Log.d(this, "updateCameraButtons() cameraEnabled=$cameraEnabled")

        if (cameraEnabled) {
            toggleFrontCameraButton.visibility = View.VISIBLE
            toggleCameraButton.setImageResource(R.drawable.ic_camera_off)
        } else {
            toggleFrontCameraButton.visibility = View.GONE
            toggleCameraButton.setImageResource(R.drawable.ic_camera_on)
        }
    }

    private fun updateControlDisplay() {
        Log.d(this, "updateControlDisplay() uiMode=$uiMode")

        val updateDebug = { enable: Boolean ->
            if (enable) {
                currentCall.setStatsCollector(statsCollector)
                callStats.visibility = View.VISIBLE
            } else {
                currentCall.setStatsCollector(null)
                callStats.visibility = View.GONE
            }

            if (enable) {
                capturePanel.visibility = View.VISIBLE
                callAddress.visibility = View.VISIBLE
            } else {
                capturePanel.visibility = View.GONE
                callAddress.visibility = View.GONE
            }
        }

        when (uiMode % 3) {
            0 -> {
                // default
                updateDebug(false)
                controlPanel.visibility = View.VISIBLE
                callName.visibility = View.VISIBLE
                callStatus.visibility = View.VISIBLE
            }
            1 -> {
                // default + debug
                updateDebug(true)
                controlPanel.visibility = View.VISIBLE
                callName.visibility = View.VISIBLE
                callStatus.visibility = View.VISIBLE
            }
            2 -> {
                // all off
                updateDebug(false)
                controlPanel.visibility = View.GONE
                callName.visibility = View.GONE
                callStatus.visibility = View.GONE
            }
        }
    }

    private fun setPipButtonEnabled(enable: Boolean) {
        Log.d(this, "setPipButtonEnabled() enable=$enable")
        if (enable) {
            changePipButton.visibility = View.VISIBLE
        } else {
            changePipButton.visibility = View.INVISIBLE
        }
    }

    private fun showPipView(enable: Boolean) {
        Log.d(this, "showPipView() enable=$enable")
        if (enable) {
            pipRenderer.visibility = View.VISIBLE
        } else {
            pipRenderer.visibility = View.INVISIBLE
        }
    }

    private fun showFullscreenView(enable: Boolean) {
        Log.d(this, "showFullscreenView() enable=$enable")
        if (enable) {
            fullscreenRenderer.visibility = View.VISIBLE
        } else {
            fullscreenRenderer.visibility = View.INVISIBLE
        }
    }

    private fun updateMicrophoneIcon() {
        Log.d(this, "updateMicrophoneIcon()")

        val enabled = currentCall.getMicrophoneEnabled() && rtcAudioManager.getMicrophoneEnabled()

        if (enabled) {
            toggleMicButton.setImageResource(R.drawable.ic_mic_on)
        } else {
            toggleMicButton.setImageResource(R.drawable.ic_mic_off)
        }

        // set background
        val settings = binder!!.getSettings()
        if (settings.pushToTalk) {
            val backgroundId = when (enabled) {
                true -> R.drawable.ic_button_background_enabled_border
                false -> R.drawable.ic_button_background_disabled_border
            }
            toggleMicButton.background = ResourcesCompat.getDrawable(resources, backgroundId, null)
        }
    }

    override fun onDataChannelReady() {
        Log.d(this, "onDataChannelReady()")
        runOnUiThread {
            updateCameraButtons()
            updateControlDisplay()

            val settings = binder!!.getSettings()
            if (settings.enableMicrophoneByDefault != currentCall.getMicrophoneEnabled()) {
                if (!settings.pushToTalk) {
                    Log.d(this, "onDataChannelReady() toggle microphone")
                    toggleMicButton.performClick()
                }
            }

            if (settings.enableCameraByDefault != currentCall.getCameraEnabled()) {
                Log.d(this, "onDataChannelReady() toggle camera")
                toggleCameraButton.performClick()
            }

            if (settings.selectFrontCameraByDefault != currentCall.getFrontCameraEnabled()) {
                Log.d(this, "onDataChannelReady() toggle front camera")
                toggleFrontCameraButton.performClick()
            }
        }
    }

    override fun onLocalVideoEnabled(enabled: Boolean) {
        Log.d(this, "onLocalVideoEnabled() enabled=$enabled")
        runOnUiThread {
            isLocalVideoAvailable = enabled
            updateVideoDisplay()
            updateCameraButtons()
            updateControlDisplay()
        }
    }

    override fun onRemoteVideoEnabled(enabled: Boolean) {
        Log.d(this, "onRemoteVideoEnabled() enabled=$enabled")
        runOnUiThread {
            isRemoteVideoAvailable = enabled
            updateVideoDisplay()
            updateControlDisplay()
        }
    }

    override fun onMicrophoneEnabled(enabled: Boolean) {
        Log.d(this, "onMicrophoneEnabled() enabled=$enabled")
        runOnUiThread {
            updateMicrophoneIcon()
        }
    }

    // set debug output
    override fun onRemoteAddressChange(address: InetSocketAddress, isConnected: Boolean) {
        runOnUiThread {
            val addressString = address.toString().replace("/", "")
            val formatString = if (isConnected) {
                getString(R.string.connected_to_address)
            } else {
                getString(R.string.connecting_to_address)
            }

            callAddress.text = String.format(formatString, addressString)
        }
    }

    override fun showTextMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate()")

        CallActivity.isCallInProgress = true
        super.onCreate(savedInstanceState)

        // keep screen on during the call
        window.addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_call)

        // keep screen on during the call
        //window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        callStatus = findViewById(R.id.callStatus)
        callStats = findViewById(R.id.callStats)
        callAddress = findViewById(R.id.callAddress)
        callName = findViewById(R.id.callName)
        pipRenderer = findViewById(R.id.pip_video_view)
        fullscreenRenderer = findViewById(R.id.fullscreen_video_view)
        toggleCameraButton = findViewById(R.id.toggleCameraButton)
        toggleMicButton = findViewById(R.id.toggleMicButton)
        acceptButton = findViewById(R.id.acceptButton)
        declineButton = findViewById(R.id.declineButton)
        toggleFrontCameraButton = findViewById(R.id.frontFacingSwitch)
        speakerphoneButton = findViewById(R.id.speakerphoneButton)
        changePipButton = findViewById(R.id.change_pip_window)
        changeUiButton = findViewById(R.id.change_ui)
        controlPanel = findViewById(R.id.controlPanel)
        capturePanel = findViewById(R.id.capturePanel)

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

        captureQualityController = CaptureQualityController(this)

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
            callName.text = resources.getString(R.string.unknown_caller)
        } else {
            callName.text = contact.name
        }

        Log.d(this, "intent: ${intent.action}, state: ${this.lifecycle.currentState}")

        when (val action = intent.action) {
            "ACTION_OUTGOING_CALL" -> initOutgoingCall()
            "ACTION_INCOMING_CALL" -> initIncomingCall()
            else -> {
                Log.e(this, "invalid action: $action, this should never happen")
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
                currentCall.setCallContext(this@CallActivity)

                captureQualityController.initFromSettings(binder!!.getSettings())

                updateControlDisplay()
                updateVideoDisplay()

                continueCallSetup()

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
            if (!this::currentCall.isInitialized) {
                Log.d(this, "currentCall not set")
                return@OnClickListener
            }

            if (callWasStarted) {
                currentCall.hangup()
            } else {
                currentCall.decline()
            }
        }

        val startCallListener = View.OnClickListener {
            Log.d(this, "start call...")
            if (!this::currentCall.isInitialized) {
                Log.d(this, "currentCall not set")
                return@OnClickListener
            }

            currentCall.setRemoteRenderer(remoteProxyVideoSink)
            currentCall.setLocalRenderer(localProxyVideoSink)
            currentCall.setEglBase(eglBase)
            currentCall.setCallContext(this@CallActivity)

            currentCall.initVideo()
            currentCall.initOutgoing()

            acceptButton.visibility = View.GONE
            declineButton.visibility = View.VISIBLE

            initCall()
        }

        acceptButton.visibility = View.VISIBLE
        declineButton.visibility = View.VISIBLE

        acceptButton.setOnClickListener(startCallListener)
        declineButton.setOnClickListener(declineListener)
    }

    private fun initIncomingCall() {
        connection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                Log.d(this@CallActivity, "onServiceConnected()")
                binder = iBinder as MainService.MainBinder
                currentCall = RTCPeerConnection.incomingRTCCall ?: run {
                    // This happens when the call is missed while in background.
                    // and then the CallActivity is started from Recent Apps.
                    Log.d(this, "initIncomingCall() no call active => start MainActivity")
                    val intent = Intent(getContext(), MainActivity::class.java)
                    startActivity(intent)
                    finish()
                    return
                }
                currentCall.setRemoteRenderer(remoteProxyVideoSink)
                currentCall.setLocalRenderer(localProxyVideoSink)
                currentCall.setCallContext(this@CallActivity)
                currentCall.setEglBase(eglBase)

                captureQualityController.initFromSettings(binder!!.getSettings())

                Thread {
                    currentCall.continueOnIncomingSocket()
                }.start()

                updateControlDisplay()
                updateVideoDisplay()

                continueCallSetup()

                if (binder!!.getSettings().autoAcceptCalls) {
                    acceptButton.performClick()
                } else {
                    startRinging()
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                // nothing to do
            }
        }

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
            if (!this::currentCall.isInitialized) {
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

        bindService(Intent(this, MainService::class.java), connection, 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun continueCallSetup() {
        Log.d(this, "continueCallSetup()"
            + " lifecycle.currentState: ${lifecycle.currentState}"
            + ", init.action: ${intent.action}"
            + ", lifecycle.currentState: ${this.lifecycle.currentState}"
            + ", audio permissions: ${Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)}"
            + ", video permissions: ${Utils.hasPermission(this, Manifest.permission.CAMERA)}"
        )

        val settings = binder!!.getSettings()

        // swap pip and fullscreen content
        pipRenderer.setOnClickListener {
            Log.d(this, "pipRenderer.setOnClickListener()")
            showPipEnabled = true
            swappedVideoFeeds = !swappedVideoFeeds
            updateVideoDisplay()
        }

        // swap pip and fullscreen content
        fullscreenRenderer.setOnClickListener {
            Log.d(this, "fullscreenRenderer.setOnClickListener()")
            swappedVideoFeeds = !swappedVideoFeeds
            showPipEnabled = true
            updateVideoDisplay()
        }

        changePipButton.setOnClickListener {
            Log.d(this, "changePipButton.setOnClickListener()")
            showPipEnabled = !showPipEnabled
            updateVideoDisplay()
        }

        changeUiButton.setOnClickListener {
            uiMode = (uiMode + 1) % 3
            updateControlDisplay()
        }

        toggleCameraButton.setOnClickListener { switchCameraEnabled() }
        speakerphoneButton.setOnClickListener { changeSpeakerphoneMode() }

        if (!settings.pushToTalk) {
            // default behavior
            toggleMicButton.setOnClickListener { switchMicrophoneEnabled() }
        } else {
            toggleMicButton.setOnTouchListener { view: View, event: MotionEvent ->
                Log.d(this, "setOnTouchListener() action=${event.action}")
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // enable microphone
                        if (!currentCall.getMicrophoneEnabled()) {
                            switchMicrophoneEnabled()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        // disable microphone
                        if (currentCall.getMicrophoneEnabled()) {
                            switchMicrophoneEnabled()
                        }
                    }
                }

                updateMicrophoneIcon()

                view.onTouchEvent(event)
            }
        }

        updateMicrophoneIcon()

        toggleFrontCameraButton.setOnClickListener {
            Log.d(this, "frontFacingSwitch() swappedVideoFeeds=$swappedVideoFeeds, frontCameraEnabled=${currentCall.getFrontCameraEnabled()}}")
            currentCall.switchCamera(
                !currentCall.getFrontCameraEnabled()
            )
        }
    }

    private fun initCall() {
        val settings = binder!!.getSettings()

        Log.d(this, "initCall() settings"
            + " microphone ${currentCall.getMicrophoneEnabled()} => ${settings.enableMicrophoneByDefault}"
            + ", camera ${currentCall.getCameraEnabled()} => ${settings.enableCameraByDefault}"
            + ", front camera ${currentCall.getFrontCameraEnabled()} => ${settings.selectFrontCameraByDefault}")

        rtcAudioManager.setEventListener(object : RTCAudioManager.AudioManagerEvents {
            private fun getAudioDeviceName(device: RTCAudioManager.AudioDevice): String {
                return when (device) {
                    RTCAudioManager.AudioDevice.SPEAKER_PHONE -> getString(R.string.audio_device_speakerphone)
                    RTCAudioManager.AudioDevice.WIRED_HEADSET -> getString(R.string.audio_device_wired_headset)
                    RTCAudioManager.AudioDevice.EARPIECE -> getString(R.string.audio_device_earpiece)
                    RTCAudioManager.AudioDevice.BLUETOOTH -> getString(R.string.audio_device_bluetooth)
                }
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
                    showTextMessage(
                        String.format(getString(R.string.audio_device_auto), nameNew)
                    )
                } else {
                    showTextMessage(
                        String.format(getString(R.string.audio_device_fixed), nameNew)
                    )
                }
                updateSpeakerphoneIcon()
            }
        })

        val speakerphoneMode = when (val mode = settings.speakerphoneMode) {
            "auto" -> RTCAudioManager.SpeakerphoneMode.AUTO
            "on" -> RTCAudioManager.SpeakerphoneMode.ON
            "off" -> RTCAudioManager.SpeakerphoneMode.OFF
            else -> {
                Log.w(this, "Invalid speakerphone mode: $mode")
                RTCAudioManager.SpeakerphoneMode.AUTO
            }
        }

        // set initial speakerphone mode
        rtcAudioManager.setSpeakerphoneMode(speakerphoneMode)
        updateSpeakerphoneIcon()
        rtcAudioManager.start()

        setProximitySensorEnabled(!settings.disableProximitySensor)

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

    private fun initRinging() {
        Log.d(this, "initRinging")

        // init ringtone
        ringtone = try {
            RingtoneManager.getRingtone(this,
            RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE
            ))
        } catch (e: SecurityException) {
            Log.w(this, "Cannot get default ringtone, use fallback ringtone: $e")
            showTextMessage("Failed to get default ringtone.")
            // Use orion ringtone from LineageOS as fallback.
            // Some Samsung phones call setRingtonesAsInitValue() and throw a SecurityException
            // because android.permission.WRITE_SETTINGS is not granted
            val uri = Uri.parse("android.resource://${packageName}/${R.raw.orion}")
            RingtoneManager.getRingtone(this, uri)
        }

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
        Log.d(this, "startRinging()")
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
        Log.d(this, "stopRinging()")
        vibrator.cancel()
        ringtone.stop()
    }

    // apply settings to camera
    override fun onCameraChanged() {
        val format = captureQualityController.getSelectedFormat()
        val framerate = captureQualityController.getSelectedFramerate()
        val degradation = captureQualityController.getSelectedDegradation()

        currentCall.changeCaptureFormat(degradation, format.width, format.height, framerate)
    }

    // called when the camera is enabled/changed
    override fun onCameraChange(name: String, isFrontFacing: Boolean, formats: List<CameraEnumerationAndroid.CaptureFormat>) {
        runOnUiThread {
            updateVideoDisplay()
            captureQualityController.onCameraChange(name, isFrontFacing, formats)
        }
    }

    fun getCurrentCall(): RTCCall {
        return currentCall
    }

    private fun updateSpeakerphoneIcon() {
        Log.d(this, "updateSpeakerphoneIcon()")

        val mode = rtcAudioManager.getSpeakerphoneMode()
        val device = rtcAudioManager.getAudioDevice()

        // get matching button icon
        val icon = when (mode) {
            RTCAudioManager.SpeakerphoneMode.AUTO -> R.drawable.ic_audio_device_automatic // preferred device
            RTCAudioManager.SpeakerphoneMode.ON -> R.drawable.ic_audio_device_speakerphone // enforced setting
            RTCAudioManager.SpeakerphoneMode.OFF -> R.drawable.ic_audio_device_phone // enforced setting
        }

        Log.d(this, "updateSpeakerphoneIcon() mode=$mode, device=$device")
        speakerphoneButton.setImageResource(icon)
    }

    private fun changeSpeakerphoneMode() {
        val oldMode = rtcAudioManager.getSpeakerphoneMode()

        // switch to the next speakerphone mode
        val newMode = when (oldMode) {
            RTCAudioManager.SpeakerphoneMode.AUTO -> RTCAudioManager.SpeakerphoneMode.OFF
            RTCAudioManager.SpeakerphoneMode.OFF -> RTCAudioManager.SpeakerphoneMode.ON
            RTCAudioManager.SpeakerphoneMode.ON -> RTCAudioManager.SpeakerphoneMode.AUTO
        }

        Log.d(this, "changeSpeakerphoneMode() $oldMode => $newMode")
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
            switchMicrophoneEnabled()
        } else {
            // do not turn on microphone
            showTextMessage(getString(R.string.missing_microphone_permission))
        }
    }

    private fun switchMicrophoneEnabled() {
        Log.d(this, "switchMicrophoneEnabled()")

        if (!currentCall.getMicrophoneEnabled()) {
            // check permission
            if (!Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                enabledMicrophoneForResult.launch(Manifest.permission.RECORD_AUDIO)
                return
            }

            // turn microphone on
            currentCall.setMicrophoneEnabled(true)
        } else {
            // turn microphone off
            currentCall.setMicrophoneEnabled(false)
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
        Log.d(this, "switchCameraEnabled()")

        if (!currentCall.getCameraEnabled()) {
            // check and request permission
            if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
                enabledCameraForResult.launch(Manifest.permission.CAMERA)
                return
            }
            // turn camera on
            currentCall.setCameraEnabled(true)
        } else {
            // turn camera off
            currentCall.setCameraEnabled(false)
        }
    }

    override fun onDestroy() {
        Log.d(this, "onDestroy()")

        try {
            proximitySensor.stop()

            stopRinging()

            if (this::currentCall.isInitialized) {
                currentCall.cleanup()
            }

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

            if (this::currentCall.isInitialized) {
                currentCall.releaseCamera()
            }

            eglBase.release()
        } catch (e: Exception) {
            Log.e(this, "onDestroy() e=$e")
        } finally {
            RTCPeerConnection.incomingRTCCall = null // free for the garbage collector
            isCallInProgress = false
        }

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
        Log.d(this, "onProximitySensorToggleCamera() isNear=$isNear")

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
        Log.d(this, "onProximitySensorToggleScreen() isProximityNear=$isProximityNear")

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

    companion object {
        @Volatile
        public var isCallInProgress: Boolean = false
    }
}
