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
import org.webrtc.SurfaceViewRenderer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;


public class MainService extends Service implements Runnable {
    private Database db = null;
    private String database_path = "";
    private String database_password = "";

    public static final int serverPort = 10001;
    private ServerSocket server;

    private volatile boolean run = true;
    private RTCCall currentCall = null;

    @Override
    public void onCreate() {
        super.onCreate();

        this.database_path = this.getFilesDir() + "/database.bin";

        // handle incoming connections
        new Thread(this).start();

        LocalBroadcastManager.getInstance(this).registerReceiver(settingsReceiver, new IntentFilter("settings_changed"));
    }

    private void loadDatabase() {
        try {
            if ((new File(this.database_path)).exists()) {
                // open existing database
                this.db = Database.load(this.database_path, this.database_password);
            } else {
                // create new database
                this.db = new Database();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void saveDatabase() {
        try {
            Database.store(MainService.this.database_path, MainService.this.db, MainService.this.database_password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.run = false;

        try {
            Database.store(this.database_path, this.db, this.database_password);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // shutdown listening socket and say goodbye
        if (this.db != null && this.server != null && this.server.isBound() && !this.server.isClosed()) {
            try {
                String message = "{\"action\": \"status_change\", \"status\", \"offline\"}";

                for (Contact contact : this.db.contacts) {
                    if (contact.getState() == Contact.State.OFFLINE) {
                        continue;
                    }

                    String encrypted = Crypto.encrypt(message, contact.getPublicKey(), this.db.settings.getSecretKey());
                    if (encrypted == null) {
                        continue;
                    }

                    for (InetSocketAddress addr : contact.getAllSocketAddresses()) {
                        Socket socket = null;
                        try {
                            socket = new Socket(addr.getAddress(), addr.getPort());
                            OutputStream os = socket.getOutputStream();
                            os.write(encrypted.getBytes());
                            os.flush();
                            socket.close();
                            break;
                        } catch (Exception e) {
                            if (socket != null) {
                                try {
                                    socket.close();
                                } catch (Exception ee) {
                                    // ignore
                                }
                            }
                            socket = null;
                        }
                    }
                }

                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(settingsReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void refreshContacts() {
        /*
        ArrayList<Contact> contacts = (ArrayList<Contact>) db.getContacts();
        if (db.getSettings() == null) {
            //userName = "Unknown";
            ignoreUnsaved = false;
        } else {
            //userName = db.getSettings().getUsername();
            if (db.getSettings().getBlockUC() == 1) {
                ignoreUnsaved = true;
            } else {
                ignoreUnsaved = false;
            }
        }
        */
    }

    private void handleClient(Socket client) {
        log("handleClient");

        try {
            InputStream is = client.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream os = client.getOutputStream();
            Contact contact = null;
            String request;

            log("waiting for line...");
            while ((request = reader.readLine()) != null) {
                String decrypted = null;

                if (contact == null) {
                    // look for contact that decrypts the message
                    for (Contact c : this.db.contacts) {
                        decrypted = Crypto.decrypt(request, c.getPublicKey(), this.db.settings.getSecretKey());
                        if (decrypted != null) {
                            contact = c;
                            break;
                        }
                    }
                } else {
                    // we know the contact
                    decrypted = Crypto.decrypt(request, contact.getPublicKey(), this.db.settings.getSecretKey());
                }

                if (decrypted == null) {
                    // cannot decrypt / unknown caller
                    break;
                }

                if (this.db.settings.getBlockUnknown() && !db.contactExists(contact.getPublicKey())) {
                    if (this.currentCall != null) {
                        this.currentCall.decline();
                    }
                    continue;
                };

                JSONObject obj = new JSONObject(decrypted);
                String action = obj.optString("action", "");
                String secretKey = this.db.settings.getSecretKey();

                switch (action) {
                    case "call": {
                        // someone calls us
                        log("call...");
                        String offer = obj.getString("offer");
                        this.currentCall = new RTCCall(this, secretKey, contact, client, offer);

                        // respond that we accept the call
                        String encrypted = Crypto.encrypt("{\"action\":\"ringing\"}", contact.getPublicKey(), secretKey);
                        os.write(encrypted.getBytes());

                        Intent intent = new Intent(this, CallActivity.class);
                        intent.setAction("ACTION_ACCEPT_CALL");
                        intent.putExtra("EXTRA_CONTACT", contact);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return;
                    }
                    case "ping": {
                        log("ping...");
                        // someone wants to know if we are online
                        setClientState(contact, Contact.State.ONLINE);
                        String encrypted = Crypto.encrypt("{\"action\":\"pong\"}", contact.getPublicKey(), secretKey);
                        os.write(encrypted.getBytes());
                        break;
                    }
                    case "status_change": {
                        if (obj.optString("status", "").equals("offline")) {
                            setClientState(contact, Contact.State.OFFLINE);
                        } else {
                            log("Received unknown status_change: " + obj.getString("status"));
                        }
                    }
                }
            }

            log("client disconnected");
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("call_declined"));
        } catch (Exception e) {
            e.printStackTrace();
            log("client disconnected (exception)");
            if (this.currentCall != null) {
                this.currentCall.decline();
            }
        }
    }

    private void setClientState(Contact contact, Contact.State state) {
        contact.setState(Contact.State.ONLINE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("contact_refresh"));
    }

    @Override
    public void run() {
        try {
            // wait until database is ready
            while (this.db == null && this.run) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    // ignore
                }
            }

            server = new ServerSocket(serverPort);
            refreshContacts();
            while (this.run) {
                try {
                    Socket socket = server.accept();
                    new Thread(() -> handleClient(socket)).start();
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            new Handler(getMainLooper()).post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            stopSelf();
            return;
        }
    }

    /*
    * Allows communication between MainService and other objects
    */
    class MainBinder extends Binder {
        RTCCall startCall(Contact contact, RTCCall.OnStateChangeListener listener, SurfaceViewRenderer renderer) {
            return RTCCall.startCall(MainService.this, MainService.this.db.settings.getSecretKey(), contact, listener);
        }

        RTCCall getCurrentCall() {
            return currentCall;
        }

        void addContact(Contact contact) {
            try {
                db.addContact(contact);
                saveDatabase();
                refreshContacts();
            } catch (Database.ContactAlreadyAddedException e) {
                Toast.makeText(MainService.this, getResources().getString(R.string.contact_already_exists), Toast.LENGTH_SHORT).show();
            }
        }

        void deleteContact(String pubKey) {
            db.deleteContact(pubKey);
            saveDatabase();
            refreshContacts();
        }

        void setContactName(String pubKey, String name) {
            int idx = db.findContact(pubKey);
            if (idx >= 0) {
                db.contacts.get(idx).setName(name);
                saveDatabase();
                refreshContacts();
            }
        }

        String getDatabasePassword() {
            return MainService.this.database_password;
        }

        void setDatabasePassword(String password) {
            MainService.this.database_password = password;
        }

        Database getDatabase() {
            return MainService.this.db;
        }

        void loadDatabase() {
            MainService.this.loadDatabase();
        }

        void replaceDatabase(Database db) {
            if (db != null) {
                if (MainService.this.db == null) {
                    MainService.this.db = db;
                } else {
                    MainService.this.db = db;
                    saveDatabase();
                }
            }
        }

        void pingContacts(ContactPingListener listener) {
            String secretKey = getSettings().getSecretKey();
            new Thread(new PingRunnable(getContacts(), secretKey, listener)).start();
        }

        void saveDatabase() {
            MainService.this.saveDatabase();
        }

        Settings getSettings() {
            return MainService.this.db.settings;
        }

        List<Contact> getContacts() {
            return MainService.this.db.contacts;
        }
    }

    class PingRunnable implements Runnable {
        private List<Contact> contacts;
        String secretKey;
        ContactPingListener listener;
        Socket socket;

        PingRunnable(List<Contact> contacts, String secretKey, ContactPingListener listener) {
            this.contacts = contacts;
            this.secretKey = secretKey;
            this.listener = listener;
            this.socket = new Socket();
        }

        @Override
        public void run() {
            for (Contact contact : contacts) {
                try {
                    Socket socket = contact.createSocket();
                    if (socket == null) {
                        continue;
                    }

                    String encrypted = Crypto.encrypt("{\"action\":\"ping\"}", contact.getPublicKey(), secretKey);
                    if (encrypted == null) {
                        continue;
                    }
                    OutputStream os = socket.getOutputStream();
                    os.write(encrypted.getBytes());
                    os.close();

                    contact.setState(Contact.State.ONLINE);
                } catch (Exception e) {
                    contact.setState(Contact.State.OFFLINE);
                    e.printStackTrace();
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (listener != null) {
                        listener.onContactPingResult(contact);
                    } else {
                        log("no listener!");
                    }
                }
            }
        }
    }

    private BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /*
            switch (intent.getAction()) {
                case "settings_changed": {
                    String subject = intent.getStringExtra("subject");
                    switch (subject) {
                        case "username": {
                            userName = intent.getStringExtra("username");
                            log("username: " + userName);
                            break;
                        }
                        case "ignoreUnsaved":{
                            ignoreUnsaved = intent.getBooleanExtra("ignoreUnsaved", false);
                            log("ignore: " + ignoreUnsaved);
                            break;
                        }
                    }
                }
            }*/
        }
    };

    public interface ContactPingListener {
        void onContactPingResult(Contact c);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new MainBinder();
    }

    private void log(String data) {
        Log.d(MainService.class.getSimpleName(), data);
    }
}
