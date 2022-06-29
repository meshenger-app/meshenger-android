package d.d.meshenger.dialog

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.service.MainService
import d.d.meshenger.R
import d.d.meshenger.activity.StartActivity
import d.d.meshenger.utils.Utils

class SetPasswordDialog(context: Context): DialogFragment() {

    private lateinit var exitButton: Button
    private lateinit var okButton: Button
    private lateinit var passwordEditText: EditText
    private var imm: InputMethodManager = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    private var startActivity: StartActivity = context as StartActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_set_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let{
            exitButton = it.findViewById(R.id.set_password_exit_button)
            okButton = it.findViewById(R.id.set_password_ok_button)
            passwordEditText = it.findViewById(R.id.set_password_text_input_edit_text)
            PushDownAnim.setPushDownAnimTo(okButton, exitButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }
        exitButton.apply{
            setOnClickListener {
                // shutdown app
                dialog?.dismiss()
                startActivity.stopService(Intent(context, MainService::class.java))
                startActivity.finish()
            }
        }



        okButton.apply {
            setOnClickListener {
                val password = passwordEditText.text.toString()
                MainService.instance!!.database_password = password
                MainService.instance!!.loadDatabase()
                if (MainService.instance!!.database == null) {
                    Snackbar.make(view, R.string.wrong_password, Snackbar.LENGTH_SHORT).show()
//                    Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                } else {
                    // close dialog
                    dialog?.dismiss()
                    startActivity.continueInit()
                }
            }
        }

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        startActivity.finish()
    }

}