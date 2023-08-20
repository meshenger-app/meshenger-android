package d.d.meshenger

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import d.d.meshenger.MainService.MainBinder

class QRShowActivity : BaseActivity(), ServiceConnection {
    private lateinit var publicKey: ByteArray
    private var binder: MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrshow)

        publicKey = intent.extras!!["EXTRA_CONTACT_PUBLICKEY"] as ByteArray

        title = getString(R.string.title_show_qr_code)

        bindService(Intent(this, MainService::class.java), this, 0)

        findViewById<View>(R.id.fabPresenter).setOnClickListener {
            startActivity(Intent(this, QRScanActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.fabShare).setOnClickListener {
            try {
                val contact = binder!!.getContactOrOwn(publicKey)!!
                val data = Contact.toJSON(contact, false).toString()
                val i = Intent(Intent.ACTION_SEND)
                i.putExtra(Intent.EXTRA_TEXT, data)
                i.type = "text/plain"
                startActivity(i)
                finish()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binder != null) {
            unbindService(this)
        }
    }

    private fun generateQR(contact: Contact) {
        findViewById<TextView>(R.id.contact_name_tv)
            .text = contact.name

        val data = Contact.toJSON(contact, false).toString()
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
        findViewById<ImageView>(R.id.QRView).setImageBitmap(bitmap)

        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder

        try {
            val contact = binder!!.getContactOrOwn(publicKey)!!
            generateQR(contact)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }
}
