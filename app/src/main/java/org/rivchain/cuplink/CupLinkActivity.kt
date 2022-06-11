package org.rivchain.cuplink

import android.os.Bundle
import android.view.View
import android.view.WindowManager
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

    fun hideActionBar(view: View?) {
        if (view != null) {
            val params: WindowManager.LayoutParams = window.getAttributes()
            params.flags = params.flags or (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN)
            window.attributes = params;
        }
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