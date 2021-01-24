package d.d.meshenger;

import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.NaCl;

/*
 * Show splash screen, name setup dialog, database password dialog and
 * start background service before starting the MainActivity.
 */
public class StartActivity extends MeshengerActivity implements ServiceConnection {
    private static final String TAG = "StartActivity";
    private int startState = 0;
    private static Sodium sodium;
    private static final int IGNORE_BATTERY_OPTIMIZATION_REQUEST = 5223;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_splash);

        // load libsodium for JNI access
        this.sodium = NaCl.sodium();

        Typeface type = Typeface.createFromAsset(getAssets(), "rounds_black.otf");
        ((TextView) findViewById(R.id.splashText)).setTypeface(type);

        // start MainService and call back via onServiceConnected()
        MainService.start(this);

        ComponentName myService = startService(new Intent(this, MainService.class));
        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    private void continueInit() {
        this.startState += 1;
        switch (this.startState) {
            case 1:
                Log.d(TAG, "init 1: load database");
                // open without password
                MainService.instance.loadDatabase();
                continueInit();
                break;
            case 2:
                Log.d(TAG, "init 2: check database");
                if (MainService.instance.getDatabase() == null) {
                    // database is probably encrypted
                    showDatabasePasswordDialog();
                } else {
                    continueInit();
                }
                break;
            case 3:
                Log.d(TAG, "init 3: check username");
                if (MainService.instance.getSettings().getUsername().isEmpty()) {
                    // set username
                    showMissingUsernameDialog();
                } else {
                    continueInit();
                }
                break;
            case 4:
                Log.d(TAG, "init 4: check key pair");
                if (MainService.instance.getSettings().getPublicKey() == null) {
                    // generate key pair
                    initKeyPair();
                }
                continueInit();
                break;
            case 5:
                Log.d(TAG, "init 5: check addresses");
                if (MainService.instance.isFirstStart()) {
                    showMissingAddressDialog();
                } else {
                    continueInit();
                }
                break;
            case 6:
                Log.d(TAG, "init 6: battery optimizations");
                if (MainService.instance.isFirstStart() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        PowerManager pMgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
                        if (!pMgr.isIgnoringBatteryOptimizations(this.getPackageName())) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + this.getPackageName()));
                            startActivityForResult(intent, IGNORE_BATTERY_OPTIMIZATION_REQUEST);
                            break;
                        }
                    } catch(Exception e) {
                        // ignore
                    }
                }
                continueInit();
                break;
            case 7:
               Log.d(TAG, "init 7: start contact list");
                // set night mode
                boolean nightMode = MainService.instance.getSettings().getNightMode();
                AppCompatDelegate.setDefaultNightMode(
                        nightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                );

                // all done - show contact list
                startActivity(new Intent(this, MainActivity.class));
                finish();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IGNORE_BATTERY_OPTIMIZATION_REQUEST) {
            // resultCode: -1 (Allow), 0 (Deny)
            continueInit();
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        //this.binder = (MainService.MainBinder) iBinder;
        Log.d(TAG, "onServiceConnected");

        if (this.startState == 0) {
            if (MainService.instance.isFirstStart()) {
                // show delayed splash page
                (new Handler()).postDelayed(() -> {
                    continueInit();
                }, 1000);
            } else {
                // show contact list as fast as possible
                continueInit();
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        //this.binder = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    private void initKeyPair() {
        // create secret/public key pair
        final byte[] publicKey = new byte[Sodium.crypto_sign_publickeybytes()];
        final byte[] secretKey = new byte[Sodium.crypto_sign_secretkeybytes()];

        Sodium.crypto_sign_keypair(publicKey, secretKey);

        Settings settings = MainService.instance.getSettings();
        settings.setPublicKey(publicKey);
        settings.setSecretKey(secretKey);

        try {
            MainService.instance.saveDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // for the database initialization
    private String getMacOfDevice(String device) {
        for (AddressEntry ae : AddressUtils.getOwnAddresses()) {
            // only MAC addresses
            if (ae.device.equals("wlan0") && Utils.isMAC(ae.address)) {
                return ae.address;
            }
        }
        return "";
    }

    private void showMissingAddressDialog() {
        String mac = getMacOfDevice("wlan0");

        if (mac.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setTitle(R.string.setup_address_title);
            builder.setMessage(R.string.setup_address_message);

            builder.setPositiveButton(R.string.ok, (DialogInterface dialog, int id) -> {
                showMissingAddressDialog();
                dialog.cancel();
            });

            builder.setNegativeButton(R.string.skip, (DialogInterface dialog, int id) -> {
                dialog.cancel();
                // continue with out address configuration
                continueInit();
            });

            builder.show();
        } else {
            MainService.instance.getSettings().addAddress(mac);
            MainService.instance.saveDatabase();

            continueInit();
        }
    }

    // initial dialog to set the username
    private void showMissingUsernameDialog() {
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
            this.stopService(new Intent(this, MainService.class));
            finish();
        });

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        builder.setPositiveButton(R.string.next, (dialogInterface, i) -> {
            // we will override this handler
        });

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
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
            String username = et.getText().toString().trim();
            if (Utils.isValidContactName(username)) {
                MainService.instance.getSettings().setUsername(username);
                MainService.instance.saveDatabase();

                // close dialog
                dialog.dismiss();
                //dialog.cancel(); // needed?
                continueInit();
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ask for database password
    private void showDatabasePasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_database_password);
        dialog.setCancelable(false);

        EditText passwordEditText = dialog.findViewById(R.id.PasswordEditText);
        Button exitButton = dialog.findViewById(R.id.ExitButton);
        Button okButton = dialog.findViewById(R.id.OkButton);

        okButton.setOnClickListener((View v) -> {
            String password = passwordEditText.getText().toString();
            MainService.instance.setDatabasePassword(password);
            MainService.instance.loadDatabase();

            if (MainService.instance.getDatabase() == null) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            } else {
                // close dialog
                dialog.dismiss();
                continueInit();
            }
        });

        exitButton.setOnClickListener((View v) -> {
            // shutdown app
            dialog.dismiss();
            this.stopService(new Intent(this, MainService.class));
            finish();
        });

        dialog.show();
    }
}
