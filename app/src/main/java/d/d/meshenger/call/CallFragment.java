/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package d.d.meshenger.call;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import org.webrtc.RendererCommon.ScalingType;

import d.d.meshenger.Log;
import d.d.meshenger.R;

/**
 * Fragment for call control.
 */
public class CallFragment extends Fragment {
  private static final String TAG = "CallFragment";
  private TextView callNameView;
  private TextView callStatusView;
  private ImageButton disconnectButton;
  private ImageButton connectButton;
  private ImageButton cameraSwitchButton;
  private ImageButton videoScalingButton;
  private ImageButton toggleMuteButton;
  //private TextView captureFormatText;
  //private SeekBar captureFormatSlider;
  private OnCallEvents callEvents;
  private ScalingType scalingType;
  private boolean videoCallEnabled = true;

  String callNameText = "";
  String callStatusText = "";

  /**
   * Call control interface for container activity.
   */
  public interface OnCallEvents {
    void onCallHangUp();
    void onCallAccept();
    void onCameraSwitch();
    void onVideoScalingSwitch(ScalingType scalingType);
    void onVideoMirrorSwitch(boolean mirror); // added by me
    void onCaptureFormatChange(int width, int height, int framerate);
    boolean onToggleMic();
  }

  boolean mirror = true;

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    Log.d(TAG, "onCreateView");
    View controlView = inflater.inflate(R.layout.fragment_call, container, false);

    // Create UI controls.
    callNameView = controlView.findViewById(R.id.call_name);
    callStatusView = controlView.findViewById(R.id.call_status);
    disconnectButton = controlView.findViewById(R.id.button_call_disconnect);
    connectButton = controlView.findViewById(R.id.button_call_connect);
    cameraSwitchButton = controlView.findViewById(R.id.button_call_switch_camera);
    videoScalingButton = controlView.findViewById(R.id.button_call_scaling_mode);
    toggleMuteButton = controlView.findViewById(R.id.button_call_toggle_mic);
    //captureFormatText = controlView.findViewById(R.id.capture_format_text_call);
    //captureFormatSlider = controlView.findViewById(R.id.capture_format_slider_call);

    // Add buttons click events.
    disconnectButton.setOnClickListener((View view) -> {
      callEvents.onCallHangUp();
    });

    connectButton.setOnClickListener((View view) -> {
      callEvents.onCallAccept();
    });

    cameraSwitchButton.setOnClickListener((View view) -> {
      callEvents.onCameraSwitch();

      // TODO: rework an TODO
      mirror = !mirror;
      callEvents.onVideoMirrorSwitch(mirror);
      //vieData.capture->SetRotateCapturedFrames(cameraId, 270
    });

    videoScalingButton.setOnClickListener((View view) -> {
      if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
        videoScalingButton.setBackgroundResource(R.drawable.ic_action_full_screen);
        scalingType = ScalingType.SCALE_ASPECT_FIT;
      } else {
        videoScalingButton.setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
        scalingType = ScalingType.SCALE_ASPECT_FILL;
      }
      callEvents.onVideoScalingSwitch(scalingType);
    });
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    toggleMuteButton.setOnClickListener((View view) -> {
      boolean enabled = callEvents.onToggleMic();
      toggleMuteButton.setAlpha(enabled ? 1.0f : 0.3f);
    });

    return controlView;
  }

  public void setContactName(String name) {
    callNameText = name;
  }


  public void setCallStatus(String status) {
    /*
    <string name="call_connecting">Calling...</string>
    <string name="call_connected">Connected.</string>
    <string name="call_denied">Declined</string>
    <string name="call_ringing">Ringing...</string>
    <string name="call_ended">Call Ended</string>
    <string name="call_error">Error</string>
    */
    callStatusText = status;
  }

  @Override
  public void onStart() {
    super.onStart();
    Log.d(TAG, "onStart()");

    boolean captureSliderEnabled = false;
    //Bundle args = getArguments();
    //if (args != null) {
      //String contactName = "contactName"; // args.getString(CallActivity.EXTRA_ROOMID);
    callNameView.setText(this.callNameText);
    callStatusView.setText(this.callStatusText);

      videoCallEnabled = true; // args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
    //  captureSliderEnabled = videoCallEnabled && false; // args.getBoolean(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
    //}
    if (!videoCallEnabled) {
      cameraSwitchButton.setVisibility(View.INVISIBLE);
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

  // Replace with onAttach(Context) once we only support API level 23+.
  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    callEvents = (OnCallEvents) activity;
  }
}
