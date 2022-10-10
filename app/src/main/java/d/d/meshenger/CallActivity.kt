package d.d.meshenger

import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.os.PowerManager.WakeLock
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
//import org.rivchain.cuplink.MainService.MainBinder
import d.d.meshenger.RTCCall.CallState
import d.d.meshenger.RTCCall.OnStateChangeListener
//import org.rivchain.cuplink.util.StatsReportUtil
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import java.io.IOException


class CallActivity : MeshengerActivity(), ServiceConnection, SensorEventListener {
    private val buttonAnimationDuration: Long = 400
    private val CAMERA_PERMISSION_REQUEST_CODE = 2
    private var statusTextView: TextView? = null
    private var callStats: TextView? = null
    private lateinit var nameTextView: TextView
    private lateinit var binder: MainService.MainBinder
    private lateinit var connection: ServiceConnection
    private lateinit var currentCall: RTCCall
    private var calledWhileScreenOff = false
    private var powerManager: PowerManager? = null
    private var wakeLock: WakeLock? = null
    private lateinit var passiveWakeLock: WakeLock
    private var permissionRequested = false
    private var contact: Contact? = null
    private var callEventType: Event.Type = Event.Type.UNKNOWN
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private val statsCollector: RTCStatsCollectorCallback = object : RTCStatsCollectorCallback {
        var statsReportUtil = StatsReportUtil()
        override fun onStatsDelivered(rtcStatsReport: RTCStatsReport) {
            val stats = statsReportUtil.getStatsReport(rtcStatsReport)
            // If you need update UI, simply do this:
            runOnUiThread {
                // update your UI component here.
                callStats!!.text = stats
            }
        }
    }
    private val activeCallback = OnStateChangeListener { callState: CallState? ->
        when (callState) {
            CallState.CONNECTING -> {
                Log.d(this, "activeCallback: CONNECTING")
                setStatusText(getString(R.string.call_connecting))
            }
            CallState.CONNECTED -> {
                Log.d(this, "activeCallback: CONNECTED")
                val devMode = true //binder.getSettings().developmentMode
                Handler(mainLooper).post {
                    if (devMode) {
                        currentCall.accept(statsCollector)
                        callStats!!.visibility = View.VISIBLE
                    } else {
                        callStats!!.visibility = View.GONE
                    }
                    findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
                    findViewById<View>(R.id.speakerMode).visibility = View.VISIBLE
                }
                setStatusText(getString(R.string.call_connected))
            }
            CallState.DISMISSED -> {
                Log.d(this, "activeCallback: DISMISSED")
                stopDelayed(getString(R.string.call_denied))
            }
            CallState.RINGING -> {
                Log.d(this, "activeCallback: RINGING")
                setStatusText(getString(R.string.call_ringing))
            }
            CallState.ENDED -> {
                Log.d(this, "activeCallback: ENDED")
                stopDelayed(getString(R.string.call_ended))
            }
            CallState.ERROR -> {
                Log.d(this, "activeCallback: ERROR")
                stopDelayed(getString(R.string.call_error))
            }
            else -> {}
        }
    }
    private val passiveCallback = OnStateChangeListener { callState: CallState? ->
        when (callState) {
            CallState.CONNECTED -> {
                Log.d(this, "passiveCallback: CONNECTED")
                setStatusText(getString(R.string.call_connected))
                val devMode = true //binder.getSettings.developmentMode
                runOnUiThread { findViewById<View>(R.id.callAccept).visibility = View.GONE }
                Handler(mainLooper).post {
                    findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
                    findViewById<View>(R.id.speakerMode).visibility = View.VISIBLE
                    if (devMode) {
                        currentCall.accept(statsCollector)
                        callStats!!.visibility = View.VISIBLE
                    } else {
                        callStats!!.visibility = View.GONE
                    }
                }
            }
            CallState.RINGING -> {
                Log.d(this, "passiveCallback: RINGING")
                setStatusText(getString(R.string.call_ringing))
            }
            CallState.ENDED -> {
                Log.d(this, "passiveCallback: ENDED")
                stopDelayed(getString(R.string.call_ended))
            }
            CallState.ERROR -> {
                Log.d(this, "passiveCallback: ERROR")
                stopDelayed(getString(R.string.call_error))
            }
            else -> {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // keep screen on during call (prevents pausing the app and cancellation of the call)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusTextView = findViewById(R.id.callStatus)
        callStats = findViewById(R.id.callStats)
        nameTextView = findViewById(R.id.callName)
        val action = intent.action

        contact = intent.extras!!["EXTRA_CONTACT"] as Contact?
        Log.d(this, "onCreate: $action")
        if ("ACTION_OUTGOING_CALL" == action) {

            callEventType = Event.Type.OUTGOING_UNKNOWN

            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    binder = iBinder as MainService.MainBinder
                    currentCall = RTCCall.startCall(
                        this@CallActivity,
                        binder,
                        contact!!,
                        activeCallback
                    )
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                    currentCall.setLocalRenderer(findViewById(R.id.localRenderer))
                    currentCall.setVideoStreamSwitchLayout(findViewById(R.id.videoStreamSwitchLayout))
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    // nothing to do
                }
            }
            if (contact!!.name.isEmpty()) {
                nameTextView.text = resources.getString(R.string.unknown_caller)
            } else {
                nameTextView.text = contact!!.name
            }
            bindService(Intent(this, MainService::class.java), connection, 0)
            val declineListener = View.OnClickListener { view: View? ->
                // end call
                currentCall.hangUp()
                callEventType = Event.Type.OUTGOING_DECLINED
                finish()
            }
            findViewById<View>(R.id.callDecline).setOnClickListener(declineListener)
            startSensor()
        } else if ("ACTION_INCOMING_CALL" == action) {
            callEventType = Event.Type.INCOMING_UNKNOWN
            calledWhileScreenOff = !(getSystemService(POWER_SERVICE) as PowerManager).isScreenOn
            passiveWakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.PARTIAL_WAKE_LOCK,
                "cuplink:wakeup"
            )
            passiveWakeLock.acquire(10000)
            connection = this
            bindService(Intent(this, MainService::class.java), this, 0)
            if (contact!!.name.isEmpty()) {
                nameTextView.text = resources.getString(R.string.unknown_caller)
            } else {
                nameTextView.text = contact!!.name
            }
            findViewById<View>(R.id.callAccept).visibility = View.VISIBLE
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
            val hangupListener = View.OnClickListener { view: View? ->
                stopRinging() // make sure ringing has stopped ;-)
                Log.d(this, "hangup call...")
                currentCall.decline()
                if (passiveWakeLock.isHeld) {
                    passiveWakeLock.release()
                }
                callEventType = Event.Type.INCOMING_ACCEPTED
                finish()
            }
            val acceptListener = View.OnClickListener {
                stopRinging()
                Log.d(this, "accepted call...")
                try {
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                    currentCall.setLocalRenderer(findViewById(R.id.localRenderer))
                    currentCall.setVideoStreamSwitchLayout(findViewById(R.id.videoStreamSwitchLayout))
                    currentCall.accept(passiveCallback)
                    if (passiveWakeLock.isHeld) {
                        passiveWakeLock.release()
                    }
                    findViewById<View>(R.id.callDecline).setOnClickListener(hangupListener)
                    startSensor()
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopDelayed("Error accepting call")
                    findViewById<View>(R.id.callAccept).visibility = View.GONE
                    callEventType = Event.Type.INCOMING_ERROR
                }
            }
            findViewById<View>(R.id.callAccept).setOnClickListener(acceptListener)
            findViewById<View>(R.id.callDecline).setOnClickListener(declineListener)
        }
        findViewById<View>(R.id.speakerMode).setOnClickListener { button: View ->
            chooseVoiceMode(
                button as ImageButton
            )
        }
        findViewById<View>(R.id.videoStreamSwitch).setOnClickListener { button: View ->
            switchVideoEnabled(button as ImageButton)
        }
        findViewById<View>(R.id.frontFacingSwitch).setOnClickListener { button: View? -> currentCall.switchFrontFacing() }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(declineBroadcastReceiver, IntentFilter("call_declined"))
    }

    private fun startRinging() {
        Log.d(this, "startRinging")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
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

    private fun chooseVoiceMode(button: ImageButton) {
        val audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
        currentCall.isSpeakerEnabled = !currentCall.isSpeakerEnabled
        if (currentCall.isSpeakerEnabled) {
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

    private fun switchVideoEnabled(button: ImageButton) {
        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE)
            permissionRequested = true
            return
        }
        currentCall.isVideoEnabled = !currentCall.isVideoEnabled
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
        animation.duration = buttonAnimationDuration / 2
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                // nothing to do
            }

            override fun onAnimationEnd(animation: Animation) {
                button.setImageResource(if (currentCall.isVideoEnabled) R.drawable.baseline_camera_alt_black_off_48 else R.drawable.baseline_camera_alt_black_48)
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
                a.duration = buttonAnimationDuration / 2
                button.startAnimation(a)
            }

            override fun onAnimationRepeat(animation: Animation) {
                // nothing to do
            }
        })
        val frontSwitch = findViewById<View>(R.id.frontFacingSwitch)
        if (currentCall.isVideoEnabled) {
            frontSwitch.visibility = View.VISIBLE
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
            scale.duration = buttonAnimationDuration
            frontSwitch.startAnimation(scale)
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
            scale.duration = buttonAnimationDuration
            scale.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    // nothing to do
                }

                override fun onAnimationEnd(animation: Animation) {
                    findViewById<View>(R.id.frontFacingSwitch).visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animation) {
                    // nothing to do
                }
            })
            frontSwitch.startAnimation(scale)
        }
        button.startAnimation(animation)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Camera permission needed in order to start video",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            switchVideoEnabled(findViewById(R.id.videoStreamSwitch))
        }
    }

    private fun startSensor() {
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager!!.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "meshenger:proximity"
        )
        wakeLock?.acquire()
    }

    override fun onPause() {
        super.onPause()
        if (calledWhileScreenOff) {
            calledWhileScreenOff = false
            return
        }
        if (permissionRequested) {
            permissionRequested = false
            return
        }
        //finish();
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(this, "onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver)
        stopRinging()
        if (currentCall.state == CallState.CONNECTED) {
            currentCall.decline()
        }
        currentCall.cleanup()
        binder.getEvents().addEvent(contact!!, callEventType)
        //if (binder != null) {
        unbindService(connection)
        //}
        wakeLock?.release()
        if (currentCall.commSocket != null && currentCall.commSocket!!.isConnected && !currentCall.commSocket!!.isClosed) {
            try {
                currentCall.commSocket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        currentCall.releaseCamera()
    }

    private fun setStatusText(text: String) {
        Handler(mainLooper).post { statusTextView!!.text = text }
    }

    private fun stopDelayed(message: String) {
        Handler(mainLooper).post {
            statusTextView!!.text = message
            Handler(mainLooper).postDelayed({ finish() }, 2000)
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainService.MainBinder
        currentCall = binder.getCurrentCall()!!
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder.shutdown()
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

    private var declineBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this, "declineBroadcastCastReceiver onReceive")
            finish()
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}