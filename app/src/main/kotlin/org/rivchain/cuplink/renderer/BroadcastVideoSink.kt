package org.rivchain.cuplink.renderer

import android.graphics.Point
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.Objects
import java.util.WeakHashMap


/**
 * Video sink implementation that handles broadcasting a single source video track to
 * multiple [VideoSink] consumers.
 *
 * Also has logic to manage rotating frames before forwarding to prevent each renderer
 * from having to copy the frame for rotation.
 */
class BroadcastVideoSink @JvmOverloads constructor(
    eglBase: EglBaseWrapper = EglBaseWrapper(),
    forceRotate: Boolean = false,
    rotateWithDevice: Boolean = true,
    deviceOrientationDegrees: Int = 0
) :
    VideoSink {
    private val eglBase: EglBaseWrapper
    private val sinks: WeakHashMap<VideoSink, Boolean>
    private val requestingSizes: WeakHashMap<Any, Point>
    private var deviceOrientationDegrees: Int
    private var rotateToRightSide: Boolean
    private val forceRotate: Boolean
    private val rotateWithDevice: Boolean
    private var currentlyRequestedMaxSize: RequestedSize? = null

    /**
     * @param eglBase                  Rendering context
     * @param forceRotate              Always rotate video frames regardless of frame dimension
     * @param rotateWithDevice         Rotate video frame to match device orientation
     * @param deviceOrientationDegrees Device orientation in degrees
     */
    init {
        this.eglBase = eglBase
        sinks = WeakHashMap()
        requestingSizes = WeakHashMap()
        this.deviceOrientationDegrees = deviceOrientationDegrees
        rotateToRightSide = false
        this.forceRotate = forceRotate
        this.rotateWithDevice = rotateWithDevice
    }

    val lockableEglBase: EglBaseWrapper
        get() = eglBase

    @Synchronized
    fun addSink(sink: VideoSink) {
        sinks[sink] = true
    }

    @Synchronized
    fun removeSink(sink: VideoSink) {
        sinks.remove(sink)
    }

    /**
     * Set the specific rotation desired when not rotating with device.
     *
     * Really only needed for properly rotating self camera views.
     */
    fun setRotateToRightSide(rotateToRightSide: Boolean) {
        this.rotateToRightSide = rotateToRightSide
    }

    fun setDeviceOrientationDegrees(deviceOrientationDegrees: Int) {
        this.deviceOrientationDegrees = deviceOrientationDegrees
    }

    @Synchronized
    override fun onFrame(videoFrame: VideoFrame) {
        var videoFrame = videoFrame
        val isDeviceRotationIgnored = deviceOrientationDegrees == DEVICE_ROTATION_IGNORE
        if (!isDeviceRotationIgnored && forceRotate) {
            var rotation = calculateRotation()
            if (rotation > 0) {
                rotation += if (rotateWithDevice) videoFrame.rotation else 0
                videoFrame = VideoFrame(videoFrame.buffer, rotation % 360, videoFrame.timestampNs)
            }
        }
        for (sink in sinks.keys) {
            sink.onFrame(videoFrame)
        }
    }

    private fun calculateRotation(): Int {
        if (forceRotate && (deviceOrientationDegrees == 0 || deviceOrientationDegrees == 180)) {
            return 0
        }
        if (rotateWithDevice) {
            return if (forceRotate) {
                deviceOrientationDegrees
            } else {
                if (deviceOrientationDegrees != 0 && deviceOrientationDegrees != 180) deviceOrientationDegrees else 270
            }
        }
        return if (rotateToRightSide) 90 else 270
    }

    fun putRequestingSize(`object`: Any, size: Point) {
        if (size.x == 0 || size.y == 0) {
            return
        }
        synchronized(requestingSizes) { requestingSizes.put(`object`, size) }
    }

    fun removeRequestingSize(`object`: Any) {
        synchronized(requestingSizes) { requestingSizes.remove(`object`) }
    }

    val maxRequestingSize: RequestedSize
        get() {
            var width = 0
            var height = 0
            synchronized(requestingSizes) {
                for (size in requestingSizes.values) {
                    if (width < size.x) {
                        width = size.x
                        height = size.y
                    }
                }
            }
            return RequestedSize(width, height)
        }

    fun setCurrentlyRequestedMaxSize(currentlyRequestedMaxSize: RequestedSize) {
        this.currentlyRequestedMaxSize = currentlyRequestedMaxSize
    }

    fun needsNewRequestingSize(): Boolean {
        return maxRequestingSize != currentlyRequestedMaxSize
    }

    class RequestedSize(val width: Int, val height: Int) {

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val that = o as RequestedSize
            return width == that.width && height == that.height
        }

        override fun hashCode(): Int {
            return Objects.hash(width, height)
        }
    }

    companion object {
        const val DEVICE_ROTATION_IGNORE = -1
    }
}