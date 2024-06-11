package org.rivchain.cuplink

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.widget.Toast
import org.rivchain.cuplink.util.Log

/*
 * Start App on Android bootup. StartActivity is started to check
 * if a password for the database is need. the name and key-pair
 * is set.
 */
class BootUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, StartActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(IS_START_ON_BOOTUP, true) // start MainService only, not MainActivity
            context.startActivity(i)
        }

        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Wrong action: ${intent?.action}")
        }
        Log.i(TAG, "CupLink enabled, starting service")
        val serviceIntent = Intent(context, MainService::class.java)
        serviceIntent.action = MainService.ACTION_START

        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            Log.i(TAG, "Need to ask for VPN permission")
            val notification = createPermissionMissingNotification(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(444, notification)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {

        const val TAG = "BootUpReceiver"

        const val IS_START_ON_BOOTUP = "IS_START_ON_BOOTUP"

        fun setEnabled(context: Context, enabled: Boolean) {
            val newState = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            try {
                context.packageManager
                    .setComponentEnabledSetting(
                        ComponentName(context, BootUpReceiver::class.java),
                        newState, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
