package d.d.meshenger;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.ColorInt;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.util.TypedValue;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;
import com.goterl.lazycode.lazysodium.utils.Key;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

public class RTCCall implements DataChannel.Observer {

    enum CallState {CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR}

    private final String StateChangeMessage = "StateChange";
    private final String CameraDisabledMessage = "CameraDisabled";
    private final String CameraEnabledMessage = "CameraEnabled";

    private OnStateChangeListener listener;

    Socket commSocket;
    private PeerConnectionFactory factory;
    private PeerConnection connection;

    private MediaConstraints constraints;

    private String offer;
    private Box.Lazy box;
    private KeyPair keyPair;
    protected LazySodiumAndroid ls;
    public byte[] nonce;
    KeyPair encryptionKeyPair;

    private ContactSqlHelper sqlHelper;
    private ArrayList<Contact> contacts;

    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;

    private EglBase.Context sharedContext;
    private CameraVideoCapturer capturer;

    private MediaStream upStream;
    private DataChannel dataChannel;

    private boolean videoEnabled;

    private Context context;

    public CallState state;

    public void setRemoteRenderer(SurfaceViewRenderer remoteRenderer) {
        this.remoteRenderer = remoteRenderer;
    }

    private RTCCall(Contact target, OnStateChangeListener listener, Context context) {
        log("starting call to " + target.getAddress());
        initRTC(context);
        this.context = context;
        context.setTheme(AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        log("init RTC done");
        this.listener = listener;
        new Thread(() -> {
            log("creating PeerConnection");
            connection = factory.createPeerConnection(Collections.emptyList(), new DefaultObserver() {
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("transferring offer...");
                        try {
                            commSocket = new Socket(target.getAddress().replace("%zone", "%wlan0"), MainService.serverPort);
                            OutputStream os = commSocket.getOutputStream();
                            reportStateChange(CallState.CONNECTING);
                            JSONObject object = new JSONObject();
                            object.put("action", "call");
                            ls = new LazySodiumAndroid(new SodiumAndroid());
                            box = (Box.Lazy) ls;
                            nonce = ls.nonce(Box.NONCEBYTES);
                            object.put("nonce" , bytesToHex(nonce));
                            object.put("offer", encryptOffer(target.getIdentifier()));
                            os.write((object.toString() + "\n").getBytes());
                            BufferedReader reader = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
                            String response = reader.readLine();
                            JSONObject responseObject = new JSONObject(response);
                            if (!responseObject.getString("action").equals("ringing")) {
                                commSocket.close();
                                reportStateChange(CallState.ERROR);
                                return;
                            }
                            log("ringing...");
                            reportStateChange(CallState.RINGING);
                            response = reader.readLine();
                            responseObject = new JSONObject(response);

                            if (responseObject.getString("action").equals("connected")) {
                                log("connected");
                                reportStateChange(CallState.CONNECTED);
                                log("answer: " + responseObject.getString("answer"));
                                handleAnswer(responseObject.getString("answer"));
                            } else if (responseObject.getString("action").equals("dismissed")) {
                                reportStateChange(CallState.DISMISSED);
                                commSocket.close();
                            } else {
                                reportStateChange(CallState.ERROR);
                                commSocket.close();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                        }
                    }
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("change " + iceConnectionState.name());
                    super.onIceConnectionChange(iceConnectionState);
                    if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED);
                    }
                }

                @Override
                public void onAddStream(MediaStream mediaStream) {
                    super.onAddStream(mediaStream);
                    handleMediaStream(mediaStream);
                }

                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    super.onDataChannel(dataChannel);
                    RTCCall.this.dataChannel = dataChannel;
                    dataChannel.registerObserver(RTCCall.this);
                }
            });
            log("PeerConnection created");
            connection.addStream(createStream());
            this.dataChannel = connection.createDataChannel("data", new DataChannel.Init());
            this.dataChannel.registerObserver(this);
            connection.createOffer(new DefaultSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    connection.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                }
            }, constraints);
        }).start();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void encryptionKeys(String identifier){
       sqlHelper = new ContactSqlHelper(context);
       String pubKey = sqlHelper.getPublicKeyFromContacts(identifier);
       Key pub_key = Key.fromHexString(pubKey);
       encryptionKeyPair = new KeyPair(pub_key, keyPair.getSecretKey());

    }

    public String encryptOffer(String identifier) throws SodiumException {
        ls = new LazySodiumAndroid(new SodiumAndroid());
        box = (Box.Lazy) ls;
        keyPair = box.cryptoBoxKeypair();
        encryptionKeys(identifier);
        String encrypted = ls.cryptoBoxEasy(connection.getLocalDescription().description, nonce, encryptionKeyPair);
        return encrypted;
    }


    public void switchFrontFacing() {
        if (this.capturer == null) return;
        this.capturer.switchCamera(null);
    }

    @Override
    public void onBufferedAmountChange(long l) {

    }

    @Override
    public void onStateChange() {

    }

    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        String s = new String(data);
        JSONObject object = null;
        try {
            object = new JSONObject(s);
            if (object.has(StateChangeMessage)) {
                String state = object.getString(StateChangeMessage);
                switch (state) {
                    case CameraEnabledMessage:
                    case CameraDisabledMessage: {
                        setRemoteVideoEnabled(state.equals(CameraEnabledMessage));
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void setRemoteVideoEnabled(boolean enabled) {
        log("setting remote video enabled: " + enabled);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (enabled) {
                this.remoteRenderer.setBackgroundColor(Color.TRANSPARENT);
            } else {
                TypedValue typedValue = new TypedValue();
                Resources.Theme theme = this.context.getTheme();
                theme.resolveAttribute(R.attr.backgroundCardColor, typedValue, true);
                @ColorInt int color = typedValue.data;
                this.remoteRenderer.setBackgroundColor(color);
            }
        });
    }

    public boolean isVideoEnabled(){
        return this.videoEnabled;
    }

    public void setVideoEnabled(boolean enabled) {
        this.videoEnabled = enabled;
        try {
            if (enabled) {
                this.capturer.startCapture(500, 500, 30);
            } else {
                this.capturer.stopCapture();
            }
            JSONObject object = new JSONObject();
            object.put(StateChangeMessage, enabled ? CameraEnabledMessage : CameraDisabledMessage);
            dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(object.toString().getBytes()), false));
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*private void initLocalRenderer() {
        if (this.localRenderer == null) return;
        log("really initng " + (this.sharedContext == null));
        this.localRenderer.init(this.sharedContext, null);
        this.localCameraTrack.addSink(localRenderer);
        this.capturer.startCapture(500, 500, 30);
    }*/

    /*private void initVideoTrack() {
        this.sharedContext = EglBase.create().getEglBaseContext();
        this.capturer = createCapturer(true);
        this.localCameraTrack = factory.createVideoTrack("video1", factory.createVideoSource(capturer));
    }*/

    private CameraVideoCapturer createCapturer() {
        CameraEnumerator enumerator = new Camera1Enumerator();
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null);
            }
        }
        return null;
    }

    public void releaseCamera() {
        if (this.capturer != null) {
            try {
                this.capturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (this.remoteRenderer != null)
            this.remoteRenderer.release();
        if (this.localRenderer != null)
            this.localRenderer.release();
    }

    private void handleMediaStream(MediaStream stream) {
        log("handling video stream");
        if (this.remoteRenderer == null || stream.videoTracks.size() == 0) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            //remoteRenderer.setBackgroundColor(Color.TRANSPARENT);
            remoteRenderer.init(this.sharedContext, null);
            stream.videoTracks.get(0).addSink(remoteRenderer);
        });
    }

    private MediaStream createStream() {
        upStream = factory.createLocalMediaStream("stream1");
        AudioTrack audio = factory.createAudioTrack("audio1", factory.createAudioSource(new MediaConstraints()));
        upStream.addTrack(audio);
        upStream.addTrack(getVideoTrack());
        //this.capturer.startCapture(500, 500, 30);
        return upStream;
    }

    private VideoTrack getVideoTrack() {
        this.capturer = createCapturer();
        return factory.createVideoTrack("video1", factory.createVideoSource(this.capturer));
    }

    private void initRTC(Context c) {
        log("initializing");
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c).createInitializationOptions());
        log("initialized");
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        log("created");
        constraints = new MediaConstraints();
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        //initVideoTrack();
    }

    static public RTCCall startCall(Contact target, OnStateChangeListener listener, Context context) {
        return new RTCCall(target, listener, context);
    }

    public RTCCall(Socket commSocket, Context context, String offer) {
        this.commSocket = commSocket;
        this.context = context;
        initRTC(context);
        this.offer = offer;
    }

    private void handleAnswer(String remoteDesc) {
        log("setting remote desc");
        connection.setRemoteDescription(new DefaultSdpObserver() {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                log("success");
            }

            @Override
            public void onSetFailure(String s) {
                super.onSetFailure(s);
                log("failure: " + s);
            }
        }, new SessionDescription(SessionDescription.Type.ANSWER, remoteDesc));
    }

    private void reportStateChange(CallState state) {
        this.state = state;
        if (this.listener != null) {
            this.listener.OnStateChange(state);
        }
    }

    public void accept(OnStateChangeListener listener) {
        this.listener = listener;
        new Thread(() -> {
            connection = factory.createPeerConnection(Collections.emptyList(), new DefaultObserver() {
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("transferring answer");
                        try {
                            JSONObject o = new JSONObject();
                            o.put("action", "connected");
                            o.put("answer", connection.getLocalDescription().description);
                            commSocket.getOutputStream().write((o.toString() + "\n").getBytes());
                            reportStateChange(CallState.CONNECTED);
                            //new Thread(new SpeakerRunnable(commSocket)).start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                        }
                    }
                }


                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("change");
                    super.onIceConnectionChange(iceConnectionState);
                    if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                        reportStateChange(CallState.ENDED);
                    }
                }

                @Override
                public void onAddStream(MediaStream mediaStream) {
                    log("onAddStream");
                    super.onAddStream(mediaStream);
                    handleMediaStream(mediaStream);
                }


                @Override
                public void onDataChannel(DataChannel dataChannel) {
                    super.onDataChannel(dataChannel);
                    RTCCall.this.dataChannel = dataChannel;
                    dataChannel.registerObserver(RTCCall.this);
                }

            });
            connection.addStream(createStream());
            //this.dataChannel = connection.createDataChannel("data", new DataChannel.Init());

            log("setting remote description");
            connection.setRemoteDescription(new DefaultSdpObserver() {
                @Override
                public void onSetSuccess() {
                    super.onSetSuccess();
                    log("creating answer...");
                    connection.createAnswer(new DefaultSdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            log("success");
                            super.onCreateSuccess(sessionDescription);
                            connection.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            super.onCreateFailure(s);
                            log("failure: " + s);
                        }
                    }, constraints);
                }
            }, new SessionDescription(SessionDescription.Type.OFFER, offer));
        }).start();
    }

    public void decline() {
        new Thread(() -> {
            try {
                log("declining...");
                commSocket.getOutputStream().write("{\"action\":\"dismissed\"}\n".getBytes());
                commSocket.getOutputStream().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void cleanup(){
        if (this.upStream != null && state == CallState.CONNECTED) {
            /*for(AudioTrack track : this.upStream.audioTracks){
                track.setEnabled(false);
                track.dispose();
            }
            for(VideoTrack track : this.upStream.videoTracks) track.dispose();*/
            if(this.connection != null) this.connection.close();
            //factory.dispose();
        }
        if (commSocket != null) {
            try {
                commSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void hangUp() {
        new Thread(() -> {
            try {
                if (commSocket != null) {
                    commSocket.getOutputStream().write("{\"action\":\"dismissed\"}\n".getBytes());
                    commSocket.close();
                }

                if (connection != null) connection.close();
                reportStateChange(CallState.ENDED);
            } catch (IOException e) {
                e.printStackTrace();
                reportStateChange(CallState.ERROR);
            }
        }).start();
    }

    public interface OnStateChangeListener {
        void OnStateChange(CallState state);
    }

    private void log(String s) {
        Log.d(RTCCall.class.getSimpleName(), s);
    }
}
