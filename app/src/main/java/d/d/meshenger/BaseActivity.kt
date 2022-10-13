package d.d.meshenger

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate

/*
 * Activity base class to apply night mode
*/
open class BaseActivity : AppCompatActivity() {
    var darkActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkActive = darkModeEnabled()
        if (darkActive) {
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
        val darkActiveNow = darkModeEnabled()
        if (darkActive != darkActiveNow) {
            darkActive = darkActiveNow
            recreate()
        }
    }
}