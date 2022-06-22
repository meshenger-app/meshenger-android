package d.d.meshenger.dialog

import android.content.Context
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
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.service.MainService
import d.d.meshenger.R
import d.d.meshenger.activity.StartActivity
import d.d.meshenger.utils.Utils

class SetUsernameDialog(context: Context): DialogFragment() {

    private lateinit var skipButton: Button
    private lateinit var nextButton: Button
    private lateinit var setUsernameEditText: EditText
    private var imm: InputMethodManager = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    private var startActivity: StartActivity = context as StartActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_set_username, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let{
            skipButton = it.findViewById(R.id.set_username_skip_button)
            nextButton = it.findViewById(R.id.set_username_next_button)
            setUsernameEditText = it.findViewById(R.id.set_username_edit_textview)
            PushDownAnim.setPushDownAnimTo(nextButton, skipButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }
        skipButton.apply{
            setOnClickListener {
                context.stopService(Intent(context, MainService::class.java))
                startActivity.finish()

            }
        }

        this.dialog?.setOnShowListener {
            setUsernameEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                    // nothing to do
                }

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                    // nothing to do
                }

                override fun afterTextChanged(editable: Editable) {
                    nextButton.let {
                        it.isClickable = editable.isNotEmpty()
                        it.alpha = if (editable.isNotEmpty()) 1.0f else 0.5f

                    }
                }
            })
            nextButton.apply {
                isClickable = false
                alpha = 0.5f
            }
            if (setUsernameEditText.requestFocus()) {
                imm.showSoftInput(setUsernameEditText, InputMethodManager.SHOW_IMPLICIT)
            //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }

        }

        nextButton.apply {
            setOnClickListener {
                imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY) //TODO: Deprecated method
                val username = setUsernameEditText.text.toString().trim { it <= ' ' }
                if (Utils.isValidContactName(username)) {
                    MainService.instance?.getSettings()?.username = username
                    MainService.instance?.saveDatabase()

                    // close dialog
                    dialog?.dismiss()
                    //dialog.cancel(); // needed?
                    startActivity.continueInit()
                } else {
                    Toast.makeText(context, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

}