package d.d.meshenger.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import org.webrtc.ThreadUtils.ThreadChecker

class AppRTCProximitySensor private constructor(context: Context, val onSensorStateListener: Runnable?): SensorEventListener {

    companion object {
        private const val TAG = "AppRTCProximitySensor"

        /** Construction  */
        fun create(context: Context, sensorStateListener: Runnable?): AppRTCProximitySensor {
            return AppRTCProximitySensor(context, sensorStateListener)
        }
    }

    // This class should be created, started and stopped on one thread
    // (e.g. the main thread). We use |nonThreadSafe| to ensure that this is
    // the case. Only active when |DEBUG| is set to true.
    private val threadChecker = ThreadChecker()

    private val sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var lastStateReportIsNear = false

    init {
        Log.d(TAG, "AppRTCProximitySensor" + AppRTCUtils.getThreadInfo())
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    /**
     * Activate the proximity sensor. Also do initialization if called for the
     * first time.
     */
    fun start(): Boolean {
        threadChecker.checkIsOnValidThread()
        Log.d(TAG, "start" + AppRTCUtils.getThreadInfo())
        if (!initDefaultSensor()) {
            // Proximity sensor is not supported on this device.
            return false
        }
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        return true
    }

    /** Deactivate the proximity sensor.  */
    fun stop() {
        threadChecker.checkIsOnValidThread()
        Log.d(TAG, "stop" + AppRTCUtils.getThreadInfo())
        if (proximitySensor == null) {
            return
        }
        sensorManager.unregisterListener(this, proximitySensor)
    }

    /** Getter for last reported state. Set to true if "near" is reported.  */
    fun sensorReportsNearState(): Boolean {
        threadChecker.checkIsOnValidThread()
        return lastStateReportIsNear
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        threadChecker.checkIsOnValidThread()
        AppRTCUtils.assertIsTrue(sensor.type == Sensor.TYPE_PROXIMITY)
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.e(TAG, "The values returned by this sensor cannot be trusted")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        threadChecker.checkIsOnValidThread()
        AppRTCUtils.assertIsTrue(event.sensor.type == Sensor.TYPE_PROXIMITY)
        // As a best practice; do as little as possible within this method and
        // avoid blocking.
        val distanceInCentimeters = event.values[0]
        lastStateReportIsNear = if (distanceInCentimeters < proximitySensor!!.maximumRange) {
            Log.d(TAG, "Proximity sensor => NEAR state")
            true
        } else {
            Log.d(TAG, "Proximity sensor => FAR state")
            false
        }

        // Report about new state to listening client. Client can then call
        // sensorReportsNearState() to query the current state (NEAR or FAR).
        if (onSensorStateListener != null) {
            onSensorStateListener.run()
        }
        Log.d(
            TAG, "onSensorChanged" + AppRTCUtils.getThreadInfo() + ": "
                    + "accuracy=" + event.accuracy + ", timestamp=" + event.timestamp + ", distance="
                    + event.values[0]
        )
    }

    /**
     * Get default proximity sensor if it exists. Tablet devices (e.g. Nexus 7)
     * does not support this type of sensor and false will be returned in such
     * cases.
     */
    private fun initDefaultSensor(): Boolean {
        if (proximitySensor != null) {
            return true
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) {
            return false
        }
        logProximitySensorInfo()
        return true
    }

    /** Helper method for logging information about the proximity sensor.  */
    private fun logProximitySensorInfo() {
        proximitySensor?.let {

            val info = StringBuilder("Proximity sensor: ")
            info.append("name=").append(it.getName())
            info.append(", vendor: ").append(it.getVendor())
            info.append(", power: ").append(it.getPower())
            info.append(", resolution: ").append(it.getResolution())
            info.append(", max range: ").append(it.getMaximumRange())
            info.append(", min delay: ").append(it.getMinDelay())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                // Added in API level 20.
                info.append(", type: ").append(it.getStringType())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Added in API level 21.
                info.append(", max delay: ").append(it.getMaxDelay())
                info.append(", reporting mode: ").append(it.getReportingMode())
                info.append(", isWakeUpSensor: ").append(it.isWakeUpSensor())
            }
            Log.d(TAG, info.toString())
        }
    }
}