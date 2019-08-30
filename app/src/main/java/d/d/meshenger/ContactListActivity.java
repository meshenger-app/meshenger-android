package d.d.meshenger;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import org.json.JSONException;

import java.util.List;


public class ContactListActivity extends MeshengerActivity implements ServiceConnection,
        MainService.ContactPingListener, AdapterView.OnItemClickListener {
    private ListView contactListView;
    private boolean fabExpanded = false;
    private FloatingActionButton fabScan;
    private FloatingActionButton fabGen;
    private FloatingActionButton fab;
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_list);
    }

    private void initViews() {
        if (this.binder == null) {
            return;
        }

        fab = findViewById(R.id.fab);
        fabScan = findViewById(R.id.fabScan1);
        fabGen = findViewById(R.id.fabGenerate1);
        contactListView = findViewById(R.id.contactList);

        fabScan.setOnClickListener(view -> startActivity(new Intent(ContactListActivity.this, QRScanActivity.class)));
        fabGen.setOnClickListener(view -> startActivity(new Intent(ContactListActivity.this, QRShowActivity.class)));
        fab.setOnClickListener(this::runFabAnimation);

        // ask for audio recording permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver, new IntentFilter("contact_refresh"));
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("refresh"));
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent intent1 = new Intent(ContactListActivity.this, ContactListActivity.class);
            startActivity(intent1);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver);

        super.onDestroy();
    }

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
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
            ((FloatingActionButton) fab).setImageResource(R.drawable.qr_glass);
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
            fab.setImageResource(R.drawable.qr_glass);
            fabScan.clearAnimation();
            fabGen.clearAnimation();
            fabScan.setY(fabScan.getY() + 200);
            fabGen.setY(fabGen.getY() + 200 * 2);
            fabExpanded = false;
        }
        unbindService(this);
    }

    private void showDeleteDialog(Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.confirm);
        builder.setMessage("Remove contact: " + contact.getName());
        builder.setCancelable(false); // prevent key shortcut to cancel dialog

        builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
            binder.deleteContact(contact.getPublicKey());
            refreshContactList();
            dialog.cancel();
        });

        builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
            dialog.cancel();
        });

        // create dialog box
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void refreshContactList() {
        if (this.binder == null || contactListView == null) {
            return;
        }

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                List<Contact> contacts = binder.getContacts();
                contactListView.setAdapter(new ContactListAdapter(ContactListActivity.this, R.layout.contact_item, contacts));
                contactListView.setOnItemClickListener(ContactListActivity.this);
                contactListView.setOnItemLongClickListener((AdapterView<?> adapterView, View view, int i, long l) -> {
                    Contact contact = contacts.get(i);
                    PopupMenu menu = new PopupMenu(ContactListActivity.this, view);
                    Resources res = getResources();
                    String delete = res.getString(R.string.delete);
                    String rename = res.getString(R.string.rename);
                    String block = res.getString(R.string.block);
                    String unblock = res.getString(R.string.unblock);
                    String share = res.getString(R.string.share);
                    String qr = "QR-ify";
                    menu.getMenu().add(delete);
                    menu.getMenu().add(rename);
                    menu.getMenu().add(share);
                    if (contact.getBlocked()) {
                        menu.getMenu().add(unblock);
                    } else {
                        menu.getMenu().add(block);
                    }
                    menu.getMenu().add(qr);
                    menu.setOnMenuItemClickListener((MenuItem menuItem) -> {
                        String title = menuItem.getTitle().toString();
                        if (title.equals(delete)) {
                            showDeleteDialog(contact);
                        } else if (title.equals(rename)) {
                            showContactEditDialog(contact);
                        } else if (title.equals(share)) {
                            shareContact(contact);
                        } else if (title.equals(block)) {
                            contact.setBlocked(true);
                        } else if (title.equals(unblock)) {
                            contact.setBlocked(false);
                        } else if (title.equals(qr)) {
                            Intent intent = new Intent(ContactListActivity.this, QRShowActivity.class);
                            intent.putExtra("EXTRA_CONTACT", contact);
                            startActivity(intent);
                        }
                        return false;
                    });
                    menu.show();
                    return true;
                });
            }
        });
    }

    private void shareContact(Contact c) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, Contact.exportJSON(c, false).toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showContactEditDialog(Contact contact) {
        EditText et = new EditText(this);
        et.setText(contact.getName());
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(getResources().getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok, (DialogInterface dialogInterface, int i) -> {
                String name = et.getText().toString();
                if (name.length() > 0) {
                    contact.setName(name);
                    refreshContactList();
                }
            }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_contact_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings: {
                Intent intent = new Intent(ContactListActivity.this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                break;
            }
            case R.id.action_backup: {
                startActivity(new Intent(this, BackupActivity.class));
                break;
            }
            case R.id.action_about: {
                startActivity(new Intent(this, AboutActivity.class));
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;

        //this.binder.setPingResultListener(this);
        this.binder.pingContacts(this);

        initViews();
        refreshContactList();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    @Override
    public void onContactPingResult(Contact c) {
        refreshContactList();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Contact contact = this.binder.getContacts().get(i);
        Intent intent = new Intent(this, CallActivity.class);
        intent.setAction("ACTION_START_CALL");
        intent.putExtra("EXTRA_CONTACT", contact);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity();
    }

    private void log(String s) {
        Log.d(ContactListActivity.class.getSimpleName(), s);
    }
}
