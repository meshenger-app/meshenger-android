package d.d.meshenger;

import android.content.Intent;
import android.content.res.Configuration;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.Locale;


public class SettingsActivity extends MeshengerActivity {
    private String nick;
    private Database db;
    private AppData appData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(getResources().getString(R.string.menu_settings));

        db = new Database(this);
        appData = db.getAppData();

        //if (appData == null) {
        //    new AppData();
        //}

        nick = db.getAppData().getUsername();

        findViewById(R.id.changeNickLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeNick();
            }
        });

        CheckBox ignoreCB = findViewById(R.id.checkBoxIgnoreUnsaved);
        if (appData != null && !appData.getBlockUC()) {
            ignoreCB.setChecked(false);
        } else if (appData != null && appData.getBlockUC()) {
            ignoreCB.setChecked(true);
        } else {
            ignoreCB.setChecked(false);
        }

        ignoreCB.setOnCheckedChangeListener((compoundButton, b) -> {
            if (appData != null) {
                if (b) {
                    appData.setBlockUC(true);
                } else {
                    appData.setBlockUC(false);
                }
                db.updateAppData(appData);
            }
            syncSettings("ignoreUnsaved", b);
        });

        CheckBox nightMode = findViewById(R.id.checkBoxNightMode);
        if (appData != null && appData.getMode() == 1) {
            nightMode.setChecked(false);
        } else if (appData != null && appData.getMode() == 2) {
            nightMode.setChecked(true);
        } else {
            nightMode.setChecked(false);
        }

        nightMode.setChecked(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
        nightMode.setOnCheckedChangeListener((compoundButton, b) -> {
            AppCompatDelegate.setDefaultNightMode(compoundButton.isChecked() ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            if (appData != null) {
                if (compoundButton.isChecked()) {
                    appData.setMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    appData.setMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                db.updateAppData(appData);
            }
            Intent intent = new Intent(SettingsActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void getLocale() {
        Configuration config = getResources().getConfiguration();
        Locale locale = config.locale;
        ((TextView) findViewById(R.id.localeTv)).setText(locale.getDisplayLanguage());

        if (appData != null) {
            appData.setLanguage(locale.getDisplayLanguage());
            db.updateAppData(appData);
        }

        Locale[] locales = new Locale[]{Locale.ENGLISH, Locale.GERMAN};
        findViewById(R.id.changeLocaleLayout).setOnClickListener((v) -> {
            RadioGroup group = new RadioGroup(this);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            for (int i = 0; i < locales.length; i += 1) {
                Locale l = locales[i];
                RadioButton button = new RadioButton(this);
                button.setId(i);
                button.setText(l.getDisplayLanguage());
                if (l.getISO3Language().equals(locale.getISO3Language())) {
                    button.setChecked(true);
                }
                group.addView(button);
            }

            builder.setView(group);
            AlertDialog dialog = builder.show();
            group.setOnCheckedChangeListener((a, position) -> {
                Log.d("Settings", "changed locale to " + locales[position].getLanguage());

                Configuration config1 = new Configuration();
                config1.locale = locales[position];

                getResources().updateConfiguration(config1, getResources().getDisplayMetrics());

                finish();
                startActivity(new Intent(getApplicationContext(), this.getClass()));

                dialog.dismiss();
            });
        });
    }

    private void changeNick() {
        EditText et = new EditText(this);
        et.setText(nick);
        et.setSelection(nick.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_nick))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                nick = et.getText().toString();
                if (appData != null) {
                    appData.setUsername(nick);
                    db.updateAppData(appData);
                }
                syncSettings("username", nick);
                initViews();
            })
            .setNegativeButton(getResources().getText(R.string.cancel), null)
            .show();
    }

    private void syncSettings(String what, boolean content) {
        Intent intent = new Intent("settings_changed");
        intent.putExtra("subject", what);
        intent.putExtra(what, content);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void syncSettings(String what, String content) {
        Intent intent = new Intent("settings_changed");
        intent.putExtra("subject", what);
        intent.putExtra(what, content);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initViews();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent intent1 = new Intent("refresh");
        // You can also include some extra data.
        intent1.putExtra("message", "This is my message!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent1);

        Intent intent = new Intent(SettingsActivity.this, ContactListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent);
    }

    private void initViews(){
        ((TextView) findViewById(R.id.nickTv)).setText(nick);
        getLocale();
    }
}
