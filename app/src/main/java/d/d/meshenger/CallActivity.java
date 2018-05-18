package d.d.meshenger;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;

import java.io.IOException;

public class CallActivity extends AppCompatActivity implements ServiceConnection, View.OnClickListener {
    TextView statusTextView, nameTextView;

    MainService.MainBinder binder = null;
    ServiceConnection connection;

    Call currentCall;

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
            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    binder = (MainService.MainBinder) iBinder;
                    currentCall = binder.startCall(
                            (Contact) extras.get("EXTRA_CONTACT"),
                            extras.getString("EXTRA_IDENTIFIER"),
                            extras.getString("EXTRA_USERNAME"),
                            activeCallback);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {

                }
            };
            bindService(new Intent(this, MainService.class), connection, 0);
            findViewById(R.id.callDecline).setOnClickListener(this);
        } else if ("ACTION_ACCEPT_CALL".equals(action)) {
            connection = this;
            bindService(new Intent(this, MainService.class), this, 0);
            nameTextView.setText(intent.getStringExtra("EXTRA_USERNAME"));
            findViewById(R.id.acceptLayout).setVisibility(View.VISIBLE);
            setStatusText("calling...");
            View.OnClickListener optionsListener = view -> {
                if (view.getId() == R.id.callDecline) {
                    Log.d(Call.class.getSimpleName(), "declining call...");
                    currentCall.decline();
                    finish();
                    return;
                }
                try {
                    currentCall.accept(passiveCallback);
                    Log.d(CallActivity.class.getSimpleName(), "call accepted");
                    findViewById(R.id.callDecline).setOnClickListener(this);
                } catch (Exception e) {
                    e.printStackTrace();
                    stopDelayed("Error accepting call");
                    findViewById(R.id.acceptLayout).setVisibility(View.GONE);
                }

            };
            findViewById(R.id.callAccept).setOnClickListener(optionsListener);
            findViewById(R.id.callDecline).setOnClickListener(optionsListener);
        }
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
    }

    d.d.meshenger.Call.OnStateChangeListener activeCallback = callState -> {

        switch (callState) {
            case CONNECTING: {
                setStatusText("connecting...");
                break;
            }
            case CONNECTED: {
                //new Handler(getMainLooper()).post(() -> findViewById(R.id.callOptions).setVisibility(View.GONE));
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

    d.d.meshenger.Call.OnStateChangeListener passiveCallback = callState -> {
        switch (callState) {
            case CONNECTED: {
                setStatusText("connected.");
                runOnUiThread(() -> findViewById(R.id.acceptLayout).setVisibility(View.GONE));
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
            currentCall.end();
            finish();
        }
    }
}
