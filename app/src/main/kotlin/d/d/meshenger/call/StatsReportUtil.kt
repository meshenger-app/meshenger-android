/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger.call

import d.d.meshenger.Log
import org.webrtc.RTCStatsReport
import java.math.BigInteger

class StatsReportUtil {
    private var lastBytesReceivedVideo = BigInteger.ZERO
    private var lastBytesSentVideo = BigInteger.ZERO
    private var lastBytesReceivedAudio = BigInteger.ZERO
    private var lastBytesSentAudio = BigInteger.ZERO

    fun getStatsReport(report: RTCStatsReport?): String {
        if (report == null) {
            return ""
        }

        var audioInFound = false
        var videoInFound = false
        var audioOutFound = false
        var videoOutFound = false

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

        val statsMap = report.statsMap

        /*
        // for debugging
        var str = "statsMap:\n"
        for (key in statsMap.keys) {
            val stats = statsMap[key]!!
            str += "stats key: $key, type: ${stats.type}, members:\n"
            for (member in stats.members) {
                val typeName = member.value::class.java.typeName
                str += "  ${member.key}: ${member.value} (${typeName})\n"
            }
        }
        Log.d(this, str)
        */

        for (stats in statsMap.values) {
            if (stats.type == "inbound-rtp") {
                val members = stats.members
                val kind = members["kind"]

                if (kind == "video") {
                    if (videoInFound) {
                        Log.w(this, "Already found inbound video track")
                        continue
                    }

                    val codecId = members["codecId"] as String?
                    if (codecId != null) {
                        val vmap = statsMap[codecId]!!
                        videoInCodec = (vmap.members["mimeType"] as String?) ?: videoInCodec
                    }

                    videoInWidth = (members["frameWidth"] as Long?) ?: 0L
                    videoInHeight = (members["frameHeight"] as Long?) ?: 0L
                    videoInFrameRate = ((members["framesPerSecond"] as Double?) ?: 0.0).toLong()

                    val bytes = (members["bytesReceived"] as BigInteger)
                    videoInBytesDelta = (bytes.toLong() - lastBytesReceivedVideo.toLong()) * 8L / STATS_INTERVAL_MS
                    lastBytesReceivedVideo = bytes

                    videoInFound = true
                }

                if (kind == "audio") {
                    if (audioInFound) {
                        Log.w(this, "Already found inbound audio track")
                        continue
                    }

                    val codecId = members["codecId"] as String?
                    if (codecId != null) {
                        val vmap = statsMap[codecId]!!
                        audioInCodec = (vmap.members["mimeType"] as String?) ?: audioInCodec
                    }

                    val bytes = members["bytesReceived"] as BigInteger
                    audioInBytesDelta = (bytes.toLong() - lastBytesReceivedAudio.toLong()) * 8 / STATS_INTERVAL_MS
                    lastBytesReceivedAudio = bytes
                    audioInFound = true
                }
            } else if (stats.type == "outbound-rtp") {
                val members = stats.members
                val kind = members["kind"]

                if (kind == "video") {
                    if (videoOutFound) {
                        Log.w(this, "Already found outbound video track")
                        continue
                    }

                    val codecId = members["codecId"] as String?
                    if (codecId != null) {
                        val vmap = statsMap[codecId]!!
                        videoOutCodec = (vmap.members["mimeType"] as String?) ?: videoOutCodec
                    }

                    videoOutWidth = (members["frameWidth"] as Long?) ?: 0L
                    videoOutHeight = (members["frameHeight"] as Long?) ?: 0L
                    videoOutFrameRate = ((members["framesPerSecond"] as Double?) ?: 0.0).toLong()

                    val bytes = (members["bytesSent"] as BigInteger)
                    videoOutBytesDelta = (bytes.toLong() - lastBytesSentVideo.toLong()) * 8L / STATS_INTERVAL_MS
                    lastBytesSentVideo = bytes

                    videoOutFound = true
                }

                if (kind == "audio") {
                    if (audioOutFound) {
                        Log.w(this, "Already found outbound audio track")
                        continue
                    }

                    val codecId = members["codecId"] as String?
                    if (codecId != null) {
                        val vmap = statsMap[codecId]!!
                        audioOutCodec = (vmap.members["mimeType"] as String?) ?: audioOutCodec
                    }

                    val bytes = members["bytesSent"] as BigInteger
                    audioOutBytesDelta = (bytes.toLong() - lastBytesSentAudio.toLong())  * 8 / STATS_INTERVAL_MS
                    lastBytesSentAudio = bytes
                    audioOutFound = true
                }
            }
        }

        if (!audioInFound) {
            lastBytesReceivedAudio = BigInteger.ZERO
        }

        if (!audioOutFound) {
            lastBytesSentAudio = BigInteger.ZERO
        }

        if (!videoInFound) {
            lastBytesReceivedVideo = BigInteger.ZERO
        }

        if (!videoOutFound) {
            lastBytesSentVideo = BigInteger.ZERO
        }

        return " Receiving\n" +
               "  Video Codec: $videoInCodec\n" +
               "   Quality: ${videoInWidth}x$videoInHeight @ $videoInFrameRate fps\n" +
               "   Bitrate: $videoInBytesDelta kbps\n" +
               "  Audio Codec: $audioInCodec\n" +
               "   Bitrate: $audioInBytesDelta kbps\n" +
               " Sending\n" +
               "  Video Codec: $videoOutCodec\n" +
               "   Quality: ${videoOutWidth}x$videoOutHeight @ $videoOutFrameRate fps\n" +
               "   Bitrate: $videoOutBytesDelta kbps\n" +
               "  Audio Codec: $audioOutCodec\n" +
               "   Bitrate: $audioOutBytesDelta kbps\n"
    }

    companion object {
        const val STATS_INTERVAL_MS = 1000L
    }
}