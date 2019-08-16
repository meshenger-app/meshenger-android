package d.d.meshenger;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;


public class AboutActivity extends MeshengerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        setTitle(getResources().getString(R.string.menu_about));

        findViewById(R.id.mailAddress1).setOnClickListener(v -> sendMail1());
        findViewById(R.id.mailAddress2).setOnClickListener(v -> sendMail2());
        findViewById(R.id.mailAddress3).setOnClickListener(v -> sendMail3());
        findViewById(R.id.licenseVersion).setOnClickListener(v -> showLicense());

        ((TextView) findViewById(R.id.versionTv)).setText(
            Utils.getApplicationVersion(this)
        );
    }

    private void showLicense() {
        Intent intent = new Intent(this, LicenseActivity.class);
        startActivity(intent);
    }

    private void sendMail1() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:dakhnod@gmail.com"));
        startActivity(intent);
    }

    private void sendMail2() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:vasu.hvardhan@gmail.com"));
        startActivity(intent);
    }

    private void sendMail3() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:moritzwarning@web.de"));
        startActivity(intent);
    }
}
