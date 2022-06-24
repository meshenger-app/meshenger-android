package d.d.meshenger.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.QRScanActivity
import d.d.meshenger.utils.Log

import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService

class QRShowDialog (val mContext: Context, var intent: Intent): DialogFragment() {

    private lateinit var fabShare: FloatingActionButton
    private lateinit var fabPresenter: FloatingActionButton
    private lateinit var qrImageView: ImageView
    private var contact: Contact? = null

    private val qrShowActivity = mContext as Activity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(
        R.layout.fragment_qrshow, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fabPresenter = view.findViewById(R.id.fabPresenter)
        fabShare = view.findViewById(R.id.fabShare)
        qrImageView = view.findViewById(R.id.QRView)
        PushDownAnim.setPushDownAnimTo(fabShare, fabPresenter)
        if (intent.hasExtra("EXTRA_CONTACT")) {
            val publicKey = intent.extras!!.getByteArray("EXTRA_CONTACT")
            this.contact = MainService.instance!!.getContacts()?.getContactByPublicKey(publicKey!!)
        }
        if (this.contact != null) {
            Log.d("QRShow Dialog", "got contact")
            fabPresenter.visibility = View.GONE
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
            params.addRule(RelativeLayout.BELOW, R.id.QRView)
            params.marginEnd = 20
            params.topMargin = 26
            fabShare.layoutParams = params
        }
        //title = getString(R.string.scan_invitation)
        //bindService();
        fabPresenter.setOnClickListener {
            qrShowActivity.startActivity(Intent(mContext, QRScanActivity::class.java))
            dialog?.cancel()
        //qrShowActivity.finish()
        }
        fabShare.setOnClickListener {
            contact ?.let {
                try {
                    val data = Contact.toJSON(it).toString()
                    val i = Intent(Intent.ACTION_SEND)
                    i.putExtra(Intent.EXTRA_TEXT, data)
                    i.type = "text/plain"
                    qrShowActivity.startActivity(i)

                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        try {
            generateQR()
        } catch (e: Exception) {
            e.printStackTrace()
            dialog?.cancel()
            //qrShowActivity.finish()
        }
    }

    @Throws(Exception::class)
    private fun generateQR() {
        if (contact == null) {
            // export own contact
            contact = MainService.instance!!.getSettings()?.getOwnContact()
        }
        val data = Contact.toJSON(contact!!).toString()
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(data, BarcodeFormat.QR_CODE, 1080, 1080)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)
       qrImageView.setImageBitmap(bitmap)
        if (contact!!.addresses.isEmpty()) {
            val snackbar = Snackbar.make(qrImageView, R.string.contact_has_no_address_warning, Snackbar.LENGTH_SHORT)
            snackbar.show()
            //Toast.makeText(this, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        }
    }

}