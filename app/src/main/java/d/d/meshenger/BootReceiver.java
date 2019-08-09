package d.d.meshenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        log("onReceive");
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE).edit().putBoolean("Splash_shown", false).apply();
        }
    }

    private void log(String s) {
        Log.d(ContactListActivity.class.getSimpleName(), s);
    }
}
