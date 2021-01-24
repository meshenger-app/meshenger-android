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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import d.d.meshenger.Contact;
import d.d.meshenger.Event;
import d.d.meshenger.MeshengerActivity;
import d.d.meshenger.Utils;
import d.d.meshenger.call.AppRTCAudioManager.AudioDevice;
import d.d.meshenger.call.AppRTCAudioManager.AudioManagerEvents;
import d.d.meshenger.call.AppRTCClient.SignalingParameters;
import d.d.meshenger.call.PeerConnectionClient.DataChannelParameters;
import d.d.meshenger.call.PeerConnectionClient.PeerConnectionParameters;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import d.d.meshenger.MainService;
import d.d.meshenger.Settings;
import d.d.meshenger.R;


/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends MeshengerActivity implements DirectRTCClient.SignalingEvents,
                                                      PeerConnectionClient.PeerConnectionEvents,
                                                      CallFragment.OnCallEvents {
  private static final String TAG = "CallActivity";

  private static final int INTERNET_PERMISSION_REQUEST_CODE = 2;
  private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 3;
  private static final int MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE = 4;
  private static final int CAMERA_PERMISSION_REQUEST_CODE = 5;

  // List of mandatory application permissions.
  private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
      "android.permission.RECORD_AUDIO", "android.permission.INTERNET"}; //, "android.permission.CAMERA"};

  // Peer connection statistics callback period in ms.
  private static final int STAT_CALLBACK_PERIOD = 1000;

  private static class ProxyVideoSink implements VideoSink {
    private VideoSink target;

    @Override
    synchronized public void onFrame(VideoFrame frame) {
      if (target == null) {
        Logging.d(TAG, "Dropping frame in proxy because target is null.");
        return;
      }

      target.onFrame(frame);
    }

    synchronized public void setTarget(VideoSink target) {
      this.target = target;
    }
  }

  private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
  private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
  @Nullable
  private PeerConnectionClient peerConnectionClient;
  @Nullable
  private DirectRTCClient appRtcClient;
  @Nullable
  private SignalingParameters signalingParameters;
  @Nullable private AppRTCAudioManager audioManager;
  @Nullable
  private SurfaceViewRenderer pipRenderer;
  @Nullable
  private SurfaceViewRenderer fullscreenRenderer;
  @Nullable
  private VideoFileRenderer videoFileRenderer;
  private final List<VideoSink> remoteSinks = new ArrayList<>();
  private Toast logToast;
  private boolean activityRunning; // needed?
  @Nullable
  private PeerConnectionParameters peerConnectionParameters;
  private boolean connected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs;
  private boolean micEnabled = true;
  // True if local view is in the fullscreen renderer.
  private boolean isSwappedFeeds;

  // Controls
  private CallFragment callFragment;
  private HudFragment hudFragment;
  private EglBase eglBase; //temporary

  private Vibrator vibrator;
  private Ringtone ringtone;

  private Event.CallType callType = Event.CallType.UNKNOWN;

  @Override
  // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
  // LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
  @SuppressWarnings("deprecation")
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    // Set window styles for fullscreen-window size. Needs to be done before
    // adding content.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
        | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
    setContentView(R.layout.activity_call);

    connected = false;
    signalingParameters = null;

    // Create UI controls.
    pipRenderer = findViewById(R.id.pip_video_view);
    fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
    //fullscreenRenderer.setBackgroundColor(Color.parseColor("#00aacc"));

    callFragment = new CallFragment();
    hudFragment = new HudFragment();

    // Show/hide call control fragment on view click.
    fullscreenRenderer.setOnClickListener((View view) -> {
      toggleCallControlFragmentVisibility();
    });

    // Swap feeds on pip view click.
    pipRenderer.setOnClickListener((View view) -> {
      setSwappedFeeds(!isSwappedFeeds);
    });

    remoteSinks.add(remoteProxyRenderer);

    final Intent intent = getIntent();
    //final EglBase eglBase = EglBase.create();
    eglBase = EglBase.create();

    // Create video renderers.
    pipRenderer.init(eglBase.getEglBaseContext(), null);
    pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);

    fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
    fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);

    pipRenderer.setZOrderMediaOverlay(true);
    pipRenderer.setEnableHardwareScaler(true /* enabled */);
    fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);

{
  fullscreenRenderer.setVisibility(View.INVISIBLE);
  pipRenderer.setVisibility(View.INVISIBLE);
}

    DataChannelParameters dataChannelParameters = null;
    if (intent.getBooleanExtra("EXTRA_DATA_CHANNEL_ENABLED", true)) {
      dataChannelParameters = new DataChannelParameters(
        true, // ORDERED
        -1, // MAX_RETRANSMITS_MS
        -1, // MAX_RETRANSMITS
        "", //PROTOCOL
        false, //NEGOTIATED
        -1 // ID
      );
      dataChannelParameters.debug();
    }
    Settings settings = MainService.instance.getSettings();
    peerConnectionParameters = new PeerConnectionParameters(
      true, //settings.getPlayVideo(),
      true, //settings.getRecordVideo(),
      true, //settings.getPlayAudio(),
      true, //settings.getRecordAudio(),
      true, // VIDEO_CALL // TODO: remove
      0, // VIDEO_WIDTH
      0, // VIDEO_HEIGHT
      0, // VIDEO_FPS
      0, // VIDEO_BITRATE
      settings.getVideoCodec(),
      true, // HWCODEC_ENABLED
      false, // FLEXFEC_ENABLED
      0, // AUDIO_BITRATE
      settings.getAudioCodec(),
      settings.getAudioProcessing(), // NOAUDIOPROCESSING_ENABLED
      false, // OPENSLES_ENABLED
      false, // DISABLE_BUILT_IN_AEC
      false, // DISABLE_BUILT_IN_AGC
      false, // DISABLE_BUILT_IN_NS
      false, // DISABLE_WEBRTC_AGC_AND_HPF
      dataChannelParameters
    );
    peerConnectionParameters.debug();

    // TODO: move into startCall!
    // so we can check permission over and over...
    checkPermissions(peerConnectionParameters);

/*
   // Check for mandatory permissions.
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }
*/

    // Activate call and HUD fragments and start the call.
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.add(R.id.call_fragment_container, callFragment);
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();

    appRtcClient = DirectRTCClient.getCurrentCall();

    if (appRtcClient == null) {
      disconnectWithErrorMessage("No connection expected!");
      return;
    }

    appRtcClient.setEventListener(this);

    //callFragment.update(false, peerConnectionParameters);
    callType = Event.CallType.MISSED;

    switch (appRtcClient.getCallDirection()) {
    case INCOMING:
        callFragment.setCallStatus(getResources().getString(R.string.call_connecting));
        Log.d(TAG, "Incoming call");

      if (MainService.instance.getSettings().getAutoAcceptCall()) {
        startCall();
      } else {
        // start ringing and wait for connect call
        Log.d(TAG, "start ringing");
        startRinging();
        //fullscreenRenderer.setBackgroundColor(Color.parseColor("#00aacc"));
      }
      break;
    case OUTGOING:
      callFragment.setCallStatus(getResources().getString(R.string.call_ringing));
      Log.d(TAG, "Outgoing call");

      if (MainService.instance.getSettings().getAutoConnectCall()) {
        startCall();
      } else {
        Log.d(TAG, "wait for explicit button call to start call");
      }
      break;
    default:
      reportError("Invalid call direction!");
      return;
    }
  }

    private void checkPermissions(PeerConnectionParameters peerConnectionParameters) {
        Log.d(TAG, "checkPermissions");

        if (peerConnectionParameters.recordAudio) {
            if (!Utils.hasPermission(this, Manifest.permission.RECORD_AUDIO)) {
                Log.d(TAG, "Ask for RECORD_AUDIO permissions");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
                return;
            }
        }

        if (peerConnectionParameters.playAudio) {
            if (!Utils.hasPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)) {
                Log.d(TAG, "Ask for MODIFY_AUDIO_SETTINGS permissions");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS}, MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE);
                return;
            }
        }

        if (peerConnectionParameters.recordVideo) {
            if (!Utils.hasPermission(this, Manifest.permission.CAMERA)) {
                Log.d(TAG, "Ask for CAMERA permissions");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }
        }

        // required!
        if (!Utils.hasPermission(this, Manifest.permission.INTERNET)) {
            Log.d(TAG, "Ask for INTERNET permissions");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, INTERNET_PERMISSION_REQUEST_CODE);
            return;
        }
    }

  @TargetApi(19)
  private static int getSystemUiVisibility() {
    int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }
    return flags;
  }

  private void startRinging() {
    Log.d(TAG, "startRinging");
    int ringerMode = ((AudioManager) getSystemService(AUDIO_SERVICE)).getRingerMode();

    switch (ringerMode) {
      case AudioManager.RINGER_MODE_NORMAL:
        Log.d(TAG, "ringerMode: RINGER_MODE_NORMAL");
        break;
      case AudioManager.RINGER_MODE_SILENT:
        Log.d(TAG, "ringerMode: RINGER_MODE_SILENT");
        break;
      case AudioManager.RINGER_MODE_VIBRATE:
        Log.d(TAG, "ringerMode: RINGER_MODE_VIBRATE");
        break;
      default:
        Log.d(TAG, "ringerMode: unknown");
    }

    if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
      // alarm disabled
      return;
    }

    if (vibrator == null) {
      vibrator = ((Vibrator) getSystemService(VIBRATOR_SERVICE));
    }

    long[] pattern = {1500, 800, 800, 800};
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      VibrationEffect vibe = VibrationEffect.createWaveform(pattern, 0);
      vibrator.vibrate(vibe);
    } else {
      vibrator.vibrate(pattern, 0);
    }

    if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
      // only vibrating
      return;
    }

    if (ringtone == null) {
      ringtone = RingtoneManager.getRingtone(getApplicationContext(), RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_RINGTONE));
    }

    // start ringing
    ringtone.play();
  }

  private void stopRinging() {
    Log.d(TAG, "stopRinging");
    if (vibrator != null) {
      vibrator.cancel();
      vibrator = null;
    }

    if (ringtone != null){
      ringtone.stop();
      ringtone = null;
    }
  }

//onPause is called before, onResume afterwards
  // TODO: need to apply changed permissions
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);

      if (grantResults.length == 0) {
        // permission request was aborted / interrupted
        return;
      }

      switch (requestCode) {
          case RECORD_AUDIO_PERMISSION_REQUEST_CODE:
              if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                  Toast.makeText(this, "Microphone disabled", Toast.LENGTH_LONG).show();
                  peerConnectionParameters.recordAudio = false;
              }
              startCall();
          case MODIFY_AUDIO_SETTINGS_PERMISSION_REQUEST_CODE:
              if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                  Toast.makeText(this, "Audio disabled", Toast.LENGTH_LONG).show();
                peerConnectionParameters.playAudio = false;
              }
              startCall();
              break;
          case CAMERA_PERMISSION_REQUEST_CODE:
              if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                  Toast.makeText(this, "Camera disabled", Toast.LENGTH_LONG).show();
                peerConnectionParameters.recordVideo = false;
              }
              startCall();
              break;
          case INTERNET_PERMISSION_REQUEST_CODE:
              if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                  Toast.makeText(this, "Internet access required", Toast.LENGTH_LONG).show();
                  isError = true;
                  disconnect();
              }
              break;
          default:
              reportError("Unknown permission request code: " + requestCode);
      }
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra("EXTRA_CAMERA2", true);
  }

  private boolean captureToTexture() {
    return true; //getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
  }

  private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    // First, try to find front facing camera
    Log.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    // Front facing camera not found, try something else
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  @Override
  public void onStop() {
    super.onStop();
    activityRunning = false;

    if (peerConnectionClient != null) {
      peerConnectionClient.stopVideoSource();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    activityRunning = true;

    if (peerConnectionClient != null) {
      peerConnectionClient.startVideoSource();
    }
  }

  @Override
  protected void onDestroy() {
    Thread.setDefaultUncaughtExceptionHandler(null);

    disconnect();

    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;

    super.onDestroy();
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override
  public void onCallHangUp() {
    Log.d(TAG, "onCallHangUp");

    if (callType != Event.CallType.ACCEPTED) {
        callType = Event.CallType.DECLINED;
    }

    disconnect();
  }

  @Override
  public void onCallAccept() {
    Log.d(TAG, "onCallAccept");

    callType = Event.CallType.ACCEPTED;

    startCall();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  // added by myself
  @Override
  public void onVideoMirrorSwitch(boolean mirror) {
    fullscreenRenderer.setMirror(mirror); //!fullscreenRenderer.getMirror());
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    fullscreenRenderer.setScalingType(scalingType);
  }

  @Override
  public void onCaptureFormatChange(int width, int height, int framerate) {
    if (peerConnectionClient != null) {
      peerConnectionClient.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override
  public boolean onToggleMic() {
    if (peerConnectionClient != null) {
      // TODO: set in peerConnectionParameters
      micEnabled = !micEnabled;
      peerConnectionClient.setAudioEnabled(micEnabled);
    }
    return micEnabled;
  }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {
    Log.d(TAG, "toggleCallControlFragmentVisibility");
    if (!connected || !callFragment.isAdded()) {
      return;
    }

    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void startCall() {
    if (peerConnectionClient != null) {
      logAndToast("Call already started");
      return;
    }

    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }

    callType = Event.CallType.ACCEPTED;
    //callFragment.update(true, peerConnectionParameters);

    // Start with local feed in fullscreen and swap it to the pip when the call is connected.
    setSwappedFeeds(true /* isSwappedFeeds */);

    fullscreenRenderer.setVisibility(View.VISIBLE);
    pipRenderer.setVisibility(View.VISIBLE);

    {
      // Create peer connection client.
      peerConnectionClient = new PeerConnectionClient(
          getApplicationContext(), eglBase, peerConnectionParameters, CallActivity.this);

      PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
      //options.disableNetworkMonitor = true; // does not work! from email by dante carvalho to fix connection in case of tethering
      peerConnectionClient.createPeerConnectionFactory(options);
    }

    callStartedTimeMs = System.currentTimeMillis();

    // Start room connection.
    //logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    logAndToast("appRtcClient.connectToRoom");
    appRtcClient.connectToRoom(); //this.contact_address, this.contact_port);

    // Create and audio manager that will take care of audio routing,
    // audio modes, audio device enumeration etc.
    audioManager = AppRTCAudioManager.create(getApplicationContext(), MainService.instance.getSettings().getSpeakerphone());
    // Store existing audio settings and change audio mode to
    // MODE_IN_COMMUNICATION for best possible VoIP performance.
    Log.d(TAG, "Starting the audio manager...");
    audioManager.start(new AudioManagerEvents() {
      // This method will be called each time the number of available audio
      // devices has changed.
      @Override
      public void onAudioDeviceChanged(AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
        onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
      }
    });
  }

  private void callConnected() {
      stopRinging();

      final long delta = System.currentTimeMillis() - callStartedTimeMs;
      Log.i(TAG, "Call connected: delay=" + delta + "ms");
      if (peerConnectionClient == null || isError) {
        Log.w(TAG, "Call is connected in closed or error state");
        return;
    }

    callFragment.setCallStatus(getResources().getString(R.string.call_connected));

    // Enable statistics callback.
    peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
    setSwappedFeeds(false /* isSwappedFeeds */);
  }

  // This method is called when the audio manager reports audio device change,
  // e.g. from wired headset to speakerphone.
  private void onAudioManagerDevicesChanged(
      final AudioDevice device, final Set<AudioDevice> availableDevices) {
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);
    // TODO(henrika): add callback handler.
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnect() {
    // read -1; message is null causes:
    // called from  onChannelClose, onDestroy 
    Log.d(TAG, "disconnect() from thread " + Thread.currentThread().getName() + " and print stacktrace now: ");
    // print stack trace as help:
    Thread.dumpStack();

    stopRinging();

    if (isError) {
        callType = Event.CallType.ERROR;
    }

    if (appRtcClient != null) {
        Log.d(TAG, "add event: " + callType.name());
        MainService.instance.getEvents().addEvent(appRtcClient.getContact(), appRtcClient.getCallDirection(), callType);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("events_changed"));
    }

    activityRunning = false;
    remoteProxyRenderer.setTarget(null);
    localProxyVideoSink.setTarget(null);

    if (appRtcClient != null) {
      logAndToast("appRtcClient.disconnectFromRoom");
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
      DirectRTCClient.setCurrentCall(null);
    }

    if (pipRenderer != null) {
      pipRenderer.release();
      pipRenderer = null;
    }

    if (videoFileRenderer != null) {
      videoFileRenderer.release();
      videoFileRenderer = null;
    }

    if (fullscreenRenderer != null) {
      fullscreenRenderer.release();
      fullscreenRenderer = null;
    }

    if (peerConnectionClient != null) {
      peerConnectionClient.close();
      peerConnectionClient = null;
    }

    if (audioManager != null) {
      audioManager.stop();
      audioManager = null;
    }

    if (connected && !isError) {
      setResult(RESULT_OK);
    } else {
      setResult(RESULT_CANCELED);
    }

    Log.d(TAG, "finish");

    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (!activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
        .setTitle("Connection error")
        .setMessage(errorMessage)
        .setCancelable(false)
        .setNeutralButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                    disconnect();
                  }
                })
        .create()
        .show();
    }
  }

  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  private void reportError(final String description) {
    runOnUiThread(() -> {
      if (!isError) {
        isError = true;
        disconnectWithErrorMessage(description);
      }
    });
  }

  private @Nullable VideoCapturer createVideoCapturer() {
    final VideoCapturer videoCapturer;
    if (useCamera2()) {
      if (!captureToTexture()) {
        reportError("Camera2 only supports capturing to texture. Either disable Camera2 or enable capturing to texture in the options.");
        return null;
      }

      Logging.d(TAG, "Creating capturer using camera2 API.");
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    } else {
      Logging.d(TAG, "Creating capturer using camera1 API.");
      videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
    }
    if (videoCapturer == null) {
      reportError("Failed to open camera");
      return null;
    }
    return videoCapturer;
  }

  private void setSwappedFeeds(boolean isSwappedFeeds) {
    Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
    this.isSwappedFeeds = isSwappedFeeds;
    localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
    remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
    fullscreenRenderer.setMirror(isSwappedFeeds);
    pipRenderer.setMirror(!isSwappedFeeds);
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and
  // are routed to UI thread.
  private void onConnectedToRoomInternal(final SignalingParameters params) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    stopRinging();

    if (appRtcClient.getContact() != null) {
      Contact contact = appRtcClient.getContact();
      if (!contact.getName().isEmpty()) {
        callFragment.setContactName(contact.getName());
      } else {
        String unknown_caller = Utils.getUnknownCallerName(this.getApplicationContext(), contact.getPublicKey());
        callFragment.setContactName(unknown_caller);
      }
      callFragment.setCallStatus(getResources().getString(R.string.call_connecting));
    }

    signalingParameters = params;
    logAndToast("Creating peer connection, delay=" + delta + "ms");
    VideoCapturer videoCapturer = null;
    if (peerConnectionParameters.videoCallEnabled) {
      videoCapturer = createVideoCapturer();
    }
    peerConnectionClient.createPeerConnection2(
        localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters.iceServers);

    if (signalingParameters.initiator) { // INCOMING call
      logAndToast("Creating OFFER...");
      // Create offer. Offer SDP will be sent to answering client in
      // PeerConnectionEvents.onLocalDescription event.
      peerConnectionClient.createOffer();
    } else {
      if (params.offerSdp != null) {
        peerConnectionClient.setRemoteDescription(params.offerSdp);
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in
        // PeerConnectionEvents.onLocalDescription event.
        peerConnectionClient.createAnswer();
      }
      if (params.iceCandidates != null) {
        // Add remote ICE candidates from room.
        for (IceCandidate iceCandidate : params.iceCandidates) {
          peerConnectionClient.addRemoteIceCandidate(iceCandidate);
        }
      }
    }
  }

  // called from DirectRTCClient.onTCPConnected (if we are server)
  // and DirectRTCClient.onTCPMessage (with sdp from offer)
  @Override
  public void onConnectedToRoom(final SignalingParameters params) {
    runOnUiThread(() -> {
      onConnectedToRoomInternal(params);
    });
  }

  // called from DirectRTCClient.onTCPMessage
  @Override
  public void onRemoteDescription(final SessionDescription desc) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
        return;
      }
      logAndToast("Received remote " + desc.type + ", delay=" + delta + "ms");
      peerConnectionClient.setRemoteDescription(desc);
      if (!signalingParameters.initiator) {
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in
        // PeerConnectionEvents.onLocalDescription event.
        peerConnectionClient.createAnswer();
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.addRemoteIceCandidate(candidate);
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.removeRemoteIceCandidates(candidates);
    });
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(() -> {
      logAndToast("Remote end hung up; dropping PeerConnection");
      disconnect();
    });
  }

  @Override
  public void onChannelError(final String description) {
    reportError(description);
  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and
  // are routed to UI thread.
  @Override
  public void onLocalDescription(final SessionDescription desc) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        logAndToast("Sending " + desc.type + ", delay=" + delta + "ms");
        if (signalingParameters.initiator) {
          logAndToast("appRtcClient.sendOfferSdp");
          appRtcClient.sendOfferSdp(desc);
        } else {
          logAndToast("appRtcClient.sendAnswerSdp");
          appRtcClient.sendAnswerSdp(desc);
        }
      }
      if (peerConnectionParameters.videoMaxBitrate > 0) {
        Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
        peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
      }
    });
  }

  @Override
  public void onIceCandidate(final IceCandidate candidate) {
    Log.d(TAG, "appRtcClient.sendLocalIceCandidate");
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
    Log.d(TAG, "appRtcClient.sendLocalIceCandidateRemovals");
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidateRemovals(candidates);
      }
    });
  }

  @Override
  public void onIceConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      logAndToast("ICE connected, delay=" + delta + "ms");
    });
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(() -> {
        logAndToast("ICE disconnected");
    });
  }

// not called?
  @Override
  public void onConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      logAndToast("DTLS connected, delay=" + delta + "ms");
      connected = true;
      callConnected();
    });
  }

  @Override
  public void onDisconnected() {
    runOnUiThread(() -> {
      logAndToast("DTLS disconnected");
      connected = false;
      disconnect();
    });
  }

  @Override
  public void onPeerConnectionClosed() {}

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    runOnUiThread(() -> {
      if (!isError && connected) {
        hudFragment.updateEncoderStatistics(reports);
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    reportError(description);
  }
}
