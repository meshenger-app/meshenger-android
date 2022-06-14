/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package org.webrtc

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import org.webrtc.MediaCodecUtils.SOFTWARE_IMPLEMENTATION_PREFIXES
import java.util.*

/** Factory for android hardware video encoders.  */
// API 16 requires the use of deprecated methods.
class HardwareExtendedVideoEncoderFactory @JvmOverloads constructor(
    sharedContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean,
    enableH264HighProfile: Boolean,
    codecAllowedPredicate: Predicate<MediaCodecInfo>? =  /* codecAllowedPredicate= */
        null
) : VideoEncoderFactory {
    private var sharedContext: EglBase14.Context? = null
    private val enableIntelVp8Encoder: Boolean
    private val enableH264HighProfile: Boolean
    private val codecAllowedPredicate: Predicate<MediaCodecInfo>?

    @Deprecated("")
    constructor(enableIntelVp8Encoder: Boolean, enableH264HighProfile: Boolean) : this(
        null,
        enableIntelVp8Encoder,
        enableH264HighProfile
    ) {
    }

    override fun createEncoder(input: VideoCodecInfo): VideoEncoder? {
        val type = VideoCodecMimeType.valueOf(input.getName())
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
        return HardwareVideoEncoder(
            MediaCodecWrapperFactoryImpl(), codecName, type,
            surfaceColorFormat, yuvColorFormat, input.params, PERIODIC_KEY_FRAME_INTERVAL_S,
            getForcedKeyFrameIntervalMs(type, codecName), createBitrateAdjuster(type, codecName),
            sharedContext
        )
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val supportedCodecInfos: MutableList<VideoCodecInfo> = ArrayList()
        // Generate a list of supported codecs in order of preference:
        // H264 (high profile), H264 (baseline profile), VP8, VP9 and AV1.
        for (type in arrayOf(
            VideoCodecMimeType.H264, VideoCodecMimeType.VP8,
            VideoCodecMimeType.VP9, VideoCodecMimeType.AV1
        )) {
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
        for (i in 0 until MediaCodecList.getCodecCount()) {
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
        return null // No support for this type.
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
        } else isHardwareSupportedInCurrentSdk(info, type) && isMediaCodecAllowed(info)
    }

    // Returns true if the given MediaCodecInfo indicates a hardware module that is supported on the
    // current SDK.
    private fun isHardwareSupportedInCurrentSdk(
        info: MediaCodecInfo,
        type: VideoCodecMimeType
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else when (type) {
            VideoCodecMimeType.VP8 -> isHardwareSupportedInCurrentSdkVp8(info)
            VideoCodecMimeType.VP9 -> isHardwareSupportedInCurrentSdkVp9(info)
            VideoCodecMimeType.H264 -> isHardwareSupportedInCurrentSdkH264(info)
            VideoCodecMimeType.AV1 -> false
        }
        return false
    }

    private fun isHardwareSupportedInCurrentSdkVp8(info: MediaCodecInfo): Boolean {
        val name = info.name
        // QCOM Vp8 encoder is always supported.
        return (name.startsWith(MediaCodecUtils.QCOM_PREFIX) // Exynos VP8 encoder is supported in M or later.
                || name.startsWith(MediaCodecUtils.EXYNOS_PREFIX) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M // Intel Vp8 encoder is always supported, with the intel encoder enabled.
                || name.startsWith(MediaCodecUtils.INTEL_PREFIX) && enableIntelVp8Encoder)
    }

    private fun isHardwareSupportedInCurrentSdkVp9(info: MediaCodecInfo): Boolean {
        val name = info.name
        return ((name.startsWith(MediaCodecUtils.QCOM_PREFIX) || name.startsWith(MediaCodecUtils.EXYNOS_PREFIX)) // Both QCOM and Exynos VP9 encoders are supported in N or later.
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
    }

    private fun isHardwareSupportedInCurrentSdkH264(info: MediaCodecInfo): Boolean {
        // First, H264 hardware might perform poorly on this model.
        if (H264_HW_EXCEPTION_MODELS.contains(Build.MODEL)) {
            return false
        }
        val name = info.name
        // #QCOM and Exynos H264 encoders are always supported.
        // exclude software codecs
        return SOFTWARE_IMPLEMENTATION_PREFIXES.indexOf(name) < 0
    }

    private fun isMediaCodecAllowed(info: MediaCodecInfo): Boolean {
        return codecAllowedPredicate?.test(info) ?: true
    }

    private fun getForcedKeyFrameIntervalMs(type: VideoCodecMimeType, codecName: String): Int {
        if (type == VideoCodecMimeType.VP8 && codecName.startsWith(MediaCodecUtils.QCOM_PREFIX)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS
            }
            return if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
                QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS
            } else QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS
        }
        // Other codecs don't need key frame forcing.
        return 0
    }

    private fun createBitrateAdjuster(
        type: VideoCodecMimeType,
        codecName: String
    ): BitrateAdjuster {
        return if (codecName.startsWith(MediaCodecUtils.EXYNOS_PREFIX)) {
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
        private const val TAG = "HardwareVideoEncoderFactory"

        // We don't need periodic keyframes. But some HW encoders, Exynos in particular, fails to
        // initialize with value -1 which should disable periodic keyframes according to the spec. Set it
        // to 1 hour.
        private const val PERIODIC_KEY_FRAME_INTERVAL_S = 3600

        // Forced key frame interval - used to reduce color distortions on Qualcomm platforms.
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_L_MS = 15000
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_M_MS = 20000
        private const val QCOM_VP8_KEY_FRAME_INTERVAL_ANDROID_N_MS = 15000

        // List of devices with poor H.264 encoder quality.
        // HW H.264 encoder on below devices has poor bitrate control - actual
        // bitrates deviates a lot from the target value.
        private val H264_HW_EXCEPTION_MODELS =
            Arrays.asList("SAMSUNG-SGH-I337", "Nexus 7", "Nexus 4")
    }
    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext The textures generated will be accessible from this context. May be null,
     * this disables texture support.
     * @param enableIntelVp8Encoder true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile true if H264 High Profile enabled.
     * @param codecAllowedPredicate optional predicate to filter codecs. All codecs are allowed
     * when predicate is not provided.
     */
    /**
     * Creates a HardwareVideoEncoderFactory that supports surface texture encoding.
     *
     * @param sharedContext The textures generated will be accessible from this context. May be null,
     * this disables texture support.
     * @param enableIntelVp8Encoder true if Intel's VP8 encoder enabled.
     * @param enableH264HighProfile true if H264 High Profile enabled.
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
    }
}