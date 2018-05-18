package d.d.meshenger;


import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

public class Call {
    enum CallState{CONNECTING, RINGING, CONNECTED, DISMISSED, ENDED, ERROR}
    CallState state;
    OnStateChangeListener listener;

    Socket commSocket;

    final int SAMPLE_RATE = 8000;
    final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    final int bufferSIze = 1024;


    private Call(Contact target, String username, String identifier, OnStateChangeListener listener) {
        log("starting call to " + target.getAddress());
        this.listener = listener;
        new Thread(() -> {
            try {
                commSocket = new Socket(target.getAddress(), MainService.serverPort);
                OutputStream os = commSocket.getOutputStream();
                reportStateChange(CallState.CONNECTING);
                JSONObject object = new JSONObject();
                object.put("action", "call");
                object.put("username", username);
                object.put("identifier", identifier);
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
                    handleCall(commSocket);
                } else if (responseObject.getString("action").equals("dismissed")) {
                    reportStateChange(CallState.DISMISSED);
                    commSocket.close();
                } else {
                    reportStateChange(CallState.ERROR);
                    commSocket.close();
                }
            }catch (Exception e){
                e.printStackTrace();
                reportStateChange(CallState.ERROR);
            }
        }).start();
    }

    static public Call startCall(Contact target, String username, String identifier, OnStateChangeListener listener){
        return new Call(target, username, identifier, listener);
    }

    public Call(Socket commSocket){
        this.commSocket = commSocket;
    }

    void handleCall(Socket commSocket){
        this.commSocket = commSocket;
        handleCall();
    }

    void handleCall() {
        AudioRecord input = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                ENCODING,
                bufferSIze
        );
        input.startRecording();
        new Thread(new SpeakerRunnable(commSocket)).start();
        byte[] buffer = new byte[bufferSIze];
        try {
            OutputStream os = commSocket.getOutputStream();
            log("transmitting...");
            while (true) {
                input.read(buffer, 0, bufferSIze);
                os.write(buffer);
            }
        } catch (Exception e) {
        } finally {
            log("Call ended");
            reportStateChange(CallState.ENDED);
            input.release();
        }
    }

    private void reportStateChange(CallState state){
        if(this.listener != null){
            this.listener.OnStateChange(state);
        }
    }

    public void accept(OnStateChangeListener listener){
        this.listener = listener;
        new Thread(() -> {
            try {
                commSocket.getOutputStream().write("{\"action\":\"connected\"}\n".getBytes());
                reportStateChange(CallState.CONNECTED);
                handleCall(commSocket);
                //new Thread(new SpeakerRunnable(commSocket)).start();
            } catch (IOException e) {
                e.printStackTrace();
                reportStateChange(CallState.ERROR);
            }
        }).start();
    }

    public void decline(){
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

    public void end(){
        new Thread(() -> {
            try {
                commSocket.close();
                reportStateChange(CallState.ENDED);
            } catch (IOException e) {
                e.printStackTrace();
                reportStateChange(CallState.ERROR);
            }
        }).start();
    }

    public interface OnStateChangeListener{
        void OnStateChange(CallState state);
    }

    private void log(String s){
        Log.d(Call.class.getSimpleName(), s);
    }

    class SpeakerRunnable implements Runnable {
        private Socket s;

        private SpeakerRunnable(Socket s) {
            this.s = s;
        }

        @Override
        public void run() {
            try {
                int bufferSIze = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                byte[] buffer = new byte[bufferSIze];
                InputStream is = s.getInputStream();

                AudioTrack output = new AudioTrack(
                        AudioManager.STREAM_VOICE_CALL,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        ENCODING,
                        bufferSIze,
                        AudioTrack.MODE_STREAM
                );
                output.play();
                int read;
                log("receiving...");
                while (true) {
                    read = is.read(buffer);
                    output.write(buffer, 0, read);
                }
            } catch (Exception e) {
            }finally {
                Call.this.reportStateChange(CallState.ENDED);
                listener = null;
            }
        }
    }
}
