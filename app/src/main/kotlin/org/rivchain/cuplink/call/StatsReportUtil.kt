package org.rivchain.cuplink.call

import org.webrtc.RTCStatsReport
import java.math.BigInteger

/**
 * @Author Vadym Vikulin
 * @Date 2021/10/10
 * @desc
 */
class StatsReportUtil {
    private var lastBytesReceivedVideo = BigInteger.ZERO
    private var lastBytesSentVideo = BigInteger.ZERO
    private var lastBytesReceivedAudio = BigInteger.ZERO
    private var lastBytesSentAudio = BigInteger.ZERO
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
        var widthIn: Long = 0
        var heightIn: Long = 0
        var widthOut: Long = 0
        var heightOut: Long = 0
        var frameRateIn: Long = 0
        var frameRateOut: Long = 0
        for (stats in report.statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val members = stats.members
                if (members["kind"] == "video") {
                    codecIdVideo = members["codecId"] as String?
                    val bytes = members["bytesReceived"] as BigInteger?
                    receivedBytesSRVideo = bytes!!.toLong() - lastBytesReceivedVideo.toLong()
                    lastBytesReceivedVideo = bytes
                    frameRateIn = ((members["framesPerSecond"] as Double?) ?: 0.0).toLong()
                    widthIn = if (members["frameWidth"] == null) 0 else members["frameWidth"] as Long
                    heightIn = if (members["frameHeight"] == null) 0 else members["frameHeight"] as Long
                }
                if (members["kind"] == "audio") {
                    codecIdAudio = members["codecId"] as String?
                    val bytes = members["bytesReceived"] as BigInteger?
                    receivedBytesSRAudio = bytes!!.toLong() - lastBytesReceivedAudio.toLong()
                    lastBytesReceivedAudio = bytes
                }
            }
            if (stats.type == "outbound-rtp") {
                val members = stats.members
                if (members["kind"] == "video") {
                    val bytes = members["bytesSent"] as BigInteger?
                    sentBytesSRVideo = bytes!!.toLong() - lastBytesSentVideo.toLong()
                    lastBytesSentVideo = bytes
                    frameRateOut = ((members["framesPerSecond"] as Double?) ?: 0.0).toLong()
                    widthOut = if (members["frameWidth"] == null) 0 else members["frameWidth"] as Long
                    heightOut = if (members["frameHeight"] == null) 0 else members["frameHeight"] as Long
                }
                if (members["kind"] == "audio") {
                    val bytes = members["bytesSent"] as BigInteger?
                    sentBytesSRAudio = bytes!!.toLong() - lastBytesSentAudio.toLong()
                    lastBytesSentAudio = bytes
                }
            }
        }
        if (codecIdVideo != null) {
            codecVideo = report.statsMap[codecIdVideo]!!.members["mimeType"] as String?
        }
        if (codecIdAudio != null) {
            codecAudio = report.statsMap[codecIdAudio]!!.members["mimeType"] as String?
        }
        return " Codecs\n" +
               "  $codecVideo\n" +
               "  $codecAudio\n" +
               " Quality ↓ ${widthIn}x$heightIn@$frameRateIn\n" +
               " Quality ↑ ${widthOut}x$heightOut@$frameRateOut\n" +
               " Bitrate ⎚ ↓ ${receivedBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate ⎚ ↑ ${sentBytesSRVideo * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate 🔊 ↓ ${receivedBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps\n" +
               " Bitrate 🔊 ↑ ${sentBytesSRAudio * 8 / STATS_INTERVAL_MS}kbps"
    }

    companion object {
        const val STATS_INTERVAL_MS = 5000L
    }
}