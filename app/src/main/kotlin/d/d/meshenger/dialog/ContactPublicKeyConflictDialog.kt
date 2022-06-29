package d.d.meshenger.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.QRScanActivity
import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService

class ContactPublicKeyConflictDialog(val mContext: Context, val oldContact: Contact, val newContact: Contact): DialogFragment() {

    private lateinit var abortButton: Button
    private lateinit var replaceButton: Button
    private lateinit var conflictingContactTextView: TextView

    private var qrScanActivity = context as QRScanActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_add_contact_name_conflict_new_ui, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let {
            replaceButton = it.findViewById(R.id.public_key_conflict_replace_button)
            abortButton = it.findViewById(R.id.public_key_conflict_abort_button)
            conflictingContactTextView = it.findViewById(R.id.public_key_conflicting_contact_textview)
            PushDownAnim.setPushDownAnimTo(replaceButton, abortButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }

        conflictingContactTextView.text = "${newContact.name} => ${oldContact.name}";

        replaceButton.setOnClickListener {
            val contacts = MainService.instance!!.getContacts()!!
            contacts.deleteContact(oldContact.publicKey)
            contacts.addContact(newContact)

            // done
            Snackbar.make(view, R.string.done, Snackbar.LENGTH_SHORT).show()
            //Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog?.cancel()
            qrScanActivity.finish()
        }
        abortButton.setOnClickListener { v: View? ->
            dialog?.cancel()
            qrScanActivity.barcodeView!!.resume()
        }

    }
}