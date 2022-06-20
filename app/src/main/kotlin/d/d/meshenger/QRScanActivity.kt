package d.d.meshenger

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import d.d.meshenger.Utils.hasCameraPermission
import d.d.meshenger.Utils.isValidContactName
import d.d.meshenger.Utils.requestCameraPermission
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import java.util.*

//TODO: Multiple OnClick Listeners
class QRScanActivity: MeshengerActivity(), BarcodeCallback {

    private lateinit var barcodeView: DecoratedBarcodeView

    companion object {
        private var TAG = "QRScanActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.scan_invited)
        if (!hasCameraPermission(this)) {
            requestCameraPermission(this, 1)
        }

        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, QRShowActivity::class.java))
            finish()
        }

        // manual input button
        findViewById<View>(R.id.fabManualInput).setOnClickListener { startManualInput() }
        if (hasCameraPermission(this)) {
            initCamera()
        }
    }

    private fun parseContact(data: String): Contact? {
        Log.d(TAG, "parseContact")
        try {
            val jsonObject = JSONObject(data)
            if (!jsonObject.has("blocked")) {
                jsonObject.put("blocked", false)
            }
            val contact = Contact.fromJSON(jsonObject)
            if (!isValidContactName(contact.name)) {
                Toast.makeText(this, "Invalid name.", Toast.LENGTH_SHORT).show()
                return null
            }
            if (contact.publicKey == null || contact.publicKey.size != Sodium.crypto_sign_publickeybytes()) {
                Toast.makeText(this, "Invalid public key.", Toast.LENGTH_SHORT).show()
                return null
            }
            return contact
        } catch (e: JSONException) {
            Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show()
        }
        return null
    }

    private fun addContact(data: String) {
        val contact = parseContact(data)
        if (contact == null) {
            finish()
            return
        }
        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_LONG).show()
        }

        // lookup existing contacts by key and name
        val contacts = MainService.instance?.getContacts()!!
        contacts.let {
            val existingKeyContact = it.getContactByPublicKey(contact.publicKey)
            val existingNameContact = it.getContactByName(contact.name)
            if (existingKeyContact != null) {
                // contact with that public key exists
                showPublicKeyConflictDialog(contact, existingKeyContact)
            } else if (existingNameContact != null) {
                // contact with that name exists
                showNameConflictDialog(contact, existingNameContact)
            } else {
                // no conflict
                it.addContact(contact)
                MainService.instance!!.saveDatabase()
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("contacts_changed"))
                finish()
            }
        }

    }

    private fun showPublicKeyConflictDialog(new_contact: Contact, old_contact: Contact) {
        val dialog = Dialog(this)
        dialog.apply {
            this.setContentView(R.layout.dialog_add_contact_key_conflict)
            this.setCancelable(false)
        }
        val contactTextView: TextView = dialog.findViewById(R.id.NameTextView)
        val abortButton: Button = dialog.findViewById(R.id.AbortButton)
        val replaceButton: Button = dialog.findViewById(R.id.ReplaceButton)
        val someText = "${new_contact.name} => ${old_contact.name}"
        contactTextView.text = someText
        replaceButton.setOnClickListener {
            val contacts = MainService.instance!!.getContacts()
            contacts.let {
                it.deleteContact(old_contact.publicKey)
                it.addContact(new_contact)
            }

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        abortButton.setOnClickListener {
            dialog.cancel()
            barcodeView.resume()
        }
        dialog.show()
    }

    private fun showNameConflictDialog(new_contact: Contact, old_contact: Contact) {
        val dialog = Dialog(this)
        dialog.apply {
            this.setContentView(R.layout.dialog_add_contact_name_conflict)
            this.setCancelable(false)
        }
        val nameEditText: EditText = dialog.findViewById(R.id.NameEditText)
        val abortButton: Button = dialog.findViewById(R.id.AbortButton)
        val replaceButton: Button = dialog.findViewById(R.id.ReplaceButton)
        val renameButton: Button = dialog.findViewById(R.id.RenameButton)
        nameEditText.setText(old_contact.name)
        replaceButton.setOnClickListener {
            val contacts = MainService.instance!!.getContacts()
            contacts.deleteContact(old_contact.publicKey)
            contacts.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        renameButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val contacts = MainService.instance!!.getContacts()
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (contacts.getContactByName(name) != null) {
                Toast.makeText(this, R.string.contact_name_exists, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // rename
            new_contact.name = name
            contacts.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        abortButton.setOnClickListener {
            dialog.cancel()
            barcodeView.resume()
        }
        dialog.show()
    }

    private fun startManualInput() {
        barcodeView.pause()
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val et = EditText(this)
        builder.setTitle(R.string.paste_invitation)
            .setPositiveButton(R.string.ok) { _, _ ->
                val data = et.text.toString()
                addContact(data)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
                barcodeView.resume()
            }
            .setView(et)
        builder.show()
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
        barcodeView.pause()
        val data = result.text
        addContact(data)
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint?>?) {
        // ignore
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    private fun initCamera() {
        barcodeView = findViewById(R.id.barcodeScannerView)
        val formats: Collection<BarcodeFormat> = Collections.singletonList(BarcodeFormat.QR_CODE)
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.decodeContinuous(this)
        barcodeView.resume()
    }
}