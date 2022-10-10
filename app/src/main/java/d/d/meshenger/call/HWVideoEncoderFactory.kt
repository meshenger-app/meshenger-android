package d.d.meshenger

import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.VideoCodecInfo

class HWVideoEncoderFactory(eglContext: EglBase.Context?,
                            enableIntelVp8Encoder: Boolean,
                            enableH264HighProfile: Boolean
) : DefaultVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile) {

    private val priorities: List<String> = listOf("VP8", "VP9", "H264")

    override fun getSupportedCodecs(): Array<VideoCodecInfo?> {

        val supported: MutableList<VideoCodecInfo> = super.getSupportedCodecs().toMutableList()

        val sorted = arrayOfNulls<VideoCodecInfo>(supported.size)
        var i = 0
        for (codec in priorities) {
            var j = 0
            while (j < supported.size && codec != supported[j].name) j++
            if (j < supported.size) {
                sorted[i++] = supported[j]
                supported.removeAt(j)
            }
        }
        while (i < sorted.size && !supported.isEmpty()) {
            val codecInfo = supported[0]
            supported.removeAt(0)
            sorted[i++] = codecInfo
        }
        return sorted
    }
}