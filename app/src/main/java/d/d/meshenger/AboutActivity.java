package d.d.meshenger;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class AboutActivity extends MeshengerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        setTitle(getResources().getString(R.string.menu_about));

        findViewById(R.id.mailAddress1).setOnClickListener(v -> sendMail1());
        findViewById(R.id.mailAddress2).setOnClickListener(v -> sendMail2());
        findViewById(R.id.licenseVersion).setOnClickListener(v -> showLicense());

        ((TextView) findViewById(R.id.versionTv)).setText(BuildConfig.VERSION_NAME);
    }

    private void showLicense(){
        Intent intent = new Intent(this, LicenseActivity.class);
        startActivity(intent);
    }

    private void sendMail1(){
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:dakhnod@gmail.com"));
        //intent.putExtra(Intent.EXTRA_EMAIL, "dakhnod@gmail.com");
        //intent.putExtra(Intent.EXTRA_SUBJECT, "Yo, crazy app!");
        //intent.setType("message/rfc822");

        startActivity(intent);
    }

    private void sendMail2(){
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:vasu.hvardhan@gmail.com"));
        //intent.putExtra(Intent.EXTRA_EMAIL, "vasu.hvardhan@gmail.com");
        //intent.putExtra(Intent.EXTRA_SUBJECT, "Hey, superb app!");
        //intent.setType("message/rfc822");

        startActivity(intent);
    }
}
