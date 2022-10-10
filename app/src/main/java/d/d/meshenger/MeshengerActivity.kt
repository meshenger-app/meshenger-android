package d.d.meshenger

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

/*
 * Activity base class to apply night mode
*/
open class MeshengerActivity : AppCompatActivity() {
    var dark_active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark_active = darkModeEnabled()
        if (dark_active) {
            setTheme(R.style.AppTheme_Dark_NoActionBar)
        } else {
            setTheme(R.style.AppTheme_Light_NoActionBar)
        }
    }

    private fun darkModeEnabled(): Boolean {
        return AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()

        // recreate activity to apply mode
        val dark_active_now = darkModeEnabled()
        if (dark_active != dark_active_now) {
            dark_active = dark_active_now
            recreate()
        }
    }
}