package d.d.meshenger;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;


public class MainService extends Service implements Runnable {
    private ContactSqlHelper sqlHelper;
    private ArrayList<Contact> contacts;

    public static final int serverPort = 10001;
    ServerSocket server;
    private final String mac = Utils.getMac();

    private volatile boolean run = true, interrupted = false;

    private String userName;
    HashMap<String, Long> challenges;

    private RTCCall currentCall = null;

    @Override
    public void onCreate() {
        log("onCreate()");
        super.onCreate();
        sqlHelper = new ContactSqlHelper(this);

        new Thread(this).start();

        log("MainService started");

        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, new IntentFilter("settings_changed"));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("onDestroy()");
        sqlHelper.close();
        if (server != null && server.isBound() && !server.isClosed()) {
            try {
                JSONObject request = new JSONObject();
                request.put("identifier", mac);
                request.put("action", "status_change");
                request.put("status", "offline");
                for(Contact c : contacts){
                    if(c.getState() == Contact.State.ONLINE){
                        try{
                            Socket s = new Socket(c.getAddress(), serverPort);
                            s.getOutputStream().write((request.toString() + "\n").getBytes());
                            s.getOutputStream().flush();
                            s.close();
                        }catch (Exception e){}
                    }
                }
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver);
        log("MainService stopped");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStart()");
        return START_STICKY;
    }

    private void initNetwork() throws IOException {
        server = new ServerSocket(serverPort);
    }

    private void refreshContacts() {
        contacts = (ArrayList<Contact>) sqlHelper.getContacts();
        userName = getSharedPreferences(getPackageName(), MODE_PRIVATE).getString("username", "Unknown");
    }

    private void mainLoop() throws IOException {
        while (run) {
            try {
                Socket s = server.accept();
                log("client " + s.getInetAddress().getHostAddress() + " connected");
                new Thread(() -> handleClient(s)).start();
            } catch (IOException e) {
                if (!interrupted) {
                    throw e;
                }
            }
        }
    }

    private void handleClient(Socket client) {
        String identifier = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream os = client.getOutputStream();
            String line;
            log("waiting for line...");
            while ((line = reader.readLine()) != null) {
                log("line: " + line);
                JSONObject request = new JSONObject(line);
                identifier = request.getString("identifier");
                if (request.has("action")) {
                    String action = request.getString("action");
                    log("action: " + action);

                    switch (action) {
                        case "call": {
                            log("ringing...");
                            String response = "{\"action\":\"ringing\"}\n";
                            os.write(response.getBytes());

                            this.currentCall = new RTCCall(client, this, request.getString("offer"));
                            Intent intent = new Intent(this, CallActivity.class);
                            intent.setAction("ACTION_ACCEPT_CALL");
                            intent.putExtra("EXTRA_USERNAME", request.getString("username"));
                            startActivity(intent);

                            return;
                        }
                        case "ping": {
                            setClientState(identifier, Contact.State.ONLINE);
                            JSONObject response = new JSONObject();
                            response.put("username", userName);
                            response.put("identifier", mac);
                            os.write((response.toString() + "\n").getBytes());
                            break;
                        }
                        case "connect": {
                            String challenge = request.has("challenge") ? request.getString("challenge") : null;
                            if (challenge != null) {
                                if (true/*challenges.containsKey(challenge) && challenges.get(challenge) <= System.currentTimeMillis()*/) {
                                    Contact c = new Contact(
                                            client.getInetAddress().getHostAddress(),
                                            request.getString("username"),
                                            "",
                                            identifier
                                    );
                                    sqlHelper.insertContact(c);
                                    contacts.add(c);
                                    JSONObject response = new JSONObject();
                                    response.put("username", userName);
                                    os.write((response.toString() + "\n").getBytes());
                                }
                                Intent intent = new Intent("incoming_contact");
                                //intent.putExtra("extra_identifier", request.getString("identifier"));
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            }
                            break;
                        }
                        case "status_change":{
                            setClientState(identifier, Contact.State.OFFLINE);
                        }
                    }

                }
            }

            log("client " + client.getInetAddress().getHostAddress() + " disconnected");
        } catch (Exception e) {
            e.printStackTrace();
            log("client " + client.getInetAddress().getHostAddress() + " disconnected (exception)");
        }
    }

    private void setClientState(String identifier, Contact.State state){
            for(Contact c : contacts){
                if(c.getIdentifier().equals(identifier)){
                    c.setState(Contact.State.ONLINE);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("contact_refresh"));
                    break;
                }
            }
    }

    private void log(String data) {
        Log.d("MainService", data);
    }

    @Override
    public void run() {
        try {
            initNetwork();
            refreshContacts();
            mainLoop();
        } catch (IOException e) {
            e.printStackTrace();
            new Handler(getMainLooper()).post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            stopSelf();
            return;
        }
    }

    class MainBinder extends Binder {
        RTCCall startCall(Contact contact, String identifier, String username, RTCCall.OnStateChangeListener listener)  {
            return RTCCall.startCall(contact, username, identifier, listener, MainService.this);
        }

        RTCCall getCurrentCall(){
            return currentCall;
        }

        String getIdentifier(){
            return mac;
        }

        String getUsername(){
            return userName;
        }

        String generateChallenge() {
            final int challengeLength = 16;
            Random generator = new Random();
            byte[] content = new byte[challengeLength];
            for (int i = 0; i < challengeLength; i++) {
                content[i] = (byte) (generator.nextInt('Z' - 'A') + 'A');
            }
            String challenge = new String(content);
            if (challenges == null) {
                challenges = new HashMap<>();
            }
            challenges.put(challenge, System.currentTimeMillis() + 60000);
            return new String(content);
        }

        void addContact(Contact c, String challenge) {
            sqlHelper.insertContact(c);
            contacts.add(c);
            log("adding contact " + c.getName() + "  " + c.getId());

            new Thread(new ConnectRunnable(c, challenge)).start();
        }

        void pingContacts(List<Contact> c, ContactPingListener listener) {
            new Thread(new PingRunnable(c, listener)).start();
        }

        public List<Contact> getContacts() {
            return MainService.this.contacts;
        }
        /*void setPingResultListener(ContactPingListener listener){
            MainService.this.pingListener = listener;
        }*/

    }

    class PingRunnable implements Runnable {
        private List<Contact> contacts;
        ContactPingListener listener;

        public PingRunnable(List<Contact> contacts, ContactPingListener listener) {
            this.contacts = contacts;
            this.listener = listener;
        }

        @Override
        public void run() {
            for (Contact c : contacts) {
                try {
                    ping(c);
                    c.setState(Contact.State.ONLINE);
                    log("client " + c.getAddress() + " online");
                } catch (Exception e) {
                    c.setState(Contact.State.OFFLINE);
                    log("client " + c.getAddress() + " offline");
                } finally {
                    if (listener != null) {
                        listener.OnContactPingResult(c);
                    } else {
                        log("no listener");
                    }
                }
            }

        }

        private void ping(Contact c) throws Exception {
            Socket s = new Socket(c.getAddress(), serverPort);
            OutputStream os = s.getOutputStream();
            os.write(("{\"action\":\"ping\",\"identifier\":\"" + mac + "\"}\n").getBytes());

            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = reader.readLine();
            JSONObject object = new JSONObject(line);
            String responseMac = object.getString("identifier");
            if (!responseMac.equals(c.getIdentifier())) {
                throw new Exception("Foreign contact");
            }
            String username = object.getString("username");
            if (!username.equals(c.getName())) {
                c.setName(new JSONObject(line).getString("username"));
                sqlHelper.updateContact(c);
            }
            //(log("ping: " + line);
            s.close();
        }
    }

    class ConnectRunnable implements Runnable {
        private String address;
        private String username;
        private String challenge;
        private String identifier;

        public ConnectRunnable(Contact contact, String challenge) {
            this.address = contact.getAddress();
            this.username = userName;
            this.challenge = challenge;
            this.identifier = Utils.getMac();
        }

        @Override
        public void run() {
            try {
                Socket s = new Socket(address, serverPort);
                OutputStream os = s.getOutputStream();
                JSONObject object = new JSONObject();

                object.put("action", "connect");
                object.put("username", username);
                object.put("challenge", challenge);
                object.put("identifier", identifier);

                log("request: " + object.toString());

                os.write((object.toString() + "\n").getBytes());
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                log("awaiting response...");
                String line = reader.readLine();
                log("contact: " + line);
                s.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case "settings_changed":{
                    String subject = intent.getStringExtra("subject");
                    switch (subject){
                        case "username":{
                            userName = intent.getStringExtra("username");
                            break;
                        }
                    }
                }
            }
        }
    };

    public interface ContactPingListener {
        void OnContactPingResult(Contact c);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("onBind()");
        return new MainBinder();
    }
}
