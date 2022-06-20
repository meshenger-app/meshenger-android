package d.d.meshenger

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder


class QRShowActivity: MeshengerActivity() {

    private var contact: Contact? = null

    companion object {
        private const val TAG = "QRShowActivity"

        private fun log(s: String) {
            Log.d(QRShowActivity::class.java.simpleName, s)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrshow)
        if (intent.hasExtra("EXTRA_CONTACT")) {
            val publicKey = intent.extras!!.getByteArray("EXTRA_CONTACT")
            contact = MainService.instance?.getContacts()?.getContactByPublicKey(publicKey)!!
        }
        Log.d(TAG, "got contact")
        findViewById<View>(R.id.fabPresenter).visibility = View.GONE
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
        params.rightMargin = 80
        params.bottomMargin = params.rightMargin
        findViewById<View>(R.id.fabShare).layoutParams = params
        title = getString(R.string.scan_invitation)
        //bindService();
        findViewById<View>(R.id.fabPresenter).setOnClickListener {
            startActivity(Intent(this, QRScanActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.fabShare).setOnClickListener {
            try {
                val data = contact?.let { it1 -> Contact.toJSON(it1).toString() }
                val i = Intent(Intent.ACTION_SEND)
                i.putExtra(Intent.EXTRA_TEXT, data)
                i.type = "text/plain"
                startActivity(i)
                finish()
            } catch (e: Exception) {
                // ignore
            }
        }
        try {
            generateQR()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Throws(Exception::class)
    private fun generateQR() {
        if(this.contact == null) {
            this.contact = MainService.instance?.getSettings()?.getOwnContact()!!
        }
        val data = Contact.toJSON(contact!!).toString()
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
        (findViewById<View>(R.id.QRView) as ImageView).setImageBitmap(bitmap)
        if (contact!!.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
    }

    //Unused
//    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context?, intent: Intent?) {
//            finish()
//        }
//    }

}