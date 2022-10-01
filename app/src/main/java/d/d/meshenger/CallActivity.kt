package d.d.meshenger

import android.content.*
import d.d.meshenger.Utils.hasCameraPermission
import d.d.meshenger.Utils.requestCameraPermission
import android.hardware.SensorEventListener
import android.widget.TextView
import d.d.meshenger.MainService.MainBinder
import android.os.PowerManager.WakeLock
import android.media.Ringtone
import android.view.WindowManager
import android.widget.ImageButton
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.view.animation.ScaleAnimation
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.widget.Toast
import d.d.meshenger.RTCCall.OnStateChangeListener
import d.d.meshenger.RTCCall.CallState
import android.hardware.SensorEvent
import android.os.*
import android.view.View
import android.view.animation.Animation
import java.io.IOException
import java.lang.Exception
import java.lang.NullPointerException

class CallActivity : MeshengerActivity(), ServiceConnection, SensorEventListener {
    private var binder: MainBinder? = null
    private lateinit var statusTextView: TextView
    private lateinit var nameTextView: TextView
    private var connection: ServiceConnection? = null
    private var currentCall: RTCCall? = null
    private var calledWhileScreenOff = false
    private var powerManager: PowerManager? = null
    private var wakeLock: WakeLock? = null
    private var passiveWakeLock: WakeLock? = null
    private val buttonAnimationDuration: Long = 400
    private val CAMERA_PERMISSION_REQUEST_CODE = 2
    private var permissionRequested = false
    private var contact: Contact? = null
    private var eventType = Event.Type.UNKNOWN
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        // keep screen on during call (prevents pausing the app and cancellation of the call)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusTextView = findViewById(R.id.callStatus)
        nameTextView = findViewById(R.id.callName)
        val action = intent.action
        contact = intent.extras!!["EXTRA_CONTACT"] as Contact?
        Log.d(this, "onCreate: $action")
        if ("ACTION_OUTGOING_CALL" == action) {
            eventType = Event.Type.OUTGOING_UNKNOWN
            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    binder = iBinder as MainBinder
                    currentCall = contact?.let {
                        RTCCall.startCall(
                            this@CallActivity,
                            binder!!,
                            it,
                            activeCallback //findViewById(R.id.localRenderer)
                        )
                    }
                    currentCall?.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    // nothing to do
                }
            }
            if (contact!!.name.isEmpty()) {
                nameTextView.setText(getString(R.string.unknown_caller))
            } else {
                nameTextView.setText(contact!!.name)
            }
            bindService(Intent(this, MainService::class.java), connection!!, 0)
            val declineListener = View.OnClickListener { _: View? ->
                // end call
                currentCall!!.hangUp()
                eventType = Event.Type.OUTGOING_DECLINED
                finish()
            }
            findViewById<View>(R.id.callDecline).setOnClickListener(declineListener)
            startSensor()
        } else if ("ACTION_INCOMING_CALL" == action) {
            eventType = Event.Type.INCOMING_UNKNOWN
            calledWhileScreenOff = !(getSystemService(POWER_SERVICE) as PowerManager).isScreenOn
            passiveWakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK,
                "meshenger:wakeup"
            )
            passiveWakeLock?.acquire(10000)
            connection = this
            bindService(Intent(this, MainService::class.java), this, 0)
            if (contact!!.name.isEmpty()) {
                nameTextView.setText(getString(R.string.unknown_caller))
            } else {
                nameTextView.setText(contact!!.name)
            }
            findViewById<View>(R.id.callAccept).visibility = View.VISIBLE
            startRinging()

            // decline call
            val declineListener = View.OnClickListener { _: View? ->
                stopRinging()
                Log.d(this, "declining call...")
                currentCall!!.decline()
                if (passiveWakeLock != null && passiveWakeLock!!.isHeld) {
                    passiveWakeLock!!.release()
                }
                eventType = Event.Type.INCOMING_DECLINED
                startActivity(Intent(this, MainActivity::class.java))
                finishAffinity()
            }

            // hangup call
            val hangupListener = View.OnClickListener { _: View? ->
                stopRinging() // make sure ringing has stopped ;-)
                Log.d(this, "hangup call...")
                currentCall!!.decline()
                if (passiveWakeLock != null && passiveWakeLock!!.isHeld) {
                    passiveWakeLock!!.release()
                }
                eventType = Event.Type.INCOMING_ACCEPTED
                finish()
            }
            val acceptListener = View.OnClickListener { _: View? ->
                stopRinging()
                Log.d(this, "accepted call...")
                try {
                    currentCall!!.setRemoteRenderer(findViewById(R.id.remoteRenderer))
                    //currentCall.setLocalRenderer(findViewById(R.id.localRenderer));
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
                    eventType = Event.Type.INCOMING_ERROR
                }
            }
            findViewById<View>(R.id.callAccept).setOnClickListener(acceptListener)
            findViewById<View>(R.id.callDecline).setOnClickListener(declineListener)
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

    var declineBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this, "declineBroadcastCastReceiver onReceive")
            finish()
        }
    }

    private fun startRinging() {
        Log.d(this, "startRinging")
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
            this, RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext, RingtoneManager.TYPE_RINGTONE
            )
        )
        ringtone?.play()
    }

    private fun stopRinging() {
        Log.d(this, "stopRinging")
        if (vibrator != null) {
            vibrator!!.cancel()
            vibrator = null
        }
        if (ringtone != null) {
            ringtone!!.stop()
            ringtone = null
        }
    }

    private fun switchVideoEnabled(button: ImageButton) {
        if (!hasCameraPermission(this)) {
            requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE)
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
                button.setImageResource(if (currentCall!!.isVideoEnabled) R.drawable.baseline_camera_alt_black_off_48 else R.drawable.baseline_camera_alt_black_48)
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
        finish()
    }

    override fun onDestroy() {
        try {
            Log.d(this, "onDestroy")
            LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver)
            stopRinging()

            if (currentCall!!.state == CallState.CONNECTED) {
                currentCall!!.decline()
            }

            currentCall!!.cleanup()

            val contact = this.contact
            if (contact != null) {
                binder?.addEvent(contact, eventType)
            } else {
                Log.w(this, "call without contact")
            }

            //if (binder != null) {
            unbindService(connection!!)
            //}
            if (wakeLock != null) {
                wakeLock!!.release()
            }
            if (currentCall != null && currentCall!!.commSocket != null && currentCall!!.commSocket!!.isConnected && !currentCall!!.commSocket!!.isClosed) {
                try {
                    currentCall!!.commSocket?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            currentCall!!.releaseCamera()
        } catch (e: NullPointerException){

        }
        super.onDestroy()
    }

    private val activeCallback = object : OnStateChangeListener {
        override fun OnStateChange(state: CallState?) {
            when (state) {
                CallState.CONNECTING -> {
                    Log.d(this, "activeCallback: CONNECTING")
                    setStatusText(getString(R.string.call_connecting))
                }
                CallState.CONNECTED -> {
                    Log.d(this, "activeCallback: CONNECTED")
                    Handler(mainLooper).post {
                        findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
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
                else -> {
                    Log.d(this, "activeCallback: unknown")
                    stopDelayed(getString(R.string.call_error))
                }
            }
        }
    }
    private val passiveCallback = object : OnStateChangeListener {
        override fun OnStateChange(state: CallState?) {
            when (state) {
                CallState.CONNECTED -> {
                    Log.d(this, "passiveCallback: CONNECTED")
                    setStatusText(getString(R.string.call_connected))
                    runOnUiThread { findViewById<View>(R.id.callAccept).visibility = View.GONE }
                    Handler(mainLooper).post {
                        findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
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
                CallState.CONNECTING -> {
                    Log.d(this, "passiveCallback: CONNECTING")
                    setStatusText(getString(R.string.call_connecting))
                }
                CallState.DISMISSED -> {
                    Log.d(this, "passiveCallback: DISMISSED")
                    setStatusText(getString(R.string.call_dismissed))
                }
                else -> {
                    Log.d(this, "passiveCallback: unknown")
                    stopDelayed(getString(R.string.call_error))
                }
            }
        }
    }

    private fun setStatusText(text: String) {
        Handler(mainLooper).post { statusTextView.text = text }
    }

    private fun stopDelayed(message: String) {
        Handler(mainLooper).post {
            statusTextView.text = message
            Handler().postDelayed({ finish() }, 2000)
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        currentCall = binder!!.getCurrentCall()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
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
}