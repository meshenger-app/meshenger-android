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

import com.google.gson.Gson;
import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.exceptions.SodiumException;
import com.goterl.lazycode.lazysodium.utils.Key;
import com.goterl.lazycode.lazysodium.utils.KeyPair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.SurfaceViewRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;


public class MainService extends Service implements Runnable {
    private ArrayList<Contact> contacts;

    String encrypted;
    byte[] nonce;
    private ContactSqlHelper sqlHelper;

    public static final int serverPort = 10001;
    private ServerSocket server;
    private final String mac = Utils.formatAddress(Utils.getMacAddress());

    private volatile boolean run = true, interrupted = false;

    private String userName, pubkeyData;

    private RTCCall currentCall = null;

    private boolean ignoreUnsaved;


    public static io.socket.client.Socket socket = null;

    @Override
    public void onCreate() {
        log("onCreate()");
        super.onCreate();
        sqlHelper = new ContactSqlHelper(this);

        new Thread(this).start();

        log("MainService started");

        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, new IntentFilter("settings_changed"));

        try {
            socket = IO.socket("https://3542dcfb.ngrok.io");
            //socket = IO.socket(Constant.ROOT_URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        socket.connect();
        
        socket.on(io.socket.client.Socket.EVENT_CONNECT, args -> {
            try {
                JSONObject data = new JSONObject();
                data.put("userName", userName);
                socket.emit("ping1", data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        socket.on("online", args -> {
            ArrayList data = new Gson().fromJson((String) args[0],ArrayList.class);
            for (Contact c : contacts) {
                c.setState(Contact.State.OFFLINE);
                for (int i  = 0 ;i<data.size();i++){
                    Log.d("user online",data.get(i).toString());
                    if (c.getName().equalsIgnoreCase(String.valueOf(data.get(i)))) {
                        c.setState(Contact.State.ONLINE);
                    }

                }
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("contact_refresh"));
            Log.d("online1", args[0].toString());
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("onDestroy()");
        sqlHelper.close();
        if(socket!=null){
            socket.emit(io.socket.client.Socket.EVENT_DISCONNECT,userName);
        }
        if (server != null && server.isBound() && !server.isClosed()) {
            try {
                JSONObject request = new JSONObject();
                request.put("publicKey", pubkeyData);
                request.put("action", "status_change");
                request.put("status", "offline");
                for (Contact c : contacts) {
                    if (c.getState() == Contact.State.ONLINE) {
                        try {
                            Socket s = c.createSocket();
                            //  Socket s = new Socket(c.getAddress().replace("%zone", "%wlan0"), serverPort);
                            s.getOutputStream().write((request.toString() + "\n").getBytes());
                            s.getOutputStream().flush();
                            s.close();
                        } catch (Exception e) {
                        }
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
        if (sqlHelper.getAppData() == null) {
            userName = "Unknown";
            ignoreUnsaved = false;
        } else {
            userName = sqlHelper.getAppData().getUsername();
            if (sqlHelper.getAppData().getBlockUC() == 1) {
                ignoreUnsaved = true;
            } else {
                ignoreUnsaved = false;
            }
        }
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
        String publickey = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream os = client.getOutputStream();
            String line;
            log("waiting for line...");
            while ((line = reader.readLine()) != null) {
                log("line: " + line);
                JSONObject request = new JSONObject(line);
                if(request.has("publicKey")) {
                    publickey = request.getString("publicKey");
                }

                if (request.has("action")) {
                    String action = request.getString("action");
                    log("action: " + action);

                    switch (action) {
                        case "call": {
                            log("ringing...");
                            String response = "{\"action\":\"ringing\"}\n";
                            os.write(response.getBytes());

                            nonce = hexStringToByteArray(request.getString("nonce"));
                            encrypted = request.getString("offer");
                            String offer = decryption();
                            this.currentCall = new RTCCall(client, this, offer);

                            if (ignoreUnsaved && !sqlHelper.contactSaved(mac)) {
                                currentCall.decline();
                                continue;
                            };
                            Intent intent = new Intent(this, CallActivity.class);
                            intent.setAction("ACTION_ACCEPT_CALL");
                            intent.putExtra("EXTRA_USERNAME", request.getString("username"));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);

                            return;
                        }
                        case "ping": {
                            setClientState(publickey, Contact.State.ONLINE);
                            JSONObject response = new JSONObject();
                            response.put("publicKey", pubkeyData);
                            os.write((response.toString() + "\n").getBytes());
                            break;
                        }

                        case "connect": {
                            publickey = request.optString("publicKey", null);
                            if (publickey != null) {
                                String hostaddress = client.getInetAddress().getHostAddress();
                                Contact c = new Contact(
                                       // client.getInetAddress().getHostAddress(),
                                        request.getString("username"),
                                        "",
                                        request.getString("publicKey")
                                        // identifier
                                );
                                if (request.getJSONArray("connection_data") == null) {
                                    return;
                                }
                                JSONArray connectionData= request.getJSONArray("connection_data");
                                JSONObject linkLocal = null;
                                for (int i = 0; i < connectionData.length(); i++) {
                                    if (connectionData.getJSONObject(i).getString("type").equalsIgnoreCase("LinkLocal")) {
                                        linkLocal = connectionData.getJSONObject(i);
                                    }
                                }
                                c.addConnectionData(new Contact.LinkLocal(linkLocal.getString("mac_address"), 10001));
                                c.addConnectionData(new Contact.Hostname(hostaddress));
                                try {
                                    sqlHelper.insertContact(c);
                                    contacts.add(c);
                                } catch (ContactSqlHelper.ContactAlreadyAddedException e){}
                                JSONObject response = new JSONObject();
                                response.put("username", userName);
                                os.write((response.toString() + "\n").getBytes());
                                Intent intent = new Intent("incoming_contact");
                                //intent.putExtra("extra_identifier", request.getString("identifier"));
                                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                            }
                            break;
                        }
                        case "status_change": {
                            setClientState(publickey, Contact.State.OFFLINE);
                        }
                    }

                }
            }

            log("client " + client.getInetAddress().getHostAddress() + " disconnected");
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("call_declined"));
        } catch (Exception e) {
            e.printStackTrace();
            log("client " + client.getInetAddress().getHostAddress() + " disconnected (exception)");
            //currentCall.decline();
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

     public String decryption() throws SodiumException {
        LazySodiumAndroid ls;
        ls = new LazySodiumAndroid(new SodiumAndroid());
        String decrypted = null;
        contacts = (ArrayList<Contact>) sqlHelper.getContacts();
        String secretKey = sqlHelper.getAppData().getSecretKey();
        Key secret_key = Key.fromHexString(secretKey);
        for (Contact c : contacts) {
            Key pub_key = Key.fromHexString(c.getPubKey());
            KeyPair decryptionKeyPair = new KeyPair(pub_key, secret_key);
            decrypted = ls.cryptoBoxOpenEasy(encrypted, nonce, decryptionKeyPair);
            if ( decrypted != null ) {
                return decrypted;
            }
        }
        return null;
    }

    private void setClientState(String publickey, Contact.State state) {
        for (Contact c : contacts) {
            if (c.matchEndpoint(publickey)) {
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
        RTCCall startCall(Contact contact, RTCCall.OnStateChangeListener listener, SurfaceViewRenderer renderer) {
            return RTCCall.startCall(contact, listener, MainService.this);
        }

        RTCCall getCurrentCall() {
            return currentCall;
        }

        String getPublicKey() {
            if (pubkeyData == null) {
                pubkeyData = sqlHelper.getAppData().getPublicKey();
            }
            return pubkeyData;
        }


        String getUsername() {
            return userName;
        }

        void addContact(Contact c) {

            try {
                sqlHelper.insertContact(c);
                contacts.add(c);
                log("adding contact " + c.getPubKey() + "  " + c.getId());

            } catch (ContactSqlHelper.ContactAlreadyAddedException e) {
                Toast.makeText(MainService.this, "Contact already added", Toast.LENGTH_SHORT).show();
            }
            new Thread(new ConnectRunnable(c)).start();
        }

        void deleteContact(Contact c){
            sqlHelper.deleteContact(c);
            refreshContacts();
        }

        void updateContact(Contact c){
            sqlHelper.updateContact(c);
            refreshContacts();
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

        PingRunnable(List<Contact> contacts, ContactPingListener listener) {
            this.contacts = contacts;
            this.listener = listener;
        }

        @Override
        public void run() {
      /*      for (Contact c : contacts) {
                try {
                    ping(c);
                    c.setState(Contact.State.ONLINE);
                    log("client " + c.getPubKey() + " online");
                } catch (Exception e) {
                    c.setState(Contact.State.OFFLINE);
                    log("client " + c.getPubKey() + " offline");
                    //e.printStackTrace();
                } finally {
                    if (listener != null) {
                        listener.OnContactPingResult(c);
                    } else {
                        log("no listener");
                    }
                }
            }
            */
            ping();
        }
//        private void ping(Contact c) throws Exception {
//            log("ping");
//            //  List<String> targets = getAddressPermutations(c);
//            //  log("targets: " + targets.size());
//            //Socket s = null;
//            //for (String target : targets) {
//            try {
//                // log("opening socket to " + target);
//                // s = new Socket(target.replace("%zone", "%wlan0"), serverPort);
//                log("opening socket to " + c.getPubKey());
//                OutputStream os = s.getOutputStream();  //crash
//                os.write(("{\"action\":\"ping\",\"publicKey\":\"" + pubkeyData + "\"}\n").getBytes());
//
//                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
//                String line = reader.readLine();
//                JSONObject object = new JSONObject(line);
//                 /*   String responseMac = object.getString("identifier");
//                    if (!responseMac.equals(c.getIdentifier())) {
//                        throw new Exception("foreign contact");
//                    }
//                */
//                s.close();
//
//                //   c.setAddress(target);
//
//                return;
//            } catch (Exception e) {
//                //   continue;
//            } finally {
//                if (s != null) {
//                    try {
//                        s.close();
//                    } catch (Exception e){}
//                }
//            }
//            // }
//
//            throw new Exception("contact not reachable");
//        }

        private void ping(){
            if(socket!=null && !socket.connected()){
                socket.connect();
            }

            try{
                JSONObject data = new JSONObject();
                data.put("userName", userName);
                socket.emit("ping1", data);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

   /* private List<String> getAddressPermutations(Contact c) {
        ArrayList<InetAddress> mutationAddresses = new ArrayList<>();
        byte[] eui64 = Utils.getEUI64();
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : all) {
                if (!networkInterface.getName().equalsIgnoreCase("wlan0")) continue;
                List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();
                loop:
                for (InterfaceAddress address : addresses) {
                    if (address.getAddress().isLoopbackAddress()) continue;
                    if (address.getAddress() instanceof Inet6Address) {
                        byte[] bytes = address.getAddress().getAddress();
                        for (int i = 0; i < 8; i++) {
                            if (bytes[i + 8] != eui64[i]) continue loop;
                        }
                        mutationAddresses.add(address.getAddress());
                        Log.d(BuildConfig.APPLICATION_ID, "found matching address: " + address.getAddress().getHostAddress());
                    }
                }
                break;
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        log("loop ended");
        byte[] targetEUI = addressToEUI64(c.getIdentifier());
        log("target: " + Utils.formatAddress(targetEUI));
        ArrayList<String> result = new ArrayList<>();
        int i = 0;
        for (InetAddress address : mutationAddresses) {
            log("mutating address: " + address.getHostAddress());
            byte[] add = address.getAddress();
            System.arraycopy(targetEUI, 0, add, 8, 8);
            try {
                address = Inet6Address.getByAddress(address.getHostAddress(), add, ((Inet6Address) address).getScopeId());
            } catch (UnknownHostException e) {
                continue;
            }
            log("mutated address: " + address.getHostAddress());
            result.add(address.getHostAddress());
        }

        if (!result.contains(c.getName())) {
            result.add(c.getName());
        }else{
            log("address duplicate");
        }
        return result;
    }

    private byte[] addressToEUI64(String address) {
        String[] hexes = address.split(":");
        byte[] bytes = new byte[]{
                (byte) (Integer.decode("0x" + hexes[0]).byteValue() | 2),
                Integer.decode("0x" + hexes[1]).byteValue(),
                Integer.decode("0x" + hexes[2]).byteValue(),
                (byte) 0xFF,
                (byte) 0xFE,
                Integer.decode("0x" + hexes[3]).byteValue(),
                Integer.decode("0x" + hexes[4]).byteValue(),
                Integer.decode("0x" + hexes[5]).byteValue(),
        };
        return bytes;
    }
    */

    class ConnectRunnable implements Runnable {
        private Contact contact;

        ConnectRunnable(Contact contact) {
            this.contact = contact;
        }


  /*      private String address;
        private String username;
        private String identifier;

        ConnectRunnable(Contact contact) {
            this.address = contact.getAddress();
            this.username = userName;
            this.identifier = Utils.formatAddress(Utils.getMacAddress());
        }
        */


        @Override
        public void run() {
            try {
                //  Socket s = new Socket(address.replace("%zone", "%wlan0"), serverPort);
                Socket s = this.contact.createSocket();
                OutputStream os = s.getOutputStream();
                JSONObject object = new JSONObject();

                object.put("action", "connect");
                //object.put("username", username);
                //object.put("identifier", identifier);
                object.put("data", Contact.exportJSON(this.contact));

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

    private BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "settings_changed": {
                    String subject = intent.getStringExtra("subject");
                    switch (subject) {
                        case "username": {
                            userName = intent.getStringExtra("username");
                            Log.d("Service", "username: " + userName);
                            break;
                        }
                        case "ignoreUnsaved":{
                            ignoreUnsaved = intent.getBooleanExtra("ignoreUnsaved", false);
                            Log.d("Service", "ignore: " + ignoreUnsaved);
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
