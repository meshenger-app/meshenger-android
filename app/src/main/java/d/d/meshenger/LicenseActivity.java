package d.d.meshenger;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class LicenseActivity extends MeshengerActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        setTitle(getResources().getString(R.string.menu_license));

        // reading the license file can be slow => use a thread
        new Thread(() -> {
            try {
                StringBuffer buffer = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("license.txt")));
                String line;
                while((line = reader.readLine()) != null) {
                    buffer.append(line.trim() + "\n");
                }
                reader.close();
                runOnUiThread(() -> {
                    findViewById(R.id.licenseLoadingBar).setVisibility(View.GONE);
                    ((TextView)findViewById(R.id.licenceText)).setText(buffer.toString());
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
