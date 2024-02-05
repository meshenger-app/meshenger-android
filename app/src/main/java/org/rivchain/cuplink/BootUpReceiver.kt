package org.rivchain.cuplink

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast

/*
 * Start App on Android bootup.
 */
class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val i = Intent(context, StartActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(IS_START_ON_BOOTUP, true) // start MainService only, not MainActivity
            context.startActivity(i)
        }
    }

    companion object {
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