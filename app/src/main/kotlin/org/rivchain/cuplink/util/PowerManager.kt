package org.rivchain.cuplink.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import org.rivchain.cuplink.Log

object PowerManager {

    // https://stackoverflow.com/questions/48166206/how-to-start-power-manager-of-all-android-manufactures-to-enable-push-notificati/48166241
    // https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
    private val POWERMANAGER_INTENTS = arrayOf(
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.iqoo.secure",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.htc.pitroad",
                "com.htc.pitroad.landingpage.activity.LandingPageActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.powersaver.PowerSaverSettings"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.autostart.AutoStartActivity"
            )
        ),
        Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT)
    )
    private val AUTOSTART_INTENTS = arrayOf(
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.privacypermissionsentry.PermissionTopActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.MainActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.transsion.phonemanager",
                "com.itel.autobootmanager.activity.AutoBootMgrActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.evenwell.powersaving.g3",
                "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.oneplus.security",
                "com.android.settings.action.BACKGROUND_OPTIMIZE"
            )
        ),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
    )

    private fun isCallable(context: Context?, intent: Intent): Boolean {
        val list = context!!.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return list.size > 0
    }

    private fun getPowermanagerIntent(context: Context): Intent? {
        for (intent in POWERMANAGER_INTENTS) {
            if (isCallable(context, intent)) {
                return intent
            }
        }
        return null
    }

    private fun getAutostartIntent(context: Context): Intent? {
        for (intent in AUTOSTART_INTENTS) {
            if (isCallable(context, intent)) {
                return intent
            }
        }
        return null
    }

    fun callPowerManager(context: Context) {
        for (intent in POWERMANAGER_INTENTS) {
            if (isCallable(context, intent)) {
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.e("Unable to start power manager activity", e.toString())
                }
            }
        }
    }

    fun callAutostartManager(context: Context) {
        for (intent in AUTOSTART_INTENTS) {
            if (isCallable(context, intent)) {
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.e("Unable to start autostart activity", e.toString())
                }
            }
        }
    }

    fun hasPowerManagerOption(context: Context): Boolean {
        return getPowermanagerIntent(context) != null
    }

    fun hasAutostartOption(context: Context): Boolean {
        return getAutostartIntent(context) != null
    }

    fun needsFixing(context: Context): Boolean {
        return !isIgnoringBatteryOptimizations(context) || hasAutostartOption(context) || hasPowerManagerOption(
            context
        )
    }

    /**
     * Try to find out whether battery optimizations are already disabled for our app.
     * If this fails (e.g. on devices older than Android M), `true` will be returned.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager =
                context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            return try {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) {
                Log.e("Exception while checking if battery optimization is disabled", e.toString())
                // don't care about buggy phones not implementing this API
                true
            }
        }
        return true
    }
}