package d.d.meshenger.call

import org.webrtc.RTCStatsReport
import java.math.BigInteger

class StatsReportUtil {
    private var lastBytesReceivedVideo = BigInteger.valueOf(0)
    private var lastBytesSentVideo = BigInteger.valueOf(0)
    private var lastBytesReceivedAudio = BigInteger.valueOf(0)
    private var lastBytesSentAudio = BigInteger.valueOf(0)
    private var lastFrameDecodedOut = 0L
    private var lastFrameDecodedIn = 0L

    fun getStatsReport(report: RTCStatsReport?): String {
        if (report == null) {
            return ""
        }

        var audioInCodec = "unknown"
        var videoInCodec = "unknown"
        var audioOutCodec = "unknown"
        var videoOutCodec = "unknown"

        var audioInBytesDelta = 0L
        var audioOutBytesDelta = 0L
        var videoInBytesDelta = 0L
        var videoOutBytesDelta = 0L

        var videoInWidth = 0L
        var videoInHeight = 0L
        var videoInFrameRate = 0L

        var videoOutWidth = 0L
        var videoOutHeight = 0L
        var videoOutFrameRate = 0L

        for (stats in report.statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val members = stats.members
                val mediaType = members["mediaType"]

                if (mediaType == "video") {
                    val codecId = members["codecId"] as String?
                    val trackId = members["trackId"] as String?

                    if (codecId != null) {
                        val vmap = report.statsMap[codecId]!!
                        videoInCodec = (vmap.members["mimeType"] as String?) ?: videoInCodec
                    }

                    if (trackId != null) {
                        val vmap = report.statsMap[trackId]!!
                        videoInWidth = (vmap.members["frameWidth"] as Long?) ?: 0L
                        videoInHeight = (vmap.members["frameHeight"] as Long?) ?: 0L
                    }

                    val bytes = members["bytesReceived"] as BigInteger?
                    videoInBytesDelta = (bytes!!.toLong() - lastBytesReceivedVideo.toLong()) * 8 / STATS_INTERVAL_MS
                    lastBytesReceivedVideo = bytes

                    val framesDecoded = members["framesDecoded"] as Long?
                    val lastFrame = lastFrameDecodedIn
                    videoInFrameRate = ((framesDecoded!! - lastFrame) * 1000L / STATS_INTERVAL_MS)
                    lastFrameDecodedIn = framesDecoded
                }

                if (mediaType == "audio") {
                    val codecId = members["codecId"] as String?
                    if (codecId != null) {
                        val vmap = report.statsMap[codecId]!!
                        audioInCodec = (vmap.members["mimeType"] as String?) ?: audioInCodec
                    }

                    val bytes = members["bytesReceived"] as BigInteger?
                    audioInBytesDelta = (bytes!!.toLong() - lastBytesReceivedAudio.toLong()) * 8 / STATS_INTERVAL_MS
                    lastBytesReceivedAudio = bytes
                }
            } else if (stats.type == "outbound-rtp") {
                val map = stats.members
                val mediaType = map["mediaType"]

                if (mediaType == "video") {
                    val trackId = map["trackId"] as String?
                    val codecId = map["codecId"] as String?

                    if (trackId != null) {
                        val vmap = report.statsMap[trackId]!!
                        videoOutWidth = (vmap.members["frameWidth"] as Long?) ?: 0L
                        videoOutHeight = (vmap.members["frameHeight"] as Long?) ?: 0L
                    }

                    if (codecId != null) {
                        val vmap = report.statsMap[codecId]!!
                        videoOutCodec = (vmap.members["mimeType"] as String?) ?: videoOutCodec
                    }

                    val bytes = map["bytesSent"] as BigInteger?
                    videoOutBytesDelta = (bytes!!.toLong() - lastBytesSentVideo.toLong()) * 8 / STATS_INTERVAL_MS
                    lastBytesSentVideo = bytes

                    val framesEncoded = map["framesEncoded"] as Long?
                    val lastFrame = lastFrameDecodedOut
                    videoOutFrameRate = ((framesEncoded!! - lastFrame) * 1000L / STATS_INTERVAL_MS)
                    lastFrameDecodedOut = framesEncoded
                }

                if (mediaType == "audio") {
                    val codecId = map["codecId"] as String?
                    if (codecId != null) {
                        val vmap = report.statsMap[codecId]!!
                        audioOutCodec = (vmap.members["mimeType"] as String?) ?: audioOutCodec
                    }

                    val bytes = map["bytesSent"] as BigInteger?
                    audioOutBytesDelta = (bytes!!.toLong() - lastBytesSentAudio.toLong())  * 8 / STATS_INTERVAL_MS
                    lastBytesSentAudio = bytes
                }
            }
        }

        return " Receiving\n" +
               "  Video Codec: $videoInCodec\n" +
               "   Resolution: ${videoInWidth}x$videoInHeight\n" +
               "   FramesRate: $videoInFrameRate\n" +
               "   Bitrate: ${videoInBytesDelta}kbps\n" +
               "  Audio Codec: $audioInCodec\n" +
               "   Bitrate: ${audioInBytesDelta}kbps\n" +
               " Sending\n" +
               "  Video Codec: $videoOutCodec\n" +
               "   Resolution: ${videoOutWidth}x$videoOutHeight\n" +
               "   FramesRate: $videoOutFrameRate\n" +
               "   Bitrate: ${videoOutBytesDelta}kbps\n" +
               "  Audio Codec: $audioOutCodec\n" +
               "   Bitrate: ${audioOutBytesDelta}kbps\n"
    }

    companion object {
        const val STATS_INTERVAL_MS = 1000L
    }
}