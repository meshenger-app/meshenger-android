package d.d.meshenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;


public class QRShowActivity extends MeshengerActivity {
    private static final String TAG = "QRShowActivity";
    private Contact contact = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrshow);

        if (getIntent().hasExtra("EXTRA_CONTACT")) {
            byte[] publicKey = getIntent().getExtras().getByteArray("EXTRA_CONTACT");
            this.contact = MainService.instance.getContacts().getContactByPublicKey(publicKey);
        }

        if (this.contact != null) {
            Log.d(TAG, "got contact");
            findViewById(R.id.fabPresenter).setVisibility(View.GONE);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            params.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE);
            params.bottomMargin = params.rightMargin = 80;
            findViewById(R.id.fabShare).setLayoutParams(params);
        }

        setTitle(getString(R.string.scan_invitation));
        //bindService();

        findViewById(R.id.fabPresenter).setOnClickListener(view -> {
            startActivity(new Intent(this, QRScanActivity.class));
            finish();
        });

        findViewById(R.id.fabShare).setOnClickListener(view -> {
            if (this.contact != null) try {
                String data = Contact.toJSON(this.contact).toString();
                Intent i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_TEXT, data);
                i.setType("text/plain");
                startActivity(i);
                finish();
            } catch (Exception e) {
                // ignore
            }
        });

        try {
            generateQR();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void generateQR() throws Exception {
        if (this.contact == null) {
            // export own contact
            this.contact = MainService.instance.getSettings().getOwnContact();
        }

        String data = Contact.toJSON(this.contact).toString();
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        BitMatrix bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
        ((ImageView) findViewById(R.id.QRView)).setImageBitmap(bitmap);

        if (this.contact.getAddresses().isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private static void log(String s) {
        Log.d(QRShowActivity.class.getSimpleName(), s);
    }
}
