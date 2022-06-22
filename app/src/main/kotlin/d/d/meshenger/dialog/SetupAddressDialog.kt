package d.d.meshenger.dialog

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.StartActivity

class SetupAddressDialog(context: Context): DialogFragment() {

    private lateinit var skipButton: Button
    private lateinit var okayButton: Button
    private var startActivity: StartActivity = context as StartActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_set_address, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.let {
            okayButton = it.findViewById(R.id.set_address_okay_button)
            skipButton = it.findViewById(R.id.set_address_skip_button)
            PushDownAnim.setPushDownAnimTo(okayButton, skipButton)
                .setDurationPush(PushDownAnim.DEFAULT_PUSH_DURATION)
                .setDurationRelease(PushDownAnim.DEFAULT_RELEASE_DURATION)
                .setInterpolatorPush(AccelerateDecelerateInterpolator())
        }
        okayButton.setOnClickListener {
            startActivity.showMissingAddressDialog()
            dialog?.cancel()
        }

        skipButton.setOnClickListener {

            dialog?.cancel()
            // continue with out address configuration
            startActivity.continueInit()
        }


    }
}