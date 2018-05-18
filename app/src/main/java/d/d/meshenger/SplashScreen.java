package d.d.meshenger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.widget.TextView;


public class SplashScreen extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Typeface type = Typeface.createFromAsset(getAssets(),"rounds_black.otf");
        ((TextView)findViewById(R.id.splashText)).setTypeface(type);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, ContactListActivity.class));
            finish();
        }, 1500);
    }
}
