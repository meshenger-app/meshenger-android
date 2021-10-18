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
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;

import java.util.List;


// the main view with tabs
public class MainActivity extends MeshengerActivity implements ServiceConnection, AdapterView.OnItemClickListener {
    public MainService.MainBinder binder;
    private ListView contactListView;
    private boolean fabExpanded = false;
    private FloatingActionButton fabScan;
    private FloatingActionButton fabGen;
    private FloatingActionButton fab;
    private BroadcastReceiver refreshContactListReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //refreshContactList();
            ContactListAdapter adapter = (ContactListAdapter) contactListView.getAdapter();
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        log("onItemClick");
        Contact contact = binder.getContactsCopy().get(i);
        Intent intent = new Intent(this, CallActivity.class);
        intent.setAction("ACTION_OUTGOING_CALL");
        intent.putExtra("EXTRA_CONTACT", contact);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log("onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fab = findViewById(R.id.fab);
        fabScan = findViewById(R.id.fabScan);
        fabGen = findViewById(R.id.fabGenerate);
        contactListView = findViewById(R.id.contactList);

        fabScan.setOnClickListener(v -> startActivity(new Intent(this, QRScanActivity.class)));
        fabGen.setOnClickListener(v -> startActivity(new Intent(this, QRShowActivity.class)));
        fab.setOnClickListener(this::runFabAnimation);

        // ask for audio recording permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(refreshContactListReceiver, new IntentFilter("refresh_contact_list"));

    }

    void refreshContactList() {
        log("refreshContactList");

        new Handler(getMainLooper()).post(() -> {
            List<Contact> contacts = binder.getContactsCopy();
            contactListView.setAdapter(new ContactListAdapter(this, R.layout.item_contact, contacts));
            contactListView.setOnItemClickListener(this);
            contactListView.setOnItemLongClickListener((AdapterView<?> adapterView, View view, int i, long l) -> {
                Contact contact = contacts.get(i);
                PopupMenu menu = new PopupMenu(this, view);
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
                    byte[] publicKey = contact.getPublicKey();
                    if (title.equals(delete)) {
                        showDeleteDialog(publicKey, contact.getName());
                    } else if (title.equals(rename)) {
                        showContactEditDialog(publicKey, contact.getName());
                    } else if (title.equals(share)) {
                        shareContact(contact);
                    } else if (title.equals(block)) {
                        setBlocked(publicKey, true);
                    } else if (title.equals(unblock)) {
                        setBlocked(publicKey, false);
                    } else if (title.equals(qr)) {
                        Intent intent = new Intent(this, QRShowActivity.class);
                        intent.putExtra("EXTRA_CONTACT", contact);
                        startActivity(intent);
                    }
                    return false;
                });
                menu.show();
                return true;
            });
        });
    }

    private void showDeleteDialog(byte[] publicKey, String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.confirm);
        builder.setMessage(getResources().getString(R.string.contact_remove) + " " + name);
        builder.setCancelable(false); // prevent key shortcut to cancel dialog

        builder.setPositiveButton(R.string.yes, (DialogInterface dialog, int id) -> {
            binder.deleteContact(publicKey);
            dialog.cancel();
        });

        builder.setNegativeButton(R.string.no, (DialogInterface dialog, int id) -> {
            dialog.cancel();
        });

        // create dialog box
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void setBlocked(byte[] publicKey, boolean blocked) {
        Contact contact = binder.getContactByPublicKey(publicKey);
        if (contact != null) {
            contact.setBlocked(blocked);
            binder.addContact(contact);
        }
    }

    private void shareContact(Contact c) {
        log("shareContact");
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, Contact.exportJSON(c, false).toString());
            startActivity(intent);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showContactEditDialog(byte[] publicKey, String name) {
        log("showContactEditDialog");
        EditText et = new EditText(this);
        et.setText(name);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.contact_edit)
                .setView(et)
                .setNegativeButton(getResources().getString(R.string.cancel), null)
                .setPositiveButton(R.string.ok, (DialogInterface dialogInterface, int i) -> {
                    String newName = et.getText().toString();
                    if (newName.length() > 0) {
                        Contact contact = binder.getContactByPublicKey(publicKey);
                        if (contact != null) {
                            contact.setName(newName);
                            binder.addContact(contact);
                        }
                    }
                }).show();
    }

    private void runFabAnimation(View fab) {
        log("runFabAnimation");
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

        fabScan.show();
        fabGen.show();

        fabScan.startAnimation(scanSet);
        fabGen.startAnimation(generateSet);

        fabExpanded = !fabExpanded;
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshContactListReceiver);
        ((AudioManager) this.getSystemService(Context.AUDIO_SERVICE)).setSpeakerphoneOn(false);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        log("OnServiceConnected");
        this.binder = (MainService.MainBinder) iBinder;
        refreshContactList();
        // call it here because EventListFragment.onResume is triggered twice
        this.binder.pingContacts();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        log("OnServiceDisconnected");
        this.binder = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        log("onOptionsItemSelected");
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                break;
            }
            case R.id.action_backup: {
                //startActivity(new Intent(this, BackupActivity.class));
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
    protected void onResume() {
        log("OnResume");
        super.onResume();

        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        log("onPause");
        super.onPause();
        collapseFab();
        unbindService(this);
    }

    private void collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass);
            fabScan.clearAnimation();
            fabGen.clearAnimation();
            fabScan.setY(fabScan.getY() + 200);
            fabGen.setY(fabGen.getY() + 200 * 2);
            fabExpanded = false;
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        log("onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
