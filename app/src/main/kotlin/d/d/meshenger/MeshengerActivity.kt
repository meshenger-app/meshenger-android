package d.d.meshenger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate


open class MeshengerActivity: AppCompatActivity() {
    var darkActive = false // dark mode


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkActive = darkModeEnabled()
        setTheme(if (darkActive) R.style.AppTheme_Dark else R.style.AppTheme_Light)
    }

    private fun darkModeEnabled(): Boolean =
        AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

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