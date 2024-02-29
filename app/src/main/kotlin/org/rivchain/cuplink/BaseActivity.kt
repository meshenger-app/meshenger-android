package org.rivchain.cuplink

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/*
 * Base class for every Activity
*/
open class BaseActivity : AppCompatActivity() {
    fun setDefaultNightMode(nightModeSetting: String) {
        nightMode = when (nightModeSetting) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            "auto" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            else -> {
                Log.e(this, "invalid night mode setting: $nightModeSetting")
                nightMode
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

        fun isNightmodeEnabled(context: Context): Boolean {
            val mode = context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
            return (mode == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}