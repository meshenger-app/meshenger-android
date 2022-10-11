package d.d.meshenger.call

import org.webrtc.RTCStatsReport
import java.math.BigInteger

/**
 * @Author Vadym Vikulin
 * @Date 2021/10/10
 * @desc
 */
class StatsReportUtil {
    private var lastBytesReceivedVideo = BigInteger.valueOf(0)
    private var lastBytesSentVideo = BigInteger.valueOf(0)
    private var lastBytesReceivedAudio = BigInteger.valueOf(0)
    private var lastBytesSentAudio = BigInteger.valueOf(0)
    private var lastFrameDecoded = java.lang.Long.valueOf(0)
    fun getStatsReport(report: RTCStatsReport?): String {
        if (report == null) return ""
        var codecIdVideo: String? = null
        var codecIdAudio: String? = null
        var codecVideo: String? = ""
        var codecAudio: String? = ""
        var receivedBytesSRVideo: Long = 0
        var sentBytesSRVideo: Long = 0
        var receivedBytesSRAudio: Long = 0
        var sentBytesSRAudio: Long = 0
        var width: Long = 0
        var height: Long = 0
        var frameRate: Long = 0
        for (stats in report.statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val members = stats.members
                if (members["mediaType"] == "video") {
                    codecIdVideo = members["codecId"] as String?
                    val bytes = members["bytesReceived"] as BigInteger?
                    receivedBytesSRVideo = bytes!!.toLong() - lastBytesReceivedVideo.toLong()
                    lastBytesReceivedVideo = bytes
                    val currentFrame = members["framesDecoded"] as Long
                    val lastFrame = lastFrameDecoded
                    frameRate = ((currentFrame - lastFrame) * 1000
                            / STATS_INTERVAL_MS)
                    lastFrameDecoded = currentFrame
                }
                if (members["mediaType"] == "audio") {
                    codecIdAudio = members["codecId"] as String?
                    val bytes = members["bytesReceived"] as BigInteger?
                    receivedBytesSRAudio = bytes!!.toLong() - lastBytesReceivedAudio.toLong()
                    lastBytesReceivedAudio = bytes
                }
            }
            if (stats.type == "outbound-rtp") {
                val members = stats.members
                if (members["mediaType"] == "video") {
                    val bytes = members["bytesSent"] as BigInteger?
                    sentBytesSRVideo = bytes!!.toLong() - lastBytesSentVideo.toLong()
                    lastBytesSentVideo = bytes
                }
                if (members["mediaType"] == "audio") {
                    val bytes = members["bytesSent"] as BigInteger?
                    sentBytesSRAudio = bytes!!.toLong() - lastBytesSentAudio.toLong()
                    lastBytesSentAudio = bytes
                }
            }
            if (stats.type == "track") {
                val members = stats.members
                if (members["kind"] == "video") {
                    width = if (members["frameWidth"] == null) 0 else members["frameWidth"] as Long
                    height =
                        if (members["frameHeight"] == null) 0 else members["frameHeight"] as Long
                }
            }
        }
        if (codecIdVideo != null) {
            codecVideo = report.statsMap[codecIdVideo]!!.members["mimeType"] as String?
        }
        if (codecIdAudio != null) {
            codecAudio = report.statsMap[codecIdAudio]!!.members["mimeType"] as String?
        }
        return """Codecs: $codecVideo $codecAudio
                Resolution: ${width}x$height
                Bitrate ⎚ ↓: ${receivedBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps
                Bitrate ⎚ ↑: ${sentBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps
                Bitrate 🔊 ↓: ${receivedBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps
                Bitrate 🔊 ↑: ${sentBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps
                FrameRate: $frameRate"""
    }

    companion object {
        private const val STATS_INTERVAL_MS = 5000
    }
}