package org.webrtc

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import org.webrtc.VideoEncoder.BitrateAllocation
import java.util.*

/**
 * 自定义硬解视频编码工厂
 * 用于解决WebRTC支持H264编码；
 * [CustomHardwareVideoEncoderFactory.isHardwareSupportedInCurrentSdkH264]
 * 目前源码中仅支持部分大厂机型，导致即使我们的手机硬件支持，也可能导致无法使用H264
 * 用于解决sdp中无H264信息
 *
 * @author ShenBen
 * @date 2021/08/28 18:28
 * @email 714081644@qq.com
 */
// API 16 requires the use of deprecated methods.
class CustomHardwareVideoEncoderFactory(
    sharedContext: EglBase.Context?, enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean, codecAllowedPredicate: Predicate<MediaCodecInfo>?,
    videoEncoderSupportedCallback: VideoEncoderSupportedCallback?
) : VideoEncoderFactory { //DefaultVideoDecoderFactory(sharedContext){
    private var sharedContext: EglBase14.Context? = null
    private val enableIntelVp8Encoder: Boolean
    private val enableH264HighProfile: Boolean
    private val codecAllowedPredicate: Predicate<MediaCodecInfo>?
    private val videoEncoderSupportedCallback: VideoEncoderSupportedCallback?


interface VideoEncoderSupportedCallback {
    fun isSupportedVp8(info: MediaCodecInfo): Boolean
    fun isSupportedVp9(info: MediaCodecInfo): Boolean
    fun isSupportedH264(info: MediaCodecInfo): Boolean
}

    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext                 The textures generated will be accessible from this context. May be null,
     * this disables texture support.
     * @param enableIntelVp8Encoder         true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile         true if H264 High Profile enabled.
     * @param videoEncoderSupportedCallback
     */
    constructor(
        sharedContext: EglBase.Context?,
        enableIntelVp8Encoder: Boolean,
        enableH264HighProfile: Boolean,
        videoEncoderSupportedCallback: VideoEncoderSupportedCallback?
    ) : this(
        sharedContext, enableIntelVp8Encoder, enableH264HighProfile,  /* codecAllowedPredicate= */
        null, videoEncoderSupportedCallback
    ) {
    }

    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext                 The textures generated will be accessible from this context. May be null,
     * this disables texture support.
     * @param enableIntelVp8Encoder         true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile         true if H264 High Profile enabled.
     * @param codecAllowedPredicate         optional predicate to filter codecs. All codecs are allowed
     * when predicate is not provided.
     * @param videoEncoderSupportedCallback
     */
    init {
        // Texture mode requires EglBase14.
        if (sharedContext is EglBase14.Context) {
            this.sharedContext = sharedContext
        } else {
            Logging.w(TAG, "No shared EglBase.Context.  Encoders will not use texture mode.")
            this.sharedContext = null
        }
        this.enableIntelVp8Encoder = enableIntelVp8Encoder
        this.enableH264HighProfile = enableH264HighProfile
        this.codecAllowedPredicate = codecAllowedPredicate
        this.videoEncoderSupportedCallback = videoEncoderSupportedCallback
    }

    @Deprecated("")
    constructor(enableIntelVp8Encoder: Boolean, enableH264HighProfile: Boolean) : this(
        null,
        enableIntelVp8Encoder,
        enableH264HighProfile,
        null
    ) {
    }

    override fun createEncoder(input: VideoCodecInfo): VideoEncoder? {
        // HW encoding is not supported below Android Kitkat.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null
        }
        val type = VideoCodecMimeType.valueOf(input.name)
        val info = findCodecForType(type) ?: return null
        val codecName = info.name
        val mime = type.mimeType()
        val surfaceColorFormat = MediaCodecUtils.selectColorFormat(
            MediaCodecUtils.TEXTURE_COLOR_FORMATS, info.getCapabilitiesForType(mime)
        )
        val yuvColorFormat = MediaCodecUtils.selectColorFormat(
            MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(mime)
        )
        if (type == VideoCodecMimeType.H264) {
            val isHighProfile = H264Utils.isSameH264Profile(
                input.params, MediaCodecUtils.getCodecProperties(type,  /* highProfile= */true)
            )
            val isBaselineProfile = H264Utils.isSameH264Profile(
                input.params, MediaCodecUtils.getCodecProperties(type,  /* highProfile= */false)
            )
            if (!isHighProfile && !isBaselineProfile) {
                return null
            }
            if (isHighProfile && !isH264HighProfileSupported(info)) {
                return null
            }
        }
        val videoEncoder: VideoEncoder = HardwareVideoEncoder(
            MediaCodecWrapperFactoryImpl(), codecName, type,
            surfaceColorFormat, yuvColorFormat, input.params, getKeyFrameIntervalSec(type),
            getForcedKeyFrameIntervalMs(type, codecName), createBitrateAdjuster(type, codecName),
            sharedContext
        )


//        videoEncoder.initEncode(settings, new VideoEncoder.Callback() {
//            @Override
//            public void onEncodedFrame(EncodedImage encodedImage, VideoEncoder.CodecSpecificInfo codecSpecificInfo) {
//
//            }
//        });
//       videoEncoder.initEncode(settings, new VideoEncoder.Callback() {
//           @Override
//           public void onEncodedFrame(EncodedImage encodedImage, VideoEncoder.CodecSpecificInfo codecSpecificInfo) {
//
//           }
//       })
        // 创建BitrateAllocation对象并设置比特率分配
        //  VideoEncoder.BitrateAllocation bitrateAllocation = new VideoEncoder.BitrateAllocation();
        val myBitrateArray =
            arrayOf(intArrayOf(100, 200, 300), intArrayOf(400, 500, 600)) // Example 2D array
        val bitrateAllocation = BitrateAllocation(myBitrateArray)

        //  VideoEncoder.ResolutionBitrateLimits bitrateLimits = new VideoEncoder.ResolutionBitrateLimits();
        //   videoEncoder.setBitrateLimits()
// 设置编码层的比特率分配
//        int spatialLayer = 0; // 编码层索引
//        int temporalLayer = 0; // 时序层索引
//        int bitrateBps = 1000000; // 比特率（bps）
//        bitrateAllocation.bitratesBbs = bitrateBps;
//
        //  videoEncoder.setRateAllocation(bitrateAllocation,25);

        // videoEncoder.getScalingSettings().on = true
        Log.i(
            "test ",
            "  videoEncoder--- " + videoEncoder.scalingSettings.toString() + " " + videoEncoder.isHardwareEncoder + " "
        )
        //  videoEncoder.setRates()
        return videoEncoder
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        // HW encoding is not supported below Android Kitkat.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return arrayOf()
        }
        val supportedCodecInfos: MutableList<VideoCodecInfo> = ArrayList()
        // Generate a list of supported codecs in order of preference:
        // VP8, VP9, H264 (high profile), H264 (baseline profile).
        for (type in arrayOf<VideoCodecMimeType>(VideoCodecMimeType.H264)) {
            val codec = findCodecForType(type)
            if (codec != null) {
                val name = type.name
                // TODO(sakal): Always add H264 HP once WebRTC correctly removes codecs that are not
                // supported by the decoder.
                if (type == VideoCodecMimeType.H264 && isH264HighProfileSupported(codec)) {
                    supportedCodecInfos.add(
                        VideoCodecInfo(
                            name, MediaCodecUtils.getCodecProperties(type,  /* highProfile= */true)
                        )
                    )
                }
                supportedCodecInfos.add(
                    VideoCodecInfo(
                        name, MediaCodecUtils.getCodecProperties(type,  /* highProfile= */false)
                    )
                )
            }
        }
        return supportedCodecInfos.toTypedArray()
    }

    private fun findCodecForType(type: VideoCodecMimeType): MediaCodecInfo? {
        val codecCount = MediaCodecList.getCodecCount()
        for (i in 0 until codecCount) {
            var info: MediaCodecInfo? = null
            try {
                info = MediaCodecList.getCodecInfoAt(i)
            } catch (e: IllegalArgumentException) {
                Logging.e(TAG, "Cannot retrieve encoder codec info", e)
            }
            if (info == null || !info.isEncoder) {
                continue
            }
            if (isSupportedCodec(info, type)) {
                return info
            }
        }
        // No support for this type.
        return null
    }

    // Returns true if the given MediaCodecInfo indicates a supported encoder for the given type.
    private fun isSupportedCodec(info: MediaCodecInfo, type: VideoCodecMimeType): Boolean {
        if (!MediaCodecUtils.codecSupportsType(info, type)) {
            return false
        }
        // Check for a supported color format.
        return if (MediaCodecUtils.selectColorFormat(
                MediaCodecUtils.ENCODER_COLOR_FORMATS, info.getCapabilitiesForType(type.mimeType())
            )
            == null
        ) {
            false
        } else isHardwareSupportedInCurrentSdk(
            info,
            type
        ) && isMediaCodecAllowed(info)
    }

    // Returns true if the given MediaCodecInfo indicates a hardware module that is supported on the
    // current SDK.
    private fun isHardwareSupportedInCurrentSdk(
        info: MediaCodecInfo,
        type: VideoCodecMimeType
    ): Boolean {
        when (type) {
            VideoCodecMimeType.VP8 -> return isHardwareSupportedInCurrentSdkVp8(info)
            VideoCodecMimeType.VP9 -> return isHardwareSupportedInCurrentSdkVp9(info)
            VideoCodecMimeType.H264 -> return isHardwareSupportedInCurrentSdkH264(info)
            else -> {}
        }
        return false
    }

    private fun isHardwareSupportedInCurrentSdkVp8(info: MediaCodecInfo): Boolean {
        val name = info.name
        // QCOM Vp8 encoder is supported in KITKAT or later.
        val isSupported =
            (name.startsWith(MediaCodecUtils.QCOM_PREFIX) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) || name.startsWith(
                MediaCodecUtils.EXYNOS_PREFIX
            ) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) || name.startsWith(MediaCodecUtils.INTEL_PREFIX) && (Build.VERSION.SDK_INT) >= Build.VERSION_CODES.LOLLIPOP) && enableIntelVp8Encoder
        return if (isSupported) {
            true
        } else {
            //自行判断是否支持VP8
            videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedVp8(
                info
            )
        }
    }

    private fun isHardwareSupportedInCurrentSdkVp9(info: MediaCodecInfo): Boolean {
        val name = info.name
        val isSupported =
            ((name.startsWith(MediaCodecUtils.QCOM_PREFIX) || name.startsWith(MediaCodecUtils.EXYNOS_PREFIX)) // Both QCOM and Exynos VP9 encoders are supported in N or later.
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        return if (isSupported) {
            true
        } else {
            //自行判断是否支持VP9
            videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedVp9(
                info
            )
        }
    }

    private fun isHardwareSupportedInCurrentSdkH264(info: MediaCodecInfo): Boolean {
        // First, H264 hardware might perform poorly on this model.
        if (H264_HW_EXCEPTION_MODELS.contains(Build.MODEL)) {
            return false
        }
        val name = info.name
        // QCOM H264 encoder is supported in KITKAT or later.
        val isSupported =
            ((name.startsWith(MediaCodecUtils.QCOM_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT || name.startsWith(
                MediaCodecUtils.EXYNOS_PREFIX
            )) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || name.startsWith(
                GOOGLE_PREFIX
            )) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        return if (isSupported) {
            true
        } else {
            //自行判断是否支持H264
            videoEncoderSupportedCallback != null && videoEncoderSupportedCallback.isSupportedH264(
                info
            )
        }
    }

    private fun isMediaCodecAllowed(info: MediaCodecInfo): Boolean {
        return codecAllowedPredicate?.test(info) ?: true
    }

    private fun getKeyFrameIntervalSec(type: VideoCodecMimeType): Int {
        when (type) {
            VideoCodecMimeType.VP8, VideoCodecMimeType.VP9 -> return 100
            VideoCodecMimeType.H264 -> return 20
            else -> {}
        }
        throw IllegalArgumentException("Unsupported VideoCodecMimeType $type")
    }

    private fun getForcedKeyFrameIntervalMs(type: VideoCodecMimeType, codecName: String): Int {
        if (type == VideoCodecMimeType.VP8 && codecName.startsWith(MediaCodecUtils.QCOM_PREFIX)) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP
                || Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1
            ) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS
            } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS
            }
        }
        // Other codecs don't need key frame forcing.
        return 0
    }

    private fun createBitrateAdjuster(
        type: VideoCodecMimeType,
        codecName: String
    ): BitrateAdjuster {
        Log.i("test", "createBitrateAdjuster $codecName")
        return if (codecName.startsWith(MediaCodecUtils.EXYNOS_PREFIX)) { //编码器组件
            if (type == VideoCodecMimeType.VP8) {
                // Exynos VP8 encoders need dynamic bitrate adjustment.
                DynamicBitrateAdjuster()
            } else {
                // Exynos VP9 and H264 encoders need framerate-based bitrate adjustment.
                FramerateBitrateAdjuster()
            }
        } else BaseBitrateAdjuster()
        // Other codecs don't need bitrate adjustment.
    }

    private fun isH264HighProfileSupported(info: MediaCodecInfo): Boolean {
        return enableH264HighProfile && Build.VERSION.SDK_INT > Build.VERSION_CODES.M && info.name.startsWith(
            MediaCodecUtils.EXYNOS_PREFIX
        )
    }

    companion object {
        private const val TAG = "CustomHardwareVideoEncoderFactory"

        // Forced key frame interval - used to reduce color distortions on Qualcomm platforms.
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000

        /**
         * 默认支持对OMX.google.xxx 的匹配 ，如：OMX.google.h264.encoder
         * 目前大部分手机都支持OMX.google.xxx ；
         */
        const val GOOGLE_PREFIX = "OMX.google."

        // List of devices with poor H.264 encoder quality.
        // HW H.264 encoder on below devices has poor bitrate control - actual
        // bitrates deviates a lot from the target value.
        private val H264_HW_EXCEPTION_MODELS =
            Arrays.asList("SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4")
    }
}