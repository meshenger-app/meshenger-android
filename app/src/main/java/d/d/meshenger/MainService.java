package d.d.meshenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import d.d.meshenger.call.CallActivity;
import d.d.meshenger.call.DirectRTCClient;


public class MainService extends Service implements Runnable {
    private static final String TAG = "MainService";
    private static DirectRTCClient currentCall = null;
    private static Object currentCallLock = new Object();
    private Database database = null;
    private boolean first_start = false;
    private String database_path = "";
    private String database_password = "";
    private ServerSocket server;
    private volatile boolean run = true;

    public static MainService instance = null;
    private final IBinder mBinder = new LocalBinder();
    private NotificationCompat.Builder notificationBuilder;

    public static final int serverPort = 10001;
    private static final int NOTIFICATION_ID = 42;

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private boolean mChangingConfiguration = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        this.instance = this;
        database_path = this.getFilesDir() + "/database.bin";

        LocalBroadcastManager.getInstance(this).registerReceiver(eventsChangedReceiver, new IntentFilter("events_changed"));
        LocalBroadcastManager.getInstance(this).registerReceiver(contactsChangedReceiver, new IntentFilter("contacts_changed"));

        // handle incoming connections
        new Thread(this).start();
    }

    public void loadDatabase() {
        try {
            if ((new File(database_path)).exists()) {
                // open existing database
                byte[] data = Utils.readInternalFile(database_path);
                database = Database.fromData(data, database_password);
                first_start = false;
            } else {
                // create new database
                database = new Database();
                first_start = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void replaceDatabase(Database database) {
        if (database != null) {
            if (this.database == null) {
                this.database = database;
            } else {
                this.database = database;
                saveDatabase();
            }
        }
    }

    public boolean isFirstStart() {
        return first_start;
    }

    public String getDatabasePassword() {
        return database_password;
    }

    public void setDatabasePassword(String password) {
        database_password = password;
    }

    public Database getDatabase() {
        return database;
    }

    public Settings getSettings() {
        return database.getSettings();
    }

    public Contacts getContacts() {
        return database.getContacts();
    }

    public Events getEvents() {
        return database.getEvents();
    }

    public void saveDatabase() {
        try {
            byte[] data = Database.toData(database, database_password);
            Utils.writeInternalFile(database_path, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        run = false;

        LocalBroadcastManager.getInstance(this).unregisterReceiver(eventsChangedReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(contactsChangedReceiver);

        // The database might be null here if no correct
        // database password was supplied to open it.

        if (database != null) {
            try {
                byte[] data = Database.toData(database, database_password);
                Utils.writeInternalFile(database_path, data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // shutdown listening socket and say goodbye
        if (database != null && server != null && server.isBound() && !server.isClosed()) {
            try {
                server.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (database != null) {
            // zero keys from memory
            database.onDestroy();
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
        notificationBuilder = new NotificationCompat.Builder(mActivity, channelId)
                //.setOngoing(true)
                .setOnlyAlertOnce(true) // keep notification update from turning on the screen
                .setSmallIcon(R.drawable.ic_logo)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_small))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentText(getResources().getText(R.string.listen_for_incoming_calls))
                .setContentIntent(pendingNotificationIntent);
                //.setVisibility(VISIBILITY_PUBLIC);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());


        // update notification, since we want do display an updated "xxx ago"
        Timer t = new Timer();
        TimerTask tt = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "trigger notification update");
                updateMissedCallsNotification();
            };
        };

        t.scheduleAtFixedRate(tt, 1000, 10 * 1000); // every 10s
    }

    private final BroadcastReceiver eventsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "received events_changed");
            updateMissedCallsNotification();
            saveDatabase();
        }
    };

    private final BroadcastReceiver contactsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "received contacts_changed");
            saveDatabase();
        }
    };

    private void updateMissedCallsNotification() {
        if (notificationBuilder == null || database == null) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        List<Event> missedCalls = getEvents().getMissedCalls();

        if (missedCalls.size() == 0) {
            notificationBuilder.setContentText(
                getResources().getText(R.string.listen_for_incoming_calls)
            );
        } else {
            Event lastCall = missedCalls.get(missedCalls.size() - 1);
            notificationBuilder.setContentText(
                String.valueOf(missedCalls.size())
                + " missed calls"
                + " - "
                + DateUtils.getRelativeTimeSpanString(
                    lastCall.date.getTime(), System.currentTimeMillis(), 0L, DateUtils.FORMAT_ABBREV_ALL
                )
            );
        }

        manager.notify(NOTIFICATION_ID, notificationBuilder.build());
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
        ContextCompat.startForegroundService(ctx, stopIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // ignore
        } else if (intent.getAction().equals(START_FOREGROUND_ACTION)) {
            Log.d(TAG, "Received Start Foreground Intent");
            showNotification();
        } else if (intent.getAction().equals(STOP_FOREGROUND_ACTION)) {
            Log.d(TAG, "Received Stop Foreground Intent");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    // runs in a thread
    @Override
    public void run() {
        try {
            // wait until database is ready
            while (this.database == null && this.run) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }

            server = new ServerSocket(serverPort);
            LocalBinder binder = (LocalBinder) onBind(null);

            while (this.run) {
                Socket socket = null;
                try {
                    socket = server.accept();
                    if (DirectRTCClient.createIncomingCall(socket)) {
                        Intent intent = new Intent(this, CallActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }

                    Thread.sleep(50); // mitigate DDOS attack
                } catch (IOException e) {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException _e) {
                            // ignore
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Handler(getMainLooper()).post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            stopSelf();
            return;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }
}
