package d.d.meshenger;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import org.w3c.dom.Text;

public class SettingsActivity extends AppCompatActivity {
    String nick;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setTitle("Settings");
        prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        nick = prefs.getString("username", "undefined");

        findViewById(R.id.changeNickLayout).setOnClickListener((v) -> changeNick());
    }


    private void changeNick(){
        EditText et = new EditText(this);
        et.setText(nick);
        et.setSelection(nick.length());
        new AlertDialog.Builder(this)
                .setTitle("change nick")
                .setView(et)
                .setPositiveButton("ok", (dialogInterface, i) -> {
                    nick = et.getText().toString();
                    prefs.edit().putString("username", nick).apply();
                    initViews();
                })
                .setNegativeButton("cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initViews();
    }

    private void initViews(){
        ((TextView) findViewById(R.id.nickTv)).setText(nick);
    }
}
