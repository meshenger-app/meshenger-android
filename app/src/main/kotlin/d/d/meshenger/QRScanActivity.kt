package d.d.meshenger

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.floor

class QRScanActivity : BaseActivity(), BarcodeCallback, ServiceConnection {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var binder: MainService.MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.title_scan_qr_code)

        barcodeView = findViewById(R.id.barcodeScannerView)

        bindService(Intent(this, MainService::class.java), this, 0)

        // button to show QR-Code
        findViewById<View>(R.id.fabCameraInput).setOnClickListener {
            val intent = Intent(this, QRShowActivity::class.java)
            intent.putExtra("EXTRA_CONTACT_PUBLICKEY", Utils.byteArrayToHexString(binder!!.getSettings().publicKey))
            startActivity(intent)
            finish()
        }

        // button for manual input
        findViewById<View>(R.id.fabManualInput).setOnClickListener { startManualInput() }

        // button to get QR-Code from image
        findViewById<View>(R.id.fabImageInput).setOnClickListener { startImageInput() }

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

    private fun showPubkeyConflictDialog(newContact: Contact, otherContact: Contact) {
        barcodeView.pause()

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_add_contact_pubkey_conflict)
        val nameTextView =
            dialog.findViewById<TextView>(R.id.public_key_conflicting_contact_textview)
        val abortButton = dialog.findViewById<Button>(R.id.public_key_conflict_abort_button)
        val replaceButton = dialog.findViewById<Button>(R.id.public_key_conflict_replace_button)
        nameTextView.text = otherContact.name
        replaceButton.setOnClickListener {
            binder!!.deleteContact(otherContact.publicKey)
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
        barcodeView.pause()

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
        val b = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        val et = EditText(this)
        b.setTitle(R.string.paste_qr_code_data)
            .setPositiveButton(R.string.button_ok) { _: DialogInterface?, _: Int ->
                try {
                    val data = et.text.toString()
                    addContact(data)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, R.string.invalid_qr_code_data, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.button_cancel) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
                barcodeView.resume()
            }
            .setView(et)
        b.show()
    }

    private var importFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                val bitmap = getThumbnail(applicationContext, uri, 800)
                val reader = MultiFormatReader()
                val qrResult = reader.decode(convertToBinaryBitmap(bitmap))
                addContact(qrResult.text)
                barcodeView.resume()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.invalid_qr_code_data, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startImageInput() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        importFileLauncher.launch(intent)
        barcodeView.pause()
    }

    override fun barcodeResult(result: BarcodeResult) {
        // no more scan until result is processed
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

    companion object {
        // make image smaller for less resource use
        private fun getThumbnail(ctx: Context, uri: Uri, size: Int): Bitmap {
            // open image stream to get size
            val input = ctx.contentResolver.openInputStream(uri)
            if (input == null) {
                throw Exception("Cannot open picture")
            }

            val onlyBoundsOptions = BitmapFactory.Options()
            onlyBoundsOptions.inJustDecodeBounds = true
            onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 // optional
            BitmapFactory.decodeStream(input, null, onlyBoundsOptions)
            input.close()

            if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1)) {
                throw Exception("Internal error")
            }

            fun getPowerOfTwoForSampleRatio(ratio: Double): Int {
                val k = Integer.highestOneBit(floor(ratio).toInt())
                return if (k == 0) 1 else k
            }

            val originalSize =
                if ((onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth)) onlyBoundsOptions.outHeight else onlyBoundsOptions.outWidth

            val ratio = if ((originalSize > size)) (originalSize / size.toDouble()) else 1.0

            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(ratio)
            bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888

            // open image stream to resize while decoding
            val stream = ctx.contentResolver.openInputStream(uri)
            if (stream == null) {
                throw Exception("Cannot open stream")
            }
            val bitmap = BitmapFactory.decodeStream(stream, null, bitmapOptions)
            stream.close()
            if (bitmap == null) {
                throw Exception("Cannot decode bitmap")
            }
            return bitmap
        }

        private fun convertToBinaryBitmap(bitmap: Bitmap): BinaryBitmap {
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            return BinaryBitmap(HybridBinarizer(source))
        }
    }
}
