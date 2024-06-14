package org.rivchain.cuplink.util

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.bluetooth.BluetoothManager
import android.content.ClipboardManager
import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.os.storage.StorageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object ServiceUtil {
    fun getInputMethodManager(context: Context): InputMethodManager {
        return context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    fun getWindowManager(context: Context): WindowManager {
        return context.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
    }

    fun getStorageManager(context: Context?): StorageManager? {
        return ContextCompat.getSystemService(context!!, StorageManager::class.java)
    }

    fun getConnectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun getTelephonyManager(context: Context): TelephonyManager {
        return context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    fun getAudioManager(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getSensorManager(context: Context): SensorManager {
        return context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    fun getPowerManager(context: Context): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    fun getAlarmManager(context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun getVibrator(context: Context): Vibrator {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getVibratorService(context: Context): VibratorManager {
        return context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    fun getDisplayManager(context: Context): DisplayManager {
        return context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    fun getAccessibilityManager(context: Context): AccessibilityManager {
        return context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }

    fun getClipboardManager(context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @RequiresApi(26)
    fun getJobScheduler(context: Context): JobScheduler {
        return context.getSystemService(JobScheduler::class.java) as JobScheduler
    }

    @RequiresApi(22)
    fun getSubscriptionManager(context: Context): SubscriptionManager {
        return context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    }

    fun getActivityManager(context: Context): ActivityManager {
        return context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun getLocationManager(context: Context): LocationManager? {
        return ContextCompat.getSystemService(context, LocationManager::class.java)
    }

    fun getKeyguardManager(context: Context): KeyguardManager? {
        return ContextCompat.getSystemService(context, KeyguardManager::class.java)
    }

    fun getBluetoothManager(context: Context): BluetoothManager? {
        return ContextCompat.getSystemService(context, BluetoothManager::class.java)
    }

    fun getDownloadManager(context: Context): DownloadManager? {
        return ContextCompat.getSystemService(context, DownloadManager::class.java)
    }

    fun getWifiManager(context: Context): WifiManager {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
}