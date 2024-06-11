package org.rivchain.cuplink

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import org.json.JSONException
import org.rivchain.cuplink.util.Utils

class QRScanActivity : AddContactActivity(), BarcodeCallback {
    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrscan)
        title = getString(R.string.title_scan_qr_code)

        barcodeView = findViewById(R.id.barcodeScannerView)

        // qr show button
        findViewById<View>(R.id.fabScan).setOnClickListener {
            val intent = Intent(this, QRShowActivity::class.java)
            intent.putExtra("EXTRA_CONTACT_PUBLICKEY", service!!.getSettings().publicKey)
            startActivity(intent)
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

    override fun barcodeResult(result: BarcodeResult) {
        // no more scan until result is processed
        try {
            super.addContact(result.text)
        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this, R.string.invalid_qr, Toast.LENGTH_LONG).show()
        }
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint>) {
        // ignore
    }

    private fun initCamera() {
        val formats = listOf(BarcodeFormat.QR_CODE)
        barcodeView.barcodeView?.decoderFactory = DefaultDecoderFactory(formats)
        barcodeView.decodeContinuous(this)
        barcodeView.resume()
    }

    override fun onServiceConnected(){
        if (Utils.hasPermission(this, Manifest.permission.CAMERA)) {
            initCamera()
        }
    }

    override fun pause(){
        barcodeView.pause()
    }

    override fun resume(){
        barcodeView.resume()
    }
}
