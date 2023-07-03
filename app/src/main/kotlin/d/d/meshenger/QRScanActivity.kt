package d.d.meshenger

import android.Manifest
import android.content.ServiceConnection
import android.os.Bundle
import org.json.JSONObject
import android.widget.Toast
import android.widget.TextView
import android.widget.EditText
import android.content.Intent
import android.content.DialogInterface
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
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONException

class QRScanActivity : BaseActivity(), BarcodeCallback, ServiceConnection {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var binder: MainService.MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.scan_invited)

        barcodeView = findViewById(R.id.barcodeScannerView)

        bindService(Intent(this, MainService::class.java), this, 0)

        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, QRShowActivity::class.java))
            finish()
        }

        // manual input button
        findViewById<View>(R.id.fabManualInput).setOnClickListener { startManualInput() }

        if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
            enabledCameraForResult.launch(Manifest.permission.CAMERA)
        }
    }

    private val enabledCameraForResult = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted -> if (isGranted) {
            initCamera()
        } else {
            Toast.makeText(this, R.string.missing_camera_permission, Toast.LENGTH_LONG).show()
            // no finish() in case no camera access wanted but contact data pasted
        }
    }

    private fun addContact(data: String) {
        val obj = JSONObject(data)
        val newContact = Contact.fromJSON(obj, false)
        if (newContact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_LONG).show()
        }

        // lookup existing contacts by key and name
        val contacts = binder!!.getContacts()
        val existingContactByPublicKey = contacts.getContactByPublicKey(newContact.publicKey)
        val existingContactByName = contacts.getContactByName(newContact.name)
        if (existingContactByPublicKey != null) {
            // contact with that public key exists
            showPubkeyConflictDialog(newContact, existingContactByPublicKey)
        } else if (existingContactByName != null) {
            // contact with that name exists
            showNameConflictDialog(newContact, existingContactByName)
        } else {
            // no conflict
            binder!!.addContact(newContact)
            finish()
        }
    }

    private fun showPubkeyConflictDialog(newContact: Contact, other_contact: Contact) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_pubkey_conflict)
        val nameTextView =
            dialog.findViewById<TextView>(R.id.public_key_conflicting_contact_textview)
        val abortButton = dialog.findViewById<Button>(R.id.public_key_conflict_abort_button)
        val replaceButton = dialog.findViewById<Button>(R.id.public_key_conflict_replace_button)
        nameTextView.text = other_contact.name
        replaceButton.setOnClickListener {
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(newContact)

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

    private fun showNameConflictDialog(newContact: Contact, other_contact: Contact) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_name_conflict)
        val nameEditText = dialog.findViewById<EditText>(R.id.conflict_contact_edit_textview)
        val abortButton = dialog.findViewById<Button>(R.id.conflict_contact_abort_button)
        val replaceButton = dialog.findViewById<Button>(R.id.conflict_contact_replace_button)
        val renameButton = dialog.findViewById<Button>(R.id.conflict_contact_rename_button)
        nameEditText.setText(other_contact.name)
        replaceButton.setOnClickListener {
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(newContact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        renameButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (!Utils.isValidName(name)) {
                Toast.makeText(this, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binder!!.getContacts().getContactByName(name) != null) {
                Toast.makeText(this, R.string.contact_name_exists, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // rename
            newContact.name = name
            binder!!.addContact(newContact)

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
        barcodeView.barcodeView?.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.decodeContinuous(this)
        barcodeView.resume()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainService.MainBinder
        if (Utils.hasPermission(this, Manifest.permission.CAMERA)) {
            initCamera()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }
}
