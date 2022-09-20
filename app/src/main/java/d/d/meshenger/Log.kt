package d.d.meshenger

import android.util.Log

/*
* Wrapper for android.util.Log to disable logging
*/
object Log {
    private fun contextString(context: Any): String {
        if (context is String) {
            return context
        } else {
            return context.javaClass.simpleName
        }
    }

    fun d(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.d(tag, message)
        }
    }

    fun i(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.i(tag, message)
        }
    }

    fun w(context: Any, message: String) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.w(tag, message)
        }
    }

    fun e(context: Any, message: String) {
        val tag = contextString(context)
        Log.e(tag, message)
    }
}
