/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import d.d.meshenger.MainService.MainBinder

class QRShowActivity : BaseActivity(), ServiceConnection {
    private lateinit var publicKey: ByteArray
    private lateinit var contact: Contact
    private var binder: MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrshow)

        publicKey = Utils.hexStringToByteArray(intent.extras!!.getString("EXTRA_CONTACT_PUBLICKEY"))
        title = getString(R.string.title_show_qr_code)

        bindService(Intent(this, MainService::class.java), this, 0)

        findViewById<View>(R.id.fabScan).setOnClickListener {
            startActivity(Intent(this, QRScanActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.fabSave).setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.putExtra(Intent.EXTRA_TITLE, "${contact.name}_qr-code.png")
            intent.type = "image/png"
            exportFileLauncher.launch(intent)
        }

        findViewById<View>(R.id.fabShare).setOnClickListener {
            try {
                val data = Contact.toJSON(contact, false).toString()
                val intent = Intent(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_TEXT, data)
                intent.type = "text/plain"
                startActivity(intent)
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

    private fun showQRCode() {
        findViewById<TextView>(R.id.contact_name_tv).text = contact.name

        val bitmap = contactToBitmap(contact)
        findViewById<ImageView>(R.id.QRView).setImageBitmap(bitmap)

        if (contact.addresses.isEmpty()) {
            Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
    }

    private var exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val bitmap = contactToBitmap(contact)
                val outStream = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream!!)
                outStream.close()
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.failed_to_export_database, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder

        try {
            contact = binder!!.getContactOrOwn(publicKey)!!
            showQRCode()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    companion object {
        private fun contactToBitmap(contact: Contact): Bitmap {
            val data = Contact.toJSON(contact, false).toString()
            val hints =  mapOf(EncodeHintType.CHARACTER_SET to "utf-8")
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080, hints)
            val barcodeEncoder = BarcodeEncoder()
            return barcodeEncoder.createBitmap(bitMatrix)
        }
    }
}
