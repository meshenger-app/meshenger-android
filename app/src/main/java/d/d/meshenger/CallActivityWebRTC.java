package d.d.meshenger;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;

public class CallActivityWebRTC extends AppCompatActivity implements SensorEventListener {
    TextView statusTextView, nameTextView;

    MainService.MainBinder binder = null;
    ServiceConnection connection;

    SensorManager sensorManager;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    PeerConnectionFactory peerConnectionFactory;
    PeerConnection localPeer;
    MediaConstraints constraints;

    Socket RTCSocket;
    String remoteSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        statusTextView = (TextView) findViewById(R.id.callStatus);
        nameTextView = (TextView) findViewById(R.id.callName);

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this).createInitializationOptions());
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        constraints = new MediaConstraints();
        constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiverAudio", "true"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiverVideo", "false"));


        Intent intent = getIntent();
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        if ("ACTION_START_CALL".equals(action)) {
            startCall((Contact) extras.get("EXTRA_CONTACT"));
            startSensor();
        } else if ("ACTION_ACCEPT_CALL".equals(action)) {
            nameTextView.setText(intent.getStringExtra("EXTRA_USERNAME"));
            findViewById(R.id.acceptLayout).setVisibility(View.VISIBLE);
            setStatusText("calling...");

            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    RTCSocket = ((MainService.MainBinder)iBinder).getCurrentRTCSocket();
                    remoteSession = ((MainService.MainBinder)iBinder).getRemoteDescription();
                    unbindService(this);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {

                }
            };
            bindService(new Intent(this, MainService.class), connection, 0);

            View.OnClickListener optionsListener = view -> {
                if (view.getId() == R.id.callDecline) {
                    log( "declining call...");
                    //currentCall.decline();
                    try {
                        RTCSocket.getOutputStream().write("{\"action\":\"declined\"}\n".getBytes());
                        RTCSocket.close();
                        finish();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    finish();
                    return;
                }
                try {
                    //currentCall.accept(passiveCallback);
                    log( "call accepted");
                    //findViewById(R.id.callDecline).setOnClickListener((view) -> {
                        //TODO
                    //});
                    acceptCall();
                    view.setVisibility(View.GONE);
                    statusTextView.setVisibility(View.GONE);
                    startSensor();
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

    private void acceptCall(){
        localPeer = peerConnectionFactory.createPeerConnection(Collections.emptyList(), new DefaultObserver(){
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);
                log("answering description: " + localPeer.getLocalDescription().description);

                try {
                    OutputStream os = RTCSocket.getOutputStream();
                    JSONObject response = new JSONObject();
                    response.put("action", "accept");
                    response.put("answer", localPeer.getLocalDescription().description);
                    os.write((response.toString() + "\n").getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("audioTrack2", peerConnectionFactory.createAudioSource(new MediaConstraints()));
        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream2");
        mediaStream.addTrack(audioTrack);
        localPeer.addStream(mediaStream);

        localPeer.setRemoteDescription(new DefaultSdpObserver(){
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                localPeer.createAnswer(new DefaultSdpObserver(){
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        localPeer.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                    }
                }, constraints);
            }
        }, new SessionDescription(SessionDescription.Type.OFFER, this.remoteSession));

    }

    private void startCall(Contact contact) {
        localPeer = peerConnectionFactory.createPeerConnection(Collections.emptyList(), new DefaultObserver() {
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);
                if (iceGatheringState != PeerConnection.IceGatheringState.COMPLETE) return;
                log("sending local description: " + localPeer.getLocalDescription().description);
                new Thread(() -> {
                    try {
                        Socket s = new Socket(contact.getAddress(), MainService.serverPort);
                        OutputStream os = s.getOutputStream();

                        JSONObject request = new JSONObject();
                        request.put("username", getIntent().getStringExtra("EXTRA_USERNAME"));
                        request.put("action", "call");
                        request.put("offer", localPeer.getLocalDescription().description);
                        request.put("identifier", getIntent().getStringExtra("EXTRA_IDENTIFIER"));

                        os.write((request.toString() + "\n").getBytes());

                        InputStream is = s.getInputStream();
                        String response = new BufferedReader(new InputStreamReader(is)).readLine();

                        JSONObject responseObject = new JSONObject(response);
                        if(responseObject.getString("action").equals("ringing")){
                            setStatusText("ringing...");
                        }
                        response = new BufferedReader(new InputStreamReader(is)).readLine();
                        responseObject = new JSONObject(response);
                        if(responseObject.getString("action").equals("declined")){
                            stopDelayed("Call declined");
                            RTCSocket.close();
                        }else if(responseObject.getString("action").equals("accept")){
                            log("remote description: \"" + responseObject.getString("answer") + "\"");
                            localPeer.setRemoteDescription(new DefaultSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, responseObject.getString("answer")));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> stopDelayed("call failed"));
                    }
                }).start();
            }
        });

        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("audioTrack1", peerConnectionFactory.createAudioSource(new MediaConstraints()));
        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream1");
        mediaStream.addTrack(audioTrack);
        localPeer.addStream(mediaStream);

        localPeer.createOffer(new DefaultSdpObserver(){
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
            }
        }, constraints);
    }

    private void stopDelayed(String message) {
        setStatusText(message);
        new Handler(getMainLooper()).postDelayed(() -> finish(), 1000);
    }

    private void log(String data) {
        Log.d("webRTC", data);
    }

    private void setStatusText(String text) {
        new Handler(getMainLooper()).post(() -> {
            statusTextView.setText(text);
        });
    }


    void startSensor() {
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
        sensorManager.unregisterListener(this);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }



    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Log.d("CallActivity", "sensor changed: " + sensorEvent.values[0]);
        if(sensorEvent.values[0] == 0.0f) {
            wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
            wakeLock.acquire();
        }else{
            wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag");
            wakeLock.acquire();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

}
