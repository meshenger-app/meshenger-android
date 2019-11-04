package d.d.meshenger;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;


/*
 * Activity base class to apply night mode
*/
public class MeshengerActivity extends AppCompatActivity {
    boolean dark_active; // dark mode

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dark_active = darkModeEnabled();
        setTheme(dark_active ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    private boolean darkModeEnabled() {
        return (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // recreate activity to apply mode
        boolean dark_active_now = darkModeEnabled();
        if (dark_active != dark_active_now) {
            dark_active = dark_active_now;
            recreate();
        }
    }
}
