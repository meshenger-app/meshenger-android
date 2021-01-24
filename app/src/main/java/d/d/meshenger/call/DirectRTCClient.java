/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package d.d.meshenger.call;

import android.util.Log;

import androidx.annotation.Nullable;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.libsodium.jni.Sodium;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.ThreadUtils;

import d.d.meshenger.AddressUtils;
import d.d.meshenger.Contact;
import d.d.meshenger.Crypto;
import d.d.meshenger.MainService;

/*
 * Handle socket connection to exchange connection details
*/
public class DirectRTCClient extends Thread implements AppRTCClient {
    private static final String TAG = "DirectRTCClient";

    private static DirectRTCClient currentCall = null;
    private static final Object currentCallLock = new Object();

    // call context for events
    private final ExecutorService executor;
    private final ThreadUtils.ThreadChecker executorThreadCheck;
    private AppRTCClient.SignalingEvents signalingEvents;
    private final CallDirection callDirection;
    private final Object socketLock;
    private SocketWrapper socket;
    private final Contact contact;
    private final byte[] ownSecretKey;
    private final byte[] ownPublicKey;

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }
    public enum CallDirection { INCOMING, OUTGOING };

    // only set in executor (or ctor)
    private ConnectionState connectionState;

    public DirectRTCClient(@Nullable final SocketWrapper socket, final Contact contact, final CallDirection callDirection) {
        this.socket = socket;
        this.contact = contact;
        this.callDirection = callDirection;
        this.socketLock = new Object();
        this.executor = Executors.newSingleThreadExecutor();
        this.connectionState = ConnectionState.NEW;
        this.ownSecretKey = MainService.instance.getSettings().getSecretKey();
        this.ownPublicKey = MainService.instance.getSettings().getPublicKey();
        this.executorThreadCheck = new ThreadUtils.ThreadChecker();
        this.executorThreadCheck.detachThread();
    }

    public Contact getContact() {
        return contact;
    }

    public void setEventListener(SignalingEvents signalingEvents) {
        this.signalingEvents = signalingEvents;
    }

    public CallDirection getCallDirection() {
        return callDirection;
    }

    // numerical presentation of how high-level an error is
    private static int getExceptionLevel(Exception e) {
        if (e instanceof ConnectException) {
            // connection went wrong
            return 3;
        }
        if (e instanceof SocketTimeoutException) {
            // no connection at all
            return 2;
        }
        if (e instanceof IOException) {
            // internal error while gettting streams?
            return 1;
        }
        return 0;
    }

    private SocketWrapper establishConnection(InetSocketAddress[] addresses, int timeout) {
        Exception errorException = null;
        //InetSocketAddress errorAddress = null;
        SocketWrapper socket = null;

        for (InetSocketAddress address : addresses) {
            Log.d(TAG, "try address: '" + address.getAddress() + "', port: " + address.getPort());
            Socket rawSocket = new Socket();
            try {
                rawSocket.connect(address, timeout);
                socket = new SocketWrapper(rawSocket);
                errorException = null;
                //errorAddress = null;
                // successful connection - abort
                break;
            } catch (Exception e) {
                if (getExceptionLevel(e) >= getExceptionLevel(errorException)) {
                    errorException = e;
                    //errorAddress = address;
                }
                try {
                    rawSocket.close();
                } catch (IOException ioe) {
                    // ignore
                }
            }
        }

        if (socket == null) {
            if (errorException != null) {
                reportError(errorException.getMessage());
            } else {
                reportError("Connection failed.");
            }
        }

        return socket;
    }

    @Override
    public void run() {
        Log.d(TAG, "Listening thread started...");
/*
        // wait for listner to be attached
        try {
            for (int i = 0; i < 50 && listener == null; i += 1) {
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Wait for listener interrupted: " + e);
            return;
        }
*/
        if (signalingEvents == null) {
            disconnectSocket();
            Log.e(TAG, "No listener found!");
            return;
        }

        if (callDirection == CallDirection.OUTGOING) {
            assert(contact != null);
            assert(socket == null);

            // contact is only set for outgoing call
            if (contact.getAddresses().isEmpty()) {
                reportError("No addresses set for contact.");
                return;
            }

            Log.d(TAG, "Create outgoing socket");
            assert(contact != null);
            assert(socket == null);

            InetSocketAddress[] addresses = AddressUtils.getAllSocketAddresses(
                    contact.getAddresses(), contact.getLastWorkingAddress(), MainService.serverPort);
            socket = establishConnection(addresses, 2000);

            if (socket == null) {
                disconnectSocket();
                return;
            }

            // send initial packet
            executor.execute(() -> {
                sendMessage("{\"type\":\"call\"}");
            });
        } else {
            assert(callDirection == CallDirection.INCOMING);
            assert(contact != null);
            assert(socket != null);

            Log.v(TAG, "Execute onConnectedToRoom");
            executor.execute(() -> {
                connectionState = ConnectionState.CONNECTED;

                SignalingParameters parameters = new SignalingParameters(
                    // Ice servers are not needed for direct connections.
                    new ArrayList<>(),
                    (callDirection == CallDirection.INCOMING), // Server side acts as the initiator on direct connections.
                    null, // clientId
                    null, // wssUrl
                    null, // wwsPostUrl
                    null, // offerSdp
                    null // iceCandidates
                );
                // call to CallActivity
                signalingEvents.onConnectedToRoom(parameters);
            });
        }

        InetSocketAddress remote_address = (InetSocketAddress) socket.getRemoteSocketAddress();

        // remember last good address (the outgoing port is random and not the server port)
        contact.setLastWorkingAddress(remote_address.getAddress());

        // read data
        while (true) {
            final byte[] message;
            try {
                message = socket.readMessage();
            } catch (IOException e) {
                reportError("Connection failed: " + e.getMessage()); // called when connection is closed by either end?
                // using reportError will cause the executor.shutdown() and also assignment of a task afterwards
                Log.d(TAG, "Failed to read from rawSocket: " + e.getMessage());
                break;
            }

            // No data received, rawSocket probably closed.
            if (message == null) {
                Log.d(TAG, "message is null");
                // hm, call hangup?
                break;
            }

            String decrypted = Crypto.decryptMessage(message, contact.getPublicKey(), ownPublicKey, ownSecretKey);
            if (decrypted == null) {
                reportError("decryption failed");
                break;
            }

            //Log.d(TAG, "decrypted: " + decrypted);
            executor.execute(() -> {
                onTCPMessage(decrypted);
            });
        }

        Log.d(TAG, "Receiving thread exiting...");

        // Close the rawSocket if it is still open.
        disconnectSocket();
    }

    private void disconnectSocket() {
        synchronized (socketLock) {
            if (socket != null) {
                socket.close();
                socket = null;

                executor.execute(() -> {
                    signalingEvents.onChannelClose();
                });
            }
        }
    }

    @Override
    public void connectToRoom() {
        executor.execute(() -> {
          connectToRoomInternal();
        });
    }

    @Override
    public void disconnectFromRoom() {
        executor.execute(() -> {
            disconnectFromRoomInternal();
        });
    }

    private void connectToRoomInternal() {
        executorThreadCheck.checkIsOnValidThread();
        this.connectionState = ConnectionState.NEW;

        // start thread and run()
        if (!this.isAlive()) {
            this.start();
        } else {
            Log.w(TAG, "Thread is already running!");
        }
    }

    private void disconnectFromRoomInternal() {
        executorThreadCheck.checkIsOnValidThread();
        connectionState = ConnectionState.CLOSED;

        disconnectSocket();

        Log.d(TAG, "shutdown executor");
        executor.shutdown();
    }

    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        if (callDirection != CallDirection.INCOMING) {
            Log.e(TAG, "we send offer as client?");
        }
        executor.execute(() -> {
            if (connectionState != ConnectionState.CONNECTED) {
                reportError("Sending offer SDP in non connected state.");
                return;
            }
            JSONObject json = new JSONObject();
            jsonPut(json, "sdp", sdp.description);
            jsonPut(json, "type", "offer");
            sendMessage(json.toString());
        });
    }

    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        executor.execute(() -> {
            JSONObject json = new JSONObject();
            jsonPut(json, "sdp", sdp.description);
            jsonPut(json, "type", "answer");
            sendMessage(json.toString());
        });
    }

    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        executor.execute(() -> {
            JSONObject json = new JSONObject();
            jsonPut(json, "type", "candidate");
            jsonPut(json, "label", candidate.sdpMLineIndex);
            jsonPut(json, "id", candidate.sdpMid);
            jsonPut(json, "candidate", candidate.sdp);

            if (connectionState != ConnectionState.CONNECTED) {
                reportError("Sending ICE candidate in non connected state.");
                return;
            }
            sendMessage(json.toString());
        });
    }

    /** Send removed Ice candidates to the other participant. */
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        executor.execute(() -> {
            JSONObject json = new JSONObject();
            jsonPut(json, "type", "remove-candidates");
            JSONArray jsonArray = new JSONArray();
            for (final IceCandidate candidate : candidates) {
                jsonArray.put(toJsonCandidate(candidate));
            }
            jsonPut(json, "candidates", jsonArray);

            if (connectionState != ConnectionState.CONNECTED) {
                reportError("Sending ICE candidate removals in non connected state.");
                return;
            }
            sendMessage(json.toString());
        });
    }

    private void onTCPMessage(String msg) {
        //Log.d(TAG, "onTCPMessage: " + msg);
        try {
            JSONObject json = new JSONObject(msg);
            String type = json.optString("type");

            // need to record for a call, so we can close the socket
            Log.e(TAG, "onTCPMessage: (" + type + "): " + msg);

            if (type.equals("call")) {
                if (callDirection != CallDirection.INCOMING) {
                    Log.e(TAG, "Dang, we are the client but got the packet of an outoing call?");
                    reportError("Unexpected answer: " + msg);
                } else {
                    // ignore - first packet of an incoming call
                }
            } else if (type.equals("candidate")) {
                signalingEvents.onRemoteIceCandidate(toJavaCandidate(json));
            } else if (type.equals("remove-candidates")) {
                JSONArray candidateArray = json.getJSONArray("candidates");
                IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                for (int i = 0; i < candidateArray.length(); i += 1) {
                    candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                }
                signalingEvents.onRemoteIceCandidatesRemoved(candidates);
            } else if (type.equals("answer")) {
                if (callDirection != CallDirection.INCOMING) {
                    Log.e(TAG, "Dang, we are the client but got an answer?");
                    reportError("Unexpected answer: " + msg);
                } else {
                    SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                    signalingEvents.onRemoteDescription(sdp);
                }
            } else if (type.equals("offer")) {
                SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));

                if (callDirection != CallDirection.OUTGOING) {
                    Log.e(TAG, "Dang, we are the server but got an offer?");
                    reportError("Unexpected offer: " + msg);
                } else {
                    SignalingParameters parameters = new SignalingParameters(
                        // Ice servers are not needed for direct connections.
                        new ArrayList<>(),
                        false, // This code will only be run on the client side. So, we are not the initiator.
                        null, // clientId
                        null, // wssUrl
                        null, // wssPostUrl
                        sdp, // offerSdp
                        null // iceCandidates
                    );
                    connectionState = ConnectionState.CONNECTED;
                    // call to CallActivity
                    signalingEvents.onConnectedToRoom(parameters);
                }
            } else {
                reportError("Unexpected message: " + msg);
            }
        } catch (JSONException e) {
            reportError("TCP message JSON parsing error: " + e.toString());
        }
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage + " (" + connectionState.name() + ")");
        try {
            executor.execute(() -> {
                if (connectionState != ConnectionState.ERROR) {
                    connectionState = ConnectionState.ERROR;
                    signalingEvents.onChannelError(errorMessage);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void sendMessage(final String message) {
        executorThreadCheck.checkIsOnValidThread();
        byte[] encrypted = Crypto.encryptMessage(message, contact.getPublicKey(), ownPublicKey, ownSecretKey);
        synchronized (socketLock) {
            if (this.socket == null) {
                reportError("Sending data on closed socket.");
                return;
            }

            try {
                this.socket.writeMessage(encrypted);
            } catch (IOException e) {
                reportError("Failed to write message: " + e);
            }
        }
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private static JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    private static IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
            json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }

    public static DirectRTCClient getCurrentCall() {
        synchronized (currentCallLock) {
            return currentCall;
        }
    }

    public static void setCurrentCall(DirectRTCClient client) {
        synchronized (currentCallLock) {
            currentCall = client;
        }
    }

    public static boolean createOutgoingCall(Contact contact) {
        Log.d(TAG, "createOutgoingCall");

        if (contact == null) {
            return false;
        }

        synchronized (currentCallLock) {
            if (currentCall != null) {
                Log.w(TAG, "Cannot handle outgoing call. Call in progress!");
                return false;
            }
            currentCall = new DirectRTCClient(null, contact, DirectRTCClient.CallDirection.OUTGOING);
            return true;
        }
    }

    public static boolean createIncomingCall(Socket rawSocket) {
        Log.d(TAG, "createIncomingCall");

        if (rawSocket == null) {
            return false;
        }

        try {
            // search for contact identity
            byte[] clientPublicKeyOut = new byte[Sodium.crypto_sign_publickeybytes()];
            byte[] ownSecretKey = MainService.instance.getSettings().getSecretKey();
            byte[] ownPublicKey = MainService.instance.getSettings().getPublicKey();

            SocketWrapper socket = new SocketWrapper(rawSocket);

            Log.d(TAG, "readMessage");
            byte[] request = socket.readMessage();

            if (request == null) {
                Log.d(TAG, "request is null");
                // invalid or timed out packet
                return false;
            }

            Log.d(TAG, "decrypt message");
            // receive public key of contact
            String decrypted = Crypto.decryptMessage(request, clientPublicKeyOut, ownPublicKey, ownSecretKey);
            if (decrypted == null) {
                Log.d(TAG, "decryption failed");
                return false;
            }
            Log.d(TAG, "decrypted message: " + decrypted);

            Contact contact = MainService.instance.getContacts().getContactByPublicKey(clientPublicKeyOut);

            if (contact == null) {
                Log.d(TAG, "unknown contact");
                if (MainService.instance.getSettings().getBlockUnknown()) {
                    Log.d(TAG, "block unknown contact => decline");
                    return false;
                }

                // unknown caller
                contact = new Contact("" /* unknown caller */, clientPublicKeyOut.clone(), new ArrayList<>(), false);
            } else {
                if (contact.getBlocked()) {
                    Log.d(TAG, "blocked contact => decline");
                    return false;
                }
            }

            synchronized (currentCallLock) {
                if (currentCall != null) {
                    Log.w(TAG, "Cannot handle incoming call. Call in progress!");
                    return false;
                }
                Log.d(TAG, "create DirectRTCClient");
                currentCall = new DirectRTCClient(socket, contact, DirectRTCClient.CallDirection.INCOMING);
            }
            return true;
        } catch (IOException e) {
            if (rawSocket != null) {
                try {
                    rawSocket.close();
                } catch (IOException _e) {
                    // ignore
                }
            }
            Log.e(TAG, "exception in createIncomingCall");
            return false;
        }
    }
}
