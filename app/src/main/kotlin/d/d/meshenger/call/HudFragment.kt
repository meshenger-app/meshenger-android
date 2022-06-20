package d.d.meshenger.call

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import d.d.meshenger.R
import org.webrtc.StatsReport


/**
 * Fragment for HUD statistics display.
 */
class HudFragment: Fragment() {
    private lateinit var encoderStatView: TextView
    private lateinit var hudViewBwe: TextView
    private lateinit var hudViewConnection: TextView
    private lateinit var hudViewVideoSend: TextView
    private lateinit var hudViewVideoRecv: TextView
    private lateinit var toggleDebugButton: ImageButton
    private val videoCallEnabled = true
    private val displayHud = false

    @Volatile
    private var isRunning = false
    //private CpuMonitor cpuMonitor;

    //private CpuMonitor cpuMonitor;
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hud, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create UI controls.
        encoderStatView = view.findViewById(R.id.encoder_stat_call)
        hudViewBwe = view.findViewById(R.id.hud_stat_bwe)
        hudViewConnection = view.findViewById(R.id.hud_stat_connection)
        hudViewVideoSend = view.findViewById(R.id.hud_stat_video_send)
        hudViewVideoRecv = view.findViewById(R.id.hud_stat_video_recv)
        toggleDebugButton = view.findViewById(R.id.button_toggle_debug)
        toggleDebugButton.setOnClickListener {
            if (displayHud) {
                val visibility: Int =
                    if (hudViewBwe.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
                hudViewsSetProperties(visibility)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        /*
    Bundle args = getArguments();
    if (args != null) {
      videoCallEnabled = args.getBoolean("CallActivity.EXTRA_VIDEO_CALL", true);
      displayHud = args.getBoolean("CallActivity.EXTRA_DISPLAY_HUD", false);
    }*/
        val visibility: Int = View.VISIBLE //displayHud ? View.VISIBLE : View.INVISIBLE;
        encoderStatView.visibility = visibility
        toggleDebugButton.visibility = visibility
        hudViewsSetProperties(View.INVISIBLE)
        isRunning = true
    }

    override fun onStop() {
        isRunning = false
        super.onStop()
    }

    /*
  public void setCpuMonitor(CpuMonitor cpuMonitor) {
    this.cpuMonitor = cpuMonitor;
  }
*/
    private fun hudViewsSetProperties(visibility: Int) {
        hudViewBwe.visibility = visibility
        hudViewConnection.visibility = visibility
        hudViewVideoSend.visibility = visibility
        hudViewVideoRecv.visibility = visibility
        hudViewBwe.setTextSize(TypedValue.COMPLEX_UNIT_PT, 5f)
        hudViewConnection.setTextSize(TypedValue.COMPLEX_UNIT_PT, 5f)
        hudViewVideoSend.setTextSize(TypedValue.COMPLEX_UNIT_PT, 5f)
        hudViewVideoRecv.setTextSize(TypedValue.COMPLEX_UNIT_PT, 5f)
    }

    private fun getReportMap(report: StatsReport): Map<String, String> {
        val reportMap: MutableMap<String, String> = HashMap()
        for (value in report.values) {
            reportMap[value.name] = value.value
        }
        return reportMap
    }

    fun updateEncoderStatistics(reports: Array<StatsReport>) {
        if (!isRunning || !displayHud) {
            return
        }
        val encoderStat = StringBuilder(128)
        val bweStat = StringBuilder()
        val connectionStat = StringBuilder()
        val videoSendStat = StringBuilder()
        val videoRecvStat = StringBuilder()
        var fps: String? = null
        var targetBitrate: String? = null
        var actualBitrate: String? = null
        for (report in reports) {
            if (report.type == "ssrc" && report.id.contains("ssrc") && report.id.contains("send")) {
                // Send video statistics.
                val reportMap = getReportMap(report)
                val trackId = reportMap["googTrackId"]
                if (trackId != null && trackId.contains(PeerConnectionClient.VIDEO_TRACK_ID)) {
                    fps = reportMap["googFrameRateSent"].toString()
                    videoSendStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        videoSendStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            } else if (report.type == "ssrc" && report.id.contains("ssrc")
                && report.id.contains("recv")
            ) {
                // Receive video statistics.
                val reportMap = getReportMap(report)
                // Check if this stat is for video track.
                val frameWidth = reportMap["googFrameWidthReceived"]
                if (frameWidth != null) {
                    videoRecvStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        videoRecvStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            } else if (report.id == "bweforvideo") {
                // BWE statistics.
                val reportMap = getReportMap(report)
                targetBitrate = reportMap["googTargetEncBitrate"].toString()
                actualBitrate = reportMap["googActualEncBitrate"].toString()
                bweStat.append(report.id).append("\n")
                for (value in report.values) {
                    val name = value.name.replace("goog", "").replace("Available", "")
                    bweStat.append(name).append("=").append(value.value).append("\n")
                }
            } else if (report.type == "googCandidatePair") {
                // Connection statistics.
                val reportMap = getReportMap(report)
                val activeConnection = reportMap["googActiveConnection"]
                if (activeConnection != null && activeConnection == "true") {
                    connectionStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        connectionStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            }
        }
        hudViewBwe.text = bweStat.toString()
        hudViewConnection.text = connectionStat.toString()
        hudViewVideoSend.text = videoSendStat.toString()
        hudViewVideoRecv.text = videoRecvStat.toString()
        if (videoCallEnabled) {
            fps?.let {
                encoderStat.append("Fps:  ").append(fps).append("\n")
            }
            targetBitrate?.let {
                encoderStat.append("Target BR: ").append(targetBitrate).append("\n")
            }
            actualBitrate?.let {
                encoderStat.append("Actual BR: ").append(actualBitrate).append("\n")
            }
        }
        /*
    if (cpuMonitor != null) {
      encoderStat.append("CPU%: ")
          .append(cpuMonitor.getCpuUsageCurrent())
          .append("/")
          .append(cpuMonitor.getCpuUsageAverage())
          .append(". Freq: ")
          .append(cpuMonitor.getFrequencyScaleAverage());
    }
    */encoderStatView.text = encoderStat.toString()
    }
}