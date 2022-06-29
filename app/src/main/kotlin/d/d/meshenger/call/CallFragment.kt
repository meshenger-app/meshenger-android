package d.d.meshenger.call

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import d.d.meshenger.Log
import d.d.meshenger.R
import org.webrtc.RendererCommon.ScalingType


/**
 * Fragment for call control.
 */
class CallFragment: Fragment() {

    companion object {
        private const val TAG = "CallFragment"
    }

    private lateinit var callNameView: TextView
    private lateinit var callStatusView: TextView
    private lateinit var disconnectButton: ImageButton
    private lateinit var connectButton: ImageButton
    private lateinit var cameraSwitchButton: ImageButton
    private lateinit var videoScalingButton: ImageButton
    private lateinit var toggleMuteButton: ImageButton

    //private TextView captureFormatText;
    //private SeekBar captureFormatSlider;
    private lateinit var callEvents: OnCallEvents
    private lateinit var scalingType: ScalingType
    private var videoCallEnabled = true

    var callNameText = ""
    var callStatusText = ""

    /**
     * Call control interface for container activity.
     */
    interface OnCallEvents {
        fun onCallHangUp()
        fun onCallAccept()
        fun onCameraSwitch()
        fun onVideoScalingSwitch(scalingType: ScalingType?)
        fun onVideoMirrorSwitch(mirror: Boolean) // added by me
        fun onCaptureFormatChange(width: Int, height: Int, framerate: Int)
        fun onToggleMic(): Boolean
    }

    var mirror = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_call, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create UI controls.
        callNameView = view.findViewById(R.id.call_name)
        callStatusView = view.findViewById(R.id.call_status)
        disconnectButton = view.findViewById(R.id.button_call_disconnect)
        connectButton = view.findViewById(R.id.button_call_connect)
        cameraSwitchButton = view.findViewById(R.id.button_call_switch_camera)
        videoScalingButton = view.findViewById(R.id.button_call_scaling_mode)
        toggleMuteButton = view.findViewById(R.id.button_call_toggle_mic)
        //captureFormatText = view.findViewById(R.id.capture_format_text_call);
        //captureFormatSlider = view.findViewById(R.id.capture_format_slider_call);

        // Add buttons click events.
        disconnectButton.setOnClickListener { callEvents.onCallHangUp() }
        connectButton.setOnClickListener { callEvents.onCallAccept() }
        cameraSwitchButton.setOnClickListener {
            callEvents.onCameraSwitch()

            // TODO: rework an TODO
            mirror = !mirror
            callEvents.onVideoMirrorSwitch(mirror)
        }
        videoScalingButton.setOnClickListener {
            scalingType = if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
                videoScalingButton.setBackgroundResource(R.drawable.ic_action_full_screen)
                ScalingType.SCALE_ASPECT_FIT
            } else {
                videoScalingButton.setBackgroundResource(R.drawable.ic_action_return_from_full_screen)
                ScalingType.SCALE_ASPECT_FILL
            }
            callEvents.onVideoScalingSwitch(scalingType)
        }
        scalingType = ScalingType.SCALE_ASPECT_FILL
        toggleMuteButton.setOnClickListener {
            val enabled = callEvents.onToggleMic()
            toggleMuteButton.alpha = if (enabled) 1.0f else 0.3f
        }
    }

    fun setContactName(name: String) {
        callNameText = name
    }


    fun setCallStatus(status: String) {
        /*
    <string name="call_connecting">Calling...</string>
    <string name="call_connected">Connected.</string>
    <string name="call_denied">Declined</string>
    <string name="call_ringing">Ringing...</string>
    <string name="call_ended">Call Ended</string>
    <string name="call_error">Error</string>
    */
        callStatusText = status
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart()")
        val captureSliderEnabled = false
        //Bundle args = getArguments();
        //if (args != null) {
        //String contactName = "contactName"; // args.getString(CallActivity.EXTRA_ROOMID);
        callNameView.text = callNameText
        callStatusView.text = callStatusText
        videoCallEnabled = true // args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
        //  captureSliderEnabled = videoCallEnabled && false; // args.getBoolean(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
        //}
        if (!videoCallEnabled) {
            cameraSwitchButton.visibility = View.INVISIBLE
        }
/*
    if (captureSliderEnabled) {
      captureFormatSlider.setOnSeekBarChangeListener(
          new CaptureQualityController(captureFormatText, callEvents));
    } else {
      captureFormatText.setVisibility(View.GONE);
      captureFormatSlider.setVisibility(View.GONE);
    }
*/
    }

/*
  public void onIncomingCall() {
    disconnectButton.setVisibility(View.VISIBLE);
    connectButton.setVisibility(View.GONE);
    //callNameView.setText();
  }

  public void onEstablishedCall() {
    // ??
  }

  public void onOutgoingCall() {
    disconnectButton.setVisibility(View.GONE);
    connectButton.setVisibility(View.VISIBLE);
  }
*/

    /*
  public void onIncomingCall() {
    disconnectButton.setVisibility(View.VISIBLE);
    connectButton.setVisibility(View.GONE);
    //callNameView.setText();
  }

  public void onEstablishedCall() {
    // ??
  }

  public void onOutgoingCall() {
    disconnectButton.setVisibility(View.GONE);
    connectButton.setVisibility(View.VISIBLE);
  }
*/
    // Replace with onAttach(Context) once we only support API level 23+.

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callEvents = (activity as OnCallEvents?)!!
    }
//    override fun onAttach(activity: Activity) {
//        super.onAttach(activity)
//    }
}