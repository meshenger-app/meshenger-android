// Original from WebRTC example: org.appspot.apprtc

/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package d.d.meshenger.call

import android.widget.TextView
import android.widget.SeekBar.OnSeekBarChangeListener
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import android.widget.SeekBar
import d.d.meshenger.CallActivity
import d.d.meshenger.Log
import d.d.meshenger.R
import java.util.*
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Control capture format based on a seekbar listener.
 */
class CaptureQualityController(
    private val captureFormatText: TextView, private val callEvents: CallActivity
) : OnSeekBarChangeListener {
    private val formats = listOf(
        CaptureFormat(3840, 2160, 0, 30000), CaptureFormat(1920, 1080, 0, 30000), 
        CaptureFormat(1280, 720, 0, 30000), CaptureFormat(960, 540, 0, 30000),
        CaptureFormat(640, 480, 0, 30000), CaptureFormat(480, 360, 0, 30000),
        CaptureFormat(320, 240, 0, 30000), CaptureFormat(256, 144, 0, 30000)
    )

    private val resolutionNames = mapOf(
        3840 to "4K", 1920 to "Full HD", 1280 to "HD", 640 to "VGA", 320 to "QVGA"
    )

    private var width = 0
    private var height = 0
    private var framerate = 0
    private var targetBandwidth = 0.0
    private val compareFormats = Comparator<CaptureFormat> { first, second ->
        val firstFps = calculateFramerate(targetBandwidth, first)
        val secondFps = calculateFramerate(targetBandwidth, second)
        if (firstFps >= FRAMERATE_THRESHOLD && secondFps >= FRAMERATE_THRESHOLD || firstFps == secondFps) {
            // Compare resolution.
            first.width * first.height - second.width * second.height
        } else {
            // Compare fps.
            firstFps - secondFps
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (progress == 0) {
            width = 0
            height = 0
            framerate = 0
            captureFormatText.setText(R.string.capture_format_muted)
            return
        }

        // Extract max bandwidth (in millipixels / second).
        var maxCaptureBandwidth = Long.MIN_VALUE
        for (format in formats) {
            maxCaptureBandwidth =
                maxCaptureBandwidth.coerceAtLeast(format.width.toLong() * format.height * format.framerate.max)
        }

        // Fraction between 0 and 1.
        var bandwidthFraction = progress.toDouble() / 100.0
        // Make a log-scale transformation, still between 0 and 1.
        val kExpConstant = 3.0
        bandwidthFraction = (exp(kExpConstant * bandwidthFraction) - 1) / (exp(kExpConstant) - 1)
        targetBandwidth = bandwidthFraction * maxCaptureBandwidth

        // Choose the best format given a target bandwidth.
        val bestFormat = Collections.max(formats, compareFormats)
        width = bestFormat.width
        height = bestFormat.height
        framerate = calculateFramerate(targetBandwidth, bestFormat)
        captureFormatText.text = String.format(
            captureFormatText.context.getString(R.string.capture_format_description), width, height, framerate
        )

        // Prepend resolution name
        resolutionNames[width]?.let {
            captureFormatText.text = "$it " + (captureFormatText.text as String)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {
        callEvents.changeCaptureFormat(width, height, framerate)
    }

    // Return the highest frame rate possible based on bandwidth and format.
    private fun calculateFramerate(bandwidth: Double, format: CaptureFormat): Int {
        return (format.framerate.max.coerceAtMost(
            (bandwidth / (format.width * format.height)).roundToInt()
        ) / 1000.0).roundToInt()
    }

    companion object {
        // Prioritize framerate below this threshold and resolution above the threshold.
        private const val FRAMERATE_THRESHOLD = 15
    }
}