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
            ContactListActivity.splash_page_shown = false;
        }
    }

    private void log(String s) {
        Log.d(BootReceiver.class.getSimpleName(), s);
    }
}
