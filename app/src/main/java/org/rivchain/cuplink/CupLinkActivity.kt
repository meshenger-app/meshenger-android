package org.rivchain.cuplink

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/*
* Activity base class to apply night mode
*/
open class CupLinkActivity : AppCompatActivity() {
    var dark_active // dark mode
            = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark_active = darkModeEnabled()
        setTheme(if (dark_active) R.style.AppTheme_Dark else R.style.AppTheme_Light)
    }

    private fun darkModeEnabled(): Boolean {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()

        // recreate acitivity to apply mode
        val dark_active_now = darkModeEnabled()
        if (dark_active != dark_active_now) {
            dark_active = dark_active_now
            recreate()
        }
    }
}