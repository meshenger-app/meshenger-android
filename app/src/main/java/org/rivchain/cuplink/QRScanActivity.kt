package org.rivchain.cuplink

import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.json.JSONException
import org.json.JSONObject
import org.rivchain.cuplink.MainService.MainBinder

class QRScanActivity : CupLinkActivity(), BarcodeCallback, ServiceConnection {
    private var barcodeView: DecoratedBarcodeView? = null
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.scan_invited)
        bindService(Intent(this, MainService::class.java), this, BIND_AUTO_CREATE)
        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, 1)
        }

        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener { view: View? ->
            startActivity(Intent(this, QRShowActivity::class.java))
            finish()
        }

        // manual input button
        findViewById<View>(R.id.fabManualInput).setOnClickListener { view: View? -> startManualInput() }
    }

    @Throws(JSONException::class)
    private fun addContact(data: String) {
        val `object` = JSONObject(data)
        val new_contact = Contact.importJSON(`object`, false)
        if (new_contact.getAddresses().isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_LONG).show()
        }

        // lookup existing contacts by key and name
        val existing_pubkey_contact = binder!!.getContactByPublicKey(new_contact.publicKey)
        val existing_name_contact = binder!!.getContactByName(new_contact.getName())
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
        val nameTextView = dialog.findViewById<TextView>(R.id.NameTextView)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        val replaceButton = dialog.findViewById<Button>(R.id.ReplaceButton)
        nameTextView.text = other_contact.getName()
        replaceButton.setOnClickListener { v: View? ->
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        abortButton.setOnClickListener { v: View? ->
            dialog.cancel()
            barcodeView!!.resume()
        }
        dialog.show()
    }

    private fun showNameConflictDialog(new_contact: Contact, other_contact: Contact) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_name_conflict)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        val replaceButton = dialog.findViewById<Button>(R.id.ReplaceButton)
        val renameButton = dialog.findViewById<Button>(R.id.RenameButton)
        nameEditText.setText(other_contact.getName())
        replaceButton.setOnClickListener { v: View? ->
            binder!!.deleteContact(other_contact.publicKey)
            binder!!.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        renameButton.setOnClickListener { v: View? ->
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binder!!.getContactByName(name) != null) {
                Toast.makeText(this, R.string.contact_name_exists, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // rename
            new_contact.setName(name)
            binder!!.addContact(new_contact)

            // done
            Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
            finish()
        }
        abortButton.setOnClickListener { v: View? ->
            dialog.cancel()
            barcodeView!!.resume()
        }
        dialog.show()
    }

    private fun startManualInput() {
        barcodeView!!.pause()
        val b = AlertDialog.Builder(this)
        val et = EditText(this)
        b.setTitle(R.string.paste_invitation)
                .setPositiveButton(R.string.ok) { dialogInterface, i ->
                    try {
                        val data = et.text.toString()
                        addContact(data)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        Toast.makeText(this, R.string.invalid_data, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog, i ->
                    dialog.cancel()
                    barcodeView!!.resume()
                }
                .setView(et)
        b.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
        try {
            val data = result.text
            addContact(data)
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
        if (barcodeView != null && binder != null) {
            barcodeView!!.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (barcodeView != null && binder != null) {
            barcodeView!!.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    private fun initCamera() {
        barcodeView = findViewById(R.id.barcodeScannerView)
        val formats: Collection<BarcodeFormat> = listOf(BarcodeFormat.QR_CODE)
        barcodeView!!.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView!!.decodeContinuous(this)
        barcodeView!!.resume()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        if (Utils.hasCameraPermission(this)) {
            initCamera()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}