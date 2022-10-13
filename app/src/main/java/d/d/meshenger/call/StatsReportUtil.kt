package d.d.meshenger.call

import org.webrtc.RTCStatsReport
import java.math.BigInteger

class StatsReportUtil {
    private var lastBytesReceivedVideo = BigInteger.valueOf(0)
    private var lastBytesSentVideo = BigInteger.valueOf(0)
    private var lastBytesReceivedAudio = BigInteger.valueOf(0)
    private var lastBytesSentAudio = BigInteger.valueOf(0)
    private var lastFrameDecoded = java.lang.Long.valueOf(0)

    fun getStatsReport(report: RTCStatsReport?): String {
        if (report == null) {
            return ""
        }

        var codecIdVideo: String? = null
        var codecIdAudio: String? = null
        var codecVideo: String? = ""
        var codecAudio: String? = ""
        var receivedBytesSRVideo = 0L
        var sentBytesSRVideo = 0L
        var receivedBytesSRAudio = 0L
        var sentBytesSRAudio = 0L
        var width = 0L
        var height = 0L
        var frameRate = 0L

        for (stats in report.statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val members = stats.members

                if (members["mediaType"] == "video") {
                    codecIdVideo = members["codecId"] as String?
                    val bytes = members["bytesReceived"] as BigInteger?
                    receivedBytesSRVideo = bytes!!.toLong() - lastBytesReceivedVideo.toLong()
                    lastBytesReceivedVideo = bytes
                    val currentFrame = members["framesDecoded"] as Long?
                    val lastFrame = lastFrameDecoded
                    frameRate = ((currentFrame!! - lastFrame) * 1000L / STATS_INTERVAL_MS)
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
                    width = (members["frameWidth"] as Long?) ?: 0L
                    height = (members["frameHeight"] as Long?) ?: 0L
                }
            }
        }

        if (codecIdVideo != null) {
            codecVideo = report.statsMap[codecIdVideo]!!.members["mimeType"] as String?
        }
        if (codecIdAudio != null) {
            codecAudio = report.statsMap[codecIdAudio]!!.members["mimeType"] as String?
        }

        return " Codecs: $codecVideo $codecAudio\n" +
               " Resolution: ${width}x$height\n" +
               " Bitrate Audio ↓: ${receivedBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate Audio ↑: ${sentBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate Video ↓: ${receivedBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate Video ↑: ${sentBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Frames Rate: $frameRate\n"
    }

    companion object {
        const val STATS_INTERVAL_MS = 1000L
    }
}