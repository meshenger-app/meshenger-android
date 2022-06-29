package d.d.meshenger.call

import android.os.Build
import d.d.meshenger.Log


/**
 * AppRTCUtils provides helper functions for managing thread safety.
 */
object AppRTCUtils {

    /** Helper method which throws an exception  when an assertion has failed.  */
    fun assertIsTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Expected condition to be true")
        }
    }

    /** Helper method for building a string of thread information. */
    fun getThreadInfo(): String {
        return "@[name= ${Thread.currentThread().name}, id= + ${Thread.currentThread().id}]"
    }

    /** Information about the current build, taken from system properties.  */
    fun logDeviceInfo(tag: String) { //TODO(IODevBlue): Use String Templates
        Log.d(
            tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
                    + "Release: " + Build.VERSION.RELEASE + ", "
                    + "Brand: " + Build.BRAND + ", "
                    + "Device: " + Build.DEVICE + ", "
                    + "Id: " + Build.ID + ", "
                    + "Hardware: " + Build.HARDWARE + ", "
                    + "Manufacturer: " + Build.MANUFACTURER + ", "
                    + "Model: " + Build.MODEL + ", "
                    + "Product: " + Build.PRODUCT
        )
    }
}