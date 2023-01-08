/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package d.d.meshenger.call
//package org.appspot.apprtc;
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import d.d.meshenger.Log
import d.d.meshenger.Utils
import org.webrtc.ThreadUtils
import java.lang.StringBuilder

/**
 * RTCProximitySensor manages functions related to the proximity sensor.
 * On most device, the proximity sensor is implemented as a boolean-sensor.
 * It returns just two values "NEAR" or "FAR". Thresholding is done on the LUX
 * value i.e. the LUX value of the light sensor is compared with a threshold.
 * A LUX-value more than the threshold means the proximity sensor returns "FAR".
 * Anything less than the threshold value and the sensor returns "NEAR".
 */
class RTCProximitySensor(context: Context) : SensorEventListener {
    // This class should be created, started and stopped on one thread
    // (e.g. the main thread). We use `nonThreadSafe` to ensure that this is
    // the case. Only active when `DEBUG` is set to true.
    private val threadChecker: ThreadUtils.ThreadChecker = ThreadUtils.ThreadChecker()
    private var onSensorStateListeners = mutableSetOf<(Boolean) -> Unit>()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var proximitySensor: Sensor? = null
    private var lastStateReportIsNear = false

    init {
        Log.d(this, "RTCProximitySensor ${Utils.getThreadInfo()}")
    }

    fun addListener(listener: (Boolean) -> Unit) {
        onSensorStateListeners.add(listener)
    }

    /**
     * Activate the proximity sensor. Also do initialization if called for the
     * first time.
     */
    fun start(): Boolean {
        threadChecker.checkIsOnValidThread()
        Log.d(this, "start ${Utils.getThreadInfo()}")
        if (!initDefaultSensor()) {
            Log.w(this, "Proximity sensor is not supported on this device.")
            return false
        } else {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            return true
        }
    }

    /** Deactivate the proximity sensor.  */
    fun stop() {
        threadChecker.checkIsOnValidThread()
        Log.d(this, "stop ${Utils.getThreadInfo()}")
        if (proximitySensor != null) {
            sensorManager.unregisterListener(this, proximitySensor)
        }
    }

    /** Getter for last reported state. Set to true if "near" is reported.  */
    fun sensorReportsNearState(): Boolean {
        threadChecker.checkIsOnValidThread()
        return lastStateReportIsNear
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        threadChecker.checkIsOnValidThread()
        Utils.assertIsTrue(sensor.type == Sensor.TYPE_PROXIMITY)
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.e(this, "The values returned by this sensor cannot be trusted")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        threadChecker.checkIsOnValidThread()
        Utils.assertIsTrue(event.sensor.type == Sensor.TYPE_PROXIMITY)
        // As a best practice; do as little as possible within this method and
        // avoid blocking.
        val distanceInCentimeters: Float = event.values[0]
        lastStateReportIsNear = if (distanceInCentimeters < proximitySensor!!.maximumRange) {
            Log.d(this, "Proximity sensor => NEAR state")
            true
        } else {
            Log.d(this, "Proximity sensor => FAR state")
            false
        }

        Log.d(this, "onSensorChanged ${Utils.getThreadInfo()}: "
            + "accuracy=${event.accuracy}, "
            + "timestamp=${event.timestamp}, "
            + "distance=${event.values[0]}"
        )

        // Report about new state to listening clients.
        for (listener in onSensorStateListeners) {
            listener(lastStateReportIsNear)
        }
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
        val sensor = proximitySensor
        if (sensor == null) {
            return
        }
        val info = StringBuilder("Proximity sensor: ")
        info.append("name=").append(sensor.name)
        info.append(", vendor: ").append(sensor.vendor)
        info.append(", power: ").append(sensor.power)
        info.append(", resolution: ").append(sensor.resolution)
        info.append(", max range: ").append(sensor.maximumRange)
        info.append(", min delay: ").append(sensor.minDelay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // Added in API level 20.
            info.append(", type: ").append(sensor.stringType)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Added in API level 21.
            info.append(", max delay: ").append(sensor.maxDelay)
            info.append(", reporting mode: ").append(sensor.reportingMode)
            info.append(", isWakeUpSensor: ").append(sensor.isWakeUpSensor)
        }
        Log.d(this, info.toString())
    }
}
