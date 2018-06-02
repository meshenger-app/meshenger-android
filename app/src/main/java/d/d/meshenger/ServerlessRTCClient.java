package d.d.meshenger;

import android.service.notification.StatusBarNotification;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ServerlessRTCClient {
    PeerConnection pc;
    PeerConnectionFactory factory;
    boolean pcInitialized = false;

    DataChannel channel = null;

    List iceServers = Arrays.asList(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

    State state;

    MediaConstraints pcConstraints = new MediaConstraints();

    enum State {
        /**
         * Initialization in progress.
         */
        INITIALIZING,
        /**
         * App is waiting for offer, fill in the offer into the edit text.
         */
        WAITING_FOR_OFFER,
        /**
         * App is creating the offer.
         */
        CREATING_OFFER,
        /**
         * App is creating answer to offer.
         */
        CREATING_ANSWER,
        /**
         * App created the offer and is now waiting for answer
         */
        WAITING_FOR_ANSWER,
        /**
         * Waiting for establishing the connection.
         */
        WAITING_TO_CONNECT,
        /**
         * Connection was established. You can chat now.
         */
        CHAT_ESTABLISHED,
        /**
         * Connection is terminated chat ended.
         */
        CHAT_ENDED
    }

    public ServerlessRTCClient() {
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    JSONObject sessionJson(SessionDescription desc) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("type", desc.type.canonicalForm());
        o.put("sdp", desc.description);
        return o;
    }

    void waitForOffer() {
        state = State.WAITING_FOR_OFFER;
    }

    void processOffer(String sdpJSON) {
        try {
            JSONObject json = new JSONObject(sdpJSON);
            String type = json.getString("type");
            String sdp = json.getString("sdp");
            state = State.CREATING_ANSWER;
            if (type != null && sdp != null && type == "offer") {
                SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                pcInitialized = true;
                pc = factory.createPeerConnection(iceServers, new DefaultObserver(){
                    @Override
                    public void onIceGatheringChange(PeerConnection.IceGatheringState p0) {
                        super.onIceGatheringChange(p0);
                        //ICE gathering complete, we should have answer now
                        if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                            //doShowAnswer(pc.localDescription)
                            state = State.WAITING_TO_CONNECT;
                        }
                    }

                    @Override
                    public void onDataChannel(DataChannel dataChannel) {
                        super.onDataChannel(dataChannel);
                        channel = dataChannel;
                        dataChannel.registerObserver(new DefaultDataObserver(dataChannel));
                    }
                });
                //we have remote offer, let's create answer for that
                pc.setRemoteDescription(new DefaultSdpObserver(){
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        pc.createAnswer(new DefaultSdpObserver(){
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                super.onCreateSuccess(sessionDescription);
                                pc.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                            }
                        }, pcConstraints);
                    }
                }, offer);
            } else {
                //console.redf("Invalid or unsupported offer.")
                state = State.WAITING_FOR_OFFER;
            }
        } catch (JSONException e) {
            //console.redf("bad json")
            state = State.WAITING_FOR_OFFER;
        }
    }

    void processAnswer(String sdpJSON) {
        try {
            JSONObject json = new JSONObject(sdpJSON);
            String type = json.getString("type");
            String sdp = json.getString("sdp");
            state = State.WAITING_TO_CONNECT;
            if (type != null && sdp != null && type == "answer") {
                SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                pc.setRemoteDescription(new DefaultSdpObserver(), answer);
            } else {
                //console.redf("Invalid or unsupported answer.")
                state = State.WAITING_FOR_ANSWER;
            }
        } catch (JSONException e) {
            //console.redf("bad json")
            state = State.WAITING_FOR_ANSWER;
        }
    }

    void makeOffer() {
        state = State.CREATING_OFFER;
        pcInitialized = true;
        pc = factory.createPeerConnection(iceServers, pcConstraints, new DefaultObserver(){
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);
                if(iceGatheringState == PeerConnection.IceGatheringState.COMPLETE){
                    state = State.WAITING_FOR_ANSWER;
                }
            }
        });
        makeDataChannel();
        pc.createOffer(new DefaultSdpObserver(){
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                if(sessionDescription != null){
                    pc.setLocalDescription(new DefaultSdpObserver(), sessionDescription);
                }
            }
        }, pcConstraints);
    }

    void sendMessage(String message) throws JSONException, UnsupportedEncodingException {
        if (channel != null || state == State.CHAT_ESTABLISHED) {
            JSONObject j = new JSONObject();
            j.put("message", message);
            ByteBuffer buf = ByteBuffer.wrap(j.toString().getBytes("UTF-8"));
            channel.send(new DataChannel.Buffer(buf, false));
        }
    }

    void makeDataChannel() {
        DataChannel.Init init = new DataChannel.Init();
        channel = pc.createDataChannel("test", init);
        channel.registerObserver(new DefaultDataObserver(channel));
    }

    void init() {
        //PeerConnectionFactory.initialize(new PeerConnectionFactory.InitializationOptions());
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        state = State.INITIALIZING;
    }

    class DefaultSdpObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {

        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }

    class DefaultObserver implements PeerConnection.Observer {


        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                //console.d("closing channel")
                channel.close();
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    class DefaultDataObserver implements DataChannel.Observer {
        DataChannel channel;

        public DefaultDataObserver(DataChannel channel) {
            this.channel = channel;
        }

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            if (channel.state() == DataChannel.State.OPEN) {
                state = State.CHAT_ESTABLISHED;
                return;
            }
            state = State.CHAT_ENDED;
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {

        }
    }
}
