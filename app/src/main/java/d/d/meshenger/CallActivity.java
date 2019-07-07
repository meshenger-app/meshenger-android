package d.d.meshenger;

import android.Manifest;
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
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class CallActivity extends MeshengerActivity implements ServiceConnection, View.OnClickListener, SensorEventListener {
    private TextView statusTextView;
    private TextView nameTextView;

    private MainService.MainBinder binder = null;
    private ServiceConnection connection;

    private RTCCall currentCall;

    private boolean calledWhileScreenOff = false;

    private SensorManager sensorManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock, passiveWakeLock = null;

    private final long buttonAnimationDuration = 400;
    private final int CAMERA_PERMISSION_REQUEST_CODE =  2;

    boolean permissionRequested = false;

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

        Intent intent = getIntent();
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        if ("ACTION_START_CALL".equals(action)) {
            Log.d("CallActivity", "starting call...");
            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    binder = (MainService.MainBinder) iBinder;
                    currentCall = binder.startCall(
                            (Contact) extras.get("EXTRA_CONTACT"),
                            activeCallback,
                            findViewById(R.id.localRenderer));
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {

                }
            };
            nameTextView.setText(((Contact) extras.get("EXTRA_CONTACT")).getName());
            bindService(new Intent(this, MainService.class), connection, 0);
            findViewById(R.id.callDecline).setOnClickListener(this);
            startSensor();
        } else if ("ACTION_ACCEPT_CALL".equals(action)) {
            calledWhileScreenOff = !((PowerManager) getSystemService(POWER_SERVICE)).isScreenOn();
            passiveWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK, "wakeup");
            passiveWakeLock.acquire(10000);
            connection = this;
            bindService(new Intent(this, MainService.class), this, 0);
            nameTextView.setText(intent.getStringExtra("EXTRA_USERNAME"));
            findViewById(R.id.callAccept).setVisibility(View.VISIBLE);
            ringPhone();
            View.OnClickListener optionsListener = view -> {
                stopRingPhone();
                if (view.getId() == R.id.callDecline) {
                    Log.d(RTCCall.class.getSimpleName(), "declining call...");
                    currentCall.decline();
                    if (passiveWakeLock != null && passiveWakeLock.isHeld()) passiveWakeLock.release();
                    finish();
                } else {
                    try {
                        currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                        //currentCall.setLocalRenderer(findViewById(R.id.localRenderer));
                        currentCall.accept(passiveCallback);
                        if (passiveWakeLock != null && passiveWakeLock.isHeld())
                            passiveWakeLock.release();
                        Log.d(CallActivity.class.getSimpleName(), "call accepted");
                        findViewById(R.id.callDecline).setOnClickListener(this);
                        startSensor();
                    } catch (Exception e) {
                        e.printStackTrace();
                        stopDelayed("Error accepting call");
                        findViewById(R.id.callAccept).setVisibility(View.GONE);
                    }
                }
            };
            findViewById(R.id.callAccept).setOnClickListener(optionsListener);
            findViewById(R.id.callDecline).setOnClickListener(optionsListener);
        }

        (findViewById(R.id.videoStreamSwitch)).setOnClickListener((button) -> {
           switchVideoEnabled((ImageButton)button);
        });
        (findViewById(R.id.frontFacingSwitch)).setOnClickListener((button) -> {
            currentCall.switchFrontFacing();
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(declineReceiver, new IntentFilter("call_declined"));
    }

    BroadcastReceiver declineReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private void ringPhone(){
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

    private void stopRingPhone(){
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }

        if (ringtone != null){
            ringtone.stop();
            ringtone = null;
        }
    }

    private void switchVideoEnabled(ImageButton button){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            permissionRequested = true;
            return;
        }
        currentCall.setVideoEnabled(!currentCall.isVideoEnabled());
        ScaleAnimation animation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(buttonAnimationDuration / 2);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
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

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    findViewById(R.id.frontFacingSwitch).setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

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
        //TODO implement for api < 21
        //sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        //sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "proximity");
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
        Log.d("CallActivity", "state: " + currentCall.state);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(declineReceiver);
        stopRingPhone();
        if (currentCall.state == RTCCall.CallState.CONNECTED) {
            currentCall.decline();
        }
        currentCall.cleanup();

        if (binder != null) {
            unbindService(connection);
        }
        Log.d("CallActivity", "unregistering sensor");
        //if (sensorManager != null)
        //    Log.d("CallActivity", "unregistering sensor2");
        //    sensorManager.unregisterListener(this);
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
                setStatusText(getString(R.string.call_connecting));
                break;
            }
            case CONNECTED: {
                new Handler(getMainLooper()).post( () -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                setStatusText(getString(R.string.call_connected));
                break;
            }
            case DISMISSED: {
                stopDelayed(getString(R.string.call_denied));
                break;
            }
            case RINGING: {
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
                stopDelayed(getString(R.string.call_error));
                break;
            }
        }
    };

    private RTCCall.OnStateChangeListener passiveCallback = callState -> {
        switch (callState) {
            case CONNECTED: {
                setStatusText(getString(R.string.call_connected));
                runOnUiThread(() -> findViewById(R.id.callAccept).setVisibility(View.GONE));
                new Handler(getMainLooper()).post(() -> findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE));
                break;
            }
            case RINGING: {
                setStatusText(getString(R.string.call_ringing));
                break;
            }
            case ENDED: {
                stopDelayed(getString(R.string.call_ended));
                break;
            }
            case ERROR: {
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
        this.currentCall = binder.getCurrentCall();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }

    @Override
    public void onClick(View view) {
        Log.d(CallActivity.class.getSimpleName(), "OnClick");
        if (view.getId() == R.id.callDecline) {
            Log.d(CallActivity.class.getSimpleName(), "endCall() 1");
            currentCall.hangUp();
            finish();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d("CallActivity", "sensor changed: " + sensorEvent.values[0]);
        if (sensorEvent.values[0] == 0.0f) {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
            wakeLock.acquire();
        } else {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
            wakeLock.acquire();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
