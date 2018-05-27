package d.d.meshenger;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class QRScanActivity extends AppCompatActivity implements BarcodeCallback, ServiceConnection{
    private DecoratedBarcodeView barcodeView;
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrscan);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }else {
            bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
        }

        findViewById(R.id.fabScan).setOnClickListener(view -> {
            startActivity(new Intent(this, QRPresenterActivity.class));
            finish();
        });
        findViewById(R.id.fabManualInput).setOnClickListener(view -> {
            startManualInput();
        });
    }

    private void startManualInput(){
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        EditText et = new EditText(this);
        b.setTitle("Paste data here")
                .setPositiveButton("ok", (dialogInterface, i) -> {
                    try {
                        handleJson(et.getText().toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "invalid data", 0).show();
                    }
                    finish();
                })
                .setNegativeButton("cancel", (dialogInterface, i) -> dialogInterface.cancel())
                .setView(et);
        b.show();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
            bindService(new Intent(this, MainService.class), this, Service.BIND_AUTO_CREATE);
        }else{
            Toast.makeText(this, "Camera permission needed to scan Codes", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void barcodeResult(BarcodeResult result) {
        //Toast.makeText(this, result.getText(), 0).show();
        String json = result.getText();
        try {
            handleJson(json);
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "invalid QR-Code", Toast.LENGTH_LONG).show();
        }

        finish();
    }

    private void handleJson(String json) throws JSONException{
        JSONObject object = new JSONObject(json);
        binder.addContact(new Contact(
                object.getString("address"),
                object.getString("username"),
                "",
                object.getString("identifier")
        ), object.getString("challenge"));
    }

    @Override
    public void possibleResultPoints(List<ResultPoint> resultPoints) {

    }

    @Override
    protected void onPause() {
        super.onPause();

        if(barcodeView != null && binder != null) {
            barcodeView.pause();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }



    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(QRScanActivity.class.getSimpleName(), "onServiceConnected " + (iBinder == null));
        this.binder = (MainService.MainBinder) iBinder;

        barcodeView = findViewById(R.id.barcodeScannerView);

        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.QR_CODE);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        barcodeView.decodeContinuous(this);

        barcodeView.resume();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(QRScanActivity.class.getSimpleName(), "onServiceDisconnected");
        binder = null;
    }
}
