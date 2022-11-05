package d.d.meshenger

import d.d.meshenger.Utils
import d.d.meshenger.Contact
import d.d.meshenger.Log
import d.d.meshenger.MainService
import d.d.meshenger.QRShowActivity
import d.d.meshenger.R
import android.content.ServiceConnection
import android.os.Bundle
import kotlin.Throws
import org.json.JSONObject
import android.widget.Toast
import android.widget.TextView
import android.widget.EditText
import android.content.Intent
import android.content.DialogInterface
import android.content.pm.PackageManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.google.zxing.ResultPoint
import com.google.zxing.BarcodeFormat
import android.content.ComponentName
import android.app.AlertDialog
import android.app.Dialog
import android.os.IBinder
import android.view.View
import android.widget.Button
import org.json.JSONException

class QRScanActivity : BaseActivity(), BarcodeCallback, ServiceConnection {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var binder: MainService.MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        setTitle(getString(R.string.scan_invited))

        barcodeView = findViewById(R.id.barcodeScannerView)

        bindService(Intent(this, MainService::class.java), this, 0)
        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, 1)
        }

        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, QRShowActivity::class.java))
            finish()
        }

        // manual input button
        findViewById<View>(R.id.fabManualInput).setOnClickListener { startManualInput() }
    }

    @Throws(JSONException::class)
    private fun addContact(data: String) {
        val obj = JSONObject(data)
        val new_contact = Contact.fromJSON(obj, false)
        if (new_contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_LONG).show()
        }

        // lookup existing contacts by key and name
        val contacts = binder!!.getContacts()
        val existing_pubkey_contact = contacts.getContactByPublicKey(new_contact.publicKey)
        val existing_name_contact = contacts.getContactByName(new_contact.name)
        if (existing_pubkey_contact != null) {
            // contact with that public key exists
            showPubkeyConflictDialog(new_contact, existing_pubkey_contact)
        } else if (existing_name_contact != null) {
            // contact with that name exists
            showNameConflictDialog(new_contact, existing_name_contact)
        } else {
            // no conflict
            binder!!.addContact(new_contact)
            finish()
        }
    }

    private fun showPubkeyConflictDialog(new_contact: Contact, other_contact: Contact) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_pubkey_conflict)
        val nameTextView =
            dialog.findViewById<TextView>(R.id.public_key_conflicting_contact_textview)
        val abortButton = dialog.findViewById<Button>(R.id.public_key_conflict_abort_button)
        val replaceButton = dialog.findViewById<Button>(R.id.public_key_conflict_replace_button)
        nameTextView.text = other_contact.name
        replaceButton.setOnClickListener {
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(new_contact)

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

    private fun showNameConflictDialog(new_contact: Contact, other_contact: Contact) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_name_conflict)
        val nameEditText = dialog.findViewById<EditText>(R.id.conflict_contact_edit_textview)
        val abortButton = dialog.findViewById<Button>(R.id.conflict_contact_abort_button)
        val replaceButton = dialog.findViewById<Button>(R.id.conflict_contact_replace_button)
        val renameButton = dialog.findViewById<Button>(R.id.conflict_contact_rename_button)
        nameEditText.setText(other_contact.name)
        replaceButton.setOnClickListener {
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        renameButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binder!!.getContacts().getContactByName(name) != null) {
                Toast.makeText(this, R.string.contact_name_exists, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // rename
            new_contact.name = name
            binder!!.addContact(new_contact)

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
        val b = AlertDialog.Builder(this)
        val et = EditText(this)
        b.setTitle(R.string.paste_invitation)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                try {
                    val data = et.text.toString()
                    addContact(data)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
                barcodeView.resume()
            }
            .setView(et)
        b.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
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
        try {
            addContact(result.text)
        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this, R.string.invalid_qr, Toast.LENGTH_LONG).show()
        }
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint>) {
        // ignore
    }

    override fun onResume() {
        super.onResume()
        if (binder != null) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (binder != null) {
            barcodeView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    private fun initCamera() {
        val formats = listOf(BarcodeFormat.QR_CODE)
        barcodeView.getBarcodeView()?.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.decodeContinuous(this)
        barcodeView.resume()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainService.MainBinder
        if (Utils.hasCameraPermission(this)) {
            initCamera()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }
}