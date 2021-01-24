package d.d.meshenger;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.List;


public class SettingsActivity extends MeshengerActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        setTitle(getResources().getString(R.string.menu_settings));

        initViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private boolean getIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pMgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            return pMgr.isIgnoringBatteryOptimizations(this.getPackageName());
        }
        return false;
    }

    private void initViews() {
        Settings settings = MainService.instance.getSettings();

        findViewById(R.id.nameLayout).setOnClickListener((View view) -> {
            showChangeNameDialog();
        });

        findViewById(R.id.addressLayout).setOnClickListener((View view) -> {
            Intent intent = new Intent(this, AddressActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.passwordLayout).setOnClickListener((View view) -> {
            showChangePasswordDialog();
        });

        findViewById(R.id.iceServersLayout).setOnClickListener((View view) -> {
            showChangeIceServersDialog();
        });

        String username = settings.getUsername();
        ((TextView) findViewById(R.id.nameTv)).setText(
            username.length() == 0 ? getResources().getString(R.string.none) : username
        );

        List<String> addresses = settings.getAddresses();
        ((TextView) findViewById(R.id.addressTv)).setText(
            addresses.size() == 0 ? getResources().getString(R.string.none) : Utils.join(addresses)
        );

        String password = MainService.instance.getDatabasePassword();
        ((TextView) findViewById(R.id.passwordTv)).setText(
            password.isEmpty() ? getResources().getString(R.string.none) : "********"
        );

        List<String> iceServers = settings.getIceServers();
        ((TextView) findViewById(R.id.iceServersTv)).setText(
            iceServers.isEmpty() ? getResources().getString(R.string.none) : Utils.join(iceServers)
        );

        boolean blockUnknown = settings.getBlockUnknown();
        CheckBox blockUnknownCB = findViewById(R.id.checkBoxBlockUnknown);
        blockUnknownCB.setChecked(blockUnknown);
        blockUnknownCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setBlockUnknown(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean nightMode = MainService.instance.getSettings().getNightMode();
        CheckBox nightModeCB = findViewById(R.id.checkBoxNightMode);
        nightModeCB.setChecked(nightMode);
        nightModeCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // apply value
            AppCompatDelegate.setDefaultNightMode(
                isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );

            // save value
            settings.setNightMode(isChecked);
            MainService.instance.saveDatabase();

            // apply theme
            SettingsActivity.this.recreate();
        });

        boolean recordAudio = settings.getRecordAudio();
        CheckBox recordAudioCB = findViewById(R.id.checkBoxSendAudio);
        recordAudioCB.setChecked(recordAudio);
        recordAudioCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setRecordAudio(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean playAudio = settings.getPlayAudio();
        CheckBox playAudioCB = findViewById(R.id.checkBoxPlayAudio);
        playAudioCB.setChecked(playAudio);
        playAudioCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setPlayAudio(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean recordVideo = settings.getRecordVideo();
        CheckBox recordVideoCB = findViewById(R.id.checkBoxRecordVideo);
        recordVideoCB.setChecked(recordVideo);
        recordVideoCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setRecordVideo(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean playVideo = settings.getPlayVideo();
        CheckBox playVideoCB = findViewById(R.id.checkBoxPlayVideo);
        playVideoCB.setChecked(playVideo);
        playVideoCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setPlayVideo(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean autoAcceptCall = settings.getAutoAcceptCall();
        CheckBox autoAcceptCallCB = findViewById(R.id.checkBoxAutoAcceptCall);
        autoAcceptCallCB.setChecked(autoAcceptCall);
        autoAcceptCallCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setAutoAcceptCall(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean autoConnectCall = settings.getAutoConnectCall();
        CheckBox autoConnectCallCB = findViewById(R.id.checkBoxAutoConnectCall);
        autoConnectCallCB.setChecked(autoConnectCall);
        autoConnectCallCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // save value
            settings.setAutoConnectCall(isChecked);
            MainService.instance.saveDatabase();
        });

        boolean ignoreBatteryOptimizations = getIgnoreBatteryOptimizations();
        CheckBox ignoreBatteryOptimizationsCB = findViewById(R.id.checkBoxIgnoreBatteryOptimizations);
        ignoreBatteryOptimizationsCB.setChecked(ignoreBatteryOptimizations);
        ignoreBatteryOptimizationsCB.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            // Only required for Android 6 or later
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + this.getPackageName()));
                this.startActivity(intent);
            }
        });

        setupSpinner(settings.getSettingsMode(), R.id.spinnerSettingsMode, R.array.settingsMode, R.array.settingsModeValues, (newSettingsMode) -> {
            settings.setSettingsMode(newSettingsMode);
            MainService.instance.saveDatabase();
            applySettingsMode(newSettingsMode);
        });

        setupSpinner(settings.getVideoCodec(), R.id.spinnerVideoCodecs, R.array.videoCodecs, R.array.videoCodecs, (newVideoCodec) -> {
            settings.setVideoCodec(newVideoCodec);
            MainService.instance.saveDatabase();
        });

        setupSpinner(settings.getAudioCodec(), R.id.spinnerAudioCodecs, R.array.audioCodecs, R.array.audioCodecs, (newAudioCodec) -> {
            settings.setAudioCodec(newAudioCodec);
            MainService.instance.saveDatabase();
        });

        setupSpinner(settings.getVideoResolution(), R.id.spinnerVideoResolutions, R.array.videoResolutions, R.array.videoResolutionsValues, (newVideoResolution) -> {
            settings.setAudioCodec(newVideoResolution);
            MainService.instance.saveDatabase();
        });

        setupSpinner(settings.getSpeakerphone(), R.id.spinnerSpeakerphone, R.array.speakerphone, R.array.speakerphoneValues, (newSpeakerphone) -> {
            settings.setSpeakerphone(newSpeakerphone);
            MainService.instance.saveDatabase();
        });

        applySettingsMode(settings.getSettingsMode());
    }

    private interface SpinnerItemSelected {
        void call(String newValue);
    }

    // allow for a customized spinner
    private void setupSpinner(String settingsMode, int spinnerId, int entriesId, int entryValuesId, SpinnerItemSelected callback) {
        Spinner spinner = findViewById(spinnerId);

        ArrayAdapter spinnerAdapter = ArrayAdapter.createFromResource(this, entriesId, R.layout.spinner_item_settings);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings);
        spinner.setAdapter(spinnerAdapter);

        spinner.setSelection(((ArrayAdapter<CharSequence>) spinner.getAdapter()).getPosition(settingsMode));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int check = 0;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (check++ > 0) {
                    final TypedArray selectedValues = getResources().obtainTypedArray(entryValuesId);
                    final String settingsMode = selectedValues.getString(pos);
                    callback.call(settingsMode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // ignore
            }
        });
    }

    private void applySettingsMode(String settingsMode) {
        switch (settingsMode) {
            case "basic":
                findViewById(R.id.basicSettingsLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.advancedSettingsLayout).setVisibility(View.INVISIBLE);
                findViewById(R.id.expertSettingsLayout).setVisibility(View.INVISIBLE);
                break;
            case "advanced":
                findViewById(R.id.basicSettingsLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.advancedSettingsLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.expertSettingsLayout).setVisibility(View.INVISIBLE);
                break;
            case "expert":
                findViewById(R.id.basicSettingsLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.advancedSettingsLayout).setVisibility(View.VISIBLE);
                findViewById(R.id.expertSettingsLayout).setVisibility(View.VISIBLE);
                break;
            default:
                Log.e(TAG, "Invalid settings mode: " + settingsMode);
                break;
        }
    }

    private void showChangeNameDialog() {
        Settings settings = MainService.instance.getSettings();
        String username = settings.getUsername();
        EditText et = new EditText(this);
        et.setText(username);
        et.setSelection(username.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                String new_username = et.getText().toString().trim();
                if (Utils.isValidContactName(new_username)) {
                    settings.setUsername(new_username);
                    MainService.instance.saveDatabase();
                    initViews();
                } else {
                    Toast.makeText(this, getResources().getString(R.string.invalid_name), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(getResources().getText(R.string.cancel), null)
            .show();
    }

    private void showChangePasswordDialog() {
        String password = MainService.instance.getDatabasePassword();
        EditText et = new EditText(this);
        et.setText(password);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setSelection(password.length());
        new AlertDialog.Builder(this)
            .setTitle(getResources().getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                String new_password = et.getText().toString();
                MainService.instance.setDatabasePassword(new_password);
                MainService.instance.saveDatabase();
                initViews();
            })
            .setNegativeButton(getResources().getText(R.string.cancel), null)
            .show();
    }

    private void showChangeIceServersDialog() {
        Settings settings = MainService.instance.getSettings();

        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_set_ice_server);

        TextView iceServersTextView = dialog.findViewById(R.id.iceServersEditText);
        Button saveButton = dialog.findViewById(R.id.SaveButton);
        Button abortButton = dialog.findViewById(R.id.AbortButton);

        iceServersTextView.setText(Utils.join(settings.getIceServers()));

        saveButton.setOnClickListener((View v) -> {
            List<String> iceServers = Utils.split(iceServersTextView.getText().toString());
            settings.setIceServers(iceServers);

            // done
            Toast.makeText(SettingsActivity.this, R.string.done, Toast.LENGTH_SHORT).show();

            dialog.cancel();
        });

        abortButton.setOnClickListener((View v) -> {
            dialog.cancel();
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initViews();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent);
    }
}
