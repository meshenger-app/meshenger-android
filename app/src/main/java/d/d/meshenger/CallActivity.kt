package d.d.meshenger

import android.content.*
import d.d.meshenger.Utils.hasCameraPermission
import d.d.meshenger.Utils.requestCameraPermission
import d.d.meshenger.Log.d
import d.d.meshenger.MeshengerActivity
import android.hardware.SensorEventListener
import android.widget.TextView
import d.d.meshenger.MainService.MainBinder
import d.d.meshenger.RTCCall
import android.os.PowerManager.WakeLock
import d.d.meshenger.Contact
import android.media.Ringtone
import d.d.meshenger.R
import android.view.WindowManager
import d.d.meshenger.CallEvent
import org.webrtc.SurfaceViewRenderer
import d.d.meshenger.MainService
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
import android.net.wifi.WifiManager
import android.os.*
import android.text.format.Formatter
import android.view.View
import android.view.animation.Animation
import java.io.IOException
import java.lang.Exception

class CallActivity : MeshengerActivity(), ServiceConnection, SensorEventListener {
    private var statusTextView: TextView? = null
    private var nameTextView: TextView? = null
    private var binder: MainBinder? = null
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
    private var callEventType: CallEvent.Type? = null
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
        log("onCreate: $action")
        if ("ACTION_OUTGOING_CALL" == action) {
            callEventType = CallEvent.Type.OUTGOING_UNKNOWN
            connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                    binder = iBinder as MainBinder
                    val addresses_s = ArrayList<String>()


                    val addr = binder!!.settings.addresses

                    if (addr.isEmpty()) {
                        val wifiManager =
                            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        if (wifiManager != null) {
                            val ipAddress: String =
                                Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                            if (ipAddress != null && !ipAddress.equals("0.0.0.0")) {
                                addresses_s.add(ipAddress)
                                binder!!.settings?.addresses = addresses_s
                                binder!!.saveDatabase()
                            }
                        }
                    }
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
            if (contact!!.getName().isEmpty()) {
                nameTextView?.setText(resources.getString(R.string.unknown_caller))
            } else {
                nameTextView?.setText(contact!!.getName())
            }
            bindService(Intent(this, MainService::class.java), connection!!, 0)
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
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK,
                "meshenger:wakeup"
            )
            passiveWakeLock?.acquire(10000)
            connection = this
            bindService(Intent(this, MainService::class.java), this, 0)
            if (contact!!.getName().isEmpty()) {
                nameTextView?.setText(resources.getString(R.string.unknown_caller))
            } else {
                nameTextView?.setText(contact!!.getName())
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
                    callEventType = CallEvent.Type.INCOMING_ERROR
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
            log("declineBroadcastCastReceiver onReceive")
            finish()
        }
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
            this, RingtoneManager.getActualDefaultRingtoneUri(
                applicationContext, RingtoneManager.TYPE_RINGTONE
            )
        )
        ringtone?.play()
    }

    private fun stopRinging() {
        log("stopRinging")
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
        super.onDestroy()
        log("onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver)
        stopRinging()
        if (currentCall!!.state == CallState.CONNECTED) {
            currentCall!!.decline()
        }
        currentCall!!.cleanup()
        binder!!.addCallEvent(contact!!, callEventType)

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
    }

    private val activeCallback = object : OnStateChangeListener {
        override fun OnStateChange(state: CallState?) {
            when (state) {
                CallState.CONNECTING -> {
                    log("activeCallback: CONNECTING")
                    setStatusText(getString(R.string.call_connecting))
                }
                CallState.CONNECTED -> {
                    log("activeCallback: CONNECTED")
                    Handler(mainLooper).post {
                        findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
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
    }
    private val passiveCallback = object : OnStateChangeListener {
        override fun OnStateChange(state: CallState?) {
            when (state) {
                CallState.CONNECTED -> {
                    log("passiveCallback: CONNECTED")
                    setStatusText(getString(R.string.call_connected))
                    runOnUiThread { findViewById<View>(R.id.callAccept).visibility = View.GONE }
                    Handler(mainLooper).post {
                        findViewById<View>(R.id.videoStreamSwitchLayout).visibility = View.VISIBLE
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
        currentCall = binder!!.getCurrentCall()
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

    private fun log(s: String) {
        d(this, s)
    }
}