package d.d.meshenger

import android.content.*
import d.d.meshenger.Contact.Companion.exportJSON
import d.d.meshenger.Log.d
import d.d.meshenger.MeshengerActivity
import d.d.meshenger.Contact
import d.d.meshenger.MainService.MainBinder
import android.os.Bundle
import d.d.meshenger.R
import android.widget.RelativeLayout
import android.view.ViewGroup
import d.d.meshenger.QRScanActivity
import d.d.meshenger.MainService
import kotlin.Throws
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.graphics.Bitmap
import android.widget.Toast
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import d.d.meshenger.QRShowActivity
import java.lang.Exception

class QRShowActivity : MeshengerActivity(), ServiceConnection {
    private var contact: Contact? = null
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrshow)
        if (intent.hasExtra("EXTRA_CONTACT")) {
            contact = intent.extras!!["EXTRA_CONTACT"] as Contact?
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
        }
        title = getString(R.string.scan_invitation)
        bindService()
        findViewById<View>(R.id.fabPresenter).setOnClickListener { view: View? ->
            startActivity(Intent(this, QRScanActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.fabShare).setOnClickListener { view: View? ->
            if (contact != null) try {
                val data = exportJSON(contact!!, false).toString()
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

    private fun bindService() {
        val serviceIntent = Intent(this, MainService::class.java)
        bindService(serviceIntent, this, BIND_AUTO_CREATE)
    }

    @Throws(Exception::class)
    private fun generateQR() {
        if (contact == null) {
            // export own contact
            contact = binder!!.settings.ownContact
        }
        val data = exportJSON(contact!!, false).toString()
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
        (findViewById<View>(R.id.QRView) as ImageView).setImageBitmap(bitmap)
        if (contact!!.getAddresses().isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        try {
            generateQR()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    companion object {
        private fun log(s: String) {
            d(QRShowActivity::class.java.simpleName, s)
        }
    }
}