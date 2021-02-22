package d.d.meshenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.json.JSONObject;
import org.libsodium.jni.Sodium;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;


public class MainService extends Service implements Runnable {
    private Database db = null;
    private boolean first_start = false;
    private String database_path = "";
    private String database_password = "";

    public static final int serverPort = 10001;
    private ServerSocket server;

    private volatile boolean run = true;
    private RTCCall currentCall = null;

    private ArrayList<CallEvent> events = null;

    private MainBinder mainBinder = new MainBinder(this);

    private int NOTIFICATION = 42;

    @Override
    public void onCreate() {
        super.onCreate();

        this.database_path = this.getFilesDir() + "/database.bin";

        // handle incoming connections
        new Thread(this).start();

        events = new ArrayList<>();
    }

    private void loadDatabase() {
        try {
            if ((new File(this.database_path)).exists()) {
                // open existing database
                this.db = Database.load(this.database_path, this.database_password);
                this.first_start = false;
            } else {
                // create new database
                this.db = new Database();
                this.first_start = true;
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

        // The database might be null here if no correct
        // database password was supplied to open it.

        if (this.db != null) {
            try {
                Database.store(this.database_path, this.db, this.database_password);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // shutdown listening socket and say goodbye
        if (this.db != null && this.server != null && this.server.isBound() && !this.server.isClosed()) {
            try {
                byte[] ownPublicKey = this.db.settings.getPublicKey();
                byte[] ownSecretKey = this.db.settings.getSecretKey();
                String message = "{\"action\": \"status_change\", \"status\", \"offline\"}";

                for (Contact contact : this.db.contacts) {
                    if (contact.getState() == Contact.State.OFFLINE) {
                        continue;
                    }

                    byte[] encrypted = Crypto.encryptMessage(message, contact.getPublicKey(), ownPublicKey, ownSecretKey);
                    if (encrypted == null) {
                        continue;
                    }

                    Socket socket = null;
                    try {
                        socket = contact.createSocket();
                        if (socket == null) {
                            continue;
                        }

                        PacketWriter pw = new PacketWriter(socket);
                        pw.writeMessage(encrypted);
                        socket.close();
                    } catch (Exception e) {
                        if (socket != null) {
                            try {
                                socket.close();
                            } catch (Exception ee) {
                                // ignore
                            }
                        }
                    }
                }

                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (this.db != null) {
            // zero keys from memory
            this.db.onDestroy();
        }
    }

    private void showNotification() {
        String channelId = "meshenger_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(channelId, "Meshenger Background Service", NotificationManager.IMPORTANCE_DEFAULT);
            chan.setLightColor(Color.RED);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            service.createNotificationChannel(chan);
        }

        // start MainActivity
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingNotificationIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Context mActivity = getApplicationContext();
        Notification notification = new NotificationCompat.Builder(mActivity, channelId)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_logo)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_small))
                .setPriority(PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText(getResources().getText(R.string.listen_for_incoming_calls))
                .setContentIntent(pendingNotificationIntent)
                .build();

        startForeground(NOTIFICATION, notification);
    }

    final static String START_FOREGROUND_ACTION = "START_FOREGROUND_ACTION";
    final static String STOP_FOREGROUND_ACTION = "STOP_FOREGROUND_ACTION";

    public static void start(Context ctx) {
        Intent startIntent = new Intent(ctx, MainService.class);
        startIntent.setAction(START_FOREGROUND_ACTION);
        ContextCompat.startForegroundService(ctx, startIntent);
    }

    public static void stop(Context ctx) {
        Intent stopIntent = new Intent(ctx, MainService.class);
        stopIntent.setAction(STOP_FOREGROUND_ACTION);
        ctx.startService(stopIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // ignore
        } else if (intent.getAction().equals(START_FOREGROUND_ACTION)) {
            log("Received Start Foreground Intent");
            showNotification();
        } else if (intent.getAction().equals(STOP_FOREGROUND_ACTION)) {
            log("Received Stop Foreground Intent");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION);
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void handleClient(MainBinder binder, Socket client) {
        // just a precaution
        if (this.db == null) {
            return;
        }

        byte[] clientPublicKey = new byte[Sodium.crypto_sign_publickeybytes()];
        byte[] ownSecretKey = this.db.settings.getSecretKey();
        byte[] ownPublicKey = this.db.settings.getPublicKey();

        try {
            PacketWriter pw = new PacketWriter(client);
            PacketReader pr = new PacketReader(client);
            Contact contact = null;

            InetSocketAddress remote_address = (InetSocketAddress) client.getRemoteSocketAddress();
            log("incoming connection from " + remote_address);

            while (true) {
                byte[] request = pr.readMessage();
                if (request == null) {
                    break;
                }

                String decrypted = Crypto.decryptMessage(request, clientPublicKey, ownPublicKey, ownSecretKey);
                if (decrypted == null) {
                    log("decryption failed");
                    break;
                }

                if (contact == null) {
                    for (Contact c : this.db.contacts) {
                        if (Arrays.equals(c.getPublicKey(), clientPublicKey)) {
                            contact = c;
                        }
                    }

                    if (contact == null && this.db.settings.getBlockUnknown()) {
                        if (this.currentCall != null) {
                            log("block unknown contact => decline");
                            this.currentCall.decline();
                        }
                        break;
                    }

                    if (contact != null && contact.getBlocked()) {
                        if (this.currentCall != null) {
                            log("blocked contact => decline");
                            this.currentCall.decline();
                        }
                        break;
                    }

                    if (contact == null) {
                        // unknown caller
                        contact = new Contact("", clientPublicKey.clone(), new ArrayList<>());
                    }
                }

                // suspicious change of identity during connection...
                if (!Arrays.equals(contact.getPublicKey(), clientPublicKey)) {
                    log("suspicious change of identity");
                    continue;
                }

                // remember last good address (the outgoing port is random and not the server port)
                contact.setLastWorkingAddress(
                    new InetSocketAddress(remote_address.getAddress(), MainService.serverPort)
                );

                JSONObject obj = new JSONObject(decrypted);
                String action = obj.optString("action", "");

                switch (action) {
                    case "call": {
                        // someone calls us
                        log("got call...");
                        String offer = obj.getString("offer");
                        this.currentCall = new RTCCall(this, binder, contact, client, offer);

                        // respond that we accept the call

                        byte[] encrypted = Crypto.encryptMessage("{\"action\":\"ringing\"}", contact.getPublicKey(), ownPublicKey, ownSecretKey);
                        pw.writeMessage(encrypted);

                        Intent intent = new Intent(this, CallActivity.class);
                        intent.setAction("ACTION_INCOMING_CALL");
                        intent.putExtra("EXTRA_CONTACT", contact);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return;
                    }
                    case "ping": {
                        log("got ping...");
                        // someone wants to know if we are online
                        binder.setContactState(contact.getPublicKey(), Contact.State.ONLINE);
                        byte[] encrypted = Crypto.encryptMessage("{\"action\":\"pong\"}", contact.getPublicKey(), ownPublicKey, ownSecretKey);
                        pw.writeMessage(encrypted);
                        break;
                    }
                    case "status_change": {
                        if (obj.optString("status", "").equals("offline")) {
                            binder.setContactState(contact.getPublicKey(), Contact.State.OFFLINE);
                        } else {
                            log("Received unknown status_change: " + obj.getString("status"));
                        }
                    }
                }
            }

            log("call disconnected");
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("call_declined"));

        } catch (Exception e) {
            e.printStackTrace();
            log("client disconnected (exception)");
            if (this.currentCall != null) {
                this.currentCall.decline();
            }
        }

        // zero out key
        Arrays.fill(clientPublicKey, (byte) 0);
    }

    @Override
    public void run() {
        try {
            // wait until database is ready
            while (this.db == null && this.run) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }

            server = new ServerSocket(serverPort);
            MainBinder binder = (MainBinder) onBind(null);

            while (this.run) {
                try {
                    Socket socket = server.accept();
                    new Thread(() -> handleClient(binder, socket)).start();
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
    static class MainBinder extends Binder {
        private MainService service;

        MainBinder(MainService service) {
            this.service = service;
        }

        Context getContext() {
            return this.service;
        }

        RTCCall getCurrentCall() {
            return this.service.currentCall;
        }

        boolean isFirstStart() {
            return this.service.first_start;
        }

        Contact getContactByPublicKey(byte[] pubKey) {
            for (Contact contact : this.service.db.contacts) {
                if (Arrays.equals(contact.getPublicKey(), pubKey)) {
                    return contact;
                }
            }
            return null;
        }

        Contact getContactByName(String name) {
            for (Contact contact : this.service.db.contacts) {
                if (contact.getName().equals(name)) {
                    return contact;
                }
            }
            return null;
        }

        void addContact(Contact contact) {
            this.service.db.addContact(contact);
            saveDatabase();
            LocalBroadcastManager.getInstance(this.service).sendBroadcast(new Intent("refresh_contact_list"));
        }

        void deleteContact(byte[] pubKey) {
            this.service.db.deleteContact(pubKey);
            saveDatabase();
            LocalBroadcastManager.getInstance(this.service).sendBroadcast(new Intent("refresh_contact_list"));
        }

        void setContactState(byte[] publicKey, Contact.State state) {
            Contact contact = getContactByPublicKey(publicKey);
            if (contact != null && contact.getState() != state) {
                contact.setState(state);
                LocalBroadcastManager.getInstance(this.service).sendBroadcast(new Intent("refresh_contact_list"));
            }
        }

        String getDatabasePassword() {
            return this.service.database_password;
        }

        void setDatabasePassword(String password) {
            this.service.database_password = password;
        }

        Database getDatabase() {
            return this.service.db;
        }

        void loadDatabase() {
            this.service.loadDatabase();
        }

        void replaceDatabase(Database db) {
            if (db != null) {
                if (this.service.db == null) {
                    this.service.db = db;
                } else {
                    this.service.db = db;
                    saveDatabase();
                }
            }
        }

        void pingContacts() {
            Log.d(this, "pingContacts");
            if (this.service.db != null) {
                new Thread(new PingRunnable(
                    this,
                    getContactsCopy(),
                    getSettings().getPublicKey(),
                    getSettings().getSecretKey())
                ).start();
            }
        }

        void saveDatabase() {
            this.service.saveDatabase();
        }

        Settings getSettings() {
            return this.service.db.settings;
        }

        // return a cloned list
        List<Contact> getContactsCopy() {
           return new ArrayList<>(this.service.db.contacts);
        }

        void addCallEvent(Contact contact, CallEvent.Type type) {
            InetSocketAddress last_working = contact.getLastWorkingAddress();
            this.service.events.add(new CallEvent(
                contact.getPublicKey(),
                    (last_working != null) ? last_working.getAddress() : null,
                type
            ));
            LocalBroadcastManager.getInstance(this.service).sendBroadcast(new Intent("refresh_event_list"));
        }

        // return a cloned list
        List<CallEvent> getEventsCopy() {
            return new ArrayList<>(this.service.events);
        }

        void clearEvents() {
            this.service.events.clear();
            LocalBroadcastManager.getInstance(this.service).sendBroadcast(new Intent("refresh_event_list"));
        }
    }

    static class PingRunnable implements Runnable {
        private List<Contact> contacts;
        byte[] ownPublicKey;
        byte[] ownSecretKey;
        MainBinder binder;

        PingRunnable(MainBinder binder, List<Contact> contacts, byte[] ownPublicKey, byte[] ownSecretKey) {
            this.binder = binder;
            this.contacts = contacts;
            this.ownPublicKey = ownPublicKey;
            this.ownSecretKey = ownSecretKey;
        }

        @Override
        public void run() {
            for (Contact contact : contacts) {
                Socket socket = null;
                byte[] publicKey = contact.getPublicKey();

                try {
                    socket = contact.createSocket();
                    if (socket == null) {
                        this.binder.setContactState(publicKey, Contact.State.OFFLINE);
                        continue;
                    }

                    PacketWriter pw = new PacketWriter(socket);
                    PacketReader pr = new PacketReader(socket);

                    log("send ping to " + contact.getName());

                    byte[] encrypted = Crypto.encryptMessage("{\"action\":\"ping\"}", publicKey, ownPublicKey, ownSecretKey);
                    if (encrypted == null) {
                        socket.close();
                        continue;
                    }

                    pw.writeMessage(encrypted);

                    byte[] request = pr.readMessage();
                    if (request == null) {
                        socket.close();
                        continue;
                    }

                    String decrypted = Crypto.decryptMessage(request, publicKey, ownPublicKey, ownSecretKey);
                    if (decrypted == null) {
                        log("decryption failed");
                        socket.close();
                        continue;
                    }

                    JSONObject obj = new JSONObject(decrypted);
                    String action = obj.optString("action", "");
                    if (action.equals("pong")) {
                        log("got pong");
                        this.binder.setContactState(publicKey, Contact.State.ONLINE);
                    }

                    socket.close();
                } catch (Exception e) {
                    this.binder.setContactState(publicKey, Contact.State.OFFLINE);
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ee) {
                            // ignore
                        }
                    }
                    e.printStackTrace();
                }
            }

            log("send refresh_contact_list");
            LocalBroadcastManager.getInstance(this.binder.getContext()).sendBroadcast(new Intent("refresh_contact_list"));
        }

        private void log(String data) {
            Log.d(this, data);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mainBinder;
    }

    private void log(String data) {
        Log.d(this, data);
    }
}
