package d.d.meshenger;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class ContactListActivity extends AppCompatActivity implements ServiceConnection, MainService.ContactPingListener, AdapterView.OnItemClickListener {
    private ListView contactListView;

    private boolean fabExpanded = false;

    private FloatingActionButton fabScan, fabGen, fab;

    private MainService.MainBinder mainBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startService(new Intent(this, MainService.class));


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }

        if (!initViews()) {
            return;
        }

        checkInit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver, new IntentFilter("contact_refresh"));

        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    private void checkInit() {
        SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        if (!prefs.contains("username")) {
            showUsernameDialog();
            return;
        }
    }

    private void showUsernameDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Hello!");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView tw = new TextView(this);
        tw.setText("What should be your name?");
        tw.setTextColor(Color.BLACK);
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
        dialog.setNegativeButton("cancel", (dialogInterface, i) -> {
            finish();
        });
        InputMethodManager imm = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        dialog.setPositiveButton("next", (dialogInterface, i) -> {
            getSharedPreferences(getPackageName(), MODE_PRIVATE).edit().putString("username", et.getText().toString()).apply();
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY);
            Intent intent = new Intent("settings_changed");
            intent.putExtra("subject", "username");
            intent.putExtra("username", et.getText().toString());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        });

        AlertDialog finalDialog = dialog.create();
        finalDialog.setOnShowListener((newDialog) -> {
            Button okButton = ((AlertDialog) newDialog).getButton(AlertDialog.BUTTON_POSITIVE);
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

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

    private boolean initViews() {
        SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        if (!prefs.getBoolean("Splash_shown", false)) {
            prefs.edit().putBoolean("Splash_shown", true).apply();
            startActivity(new Intent(this, SplashScreen.class));
            finish();
            return false;
        }

        setContentView(R.layout.activity_contact_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fab = findViewById(R.id.fab);
        fabScan = findViewById(R.id.fabScan1);
        fabGen = findViewById(R.id.fabGenerate1);
        fabScan.setOnClickListener(view -> startActivity(new Intent(ContactListActivity.this, QRScanActivity.class)));
        fabGen.setOnClickListener(view -> startActivity(new Intent(ContactListActivity.this, QRPresenterActivity.class)));
        fab.setOnClickListener(this::runFabAnimation);

        //sqlHelper = new ContactSqlHelper(this);
        contactListView = findViewById(R.id.contactList);

        return true;
    }

    void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshContactList();
        }
    };

    private void runFabAnimation(View fab) {

        AnimationSet scanSet = new AnimationSet(this, null);
        AnimationSet generateSet = new AnimationSet(this, null);

        final int distance = 200;
        final int duration = 300;
        TranslateAnimation scanAnimation;
        TranslateAnimation generateAnimnation;
        AlphaAnimation alphaAnimation;
        if (fabExpanded) {
            scanAnimation = new TranslateAnimation(0, 0, -distance, 0);
            generateAnimnation = new TranslateAnimation(0, 0, -distance * 2, 0);
            alphaAnimation = new AlphaAnimation(1.0f, 0.0f);
            ((FloatingActionButton) fab).setImageResource(R.drawable.qr_glass1);
            fabScan.setY(fabScan.getY() + distance);
            fabGen.setY(fabGen.getY() + distance * 2);
        } else {
            scanAnimation = new TranslateAnimation(0, 0, distance, 0);
            generateAnimnation = new TranslateAnimation(0, 0, distance * 2, 0);
            alphaAnimation = new AlphaAnimation(0.0f, 1.0f);
            ((FloatingActionButton) fab).setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            fabScan.setY(fabScan.getY() - distance);
            fabGen.setY(fabGen.getY() - distance * 2);
        }

        scanSet.addAnimation(scanAnimation);
        scanSet.addAnimation(alphaAnimation);
        scanSet.setFillAfter(true);

        generateSet.addAnimation(generateAnimnation);
        generateSet.addAnimation(alphaAnimation);
        generateSet.setFillAfter(true);

        scanSet.setDuration(duration);
        generateSet.setDuration(duration);

        fabScan.setVisibility(View.VISIBLE);
        fabGen.setVisibility(View.VISIBLE);

        fabScan.startAnimation(scanSet);
        fabGen.startAnimation(generateSet);

        fabExpanded = !fabExpanded;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass1);
            fabScan.clearAnimation();
            fabGen.clearAnimation();
            fabScan.setY(fabScan.getY() + 200);
            fabGen.setY(fabGen.getY() + 200 * 2);
            fabExpanded = false;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver);
        unbindService(this);
    }

    private void refreshContactList() {
        Log.d(ContactListActivity.class.getSimpleName(), "refreshing...");
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                contactListView.setAdapter(new ContactListAdapter(ContactListActivity.this, R.layout.contact_item, mainBinder.getContacts()));
                contactListView.setOnItemClickListener(ContactListActivity.this);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_contact_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        /*if (id == R.id.action_settings) {
            return true;
        }

        if (id == R.id.action_generate_qr) {
            startActivity(new Intent(this, QRPresenterActivity.class));
            return true;
        }

        if (id == R.id.action_scan_qr) {
            startActivity(new Intent(this, QRScanActivity.class));
            return true;
        }*/

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(QRPresenterActivity.class.getSimpleName(), "onServiceConnected()");
        this.mainBinder = (MainService.MainBinder) iBinder;

        //mainBinder.setPingResultListener(this);
        mainBinder.pingContacts(mainBinder.getContacts(), this);

        refreshContactList();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(QRPresenterActivity.class.getSimpleName(), "onServiceDisconnected");
        mainBinder = null;
    }

    @Override
    public void OnContactPingResult(Contact c) {
        refreshContactList();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Contact c = mainBinder.getContacts().get(i);
        Intent intent = new Intent(this, CallActivity.class);
        intent.setAction("ACTION_START_CALL");
        intent.putExtra("EXTRA_CONTACT", c);
        intent.putExtra("EXTRA_IDENTIFIER", mainBinder.getIdentifier());
        intent.putExtra("EXTRA_USERNAME", mainBinder.getUsername());
        startActivity(intent);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, "Microphone permission needed to make calls", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
