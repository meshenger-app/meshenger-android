package d.d.meshenger.util;

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
    private BigInteger lastBytesReceived = BigInteger.valueOf(0);
    private BigInteger lastBytesSent = BigInteger.valueOf(0);
    private Long lastFrameDecoded = Long.valueOf(0);

    public String getStatsReport(RTCStatsReport report) {
        if (report == null) return "";
        String codecId = null;
        String codec = "";
        long bytesSR = 0;
        long sentBytesSR = 0;
        long width = 0, height = 0;
        long frameRate = 0;
        for (RTCStats stats : report.getStatsMap().values()) {
            if (stats.getType().equals("inbound-rtp")) {
                Map<String, Object> members = stats.getMembers();
                if (members.get("mediaType").equals("video")) {
                    codecId = (String) members.get("codecId");
                    BigInteger bytes = (BigInteger) members.get("bytesReceived");
                    bytesSR = bytes.longValue() - lastBytesReceived.longValue();
                    lastBytesReceived = bytes;

                    long currentFrame = (long) members.get("framesDecoded");
                    long lastFrame = lastFrameDecoded;
                    frameRate = (currentFrame - lastFrame) * 1000
                            / STATS_INTERVAL_MS;
                    lastFrameDecoded = currentFrame;
                }
            }
            if (stats.getType().equals("outbound-rtp")) {
                Map<String, Object> members = stats.getMembers();
                if (members.get("mediaType").equals("video")) {
                    //codecId = (String) members.get("codecId");
                    BigInteger bytes = (BigInteger) members.get("bytesSent");
                    sentBytesSR = bytes.longValue() - lastBytesSent.longValue();
                    lastBytesSent = bytes;
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
        if (codecId != null) {
            codec = (String) report.getStatsMap().get(codecId).getMembers().get("mimeType");
        }

        final String statsReport = "Codec: " + codec
                + "\nResolution: " + width + "x" + height
                + "\nBitrate ↓: " + bytesSR * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nBitrate ↑: " + sentBytesSR * 8 / STATS_INTERVAL_MS + "kbps"
                + "\nFrameRate: " + frameRate;
        return statsReport;
    }
}