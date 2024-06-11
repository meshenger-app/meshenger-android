package org.rivchain.cuplink.renderer

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.os.Looper
import android.util.AttributeSet
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.ViewUtil
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.RendererCommon.ScalingType
import org.webrtc.RendererCommon.VideoLayoutMeasure
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import kotlin.math.min


/**
 * This class is a modified version of [org.webrtc.SurfaceViewRenderer] which is based on [TextureView]
 */
class TextureViewRenderer : TextureView, SurfaceTextureListener, VideoSink, RendererEvents {
    private val eglRenderer: SurfaceTextureEglRenderer
    private var context: Context
    private val videoLayoutMeasure = VideoLayoutMeasure()
    private var rendererEvents: RendererEvents? = null
    private var rotatedFrameWidth = 0
    private var rotatedFrameHeight = 0
    private val enableFixedSize = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var isInitialized = false
    private var attachedVideoSink: BroadcastVideoSink? = null
    private var lifecycle: Lifecycle? = null

    constructor(context: Context) : super(context) {
        eglRenderer = SurfaceTextureEglRenderer(resourceName)
        this.context = context
        this.surfaceTextureListener = this
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        eglRenderer = SurfaceTextureEglRenderer(resourceName)
        this.context = context
        this.surfaceTextureListener = this
    }

    fun init(eglBase: EglBase) {
        if (isInitialized) return
        isInitialized = true
        this.init(eglBase.eglBaseContext, null, EglBase.CONFIG_PLAIN, GlRectDrawer())
    }

    fun init(
        sharedContext: EglBase.Context,
        rendererEvents: RendererEvents?,
        configAttributes: IntArray,
        drawer: GlDrawer,
    ) {
        ThreadUtils.checkIsOnMainThread()
        this.rendererEvents = rendererEvents
        rotatedFrameWidth = 0
        rotatedFrameHeight = 0
        eglRenderer.init(sharedContext, this, configAttributes, drawer)
        lifecycle = ViewUtil.getActivityLifecycle(this)
        if (lifecycle != null) {
            lifecycle!!.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    release()
                }
            })
        }
    }

    fun attachBroadcastVideoSink(videoSink: BroadcastVideoSink?) {
        if (attachedVideoSink === videoSink) {
            return
        }
        eglRenderer.clearImage()
        if (attachedVideoSink != null) {
            attachedVideoSink!!.removeSink(this)
            attachedVideoSink!!.removeRequestingSize(this)
        }
        if (videoSink != null) {
            videoSink.addSink(this)
            videoSink.putRequestingSize(this, Point(width, height))
        } else {
            clearImage()
        }
        attachedVideoSink = videoSink
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (lifecycle == null || lifecycle!!.currentState == Lifecycle.State.DESTROYED) {
            release()
        }
    }

    fun release() {
        eglRenderer.release()
        if (attachedVideoSink != null) {
            attachedVideoSink!!.removeSink(this)
            attachedVideoSink!!.removeRequestingSize(this)
        }
    }

    fun removeFrameListener(listener: EglRenderer.FrameListener) {
        eglRenderer.removeFrameListener(listener)
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    fun setScalingType(scalingType: ScalingType) {
        ThreadUtils.checkIsOnMainThread()
        videoLayoutMeasure.setScalingType(scalingType)
        requestLayout()
    }

    fun setScalingType(
        scalingTypeMatchOrientation: ScalingType,
        scalingTypeMismatchOrientation: ScalingType,
    ) {
        ThreadUtils.checkIsOnMainThread()
        videoLayoutMeasure.setScalingType(
            scalingTypeMatchOrientation,
            scalingTypeMismatchOrientation
        )
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var widthSpec = widthSpec
        var heightSpec = heightSpec
        ThreadUtils.checkIsOnMainThread()
        widthSpec =
            MeasureSpec.makeMeasureSpec(resolveSizeAndState(0, widthSpec, 0), MeasureSpec.AT_MOST)
        heightSpec =
            MeasureSpec.makeMeasureSpec(resolveSizeAndState(0, heightSpec, 0), MeasureSpec.AT_MOST)
        val size = videoLayoutMeasure.measure(
            widthSpec, heightSpec, rotatedFrameWidth,
            rotatedFrameHeight
        )
        setMeasuredDimension(size.x, size.y)
        attachedVideoSink?.putRequestingSize(this, size)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        ThreadUtils.checkIsOnMainThread()
        eglRenderer.setLayoutAspectRatio((right - left).toFloat() / (bottom - top).toFloat())
        updateSurfaceSize()
    }

    private fun updateSurfaceSize() {
        ThreadUtils.checkIsOnMainThread()
        if (!isAvailable) {
            return
        }
        if (enableFixedSize && rotatedFrameWidth != 0 && rotatedFrameHeight != 0 && this.width != 0 && this.height != 0) {
            val layoutAspectRatio = this.width.toFloat() / this.height.toFloat()
            val frameAspectRatio = rotatedFrameWidth.toFloat() / rotatedFrameHeight.toFloat()
            val drawnFrameWidth: Int
            val drawnFrameHeight: Int
            if (frameAspectRatio > layoutAspectRatio) {
                drawnFrameWidth = (rotatedFrameHeight.toFloat() * layoutAspectRatio).toInt()
                drawnFrameHeight = rotatedFrameHeight
            } else {
                drawnFrameWidth = rotatedFrameWidth
                drawnFrameHeight = (rotatedFrameWidth.toFloat() / layoutAspectRatio).toInt()
            }
            val width =
                min(this.width.toDouble(), drawnFrameWidth.toDouble()).toInt()
            val height =
                min(this.height.toDouble(), drawnFrameHeight.toDouble()).toInt()
            Log.d(
                this,
                "updateSurfaceSize. Layout size: " + width + "x" + height + ", frame size: " + rotatedFrameWidth + "x" + rotatedFrameHeight + ", requested surface size: " + width + "x" + height + ", old surface size: " + surfaceWidth + "x" + surfaceHeight
            )
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width
                surfaceHeight = height
                surfaceTexture!!.setDefaultBufferSize(width, height)
            }
        } else {
            surfaceHeight = 0
            surfaceWidth = surfaceHeight
            this.surfaceTexture!!.setDefaultBufferSize(measuredWidth, measuredHeight)
        }
    }

    override fun onFirstFrameRendered() {
        if (rendererEvents != null) {
            rendererEvents!!.onFirstFrameRendered()
        }
    }

    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {
        if (rendererEvents != null) {
            rendererEvents!!.onFrameResolutionChanged(videoWidth, videoHeight, rotation)
        }
        val rotatedWidth = if (rotation != 0 && rotation != 180) videoHeight else videoWidth
        val rotatedHeight = if (rotation != 0 && rotation != 180) videoWidth else videoHeight

        // Set picture in picture params to the given aspect ratio
        ViewUtil.setPictureInPicture(context)

        postOrRun {
            rotatedFrameWidth = rotatedWidth
            rotatedFrameHeight = rotatedHeight
            updateSurfaceSize()
            requestLayout()
        }
    }

    override fun onFrame(videoFrame: VideoFrame) {
        if (isAttachedToWindow) {
            eglRenderer.onFrame(videoFrame)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        surfaceWidth = 0
        surfaceHeight = 0
        updateSurfaceSize()
        eglRenderer.onSurfaceTextureAvailable(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        eglRenderer.onSurfaceTextureSizeChanged(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return eglRenderer.onSurfaceTextureDestroyed(surface)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    private val resourceName: String
        get() = try {
            this.resources.getResourceEntryName(this.id)
        } catch (var2: Resources.NotFoundException) {
            ""
        }

    private fun clearImage() {
        eglRenderer.clearImage()
    }

    private fun postOrRun(r: Runnable) {
        if (Thread.currentThread() === Looper.getMainLooper().thread) {
            r.run()
        } else {
            post(r)
        }
    }
}