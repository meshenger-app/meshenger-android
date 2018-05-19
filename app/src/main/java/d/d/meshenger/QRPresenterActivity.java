package d.d.meshenger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QRPresenterActivity extends AppCompatActivity implements ServiceConnection{
    MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrpresenter);

        setTitle("Scan this code");
        bindService();

        findViewById(R.id.fabPresenter).setOnClickListener(view -> {
            startActivity(new Intent(this, QRScanActivity.class));
            finish();
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("incoming_contact"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(binder != null) {
            unbindService(this);
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    private void bindService(){
        Intent serviceIntent = new Intent(this, MainService.class);

        bindService(serviceIntent, this, Service.BIND_AUTO_CREATE);
    }

    private void generateQR() throws Exception {
        JSONObject object = new JSONObject();

        Log.d(QRPresenterActivity.class.getSimpleName(), "generateQR " + (this.binder == null));

        SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        object.put("username", prefs.getString("username", "Unknown"));
        object.put("address", Utils.getAddress());
        object.put("challenge", this.binder.generateChallenge());
        object.put("identifier", Utils.getMac());

        Log.d(QRPresenterActivity.class.getSimpleName(), object.toString());

        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(object.toString(), BarcodeFormat.QR_CODE,1080,1080);
            //TODO dynamic metrics
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ((ImageView) findViewById(R.id.QRView)).setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    /*private String getAddress() throws Exception{
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wm.getConnectionInfo();
        if(info == null){
            throw new Exception("Device needs to be connected to WIFI");
        }
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        return ip;
    }*/

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.d(QRPresenterActivity.class.getSimpleName(), "onServiceConnected " + (iBinder == null));
        this.binder = (MainService.MainBinder) iBinder;

        try {
            generateQR();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.d(QRPresenterActivity.class.getSimpleName(), "onServiceDisconnected");
        binder = null;
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };
}
