package org.rivchain.cuplink

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.util.RlpUtils

class QRShowActivity : BaseActivity(), ServiceConnection {
    private lateinit var publicKey: ByteArray
    private var service: MainService? = null

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
                val contact = service!!.getContactOrOwn(publicKey)!!
                val data = RlpUtils.generateLink(contact)
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
        if (service != null) {
            unbindService(this)
        }
    }

    private fun generateQR(contact: Contact) {
        findViewById<TextView>(R.id.contact_name_tv)
            .text = contact.name

        val data = Contact.toJSON(contact, false).toString()
        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
        if (contact.name.isEmpty()) {
            Toast.makeText(this, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
        }
        if (contact.publicKey.isEmpty()) {
            Toast.makeText(this, R.string.contact_public_key_invalid, Toast.LENGTH_SHORT).show()
        }
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
        findViewById<ImageView>(R.id.QRView).setImageBitmap(bitmap)
    }

    private fun generateDeepLinkQR(contact: Contact) {
        findViewById<TextView>(R.id.contact_name_tv)
            .text = contact.name

        val data = RlpUtils.generateLink(contact)
        if(data == null){
            Toast.makeText(this, R.string.contact_is_invalid, Toast.LENGTH_SHORT).show()
        }
        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
        if (contact.name.isEmpty()) {
            Toast.makeText(this, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
        }
        if (contact.publicKey.isEmpty()) {
            Toast.makeText(this, R.string.contact_public_key_invalid, Toast.LENGTH_SHORT).show()
        }
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
        findViewById<ImageView>(R.id.QRView).setImageBitmap(bitmap)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        service = (iBinder as MainBinder).getService()
        try {
            val contact = service!!.getContactOrOwn(publicKey)!!
            generateDeepLinkQR(contact)
        } catch (e: NullPointerException) {
            e.printStackTrace()
            Toast.makeText(this, "NPE", Toast.LENGTH_LONG).show()
        } catch (e: Exception){
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }
}
