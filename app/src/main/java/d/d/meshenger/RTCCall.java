package d.d.meshenger;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.TypedValue;

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
//import org.webrtc.VideoCapturer;
import org.webrtc.VideoTrack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class RTCCall implements DataChannel.Observer {
    enum CallState { CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR }

    private final String StateChangeMessage = "StateChange";
    private final String CameraDisabledMessage = "CameraDisabled";
    private final String CameraEnabledMessage = "CameraEnabled";

    private PeerConnectionFactory factory;
    private PeerConnection connection;

    private MediaConstraints constraints;

    private String offer;

    private SurfaceViewRenderer remoteRenderer;
    private SurfaceViewRenderer localRenderer;

    private EglBase.Context sharedContext;
    private CameraVideoCapturer capturer;

    private MediaStream upStream;
    private DataChannel dataChannel;

    private boolean videoEnabled;

    private Context context;
    private Contact contact;
    private byte[] ownPublicKey;
    private byte[] ownSecretKey;
    private List<PeerConnection.IceServer> iceServers;
    private OnStateChangeListener listener;
    private MainService.MainBinder binder;

    public CallState state;
    public Socket commSocket;

    static public RTCCall startCall(Context context, MainService.MainBinder binder, Contact contact, OnStateChangeListener listener) {
        return new RTCCall(context, binder, contact, listener);
    }

    // called for incoming calls
    public RTCCall(Context context, MainService.MainBinder binder, Contact contact, Socket commSocket, String offer) {
        this.context = context;
        this.contact = contact;
        this.commSocket = commSocket;
        this.listener = null;
        this.binder = binder;
        this.ownPublicKey = binder.getSettings().getPublicKey();
        this.ownSecretKey = binder.getSettings().getSecretKey();
        this.offer = offer;

        // usually empty
        this.iceServers = new ArrayList<>();
        for (String server : binder.getSettings().getIceServers()) {
            this.iceServers.add(PeerConnection.IceServer.builder(server).createIceServer());
        }

        initRTC(context);
    }

    // called for outgoing calls
    private RTCCall(Context context, MainService.MainBinder binder, Contact contact, OnStateChangeListener listener) {
        this.context = context;
        this.contact = contact;
        this.commSocket = null;
        this.listener = listener;
        this.binder = binder;
        this.ownPublicKey = binder.getSettings().getPublicKey();
        this.ownSecretKey = binder.getSettings().getSecretKey();

        log("RTCCall created");

        // usually empty
        this.iceServers = new ArrayList<>();
        for (String server : binder.getSettings().getIceServers()) {
            this.iceServers.add(PeerConnection.IceServer.builder(server).createIceServer());
        }

        initRTC(context);

        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            context.setTheme(R.style.AppTheme_Dark);
        } else {
            context.setTheme(R.style.AppTheme_Light);
        }

        new Thread(() -> {
            connection = factory.createPeerConnection(Collections.emptyList(), new DefaultObserver() {
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("transferring offer...");
                        try {
                            commSocket = contact.createSocket();
                            if (commSocket == null) {
                                log("cannot establish connection");
                                reportStateChange(CallState.ERROR);
                                //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                return;
                            }
                            InetSocketAddress remote_address = (InetSocketAddress) commSocket.getRemoteSocketAddress();
                            byte[] otherPublicKey = remote_address.getAddress().getAddress();
                            log("outgoing call from remote address: " + remote_address);

                            // remember latest working address
                            contact.setLastWorkingAddress(
                                    new InetSocketAddress(remote_address.getAddress(), MainService.serverPort)
                            );

                            log("connect..");

                            PacketReader pr = new PacketReader(commSocket);
                            reportStateChange(CallState.CONNECTING);

                            {
                                JSONObject obj = new JSONObject();
                                obj.put("action", "call");
                                obj.put("offer", connection.getLocalDescription().description);
                                byte[] encrypted = Crypto.encryptMessage(obj.toString(), contact.getPublicKey(), ownPublicKey, ownSecretKey);
                                if (encrypted == null) {
                                    log("encrypted var is null");
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                PacketWriter pw = new PacketWriter(commSocket);
                                pw.writeMessage(encrypted);
                            }

                            {
                                byte[] response = pr.readMessage();
                                String decrypted = Crypto.decryptMessage(response, otherPublicKey, ownPublicKey, ownSecretKey);
                                if (decrypted == null || !Arrays.equals(contact.getPublicKey(), otherPublicKey)) {
                                    log("decrypted var is null or pubkey does not match");
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                JSONObject obj = new JSONObject(decrypted);
                                if (!obj.optString("action", "").equals("ringing")) {
                                    log("action not equals ringing");
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                    return;
                                }
                                log("ringing...");
                                reportStateChange(CallState.RINGING);
                            }

                            {
                                byte[] response = pr.readMessage();
                                String decrypted = Crypto.decryptMessage(response, otherPublicKey, ownPublicKey, ownSecretKey);
                                if (decrypted == null || !Arrays.equals(contact.getPublicKey(), otherPublicKey)) {
                                    log("decrypted (201) var is null or pubkey does not match");
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    return;
                                }

                                JSONObject obj = new JSONObject(decrypted);

                                String action = obj.getString("action");
                                if (action.equals("connected")) {
                                    reportStateChange(CallState.CONNECTED);
                                    handleAnswer(obj.getString("answer"));
                                    // contact accepted receiving call
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ACCEPTED);
                                } else if (action.equals("dismissed")) {
                                    log("dismissed");
                                    closeCommSocket();
                                    reportStateChange(CallState.DISMISSED);
                                    // contact declined receiving call
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_DECLINED);
                                } else {
                                    log("unknown action reply: " + action);
                                    closeCommSocket();
                                    reportStateChange(CallState.ERROR);
                                    //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                                }
                            }
                        } catch (Exception e) {
                            closeCommSocket();
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                            //RTCCall.this.binder.addCallEvent(contact, CallEvent.Type.OUTGOING_ERROR);
                        }
                    }
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("onIceConnectionChange " + iceConnectionState.name());
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
            PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList());
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;

            connection.setConfiguration(config);
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

    private void closeCommSocket() {
        log("closeCommSocket");
        if (this.commSocket != null) {
            try {
                this.commSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.commSocket = null;
        }
    }

    private void closePeerConnection() {
        log("closePeerConnection");
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.connection = null;
        }
    }

    public void setRemoteRenderer(SurfaceViewRenderer remoteRenderer) {
        this.remoteRenderer = remoteRenderer;
    }

    public void switchFrontFacing() {
        if (this.capturer != null) {
            this.capturer.switchCamera(null);
        }
    }

    @Override
    public void onBufferedAmountChange(long l) {
        // nothing to do
    }

    @Override
    public void onStateChange() {
        // nothing to do
    }

    @Override
    public void onMessage(DataChannel.Buffer buffer) {
        byte[] data = new byte[buffer.data.remaining()];
        buffer.data.get(data);
        String s = new String(data);
        JSONObject object = null;
        try {
            log("onMessage: " + s);
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
            log("setVideoEnabled: " + object);
            dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(object.toString().getBytes()), false));
        } catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*private void initLocalRenderer() {
        if (this.localRenderer != null) {
            log("really initng " + (this.sharedContext == null));
            this.localRenderer.init(this.sharedContext, null);
            this.localCameraTrack.addSink(localRenderer);
            this.capturer.startCapture(500, 500, 30);
        }
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

        if (this.remoteRenderer != null) {
            this.remoteRenderer.release();
        }

        if (this.localRenderer != null) {
            this.localRenderer.release();
        }
    }

    private void handleMediaStream(MediaStream stream) {
        log("handleMediaStream");
        if (this.remoteRenderer == null || stream.videoTracks.size() == 0) {
            return;
        }

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
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c).createInitializationOptions());
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        constraints = new MediaConstraints();
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"));
        constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        //initVideoTrack();
    }

    private void handleAnswer(String remoteDesc) {
        connection.setRemoteDescription(new DefaultSdpObserver() {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                log("onSetSuccess");
            }

            @Override
            public void onSetFailure(String s) {
                super.onSetFailure(s);
                log("onSetFailure: " + s);
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
            connection = factory.createPeerConnection(this.iceServers, new DefaultObserver() {
                @Override
                public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                    super.onIceGatheringChange(iceGatheringState);
                    if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                        log("onIceGatheringChange");
                        try {
                            PacketWriter pw = new PacketWriter(commSocket);
                            JSONObject obj = new JSONObject();
                            obj.put("action", "connected");
                            obj.put("answer", connection.getLocalDescription().description);
                            byte[] encrypted = Crypto.encryptMessage(obj.toString(), contact.getPublicKey(), ownPublicKey, ownSecretKey);
                            if (encrypted != null) {
                                pw.writeMessage(encrypted);
                                reportStateChange(CallState.CONNECTED);
                            } else {
                                reportStateChange(CallState.ERROR);
                            }
                            //new Thread(new SpeakerRunnable(commSocket)).start();
                        } catch (Exception e) {
                            e.printStackTrace();
                            reportStateChange(CallState.ERROR);
                        }
                    }
                }

                @Override
                public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                    log("onIceConnectionChange");
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
                            log("onCreateSuccess");
                            super.onCreateSuccess(sessionDescription);
                            connection.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            super.onCreateFailure(s);
                            log("onCreateFailure: " + s);
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
                if (this.commSocket != null) {
                    PacketWriter pw = new PacketWriter(commSocket);
                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"dismissed\"}", this.contact.getPublicKey(), this.ownPublicKey, this.ownSecretKey);
                    pw.writeMessage(encrypted);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                cleanup();
            }
        }).start();
    }

    public void cleanup() {
        closeCommSocket();

        if (this.upStream != null && state == CallState.CONNECTED) {
            /*for(AudioTrack track : this.upStream.audioTracks){
                track.setEnabled(false);
                track.dispose();
            }
            for(VideoTrack track : this.upStream.videoTracks) track.dispose();*/
            closePeerConnection();
            //factory.dispose();
        }
    }

    public void hangUp() {
        new Thread(() -> {
            try {
                if (this.commSocket != null) {
                    PacketWriter pw = new PacketWriter(this.commSocket);
                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"dismissed\"}", this.contact.getPublicKey(), this.ownPublicKey, this.ownSecretKey);
                    pw.writeMessage(encrypted);
                }

                closeCommSocket();
                closePeerConnection();
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
        Log.d(this, s);
    }
}
