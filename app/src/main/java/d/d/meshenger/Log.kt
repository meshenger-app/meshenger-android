package d.d.meshenger

import android.util.Log

/*
* Wrapper for android.util.Log to disable logging
*/
object Log {
    private fun contextString(context: Any): String {
        return if (context is String) {
            context
        } else {
            context.javaClass.simpleName
        }
    }

    @JvmStatic
    fun d(context: Any, message: String?) {
        if (BuildConfig.DEBUG) {
            val tag = contextString(context)
            Log.d(tag, message!!)
        }
    }
}