package org.rivchain.cuplink.renderer

import android.opengl.EGL14
import android.os.Build
import androidx.annotation.RequiresApi
import org.rivchain.cuplink.renderer.EglBaseWrapper.Companion.acquireEglBase
import org.rivchain.cuplink.util.Log
import org.webrtc.EglBase
import org.webrtc.EglBase10
import org.webrtc.EglBase14
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.microedition.khronos.egl.EGL10
import kotlin.concurrent.withLock

/**
 * Wrapper which allows caller to perform synchronized actions on an EglBase object.
 * Must use [acquireEglBase] to get a valid instance to an [EglBaseWrapper] for use in calling.
 * The instance returned may be shared across calls. Call [releaseEglBase] when it is no longer
 * required. When the wrapper has no others are using it, it will be properly released (a la reference counting).
 */
class EglBaseWrapper private constructor(val eglBase: EglBase?) {

    private val lock: Lock = ReentrantLock()

    @Volatile
    private var isReleased: Boolean = false

    constructor() : this(null)

    fun require(): EglBase = requireNotNull(eglBase)

    @RequiresApi(Build.VERSION_CODES.N)
    fun performWithValidEglBase(consumer: Consumer<EglBase>) {
        if (isReleased) {
            Log.d(this, "Tried to use a released EglBase")
            return
        }

        if (eglBase == null) {
            return
        }

        lock.withLock {
            if (isReleased) {
                Log.d(this, "Tried to use a released EglBase")
                return
            }

            val hasSharedContext = when (val context: EglBase.Context = eglBase.eglBaseContext) {
                is EglBase14.Context -> context.rawContext != EGL14.EGL_NO_CONTEXT
                is EglBase10.Context -> context.rawContext != EGL10.EGL_NO_CONTEXT
                else -> throw IllegalStateException("Unknown context")
            }

            if (hasSharedContext) {
                consumer.accept(eglBase)
            }
        }
    }

    private fun releaseEglBase() {
        if (isReleased || eglBase == null) {
            return
        }

        lock.withLock {
            if (isReleased) {
                return
            }

            isReleased = true
            eglBase.release()
        }
    }

    companion object {
        const val OUTGOING_PLACEHOLDER: String = "OUTGOING_PLACEHOLDER"

        private var eglBaseWrapper: EglBaseWrapper? = null
        private val holders: MutableSet<Any> = mutableSetOf()

        @JvmStatic
        fun acquireEglBase(holder: Any): EglBaseWrapper {
            val eglBase: EglBaseWrapper = eglBaseWrapper ?: EglBaseWrapper(EglBase.create())
            eglBaseWrapper = eglBase
            holders += holder
            Log.d(this, "Acquire EGL $eglBaseWrapper with holder: $holder")
            return eglBase
        }

        @JvmStatic
        fun releaseEglBase(holder: Any) {
            Log.d(this, "Release EGL with holder: $holder")
            holders.remove(holder)
            if (holders.isEmpty()) {
                Log.d(this, "Holders empty, release EGL Base")
                eglBaseWrapper?.releaseEglBase()
                eglBaseWrapper = null
            }
        }

        @JvmStatic
        fun replaceHolder(currentHolder: Any, newHolder: Any) {
            if (currentHolder == newHolder) {
                return
            }
            Log.d(this, "Replace holder $currentHolder with $newHolder")
            holders += newHolder
            holders.remove(currentHolder)
        }

        @JvmStatic
        fun forceRelease() {
            eglBaseWrapper?.releaseEglBase()
            eglBaseWrapper = null
            holders.clear()
        }
    }
}