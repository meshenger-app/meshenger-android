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
        new Thread(() -> {
            try {
                StringBuffer buffer = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("license.txt")));
                String line;
                while((line = reader.readLine()) != null) buffer.append(line + "\n");
                reader.close();
                runOnUiThread(() ->{
                    findViewById(R.id.licenseLoadingBar).setVisibility(View.GONE);
                    ((TextView)findViewById(R.id.licenceText)).setText(buffer.toString());
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        findViewById(R.id.mailText).setOnClickListener(v -> sendMail());
        findViewById(R.id.mailText2).setOnClickListener(v -> sendMail2());

        ((TextView) findViewById(R.id.versionTv)).setText(BuildConfig.VERSION_NAME);
    }

    public void sendMail(){
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:dakhnod@gmail.com"));
        //intent.putExtra(Intent.EXTRA_EMAIL, "dakhnod@gmail.com");
        //intent.putExtra(Intent.EXTRA_SUBJECT, "Yo, crazy app!");
        //intent.setType("message/rfc822");

        startActivity(intent);
    }

    public void sendMail2(){
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:vasu.hvardhan@gmail.com"));
        //intent.putExtra(Intent.EXTRA_EMAIL, "vasu.hvardhan@gmail.com");
        //intent.putExtra(Intent.EXTRA_SUBJECT, "Hey, superb app!");
        //intent.setType("message/rfc822");

        startActivity(intent);
    }
}
