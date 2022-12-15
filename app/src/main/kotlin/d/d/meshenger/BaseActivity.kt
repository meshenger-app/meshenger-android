package d.d.meshenger

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/*
 * Base class for every Activity
*/
open class BaseActivity : AppCompatActivity() {
    fun updateNightMode(nightModeSetting: String) {
        Log.d(this, "Make sure night is $nightModeSetting")

        val nightMode = when (nightModeSetting) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            }
        }

        if (nightMode != AppCompatDelegate.getDefaultNightMode()) {
            Log.d(this, "Change night mode to $nightModeSetting")
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}