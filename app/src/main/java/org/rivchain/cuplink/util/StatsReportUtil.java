package org.rivchain.cuplink.util;

import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;

import java.math.BigInteger;
import java.util.Map;

/**
 * @Author Vadym Vikulin
 * @Date 2021/10/10
 * @desc
 */
public class StatsReportUtil {

    private final static int STATS_INTERVAL_MS = 5000;
    private BigInteger lastBytesReceivedVideo = BigInteger.valueOf(0);
    private BigInteger lastBytesSentVideo = BigInteger.valueOf(0);
    private BigInteger lastBytesReceivedAudio = BigInteger.valueOf(0);
    private BigInteger lastBytesSentAudio = BigInteger.valueOf(0);
    private Long lastFrameDecoded = Long.valueOf(0);

    public String getStatsReport(RTCStatsReport report) {
        if (report == null) return "";
        String codecIdVideo = null;
        String codecIdAudio = null;
        String codecVideo = "";
        String codecAudio = "";
        long receivedBytesSRVideo = 0;
        long sentBytesSRVideo = 0;
        long receivedBytesSRAudio = 0;
        long sentBytesSRAudio = 0;
        long width = 0, height = 0;
        long frameRate = 0;
        for (RTCStats stats : report.getStatsMap().values()) {
            if (stats.getType().equals("inbound-rtp")) {
                Map<String, Object> members = stats.getMembers();
                if (members.get("mediaType").equals("video")) {
                    codecIdVideo = (String) members.get("codecId");
                    BigInteger bytes = (BigInteger) members.get("bytesReceived");
                    receivedBytesSRVideo = bytes.longValue() - lastBytesReceivedVideo.longValue();
                    lastBytesReceivedVideo = bytes;
                    long currentFrame = (long) members.get("framesDecoded");
                    long lastFrame = lastFrameDecoded;
                    frameRate = (currentFrame - lastFrame) * 1000
                            / STATS_INTERVAL_MS;
                    lastFrameDecoded = currentFrame;
                }
                if (members.get("mediaType").equals("audio")) {
                    codecIdAudio = (String) members.get("codecId");
                    BigInteger bytes = (BigInteger) members.get("bytesReceived");
                    receivedBytesSRAudio = bytes.longValue() - lastBytesReceivedAudio.longValue();
                    lastBytesReceivedAudio = bytes;
                }
            }
            if (stats.getType().equals("outbound-rtp")) {
                Map<String, Object> members = stats.getMembers();
                if (members.get("mediaType").equals("video")) {
                    BigInteger bytes = (BigInteger) members.get("bytesSent");
                    sentBytesSRVideo = bytes.longValue() - lastBytesSentVideo.longValue();
                    lastBytesSentVideo = bytes;
                }
                if (members.get("mediaType").equals("audio")) {
                    BigInteger bytes = (BigInteger) members.get("bytesSent");
                    sentBytesSRAudio = bytes.longValue() - lastBytesSentAudio.longValue();
                    lastBytesSentAudio = bytes;
                }
            }
            if (stats.getType().equals("track")) {
                Map<String, Object> members = stats.getMembers();
                if (members.get("kind").equals("video")) {
                    width = members.get("frameWidth") == null ? 0 : (long) members.get(
                            "frameWidth");
                    height = members.get("frameHeight") == null ? 0 : (long) members.get(
                            "frameHeight");
                }
            }
        }
        if (codecIdVideo != null) {
            codecVideo = (String) report.getStatsMap().get(codecIdVideo).getMembers().get("mimeType");
        }
        if (codecIdAudio != null) {
            codecAudio = (String) report.getStatsMap().get(codecIdAudio).getMembers().get("mimeType");
        }

        final String statsReport = "Codecs: " + codecVideo + " " + codecAudio
                + "\nResolution: " + width + "x" + height
                + "\nBitrate ⎚ ↓: " + receivedBytesSRVideo * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nBitrate ⎚ ↑: " + sentBytesSRVideo * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nBitrate \uD83D\uDD0A ↓: " + receivedBytesSRAudio * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nBitrate \uD83D\uDD0A ↑: " + sentBytesSRAudio * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nFrameRate: " + frameRate;
        return statsReport;
    }
}