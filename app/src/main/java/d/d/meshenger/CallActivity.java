package d.d.meshenger;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;

public class CallActivity extends AppCompatActivity implements ServiceConnection, View.OnClickListener, SensorEventListener {
    private TextView statusTextView;
    private TextView nameTextView;

    private MainService.MainBinder binder = null;
    private ServiceConnection connection;

    private RTCCall currentCall;


    private SensorManager sensorManager;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private final long buttonAnimationDuration = 400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

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
                            extras.getString("EXTRA_IDENTIFIER"),
                            extras.getString("EXTRA_USERNAME"),
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
            connection = this;
            bindService(new Intent(this, MainService.class), this, 0);
            nameTextView.setText(intent.getStringExtra("EXTRA_USERNAME"));
            findViewById(R.id.callAccept).setVisibility(View.VISIBLE);
            setStatusText("calling...");
            View.OnClickListener optionsListener = view -> {
                if (view.getId() == R.id.callDecline) {
                    Log.d(RTCCall.class.getSimpleName(), "declining call...");
                    currentCall.decline();
                    finish();
                    return;
                }
                try {
                    currentCall.setRemoteRenderer(findViewById(R.id.remoteRenderer));
                    //currentCall.setLocalRenderer(findViewById(R.id.localRenderer));
                    currentCall.accept(passiveCallback);
                    Log.d(CallActivity.class.getSimpleName(), "call accepted");
                    findViewById(R.id.callDecline).setOnClickListener(this);
                    startSensor();
                } catch (Exception e) {
                    e.printStackTrace();
                    stopDelayed("Error accepting call");
                    findViewById(R.id.callAccept).setVisibility(View.GONE);
                }

            };
            findViewById(R.id.callAccept).setOnClickListener(optionsListener);
            findViewById(R.id.callDecline).setOnClickListener(optionsListener);
        }

        (findViewById(R.id.videoStreamSwitch)).setOnClickListener((button) -> {
            currentCall.setVideoEnabled(!currentCall.isVideoEnabled());
            ScaleAnimation animation = new ScaleAnimation(1.0f, 0.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            animation.setDuration(buttonAnimationDuration / 2);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    ((ImageButton) button).setImageResource(currentCall.isVideoEnabled() ? R.drawable.baseline_camera_alt_black_off_48 : R.drawable.baseline_camera_alt_black_48);
                    Animation a = new ScaleAnimation(0.0f, 1.0f, 1.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    a.setDuration(buttonAnimationDuration / 2);
                    button.startAnimation(a);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            View frontSwitch = findViewById(R.id.frontFacingSwitch);
            if(currentCall.isVideoEnabled()){
                frontSwitch.setVisibility(View.VISIBLE);
                Animation scale = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                scale.setDuration(buttonAnimationDuration);
                frontSwitch.startAnimation(scale);
            }else{
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
        });
        (findViewById(R.id.frontFacingSwitch)).setOnClickListener((button) -> {
            currentCall.switchFrontFacing();
        });

    }

    private void startSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        Sensor s = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binder != null) {
            unbindService(connection);
        }
        if (sensorManager != null)
            sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
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
                setStatusText("connecting...");
                break;
            }
            case CONNECTED: {
                new Handler(getMainLooper()).post(() -> {
                    findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE);
                });
                setStatusText("connected.");
                break;
            }
            case DISMISSED: {
                //setStatusText("dismissed");
                stopDelayed("call denied");
                break;
            }
            case RINGING: {
                setStatusText("ringing...");
                break;
            }
            case ENDED: {
                stopDelayed("call ended");
                break;
            }
        }
    };

    private RTCCall.OnStateChangeListener passiveCallback = callState -> {
        switch (callState) {
            case CONNECTED: {
                setStatusText("connected.");
                runOnUiThread(() -> findViewById(R.id.callAccept).setVisibility(View.GONE));
                new Handler(getMainLooper()).post(() -> {
                    findViewById(R.id.videoStreamSwitchLayout).setVisibility(View.VISIBLE);
                });
                break;
            }
            case RINGING: {
                setStatusText("calling...");
                break;
            }
            case ENDED: {
                stopDelayed("call ended");
                break;
            }
        }
    };

    private void setStatusText(String text) {
        new Handler(getMainLooper()).post(() -> {
            statusTextView.setText(text);
        });
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
