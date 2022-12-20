package d.d.meshenger

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/*
 * Base class for every Activity
*/
open class BaseActivity : AppCompatActivity() {
    fun setDefaultNightMode(nightModeSetting: String) {
        when (nightModeSetting) {
            "on" -> nightMode = AppCompatDelegate.MODE_NIGHT_YES
            "off" -> nightMode = AppCompatDelegate.MODE_NIGHT_NO
            "auto" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            else -> {
                Log.e(this, "invalid night mode setting: $nightModeSetting")
            }
        }
    }

    // prefer to be called before super.onCreate()
    fun applyNightMode() {
        if (nightMode != AppCompatDelegate.getDefaultNightMode()) {
            Log.d(this, "Change night mode to $nightMode")
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    companion object {
        private var nightMode = AppCompatDelegate.getDefaultNightMode()
    }
}