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
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONException;
import org.json.JSONObject;

public class QRPresenterActivity extends MeshengerActivity implements ServiceConnection{
    private MainService.MainBinder binder;
    private String json;

    String identifier1;

    private ContactSqlHelper sqlHelper;
    AppData appData;

    private Contact contact = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrpresenter);

        if(getIntent().hasExtra("EXTRA_CONTACT")){
            this.contact = (Contact) getIntent().getExtras().get("EXTRA_CONTACT");
            findViewById(R.id.fabPresenter).setVisibility(View.GONE);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
            params.bottomMargin = params.rightMargin = 80;
            findViewById(R.id.fabShare).setLayoutParams(params);
        }

        setTitle(getString(R.string.scan_invitation));
        bindService();

        findViewById(R.id.fabPresenter).setOnClickListener(view -> {
            startActivity(new Intent(this, QRScanActivity.class));
            finish();
        });


        findViewById(R.id.fabShare).setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_TEXT, json);
            i.setType("text/plain");
            startActivity(i);
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
        json = generateJson();

        Log.d(QRPresenterActivity.class.getSimpleName(), json);

        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(json, BarcodeFormat.QR_CODE,1080,1080);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ((ImageView) findViewById(R.id.QRView)).setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private String generateJson() throws JSONException{
        JSONObject object = new JSONObject();

        if(this.contact != null){
            object.put("address", contact.getAddress());
            object.put("identifier", contact.getIdentifier());
            object.put("pubKey", contact.getPubKey());    //CORRECT
            object.put("username", contact.getName());
            object.put("challenge", this.binder.generateChallenge());
            return object.toString();
        }

       // SharedPreferences prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);
       // object.put("username", prefs.getString("username", "Unknown"));
        sqlHelper = new ContactSqlHelper(this);
        appData= sqlHelper.getAppData();
        if(appData==null){
            new AppData();
        }
        object.put("username", sqlHelper.getAppData().getUsername());
        String address = Utils.getLinkLocalAddress();
        if(address == null){
            Toast.makeText(this, R.string.network_connect_invitation, Toast.LENGTH_LONG).show();
            finish();
        }
        object.put("address", address);
        object.put("publicKey", sqlHelper.getAppData().getPublicKey());          //correct
        object.put("challenge", this.binder.generateChallenge());

        if(appData!=null) {
            identifier1 = (Utils.formatAddress(Utils.getMacAddress()));
            appData.setIdentifier1(identifier1);
            sqlHelper.updateAppData(appData);
        }
        object.put("identifier", sqlHelper.getAppData().getIdentifier1());
        return object.toString();
    }

    /*private String getLinkLocalAddress() throws Exception{
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

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };
}
