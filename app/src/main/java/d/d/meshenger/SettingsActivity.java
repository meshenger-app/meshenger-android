package d.d.meshenger;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatDelegate;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;


public class SettingsActivity extends MeshengerActivity implements ServiceConnection {
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        setTitle(getResources().getString(R.string.menu_settings));

        bindService();
        initViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.binder != null) {
            unbindService(this);
        }
    }

    private void bindService() {
        // ask MainService to get us the binder object
        Intent serviceIntent = new Intent(this, MainService.class);
        bindService(serviceIntent, this, Service.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;
        initViews();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    private void initViews() {
        if (this.binder == null) {
            return;
        }

        findViewById(R.id.changeNameLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChangeNameDialog();
            }
        });

        findViewById(R.id.changeAddressLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChangeAddressDialog();
            }
        });

        findViewById(R.id.changePasswordLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChangePasswordDialog();
            }
        });

        String username = this.binder.getSettings().getUsername();
        ((TextView) findViewById(R.id.nameTv)).setText(
            username.length() == 0 ? "None" : username
        );

        ArrayList<String> addresses = this.binder.getSettings().getAddresses();
        ((TextView) findViewById(R.id.addressTv)).setText(
            addresses.size() == 0 ? "None" : Utils.join(addresses)
        );

        String password = this.binder.getDatabasePassword();
        ((TextView) findViewById(R.id.passwordTv)).setText(
            password.isEmpty() ? "None" : "********"
        );

        CheckBox ignoreCB = findViewById(R.id.checkBoxIgnoreUnsaved);
        ignoreCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            this.binder.getSettings().setBlockUnknown(isChecked);
            //this.db.updateSettings(settings);

            syncSettings("ignoreUnsaved", isChecked);
        });

        boolean nightMode = this.binder.getSettings().getNightMode();
        CheckBox nightModeCB = findViewById(R.id.checkBoxNightMode);
        nightModeCB.setChecked(nightMode);
        nightModeCB.setOnCheckedChangeListener((nightModeCheckBox, b) -> {
            boolean checked = nightModeCheckBox.isChecked();

            // apply value
            AppCompatDelegate.setDefaultNightMode(
                checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );

            // save value
            this.binder.getSettings().setNightMode(checked);
            this.binder.saveDatabase();

            Intent intent = new Intent(SettingsActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        getLocale();
    }

    private ArrayList<String> getInvalidAddresses(ArrayList<String> addresses) {
        ArrayList<String> invalid_addresses = new ArrayList<>();

        for (String address : addresses) {
            try {
                if (Utils.isMAC(address)) {
                    // ok - ignore
                } else {
                    Utils.parseInetSocketAddress(address, MainService.serverPort);
                }
            } catch (Exception e) {
                invalid_addresses.add(address);
            }
        }

        return invalid_addresses;
    }

    private void getLocale() {
        Configuration config = getResources().getConfiguration();
        Locale locale = config.locale;
        ((TextView) findViewById(R.id.localeTv)).setText(locale.getDisplayLanguage());

        this.binder.getSettings().setLanguage(locale.getDisplayLanguage());
        this.binder.saveDatabase();

        Locale[] locales = new Locale[]{Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN};
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
                log("changed locale to " + locales[position].getLanguage());

                Configuration config1 = new Configuration();
                config1.locale = locales[position];

                getResources().updateConfiguration(config1, getResources().getDisplayMetrics());

                finish();
                startActivity(new Intent(getApplicationContext(), this.getClass()));

                dialog.dismiss();
            });
        });
    }

    private void showChangeNameDialog() {
        String username = this.binder.getSettings().getUsername();
        EditText et = new EditText(this);
        et.setText(username);
        et.setSelection(username.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                String new_username = et.getText().toString();
                this.binder.getSettings().setUsername(new_username);
                this.binder.saveDatabase();
                syncSettings("username", new_username);
                initViews();
            })
            .setNegativeButton(getResources().getText(R.string.cancel), null)
            .show();
    }

    private void showChangeAddressDialog() {
        String addresses_string = Utils.join(this.binder.getSettings().getAddresses());
        EditText et = new EditText(this);
        et.setText(addresses_string);
        et.setSelection(addresses_string.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_address))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                ArrayList<String> new_addresses = Utils.split(et.getText().toString());
                ArrayList<String> invalid_addresses = getInvalidAddresses(new_addresses);
                if (invalid_addresses.isEmpty()) {
                    this.binder.getSettings().setAddresses(new_addresses);
                    this.binder.saveDatabase();
                    syncSettings("address", Utils.join(new_addresses)); //needed?
                } else {
                    // show invalid addresses
                    for (String address : invalid_addresses) {
                        Toast.makeText(this, getResources().getString(R.string.invalid_address) + ": " + address, Toast.LENGTH_LONG).show();
                    }
                }
                initViews();
            })
            .setNegativeButton(getResources().getText(R.string.cancel), null)
            .show();
    }

    private void showChangePasswordDialog() {
        String password = this.binder.getDatabasePassword();
        EditText et = new EditText(this);
        et.setText(password);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSelection(password.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                String new_password = et.getText().toString();
                this.binder.setDatabasePassword(new_password);
                this.binder.saveDatabase();
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
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent1);

        Intent intent2 = new Intent(SettingsActivity.this, ContactListActivity.class);
        intent2.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent2);
    }

    private void log(String s) {
        Log.d(SettingsActivity.class.getSimpleName(), s);
    }
}
