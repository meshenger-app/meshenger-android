package org.rivchain.cuplink

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.IBinder
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.rivchain.cuplink.automotive.AutoControlScreen
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.RlpUtils
@RequiresApi(Build.VERSION_CODES.O)
class CarService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            // FIXME
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
            //HostValidator.Builder(applicationContext)
            //    .addAllowedHosts(R.array.hosts_allowlist)
            //    .build()
        }
    }

    override fun onCreate() {
        bindToMainService(this)
        super.onCreate()
    }

    override fun onDestroy() {
        unbindFromMainService(this)
        super.onDestroy()
    }

    override fun onCreateSession() = SettingsSession(mainService)

    private var mainService: MainService? = null
    private var mainServiceConnection: ServiceConnection? = null
    private fun bindToMainService(context: Context) {

        mainServiceConnection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
                mainService = (iBinder as MainService.MainBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // nothing todo
            }
        }

        val intent = Intent(context, MainService::class.java)
        context.bindService(intent, mainServiceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun unbindFromMainService(context: Context) {
        mainService = null
        mainServiceConnection?.let {
            context.unbindService(it)
            mainServiceConnection = null
        }
    }

}
@RequiresApi(Build.VERSION_CODES.M)
class SettingsSession(private var mainService: MainService?) : Session(), DefaultLifecycleObserver {

    init {
        lifecycle.addObserver(this@SettingsSession)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        carContext.onBackPressedDispatcher.addCallback(this@SettingsSession, object : OnBackPressedCallback(true) {
            /**
             * Finish the app when the back button is pressed on the root menu
             */
            override fun handleOnBackPressed() {
                val screenManager = carContext.getCarService(ScreenManager::class.java)
                when {
                    screenManager.stackSize > 1 -> screenManager.pop()
                    else -> carContext.finishCarApp()
                }
            }
        })

    }

    override fun onCreateScreen(intent: Intent): Screen {
        // fix NPE below for mainServiceBinder
        Log.d(this, "car action:"+intent.action.toString())
        if(intent.data != null) {
            val uri = intent.data.toString()
            Log.d(this, "uri:" + intent.data)
            if(uri != "cpl://localhost/#r/decline_call"){
                mainService!!.startActivity(
                    CallActivity.clearTop(mainService!!)
                        .setAction("ANSWER_INCOMING_CALL")
                        .putExtra("EXTRA_CONTACT", RlpUtils.parseLink(uri))
                )
            } else {
                PendingIntent.getBroadcast(
                    mainService!!,
                    0,
                    Intent().apply {
                        action = CallService.STOP_CALL_ACTION
                    },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE
                    else
                        0
                )
            }
            carContext.finishCarApp()
        }
        return AutoControlScreen(carContext, mainService!!.getContacts())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}