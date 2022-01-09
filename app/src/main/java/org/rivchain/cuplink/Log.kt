package org.rivchain.cuplink

import android.util.Log

/*
* Wrapper for android.util.Log to disable logging
*/
class Log {

    companion object {
    private fun contextString(context: Any): String {
        return if (context is String) {
            context
        } else {
            context.javaClass.simpleName
        }
    }

        fun d(context: Any, message: String?) {
            if (BuildConfig.DEBUG) {
                val tag = contextString(context)
                Log.d(tag, message!!)
            }
        }
    }
}