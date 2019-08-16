package d.d.meshenger;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;


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
        startActivity(new Intent(this, ContactListActivity.class));
        finish();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        log("onServiceConnected()");
        this.binder = (MainService.MainBinder) iBinder;
        init();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        log("onServiceDisconnected");
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

    private void initializeSettings(String username) {
        // create key pair
        SodiumAndroid sa = new SodiumAndroid();
        byte[] publicKey = new byte[Box.PUBLICKEYBYTES];
        byte[] secretKey = new byte[Box.SECRETKEYBYTES];
        sa.crypto_box_keypair(publicKey, secretKey);

        Settings settings = this.binder.getSettings();
        settings.setUsername(username);
        settings.setPublicKey(Utils.byteArrayToHexString(publicKey));
        settings.setSecretKey(Utils.byteArrayToHexString(secretKey));

        for (String mac : Utils.getMacAddresses()) {
            settings.addAddress(mac);
        }

        try {
            this.binder.saveDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // initial dialog to set the username
    private void showUsernameDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.hello);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView tw = new TextView(this);
        tw.setText(R.string.name_prompt);
        //tw.setTextColor(Color.BLACK);
        tw.setTextSize(20);
        tw.setGravity(Gravity.CENTER_HORIZONTAL);

        layout.addView(tw);

        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        et.setSingleLine(true);

        layout.addView(et);
        layout.setPadding(40, 80, 40, 40);
        //layout.setGravity(Gravity.CENTER_HORIZONTAL);

        dialog.setView(layout);
        dialog.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            finish();
        });

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        dialog.setPositiveButton(R.string.next, (dialogInterface, i) -> {
            String username = et.getText().toString();
            initializeSettings(username);

            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
/*
            Intent intent = new Intent("settings_changed");
            intent.putExtra("subject", "username");
            intent.putExtra("username", username);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
*/
            conclude();
        });

        AlertDialog finalDialog = dialog.create();
        finalDialog.setOnShowListener((newDialog) -> {
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

        finalDialog.show();
    }

    // ask for database password
    private void showPasswordDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getResources().getString(R.string.password));
        EditText et = new EditText(this);
        //et.setText(password);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        //et.setSelection(password.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.enter_password))
            .setView(et)
            .setPositiveButton("ok", (dialogInterface, i) -> {
                String password = et.getText().toString();
                this.binder.setDatabasePassword(password);
                this.binder.loadDatabase();
                if (this.binder.getDatabase() == null) {
                    Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
                    // do nothing
                } else {
                    conclude();
                }
            })
            .setNegativeButton(getResources().getText(R.string.cancel), (dialogInterface, i) -> {
                // quit app
                finish();
            })
            .show();
    }

    private void log(String s) {
        Log.d(StartActivity.class.getSimpleName(), s);
    }
}
