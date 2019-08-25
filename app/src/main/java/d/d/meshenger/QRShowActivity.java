package d.d.meshenger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
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


public class QRShowActivity extends MeshengerActivity implements ServiceConnection {
    private Contact contact = null;
    private MainService.MainBinder binder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrshow);

        if (getIntent().hasExtra("EXTRA_CONTACT")) {
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
            if (this.contact != null) try {
                String data = Contact.exportJSON(this.contact, false).toString();
                Intent i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_TEXT, data);
                i.setType("text/plain");
                startActivity(i);
                finish();
            } catch (Exception e) {
                // ignore
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("incoming_contact"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.binder != null) {
            unbindService(this);
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
    }

    private void bindService() {
        Intent serviceIntent = new Intent(this, MainService.class);
        bindService(serviceIntent, this, Service.BIND_AUTO_CREATE);
    }

    private void generateQR() throws Exception {
        if (this.contact == null) {
            // export own contact
            this.contact = this.binder.getSettings().getOwnContact();
        }

        String data = Contact.exportJSON(this.contact, false).toString();
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        BitMatrix bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
        ((ImageView) findViewById(R.id.QRView)).setImageBitmap(bitmap);

        if (this.contact.getAddresses().isEmpty()) {
            Toast.makeText(this, getResources().getString(R.string.contact_has_no_address_warning), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
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
        this.binder = null;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private void log(String s) {
        Log.d(ContactListActivity.class.getSimpleName(), s);
    }
}
