package d.d.meshenger.call

import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import d.d.meshenger.fragment.CallFragment.OnCallEvents
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import java.util.*

/**
 * Control capture format based on a seekbar listener.
 */
class CaptureQualityController(val captureFormatText: TextView, callEvents: OnCallEvents): OnSeekBarChangeListener {

    private val formats = listOf(
        CaptureFormat(1280, 720, 0, 30000), CaptureFormat(960, 540, 0, 30000),
        CaptureFormat(640, 480, 0, 30000), CaptureFormat(480, 360, 0, 30000),
        CaptureFormat(320, 240, 0, 30000), CaptureFormat(256, 144, 0, 30000)
    )

    companion object {
        // Prioritize framerate below this threshold and resolution above the threshold.
        private const val FRAMERATE_THRESHOLD = 15
    }

    private var width = 0
    private var height = 0
    private var framerate = 0
    private var targetBandwidth = 0.0

    private val compareFormats: Comparator<CaptureFormat> =
        Comparator { first, second ->
            val firstFps = calculateFramerate(targetBandwidth, first)
            val secondFps = calculateFramerate(targetBandwidth, second)
            if (((firstFps >= FRAMERATE_THRESHOLD && secondFps >= FRAMERATE_THRESHOLD)
                        || firstFps == secondFps)
            ) {
                // Compare resolution.
                first.width * first.height - second.width * second.height
            } else {
                // Compare fps.
                firstFps - secondFps
            }
        }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (progress == 0) {
            width = 0
            height = 0
            framerate = 0
            captureFormatText.setText("Muted")
            return
        }

        // Extract max bandwidth (in millipixels / second).
        var maxCaptureBandwidth = Long.MIN_VALUE
        for (format: CaptureFormat in formats) {
            maxCaptureBandwidth = Math.max(
                maxCaptureBandwidth,
                format.width.toLong() * format.height * format.framerate.max
            )
        }

        // Fraction between 0 and 1.
        var bandwidthFraction = progress.toDouble() / 100.0
        // Make a log-scale transformation, still between 0 and 1.
        val kExpConstant = 3.0
        bandwidthFraction =
            (Math.exp(kExpConstant * bandwidthFraction) - 1) / (Math.exp(kExpConstant) - 1)
        targetBandwidth = bandwidthFraction * maxCaptureBandwidth

        // Choose the best format given a target bandwidth.
        val bestFormat = Collections.max(formats, compareFormats)
        width = bestFormat.width
        height = bestFormat.height
        framerate = calculateFramerate(targetBandwidth, bestFormat)
        captureFormatText.setText(
            String.format(
                "%1\$dx%2\$d @ %3\$d fps" /*captureFormatText.getContext().getString(R.string.format_description)*/,
                width,
                height,
                framerate
            )
        )
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        //callEvents.onCaptureFormatChange(width, height, framerate);
    }

    // Return the highest frame rate possible based on bandwidth and format.
    private fun calculateFramerate(bandwidth: Double, format: CaptureFormat): Int {
        return Math.round(
            Math.min(
                format.framerate.max,
                Math.round(bandwidth / (format.width * format.height)).toInt()
            )
                    / 1000.0
        ).toInt()
    }


}