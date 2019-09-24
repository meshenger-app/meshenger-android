package d.d.meshenger;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;


public class CallActivity extends MeshengerActivity implements ServiceConnection, SensorEventListener {
    private TextView statusTextView;
    private TextView nameTextView;

    private MainService.MainBinder binder = null;
    private ServiceConnection connection;

    private RTCCall currentCall;

    private boolean calledWhileScreenOff = false;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock  passiveWakeLock = null;

    private final long buttonAnimationDuration = 400;
    private final int CAMERA_PERMISSION_REQUEST_CODE =  2;

    private boolean permissionRequested = false;

    private Contact contact = null;
    private CallEvent.Type callEventType = null;

    private Vibrator vibrator = null;
    private Ringtone ringtone = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // keep screen on during call (prevents pausing the app and cancellation of the call)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        statusTextView = findViewById(R.id.callStatus);
        nameTextView = findViewById(R.id.callName);

        String action = getIntent().getAction();
        contact = (Contact) getIntent().getExtras().get("EXTRA_CONTACT");

        log("onCreate: " + action);

        if ("ACTION_OUTGOING_CALL".equals(action)) {
            callEventType = CallEvent.Type.OUTGOING_UNKNOWN;

            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    binder = (MainService.MainBinder) iBinder;
                    currentCall = RTCCall.startCall(
                        CallActivity.this,
                        binder,
                        contact,
                        activeCallback
                        //findViewById(R.id.localRenderer)
                    );
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    // nothing to do
                }
            };

            if (contact.getName().isEmpty()) {
                nameTextView.setText(getResources().getString(R.string.unknown_caller));
            } else {
                nameTextView.setText(contact.getName());
            }

            bindService(new Intent(this, MainService.class), connection, 0);

            View.OnClickListener declineListener = view -> {
                // end call
                currentCall.hangUp();
                callEventType = CallEvent.Type.OUTGOING_DECLINED;
                finish();
            };

            findViewById(R.id.callDecline).setOnClickListener(declineListener);
            startSensor();
        } else if ("ACTION_INCOMING_CALL".equals(action)) {
            callEventType = CallEvent.Type.INCOMING_UNKNOWN;

            calledWhileScreenOff = !((PowerManager) getSystemService(POWER_SERVICE)).isScreenOn();
            passiveWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK, "meshenger:wakeup"
            );
            passiveWakeLock.acquire(10000);

            connection = this;
            bindService(new Intent(this, MainService.class), this, 0);

            if (contact.getName().isEmpty()) {
                nameTextView.setText(getResources().getString(R.string.unknown_caller));
            } else {
                nameTextView.setText(contact.getName());
            }

            findViewById(R.id.callAccept).setVisibility(View.VISIBLE);
            startRinging();

            // decline call
            View.OnClickListener declineListener = view -> {
                stopRinging();

                log("declining call...");
                currentCall.decline();
                if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                    passiveWakeLock.release();
                }

                callEventType = CallEvent.Type.INCOMING_DECLINED;

                finish();
            };

            // hangup call
            View.OnClickListener hangupListener = view -> {
                stopRinging(); // make sure ringing has stopped ;-)

                log("hangup call...");
                currentCall.decline();
                if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                    passiveWakeLock.release();
                }

                callEventType = CallEvent.Type.INCOMING_ACCEPTED;

                finish();
            };

            View.OnClickListener acceptListener = view -> {
                stopRinging();

                log("accepted call...");
                try {
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                    //currentCall.setLocalRenderer(findViewById(R.id.localRenderer));
                    currentCall.accept(passiveCallback);
                    if (passiveWakeLock != null && passiveWakeLock.isHeld()) {
                        passiveWakeLock.release();
                    }
                    findViewById(R.id.callDecline).setOnClickListener(hangupListener);
                    startSensor();
                } catch (Exception e) {
                    e.printStackTrace();
                    stopDelayed("Error accepting call");
                    findViewById(R.id.callAccept).setVisibility(View.GONE);
                    callEventType = CallEvent.Type.INCOMING_ERROR;
                }
            };

            findViewById(R.id.callAccept).setOnClickListener(acceptListener);
            findViewById(R.id.callDecline).setOnClickListener(declineListener);
        }

        (findViewById(R.id.videoStreamSwitch)).setOnClickListener((button) -> {
           switchVideoEnabled((ImageButton)button);
        });
        (findViewById(R.id.frontFacingSwitch)).setOnClickListener((button) -> {
            currentCall.switchFrontFacing();
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(declineBroadcastReceiver, new IntentFilter("call_declined"));
    }

    BroadcastReceiver declineBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            log("declineBroadcastCastReceiver onReceive");
            finish();
        }
    };

    private void startRinging(){
        log("startRinging");
        int ringerMode = ((AudioManager) getSystemService(AUDIO_SERVICE)).getRingerMode();

        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return;
        }

        vibrator = ((Vibrator) getSystemService(VIBRATOR_SERVICE));
        long[] pattern = {1500, 800, 800, 800};
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            VibrationEffect vibe = VibrationEffect.createWaveform(pattern, 0);
            vibrator.vibrate(vibe);
        } else {
            vibrator.vibrate(pattern, 0);
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return;
        }

        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getActualDefaultRingtoneUri(getApplicationContext(), RingtoneManager.TYPE_RINGTONE));
        ringtone.play();
    }

    private void stopRinging(){
        log("stopRinging");
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }

        if (ringtone != null){
            ringtone.stop();
            ringtone = null;
        }
    }

    private void switchVideoEnabled(ImageButton button) {
        if (!Utils.hasCameraPermission(this)) {
            Utils.requestCameraPermission(this, CAMERA_PERMISSION_REQUEST_CODE);
            permissionRequested = true;
            return;
        }

        currentCall.setVideoEnabled(!currentCall.isVideoEnabled());
        ScaleAnimation animation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(buttonAnimationDuration / 2);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // nothing to do
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                button.setImageResource(currentCall.isVideoEnabled() ? R.drawable.baseline_camera_alt_black_off_48 : R.drawable.baseline_camera_alt_black_48);
                Animation a = new ScaleAnimation(0.0f, 1.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                a.setDuration(buttonAnimationDuration / 2);
                button.startAnimation(a);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // nothing to do
            }
        });
        View frontSwitch = findViewById(R.id.frontFacingSwitch);
        if (currentCall.isVideoEnabled()){
            frontSwitch.setVisibility(View.VISIBLE);
            Animation scale = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(buttonAnimationDuration);
            frontSwitch.startAnimation(scale);
        } else {
            Animation scale = new ScaleAnimation(1f, 0f, 1f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(buttonAnimationDuration);
            scale.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    // nothing to do
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    findViewById(R.id.frontFacingSwitch).setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    // nothing to do
                }
            });
            frontSwitch.startAnimation(scale);
        }
        button.startAnimation(animation);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission needed in order to start video", Toast.LENGTH_SHORT).show();
                return;
            }
            switchVideoEnabled(findViewById(R.id.videoStreamSwitch));
        }
    }

    private void startSensor() {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "meshenger:proximity");
        wakeLock.acquire();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (calledWhileScreenOff) {
            calledWhileScreenOff = false;
            return;
        }

        if (permissionRequested){
            permissionRequested = false;
            return;
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log("onDestroy");

        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineBroadcastReceiver);

        stopRinging();

        if (currentCall.state == RTCCall.CallState.CONNECTED) {
            currentCall.decline();
        }
        currentCall.cleanup();

        this.binder.addCallEvent(this.contact, this.callEventType);

        //if (binder != null) {
            unbindService(connection);
        //}

        if (wakeLock != null) {
            wakeLock.release();
        }

        if (currentCall != null && currentCall.commSocket != null && currentCall.commSocket.isConnected() && !currentCall.commSocket.isClosed()) {
            try {
                currentCall.commSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        currentCall.releaseCamera();
    }

    private RTCCall.OnStateChangeListener activeCallback = callState -> {
        switch (callState) {
            case CONNECTING: {
                log("activeCallback: CONNECTING");
                setStatusText(getString(R.string.call_connecting));
                break;
            }
            case CONNECTED: {
                log("activeCallback: CONNECTED");
                new Handler(getMainLooper()).post( () -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                setStatusText(getString(R.string.call_connected));
                break;
            }
            case DISMISSED: {
                log("activeCallback: DISMISSED");
                stopDelayed(getString(R.string.call_denied));
                break;
            }
            case RINGING: {
                log("activeCallback: RINGING");
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                log("activeCallback: ENDED");
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
                log("activeCallback: ERROR");
                stopDelayed(getString(R.string.call_error));
                break;
            }
        }
    };

    private RTCCall.OnStateChangeListener passiveCallback = callState -> {
        switch (callState) {
            case CONNECTED: {
                log("passiveCallback: CONNECTED");
                setStatusText(getString(R.string.call_connected));
                runOnUiThread(() -> findViewById(R.id.callAccept).setVisibility(View.GONE));
                new Handler(getMainLooper()).post(() -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                break;
            }
            case RINGING: {
                log("passiveCallback: RINGING");
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                log("passiveCallback: ENDED");
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
                log("passiveCallback: ERROR");
                stopDelayed(getString(R.string.call_error));
                break;
            }
        }
    };

    private void setStatusText(String text) {
        new Handler(getMainLooper()).post(() -> statusTextView.setText(text));
    }

    private void stopDelayed(String message) {
        new Handler(getMainLooper()).post(() -> {
            statusTextView.setText(message);
            new Handler().postDelayed(this::finish, 2000);
        });
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;
        this.currentCall = this.binder.getCurrentCall();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        log("sensor changed: " + sensorEvent.values[0]);

        if (sensorEvent.values[0] == 0.0f) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "meshenger:tag");
            wakeLock.acquire();
        } else {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "meshenger:tag");
            wakeLock.acquire();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // nothing to do
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
