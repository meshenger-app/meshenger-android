package d.d.meshenger.activity

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import d.d.meshenger.utils.Log.d
import d.d.meshenger.service.MainService
import d.d.meshenger.R
import d.d.meshenger.utils.Utils.hasCameraPermission
import d.d.meshenger.utils.Utils.isValidContactName
import d.d.meshenger.utils.Utils.requestCameraPermission
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.dialog.ContactNameConflictDialog
import d.d.meshenger.dialog.ContactPublicKeyConflictDialog
import d.d.meshenger.dialog.PasteDataHereDialog
import d.d.meshenger.dialog.QRShowDialog
import d.d.meshenger.model.Contact
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium

class QRScanActivity: MeshengerActivity(), BarcodeCallback {

    companion object {
        private const val TAG = "QRScanActivity"

    }

    var barcodeView: DecoratedBarcodeView? = null
    private lateinit var qrScanRoot: RelativeLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.scan_invited)
        if (!hasCameraPermission(this)) {
            requestCameraPermission(this, 1)
        }

        qrScanRoot = findViewById(R.id.qrscan_root)
        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener { view: View? ->
            //startActivity(Intent(this, QRShowActivity::class.java))
            QRShowDialog(this, intent).show(supportFragmentManager, "QrShow Dialog")
        }

        // manual input button
        findViewById<View>(R.id.fabManualInput).setOnClickListener { view: View? ->
            startManualInput() }
        if (hasCameraPermission(this)) {
            initCamera()
        }
    }

    private fun parseContact(data: String): Contact? {
        d(TAG, "parseContact")
        try {
            val `object` = JSONObject(data)
            if (!`object`.has("blocked")) {
                `object`.put("blocked", false)
            }
            val contact = Contact.fromJSON(`object`)
            if (!isValidContactName(contact.name)) {
                Toast.makeText(this, "Invalid name.", Toast.LENGTH_SHORT).show()
                return null
            }
            if (contact.publicKey.size != Sodium.crypto_sign_publickeybytes()) {
                Toast.makeText(this, "Invalid public key.", Toast.LENGTH_SHORT).show()
                return null
            }
            return contact
        } catch (e: JSONException) {
            //Snackbar.make(qrScanRoot, R.string.invalid_data, Snackbar.LENGTH_SHORT).show()
            Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show()
        }
        return null
    }

    fun addContact(data: String) {
        val contact = parseContact(data)
        if (contact == null) {
            finish()
            return
        }
        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_LONG).show()
        }

        // lookup existing contacts by key and name
        val contacts = MainService.instance!!.getContacts()!!
        val existing_key_contact = contacts.getContactByPublicKey(contact.publicKey)
        val existing_name_contact = contacts.getContactByName(contact.name)
        if (existing_key_contact != null) {
            // contact with that public key exists
            showPublicKeyConflictDialog(contact, existing_key_contact)
        } else if (existing_name_contact != null) {
            // contact with that name exists
            showNameConflictDialog(contact, existing_name_contact)
        } else {
            // no conflict
            contacts.addContact(contact)
            MainService.instance!!.saveDatabase()
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("contacts_changed"))
            finish()
        }
    }

    private fun showPublicKeyConflictDialog(new_contact: Contact, old_contact: Contact) {
        val dialog = ContactPublicKeyConflictDialog(this, old_contact, new_contact)
        dialog.show(supportFragmentManager, "OnPublicKey Conflict")
    }

    private fun showNameConflictDialog(new_contact: Contact, old_contact: Contact) {
        val dialog = ContactNameConflictDialog(this, old_contact, new_contact)
        dialog.show(supportFragmentManager, ("OnConflict Contact"))
    }

    private fun startManualInput() {
        barcodeView!!.pause()
        val pasteDataHereDialog = PasteDataHereDialog(this)
        pasteDataHereDialog.show(supportFragmentManager, "PasteDataHere Dialog")

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_request, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun barcodeResult(result: BarcodeResult) {
        // no more scan until result is processed
        barcodeView!!.pause()
        val data = result.text
        addContact(data)
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint?>?) {
        // ignore
    }


    override fun onResume() {
        super.onResume()
        barcodeView ?.resume()

    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()

    }

    private fun initCamera() {
        barcodeView = findViewById(R.id.barcodeScannerView)
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE)
        barcodeView?.apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(formats)

            decodeContinuous(this@QRScanActivity)
            resume()
        }
    }
}