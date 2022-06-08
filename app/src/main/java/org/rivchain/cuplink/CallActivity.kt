package org.rivchain.cuplink

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
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.RTCCall.CallState
import org.rivchain.cuplink.RTCCall.OnStateChangeListener
import org.rivchain.cuplink.util.StatsReportUtil
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import java.io.IOException

class CallActivity : CupLinkActivity(), ServiceConnection, SensorEventListener {
    private val buttonAnimationDuration: Long = 400
    private val CAMERA_PERMISSION_REQUEST_CODE = 2
    var declineBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            log("declineBroadcastCastReceiver onReceive")
            finish()
        }
    }
    private var statusTextView: TextView? = null
    private var callStats: TextView? = null
    private lateinit var nameTextView: TextView
    private var binder: MainBinder? = null
    private lateinit var connection: ServiceConnection
    private lateinit var currentCall: RTCCall
    private var calledWhileScreenOff = false
    private var powerManager: PowerManager? = null
    private lateinit var wakeLock: WakeLock
    private lateinit var passiveWakeLock: WakeLock
    private var permissionRequested = false
    private var contact: Contact? = null
    private var callEventType: CallEvent.Type? = null
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
                log("activeCallback: CONNECTING")
                setStatusText(getString(R.string.call_connecting))
            }
            CallState.CONNECTED -> {
                log("activeCallback: CONNECTED")
                val devMode = binder!!.settings.developmentMode
                Handler(mainLooper).post {
                    if (devMode) {
                        currentCall!!.accept(statsCollector)
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
                log("activeCallback: DISMISSED")
                stopDelayed(getString(R.string.call_denied))
            }
            CallState.RINGING -> {
                log("activeCallback: RINGING")
                setStatusText(getString(R.string.call_ringing))
            }
            CallState.ENDED -> {
                log("activeCallback: ENDED")
                stopDelayed(getString(R.string.call_ended))
            }
            CallState.ERROR -> {
                log("activeCallback: ERROR")
                stopDelayed(getString(R.string.call_error))
            }
        }
    }
    private val passiveCallback = OnStateChangeListener { callState: CallState? ->
        when (callState) {
            CallState.CONNECTED -> {
                log("passiveCallback: CONNECTED")
                setStatusText(getString(R.string.call_connected))
                val devMode = binder!!.settings.developmentMode
                runOnUiThread { findViewById<View>(R.id.callAccept).visibility = View.GONE }
                Handler(mainLooper).post {
                    findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
                    findViewById<View>(R.id.speakerMode).visibility = View.VISIBLE
                    if (devMode) {
                        currentCall!!.accept(statsCollector)
                        callStats!!.visibility = View.VISIBLE
                    } else {
                        callStats!!.visibility = View.GONE
                    }
                }
            }
            CallState.RINGING -> {
                log("passiveCallback: RINGING")
                setStatusText(getString(R.string.call_ringing))
            }
            CallState.ENDED -> {
                log("passiveCallback: ENDED")
                stopDelayed(getString(R.string.call_ended))
            }
            CallState.ERROR -> {
                log("passiveCallback: ERROR")
                stopDelayed(getString(R.string.call_error))
            }
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
        log("onCreate: $action")
        if ("ACTION_OUTGOING_CALL" == action) {
            callEventType = CallEvent.Type.OUTGOING_UNKNOWN
            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    binder = iBinder as MainBinder
                    currentCall = RTCCall.startCall(
                        this@CallActivity,
                        binder!!,
                        contact!!,
                        activeCallback
                    )
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                    currentCall.setLocalRenderer(findViewById(R.id.localRenderer))
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    // nothing to do
                }
            }
            if (contact!!.name.isEmpty()) {
                nameTextView.setText(resources.getString(R.string.unknown_caller))
            } else {
                nameTextView.setText(contact!!.name)
            }
            bindService(Intent(this, MainService::class.java), connection, 0)
            val declineListener = View.OnClickListener { view: View? ->
                // end call
                currentCall!!.hangUp()
                callEventType = CallEvent.Type.OUTGOING_DECLINED
                finish()
            }
            findViewById<View>(R.id.callDecline).setOnClickListener(declineListener)
            startSensor()
        } else if ("ACTION_INCOMING_CALL" == action) {
            callEventType = CallEvent.Type.INCOMING_UNKNOWN
            calledWhileScreenOff = !(getSystemService(POWER_SERVICE) as PowerManager).isScreenOn
            passiveWakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.PARTIAL_WAKE_LOCK,
                "cuplink:wakeup"
            )
            passiveWakeLock.acquire(10000)
            connection = this
            bindService(Intent(this, MainService::class.java), this, 0)
            if (contact!!.name.isEmpty()) {
                nameTextView.setText(resources.getString(R.string.unknown_caller))
            } else {
                nameTextView.setText(contact!!.name)
            }
            findViewById<View>(R.id.callAccept).visibility = View.VISIBLE
            startRinging()

            // decline call
            val declineListener = View.OnClickListener { view: View? ->
                stopRinging()
                log("declining call...")
                currentCall!!.decline()
                if (passiveWakeLock != null && passiveWakeLock!!.isHeld) {
                    passiveWakeLock!!.release()
                }
                callEventType = CallEvent.Type.INCOMING_DECLINED
                finish()
            }

            // hangup call
            val hangupListener = View.OnClickListener { view: View? ->
                stopRinging() // make sure ringing has stopped ;-)
                log("hangup call...")
                currentCall!!.decline()
                if (passiveWakeLock != null && passiveWakeLock!!.isHeld) {
                    passiveWakeLock!!.release()
                }
                callEventType = CallEvent.Type.INCOMING_ACCEPTED
                finish()
            }
            val acceptListener = View.OnClickListener { view: View? ->
                stopRinging()
                log("accepted call...")
                try {
                    currentCall!!.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                    currentCall!!.setLocalRenderer(findViewById(R.id.localRenderer))
                    currentCall!!.accept(passiveCallback)
                    if (passiveWakeLock != null && passiveWakeLock!!.isHeld) {
                        passiveWakeLock!!.release()
                    }
                    findViewById<View>(R.id.callDecline).setOnClickListener(hangupListener)
                    startSensor()
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopDelayed("Error accepting call")
                    findViewById<View>(R.id.callAccept).visibility = View.GONE
                    callEventType = CallEvent.Type.INCOMING_ERROR
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
            switchVideoEnabled(
                button as ImageButton
            )
        }
        findViewById<View>(R.id.frontFacingSwitch).setOnClickListener { button: View? -> currentCall!!.switchFrontFacing() }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(declineBroadcastReceiver, IntentFilter("call_declined"))
    }

    private fun startRinging() {
        log("startRinging")
        val ringerMode = (getSystemService(AUDIO_SERVICE) as AudioManager).ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(1500, 800, 800, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibe = VibrationEffect.createWaveform(pattern, 0)
            vibrator!!.vibrate(vibe)
        } else {
            vibrator!!.vibrate(pattern, 0)
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
        log("stopRinging")
        if (vibrator != null) {
            vibrator!!.cancel()
            vibrator = null
        }
        ringtone?.stop()
    }

    private fun chooseVoiceMode(button: ImageButton) {
        val audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
        currentCall!!.isSpeakerEnabled = !currentCall!!.isSpeakerEnabled
        if (currentCall!!.isSpeakerEnabled) {
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
        currentCall!!.isVideoEnabled = !currentCall!!.isVideoEnabled
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
                button.setImageResource(if (currentCall!!.isVideoEnabled) R.drawable.baseline_camera_alt_off_48 else R.drawable.baseline_camera_alt_48)
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
        if (currentCall!!.isVideoEnabled) {
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
        wakeLock.acquire()
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
        log("onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver)
        stopRinging()
        if (currentCall.state == CallState.CONNECTED) {
            currentCall.decline()
        }
        currentCall.cleanup()
        if(contact!=null) {
            binder!!.addCallEvent(contact!!, callEventType)
        }
        //if (binder != null) {
        unbindService(connection)
        //}
        wakeLock.release()
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
            Handler().postDelayed({ finish() }, 2000)
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        currentCall = binder!!.getCurrentCall()!!
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        log("sensor changed: " + sensorEvent.values[0])
        if (sensorEvent.values[0] == 0.0f) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "meshenger:tag"
                )
            wakeLock.acquire()
        } else {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "meshenger:tag"
                )
            wakeLock.acquire()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {
        // nothing to do
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}