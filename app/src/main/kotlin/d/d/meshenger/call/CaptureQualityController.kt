package d.d.meshenger.call

import android.view.View
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import d.d.meshenger.*
import d.d.meshenger.Utils
import org.webrtc.CameraEnumerationAndroid.CaptureFormat
import org.webrtc.RtpParameters
import java.util.*
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Control capture format based on a seekbar listeners.
 */
class CaptureQualityController(private val callActivity: CallActivity) {
    private val resolutionSlider = callActivity.findViewById<SeekBar>(R.id.captureResolutionSlider)
    private val framerateSlider = callActivity.findViewById<SeekBar>(R.id.captureFramerateSlider)
    private val degradationSpinner = callActivity.findViewById<Spinner>(R.id.degradationSpinner)
    private val formatText = callActivity.findViewById<TextView>(R.id.captureFormatText)

    private val degradationValues =
            degradationSpinner.resources.getStringArray(R.array.videoDegradationModeValues)
    private val resolutionNames = mapOf(
        "160x120" to "QQVGA", "240x160" to "HQVGA", "320x240" to "QVGA", "400x240" to "WQVGA",
        "480x320" to "HVGA", "640x360" to "nHD", "640x480" to "VGA", "768x480" to "WVGA",
        "854x480" to "FWVGA", "800x600" to "SVGA", "960x540" to "qHD", "960x640" to "DVGA",
        "1024x576" to "WSVGA", "1024x600" to "WVSGA", "1280x720" to "HD", "1280x1024" to "SXGA",
        "1920x1080" to "Full HD", "1920x1440" to "Full HD 4:3", "2560x1440" to "QHD", "3840x2160" to "UHD"
    )
    private var cameraName = ""
    private val defaultFormats = listOf(
        CaptureFormat(256, 144, 0, 60000), CaptureFormat(320, 240, 0, 60000),
        CaptureFormat(480, 360, 0, 60000), CaptureFormat(640, 480, 0, 60000),
        CaptureFormat(960, 540, 0, 60000), CaptureFormat(1280, 720, 0, 60000),
        CaptureFormat(1920, 1080, 0, 60000), CaptureFormat(3840, 2160, 0, 60000),
        CaptureFormat(7680, 4320, 0, 60000)
    )
    private var degradationSpinnerValue = ""
    private var degradationSpinnerInitialized = false
    private var resolutionSliderFraction = 0.5
    private var resolutionSliderInitialized = false
    private var framerateSliderFraction = 0.5
    private var framerateSliderInitialized = false
    //private var availableCaptureFormats = listOf<CaptureFormat>()
    //private var availableCaptureFormatsInitialized = false

    // from settings
    private var defaultHeight = 0
    private var defaultWidth = 0
    private var defaultFramerate = 0
    private var defaultDegradation = ""

    init {
        // setup spinner
        val spinnerAdapter = ArrayAdapter.createFromResource(
            degradationSpinner.context,
            R.array.videoDegradationModeLabels,
            R.layout.spinner_item_settings
        )
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)

        degradationSpinner.adapter = spinnerAdapter
        //degradationSpinner.setSelection(degradationValues.indexOf(degradation))
        degradationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (check++ > 0) {
                    degradationSpinnerValue = degradationValues[pos]
                    degradationSpinnerInitialized = true
                    updateView()
                    changeCameraFormat()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // ignore
            }
        }

        resolutionSlider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                resolutionSliderFraction = (progress.toDouble() / 100.0)
                resolutionSliderInitialized = true
                updateView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateView()
                changeCameraFormat()
            }
        })

        framerateSlider.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                framerateSliderFraction = (progress.toDouble() / 100.0)
                framerateSliderInitialized = true
                updateView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                updateView()
                changeCameraFormat()
            }
        })
    }

    fun initFromSettings(settings: Settings) {
        Log.d(this, "initFromSettings() "
            + "videoDegradationMode=${settings.videoDegradationMode}, "
            + "cameraFramerate=${settings.cameraFramerate}, "
            + "cameraResolution=${settings.cameraResolution}")

        defaultDegradation = settings.videoDegradationMode

        // parse framerate setting
        if (settings.cameraFramerate == "auto") {
            defaultFramerate = RTCCall.DEFAULT_FRAMERATE
        } else try {
            defaultFramerate = settings.cameraFramerate.toInt()
        } catch (e: Exception) {
            Log.e(this, "applySettings() unhandled cameraFramerate=${settings.cameraFramerate}")
            defaultFramerate = RTCCall.DEFAULT_FRAMERATE
        }

        // parse resolution setting
        if (settings.cameraResolution == "auto") {
            defaultWidth = RTCCall.DEFAULT_WIDTH
            defaultHeight = RTCCall.DEFAULT_HEIGHT
        } else try {
            val parts = settings.cameraResolution.split("x")
            defaultWidth = parts[0].toInt()
            defaultHeight = parts[1].toInt()
        } catch (e: Exception) {
            Log.e(this, "applySettings() unhandled cameraResolution=${settings.cameraResolution}")
            defaultWidth = RTCCall.DEFAULT_WIDTH
            defaultHeight = RTCCall.DEFAULT_HEIGHT
        }

        updateView()
    }

    private fun changeCameraFormat() {
        Log.d(this, "changeCameraFormat()")

        val degradation = getSelectedDegradation()
        val format =  getSelectedFormat()
        val framerate = getSelectedFramerate()

        callActivity
            .getCurrentCall()
            .changeCaptureFormat(degradation, format.width, format.height, framerate)
    }

    private val compareFormats = Comparator<CaptureFormat> { first, second ->
        val firstPixels = first.width * first.height
        val secondPixels = second.width * second.height
        if (firstPixels != secondPixels) {
            secondPixels - firstPixels
        } else {
            second.framerate.max - first.framerate.max
        }
    }

    private fun updateView() {
        Log.d(this, "updateView()")

        val degradation = getSelectedDegradation()
        degradationSpinner.setSelection(degradationValues.indexOf(degradation))

        when (degradation) {
            "maintain_resolution" -> {
                resolutionSlider.visibility = View.VISIBLE
                framerateSlider.visibility = View.GONE
                formatText.visibility = View.VISIBLE
            }
            "maintain_framerate" -> {
                resolutionSlider.visibility = View.GONE
                framerateSlider.visibility = View.VISIBLE
                formatText.visibility = View.VISIBLE
            }
            "balanced" -> {
                resolutionSlider.visibility = View.GONE
                framerateSlider.visibility = View.GONE
                formatText.visibility = View.GONE
            }
            "disabled" -> {
                resolutionSlider.visibility = View.VISIBLE
                framerateSlider.visibility = View.VISIBLE
                formatText.visibility = View.VISIBLE
            }
            else -> {
                Log.w(this, "updateView() unhandled degradation=$degradation")
                return
            }
        }

        var label = "" // "<name> <resolution>@<framerate>"
        if (formatText.visibility == View.VISIBLE) {
            val format =  getSelectedFormat()
            val framerate = getSelectedFramerate()

            if (resolutionSlider.visibility == View.VISIBLE) {
                val resolution = "${format.width}x${format.height}"
                if (resolution in resolutionNames) {
                    label += "${resolutionNames[resolution]} "
                }
                label += resolution
            }

            if (framerateSlider.visibility == View.VISIBLE) {
                if (label.isNotEmpty()) {
                    label += " @ "
                }
                label += "$framerate fps"
            }

            if (cameraName.isNotEmpty()) {
                if (label.isNotEmpty()) {
                    label += " / "
                }
                label += cameraName
            }
        }

        formatText.text = label
    }

    fun getSelectedDegradation(): String {
        if (degradationSpinnerInitialized) {
            Log.d(this, "getSelectedDegradation: from slider: $degradationSpinnerValue")
            return degradationSpinnerValue
        } else {
            // default
            Log.d(this, "getSelectedDegradation: by default: $defaultDegradation")
            return defaultDegradation
        }
    }

    fun getSelectedFormat(): CaptureFormat {
        if (resolutionSliderInitialized) {
            val index = (resolutionSliderFraction * (defaultFormats.size - 1)).toInt()
            return defaultFormats[index]
        } else {
            // default
            return CaptureFormat(defaultWidth, defaultHeight, defaultFramerate, defaultFramerate)
        }
    }

    fun getSelectedFramerate(): Int {
        if (framerateSliderInitialized) {
            val format = getSelectedFormat()
            val min = format.framerate.min
            val max = format.framerate.max
            return (min + framerateSliderFraction * (max - min)).toInt() / 1000
        } else {
            // default
            return defaultFramerate
        }
    }

    fun onCameraChange(newCameraName: String, isFrontFacing: Boolean, newFormats: List<CaptureFormat>) {
        Log.d(this, "onCameraChange() newCameraName=$newCameraName")

        degradationSpinnerInitialized = false
        resolutionSliderInitialized = false
        framerateSliderInitialized = false
        //availableCaptureFormatsInitialized = false

        // newCameraName is rather bad
        cameraName = if (isFrontFacing) {
            "Front Camera"
        } else {
            "Back Camera"
        }
/*
        if (newFormats.isNotEmpty()) {
            availableCaptureFormatsInitialized = true
            availableCaptureFormats = newFormats
        } else {
            availableCaptureFormats = defaultFormats
        }
*/

        // slide of sorted resolutions
        // availableCaptureFormats.sortedWith(compareFormats)

        updateView()
    }
}
