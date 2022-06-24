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
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.QRScanActivity

class PasteDataHereDialog(context: Context): DialogFragment() {

    private lateinit var cancelButton: Button
    private lateinit var okayButton: Button
    private lateinit var editText: EditText
    private var qrScanActivity = context as QRScanActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_paste_contact_data, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let {
            okayButton = it.findViewById(R.id.paste_data_okay_button)
            cancelButton = it.findViewById(R.id.paste_data_cancel_button)
            editText = it.findViewById(R.id.paste_data_edit_textview)
            PushDownAnim.setPushDownAnimTo(okayButton, cancelButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }
        okayButton.setOnClickListener {
            val data = editText.text.toString()
            qrScanActivity.addContact(data)
            dialog?.cancel()
        }

        cancelButton.setOnClickListener {

            dialog?.cancel()
            qrScanActivity.barcodeView?.resume()
            // continue with out address configuration
        }


    }
}