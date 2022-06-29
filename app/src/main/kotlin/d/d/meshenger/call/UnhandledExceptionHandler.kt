package d.d.meshenger.call

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.util.Log
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter


/**
 * Singleton helper: install a default unhandled exception handler which shows
 * an informative dialog and kills the app.  Useful for apps whose
 * error-handling consists of throwing RuntimeExceptions.
 * NOTE: almost always more useful to
 * Thread.setDefaultUncaughtExceptionHandler() rather than
 * Thread.setUncaughtExceptionHandler(), to apply to background threads as well.
 */
class UnhandledExceptionHandler(val activity: Activity): Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "AppRTCMobileActivity"
        // Returns the Message attached to the original Cause of |t|.
        private fun getTopLevelCauseMessage(t: Throwable): String? {
            var topLevelCause: Throwable? = t
            while (topLevelCause!!.cause != null) {
                topLevelCause = topLevelCause.cause
            }
            return topLevelCause.message
        }

        // Returns a human-readable String of the stacktrace in |t|, recursively
        // through all Causes that led to |t|.
        private fun getRecursiveStackTrace(t: Throwable): String {
            val writer = StringWriter()
            t.printStackTrace(PrintWriter(writer))
            return writer.toString()
        }

    }
    override fun uncaughtException(unusedThread: Thread?, e: Throwable) {
        activity!!.runOnUiThread {
            val title = "Fatal error: " + getTopLevelCauseMessage(e)
            val msg = getRecursiveStackTrace(e)
            val errorView = TextView(activity)
            errorView.text = msg
            errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            val scrollingContainer = ScrollView(activity)
            scrollingContainer.addView(errorView)
            Log.e(
                TAG, """
     $title
     
     $msg
     """.trimIndent()
            )
            val listener =
                DialogInterface.OnClickListener { dialog, which ->
                    dialog.dismiss()
                    System.exit(1)
                }
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(title)
                .setView(scrollingContainer)
                .setPositiveButton("Exit", listener)
                .show()
        }
    }


}