package d.d.meshenger

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import d.d.meshenger.R
import androidx.appcompat.app.AppCompatDelegate

/*
 * Activity base class to apply night mode
*/
open class MeshengerActivity : AppCompatActivity() {
    var dark_active // dark mode
            = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark_active = darkModeEnabled()
        setTheme(if (dark_active) R.style.AppTheme_Dark_NoActionBar else R.style.AppTheme_Light_NoActionBar)
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