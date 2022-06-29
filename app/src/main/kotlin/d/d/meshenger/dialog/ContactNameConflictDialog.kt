package d.d.meshenger.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.QRScanActivity
import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService

class ContactNameConflictDialog(val mContext: Context, val oldContact: Contact, val newContact: Contact): DialogFragment() {

    private lateinit var abortButton: Button
    private lateinit var replaceButton: Button
    private lateinit var renameButton: Button
    private lateinit var editText: EditText

    private var qrScanActivity = context as QRScanActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_add_contact_name_conflict_new_ui, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let {
            replaceButton = it.findViewById(R.id.conflict_contact_replace_button)
            abortButton = it.findViewById(R.id.conflict_contact_abort_button)
            renameButton = it.findViewById(R.id.conflict_contact_rename_button)
            editText = it.findViewById(R.id.conflict_contact_edit_textview)
            PushDownAnim.setPushDownAnimTo(replaceButton, abortButton, renameButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }
        replaceButton.setOnClickListener {
            val contacts = MainService.instance!!.getContacts()
            contacts?.deleteContact(oldContact.publicKey)
            contacts?.addContact(newContact)

            // done
            Snackbar.make(view, R.string.done, Snackbar.LENGTH_SHORT).show()
            //Toast.makeText(this@QRScanActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog?.cancel()
            qrScanActivity.finish()
        }
        renameButton.setOnClickListener { v: View? ->
            val name = editText.text.toString()
            val contacts = MainService.instance!!.getContacts()!!
            if (name.isEmpty()) {
                Snackbar.make(view, R.string.contact_name_empty, Snackbar.LENGTH_SHORT).show()
                //Toast.makeText(this, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (contacts.getContactByName(name) != null) {
                Snackbar.make(view, R.string.contact_name_exists, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // rename
            newContact.name = name
            contacts.addContact(newContact)

            // done
            Snackbar.make(view, R.string.done, Snackbar.LENGTH_SHORT).show()
            dialog?.cancel()
            qrScanActivity.finish()
        }
        abortButton.setOnClickListener { v: View? ->
            dialog?.cancel()
            qrScanActivity.barcodeView!!.resume()
        }

    }
}