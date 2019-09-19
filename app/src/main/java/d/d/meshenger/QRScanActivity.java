package d.d.meshenger;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class QRScanActivity extends MeshengerActivity implements BarcodeCallback, ServiceConnection {
    private DecoratedBarcodeView barcodeView;
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_qrscan);
        setTitle(getString(R.string.scan_invited));

        bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);

        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, 1);
        }

        // qr show button
        findViewById(R.id.fabScan).setOnClickListener(view -> {
            startActivity(new Intent(this, QRShowActivity.class));
            finish();
        });

        // manual input button
        findViewById(R.id.fabManualInput).setOnClickListener(view -> {
            startManualInput();
        });
    }

    private void addContact(String data) throws JSONException {
        JSONObject object = new JSONObject(data);
        Contact new_contact = Contact.importJSON(object, false);

        if (new_contact.getAddresses().isEmpty()) {
            Toast.makeText(this, getResources().getString(R.string.contact_has_no_address_warning), Toast.LENGTH_LONG).show();
        }

        // lookup existing contacts by key and name
        Contact existing_pubkey_contact = binder.getContactByPublicKey(new_contact.getPublicKey());
        Contact existing_name_contact = binder.getContactByName(new_contact.getName());

        if (existing_pubkey_contact != null) {
            // contact with that public key exists
            showPubkeyConflictDialog(new_contact, existing_pubkey_contact);
        } else if (existing_name_contact != null) {
            // contact with that name exists
            showNameConflictDialog(new_contact, existing_name_contact);
        } else {
            // no conflict
            binder.addContact(new_contact);
            finish();
        }
    }

    private void showPubkeyConflictDialog(Contact new_contact, Contact other_contact) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_contact_pubkey_conflict);

        TextView nameTextView = dialog.findViewById(R.id.NameTextView);
        Button abortButton = dialog.findViewById(R.id.AbortButton);
        Button replaceButton = dialog.findViewById(R.id.ReplaceButton);

        nameTextView.setText(other_contact.getName());

        replaceButton.setOnClickListener((View v) -> {
            QRScanActivity.this.binder.deleteContact(other_contact.getPublicKey());
            QRScanActivity.this.binder.addContact(new_contact);

            // done
            Toast.makeText(QRScanActivity.this, R.string.done, Toast.LENGTH_SHORT).show();

            dialog.cancel();
            QRScanActivity.this.finish();
        });

        abortButton.setOnClickListener((View v) -> {
            dialog.cancel();
            barcodeView.resume();
        });

        dialog.show();
    }

    private void showNameConflictDialog(Contact new_contact, Contact other_contact) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_add_contact_name_conflict);

        EditText nameEditText = dialog.findViewById(R.id.NameEditText);
        Button abortButton = dialog.findViewById(R.id.AbortButton);
        Button replaceButton = dialog.findViewById(R.id.ReplaceButton);
        Button renameButton = dialog.findViewById(R.id.RenameButton);

        nameEditText.setText(other_contact.getName());

        replaceButton.setOnClickListener((View v) -> {
            QRScanActivity.this.binder.deleteContact(other_contact.getPublicKey());
            QRScanActivity.this.binder.addContact(new_contact);

            // done
            Toast.makeText(QRScanActivity.this, R.string.done, Toast.LENGTH_SHORT).show();

            dialog.cancel();
            QRScanActivity.this.finish();
        });

        renameButton.setOnClickListener((View v) -> {
            String name = nameEditText.getText().toString();

            if (name.isEmpty()) {
                Toast.makeText(this, "Name is empty.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (QRScanActivity.this.binder.getContactByName(name) != null) {
                Toast.makeText(this, "A contact with that name exists already.", Toast.LENGTH_SHORT).show();
                return;
            }

            // rename
            new_contact.setName(name);
            QRScanActivity.this.binder.addContact(new_contact);

            // done
            Toast.makeText(QRScanActivity.this, R.string.done, Toast.LENGTH_SHORT).show();

            dialog.cancel();
            QRScanActivity.this.finish();
        });

        abortButton.setOnClickListener((View v) -> {
            dialog.cancel();
            barcodeView.resume();
        });

        dialog.show();
    }

    private void startManualInput() {
        barcodeView.pause();
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        EditText et = new EditText(this);
        b.setTitle(R.string.paste_invitation)
            .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                try {
                    String data = et.getText().toString();
                    addContact(data);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, (dialog, i) -> {
                dialog.cancel();
                barcodeView.resume();
            })
            .setView(et);
        b.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCamera();
        } else {
            Toast.makeText(this, R.string.camera_permission_request, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        // no more scan until result is processed
        barcodeView.pause();

        try {
            String data = result.getText();
            addContact(data);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.invalid_qr, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
        // ignore
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (barcodeView != null && binder != null) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (barcodeView != null && binder != null) {
            barcodeView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    private void initCamera() {
        barcodeView = findViewById(R.id.barcodeScannerView);

        Collection<BarcodeFormat> formats = Collections.singletonList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.decodeContinuous(this);
        barcodeView.resume();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;

        if (Utils.hasCameraPermission(this)) {
            initCamera();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        binder = null;
    }

    private static void log(String s) {
        Log.d(QRScanActivity.class.getSimpleName(), s);
    }
}
