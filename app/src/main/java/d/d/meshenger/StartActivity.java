package d.d.meshenger;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;

import java.util.ArrayList;


public class StartActivity extends MeshengerActivity implements ServiceConnection {
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        Typeface type = Typeface.createFromAsset(getAssets(), "rounds_black.otf");
        ((TextView) findViewById(R.id.splashText)).setTypeface(type);

        // start MainService and call back via onServiceConnected()
        startService(new Intent(this, MainService.class));
    }

    void init() {
        this.binder.loadDatabase();
        if (this.binder.getDatabase() == null) {
            showPasswordDialog();
        } else {
            if (this.binder.getSettings().getUsername().isEmpty()) {
                // initialize database
                showUsernameDialog();
            } else {
                conclude();
            }
        }
    }

    private void conclude() {
        // set night mode
        boolean nightMode = this.binder.getSettings().getNightMode();
        AppCompatDelegate.setDefaultNightMode(
            nightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        startActivity(new Intent(this, ContactListActivity.class));
        finish();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;

        // delayed execution of init() to show splash page
        (new Handler()).postDelayed(() -> { init(); }, 1000);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(this);
    }

    private void initializeSettings(String username, ArrayList<String> addresses) {
        // create secret/public key pair
        SodiumAndroid sa = new SodiumAndroid();
        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        byte[] secretKey = new byte[Box.SECRETKEYBYTES];
        sa.crypto_box_keypair(publicKey, secretKey);

        Settings settings = this.binder.getSettings();
        settings.setUsername(username);
        settings.setPublicKey(Utils.byteArrayToHexString(publicKey));
        settings.setSecretKey(Utils.byteArrayToHexString(secretKey));

        for (String address : addresses) {
            settings.addAddress(address);
        }

        try {
            this.binder.saveDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // initial dialog to set the username
    private void showUsernameDialog() {
        TextView tw = new TextView(this);
        tw.setText(R.string.name_prompt);
        //tw.setTextColor(Color.BLACK);
        tw.setTextSize(20);
        tw.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(tw);

        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        et.setSingleLine(true);

        layout.addView(et);
        layout.setPadding(40, 80, 40, 40);
        //layout.setGravity(Gravity.CENTER_HORIZONTAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.hello);
        builder.setView(layout);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            this.binder.shutdown();
            finish();
        });

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        builder.setPositiveButton(R.string.next, (dialogInterface, i) -> {
            // we will override this handler
        });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener((newDialog) -> {
            Button okButton = ((AlertDialog) newDialog).getButton(AlertDialog.BUTTON_POSITIVE);
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // nothing to do
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // nothing to do
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    okButton.setClickable(editable.length() > 0);
                    okButton.setAlpha(editable.length() > 0 ? 1.0f : 0.5f);
                }
            });

            okButton.setClickable(false);
            okButton.setAlpha(0.5f);

            if (et.requestFocus()) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        dialog.show();

        // override handler (to be able to dismiss the dialog manually)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);

            String username = et.getText().toString();
            ArrayList<String> addresses = Utils.getMacAddresses();

            if (addresses.isEmpty()) {
                Toast.makeText(this, "No hardware addresses found. Please enable e.g. WiFi for this step.", Toast.LENGTH_SHORT).show();
            } else {
                initializeSettings(username, addresses);
                // close dialog
                dialog.dismiss();
                conclude();
            }
/*
            Intent intent = new Intent("settings_changed");
            intent.putExtra("subject", "username");
            intent.putExtra("username", username);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
*/
        });
    }

    // ask for database password
    private void showPasswordDialog() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.enter_password))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                // we will override this handler
            })
            .setNegativeButton(getResources().getText(R.string.cancel), (dialogInterface, i) -> {
                this.binder.shutdown();
                finish();
            });

        final AlertDialog dialog = builder.create();
        dialog.show();
        // override handler (to be able to dismiss the dialog manually)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((View v) -> {
            String password = et.getText().toString();
            this.binder.setDatabasePassword(password);
            this.binder.loadDatabase();
            if (this.binder.getDatabase() == null) {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
            } else {
                // close dialog
                dialog.dismiss();
                conclude();
            }
        });
    }

    private void log(String s) {
        Log.d(StartActivity.class.getSimpleName(), s);
    }
}
