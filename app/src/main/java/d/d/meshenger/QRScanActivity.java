package d.d.meshenger;

import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
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

        Contact old_pubkey_contact = binder.getContactByPublicKey(new_contact.getPublicKey());
        Contact old_name_contact = binder.getContactByName(new_contact.getName());

        if (old_pubkey_contact != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Contact exists");
            builder.setMessage("Replace contact for " + old_pubkey_contact.getName());
            builder.setPositiveButton(R.string.replace, (DialogInterface dialog, int id) -> {
                binder.deleteContact(old_pubkey_contact.getPublicKey());
                binder.addContact(new_contact);
            });

            builder.show();
        } else if (old_name_contact != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Contact exists");
            builder.setMessage("Replace contact for " + old_name_contact.getName());
            builder.setPositiveButton(R.string.replace, (DialogInterface dialog, int id) -> {
                binder.deleteContact(old_name_contact.getPublicKey());
                binder.addContact(new_contact);
            });

            builder.show();
        } else {
            binder.addContact(new_contact);
        }
    }

    private void startManualInput(){
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
                finish();
            })
            .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel())
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
        try {
            String data = result.getText();
            addContact(data);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.invalid_qr, Toast.LENGTH_LONG).show();
        }

        finish();
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {
        // ignore
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (barcodeView != null && binder != null) {
            barcodeView.pause();
            finish();
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

    private void log(String s) {
        Log.d(QRScanActivity.class.getSimpleName(), s);
    }
}
