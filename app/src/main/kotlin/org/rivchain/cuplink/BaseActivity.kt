package org.rivchain.cuplink

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import org.rivchain.cuplink.util.Log

/*
 * Base class for every Activity
*/
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    protected open fun onServiceRestart(){

    }

    protected open fun restartService(){
        // Inflate the layout for the dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_restart_service, null)
        // Create the AlertDialog
        val serviceRestartDialog = AlertDialog.Builder(this, R.style.PPTCDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        // Show the dialog
        serviceRestartDialog.show()

        // Restart service
        val intentStop = Intent(this, MainService::class.java)
        intentStop.action = MainService.ACTION_STOP
        startService(intentStop)
        Thread {
            Thread.sleep(1000)
            val intentStart = Intent(this, MainService::class.java)
            intentStart.action = MainService.ACTION_START
            startService(intentStart)
            Thread.sleep(2000)
            runOnUiThread {
                serviceRestartDialog.dismiss()
                onServiceRestart()
            }
        }.start()
    }

    fun setDefaultNightMode(nightModeSetting: String) {
        nightMode = when (nightModeSetting) {
            "on" -> AppCompatDelegate.MODE_NIGHT_YES
            "off" -> AppCompatDelegate.MODE_NIGHT_NO
            "auto" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else {
                    AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                }
            else -> {
                Log.e(this, "invalid night mode setting: $nightModeSetting")
                nightMode
            }
        }
    }

    // prefer to be called before super.onCreate()
    fun applyNightMode() {
        if (nightMode != AppCompatDelegate.getDefaultNightMode()) {
            Log.d(this, "Change night mode to $nightMode")
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    companion object {
        private var nightMode = AppCompatDelegate.getDefaultNightMode()

        fun isNightmodeEnabled(context: Context): Boolean {
            val mode = context.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
            return (mode == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}