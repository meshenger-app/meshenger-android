package org.rivchain.cuplink.renderer

import android.graphics.SurfaceTexture
import android.view.TextureView.SurfaceTextureListener
import org.rivchain.cuplink.util.Log
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.RendererCommon.GlDrawer
import org.webrtc.RendererCommon.RendererEvents
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import java.util.concurrent.CountDownLatch


/**
 * This class is a modified copy of [org.webrtc.SurfaceViewRenderer] designed to work with a
 * [SurfaceTexture] to facilitate easier animation, rounding, elevation, etc.
 */
class SurfaceTextureEglRenderer(name: String) : EglRenderer(name), SurfaceTextureListener {
    private val layoutLock = Any()
    private var rendererEvents: RendererEvents? = null
    private var isFirstFrameRendered = false
    private var isRenderingPaused = false
    private var rotatedFrameWidth = 0
    private var rotatedFrameHeight = 0
    private var frameRotation = 0
    fun init(
        sharedContext: EglBase.Context?,
        rendererEvents: RendererEvents?,
        configAttributes: IntArray,
        drawer: GlDrawer
    ) {
        ThreadUtils.checkIsOnMainThread()
        this.rendererEvents = rendererEvents
        synchronized(this.layoutLock) {
            isFirstFrameRendered = false
            rotatedFrameWidth = 0
            rotatedFrameHeight = 0
            frameRotation = 0
        }
        super.init(sharedContext, configAttributes, drawer)
    }

    override fun init(
        sharedContext: EglBase.Context?,
        configAttributes: IntArray,
        drawer: GlDrawer
    ) {
        this.init(sharedContext, null, configAttributes, drawer)
    }

    override fun setFpsReduction(fps: Float) {
        synchronized(this.layoutLock) { isRenderingPaused = fps == 0.0f }
        super.setFpsReduction(fps)
    }

    override fun disableFpsReduction() {
        synchronized(this.layoutLock) { isRenderingPaused = false }
        super.disableFpsReduction()
    }

    override fun pauseVideo() {
        synchronized(this.layoutLock) { isRenderingPaused = true }
        super.pauseVideo()
    }

    override fun onFrame(frame: VideoFrame) {
        updateFrameDimensionsAndReportEvents(frame)
        super.onFrame(frame)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        Log.d(this, "onSurfaceTextureSizeChanged: size: " + width + "x" + height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        ThreadUtils.checkIsOnMainThread()
        val completionLatch = CountDownLatch(1)
        releaseEglSurface { completionLatch.countDown() }
        ThreadUtils.awaitUninterruptibly(completionLatch)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    private fun updateFrameDimensionsAndReportEvents(frame: VideoFrame) {
        synchronized(this.layoutLock) {
            if (!isRenderingPaused) {
                if (!isFirstFrameRendered) {
                    isFirstFrameRendered = true
                    Log.d(
                        this,
                        "Reporting first rendered frame."
                    )
                    if (rendererEvents != null) {
                        rendererEvents!!.onFirstFrameRendered()
                    }
                }
                if (rotatedFrameWidth != frame.rotatedWidth || rotatedFrameHeight != frame.rotatedHeight || frameRotation != frame.rotation) {
                    Log.d(
                        this,
                        "Reporting frame resolution changed to " + frame.buffer
                            .width + "x" + frame.buffer
                            .height + " with rotation " + frame.rotation
                    )
                    if (rendererEvents != null) {
                        rendererEvents!!.onFrameResolutionChanged(
                            frame.buffer.width,
                            frame.buffer.height,
                            frame.rotation
                        )
                    }
                    rotatedFrameWidth = frame.rotatedWidth
                    rotatedFrameHeight = frame.rotatedHeight
                    frameRotation = frame.rotation
                }
            }
        }
    }
}