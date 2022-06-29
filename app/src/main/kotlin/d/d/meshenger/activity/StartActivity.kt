package d.d.meshenger.activity

import android.app.Dialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import d.d.meshenger.R
import d.d.meshenger.utils.AddressUtils.getOwnAddresses
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Utils.isMAC
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.dialog.SetUsernameDialog
import d.d.meshenger.dialog.SetAddressDialog
import d.d.meshenger.dialog.SetPasswordDialog
import d.d.meshenger.service.MainService
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium

class StartActivity: MeshengerActivity(), ServiceConnection {

    companion object {

        private const val TAG = "StartActivity"
        private var sodium: Sodium? = null
        private const val IGNORE_BATTERY_OPTIMIZATION_REQUEST = 5223

    }

    private var startState = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d(TAG, "onCreate")
        setContentView(R.layout.activity_splash)

        // load libsodium for JNI access
        sodium = NaCl.sodium()
        val type = Typeface.createFromAsset(assets, "rounds_black.otf")
        (findViewById<View>(R.id.splashText) as TextView).setTypeface(type)

        // start MainService and call back via onServiceConnected()
        MainService.start(this)
        val myService = startService(Intent(this, MainService::class.java))
        bindService(Intent(this, MainService::class.java), this, BIND_AUTO_CREATE)
    }

    fun continueInit() {
        startState += 1
        when (startState) {
            1 -> {
                d(TAG, "init 1: load database")
                // open without password
                MainService.instance!!.loadDatabase()
                continueInit()
            }
            2 -> {
                d(TAG, "init 2: check database")
                if (MainService.instance!!.database ==  null) {
                    // database is probably encrypted
                    showDatabasePasswordDialog()
                } else {
                    continueInit()
                }
            }
            3 -> {
                d(TAG, "init 3: check username")
                if (MainService.instance!!.getSettings()?.username!!.isEmpty()) {
                    // set username
                    showMissingUsernameDialog()
                } else {
                    continueInit()
                }
            }
            4 -> {
                d(TAG, "init 4: check key pair")
                if (MainService.instance!!.getSettings()?.publicKey == null) {
                    // generate key pair
                    initKeyPair()
                }
                continueInit()
            }
            5 -> {
                d(TAG, "init 5: check addresses")
                if (MainService.instance!!.first_start) {
                    showMissingAddressDialog()
                } else {
                    continueInit()
                }
            }
            6 -> {
                d(TAG, "init 6: battery optimizations")
                if (MainService.instance!!.first_start && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val pMgr = this.getSystemService(POWER_SERVICE) as PowerManager
                        if (!pMgr.isIgnoringBatteryOptimizations(this.packageName)) {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:" + this.packageName)
                            startActivityForResult(intent, IGNORE_BATTERY_OPTIMIZATION_REQUEST)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                continueInit()
            }
            7 -> {
                d(TAG, "init 7: start contact list")
                // set night mode
                val nightMode = MainService.instance!!.getSettings()?.nightMode!!
                AppCompatDelegate.setDefaultNightMode(
                    if (nightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )

                // all done - show contact list
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IGNORE_BATTERY_OPTIMIZATION_REQUEST) {
            // resultCode: -1 (Allow), 0 (Deny)
            continueInit()
        }
    }

    override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {
        //this.binder = (MainService.MainBinder) iBinder;
        d(TAG, "onServiceConnected")
        if (startState == 0) {
            if (MainService.instance!!.first_start) {
                // show delayed splash page

                Handler(Looper.getMainLooper()).postDelayed({ continueInit() }, 1000)
            } else {
                // show contact list as fast as possible
                continueInit()
            }
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName?) {
        //this.binder = null;
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }


    private fun initKeyPair() {
        // create secret/public key pair
        val publicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val secretKey = ByteArray(Sodium.crypto_sign_secretkeybytes())
        Sodium.crypto_sign_keypair(publicKey, secretKey)
        val settings = MainService.instance!!.getSettings()!!
        settings.publicKey = publicKey
        settings.secretKey = secretKey
        try {
            MainService.instance!!.saveDatabase()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    // for the database initialization
    private fun getMacOfDevice(device: String): String {
        for ( ae in getOwnAddresses()) {
            // only MAC addresses
            if (ae.device == "wlan0" && isMAC(ae.address)) {
                return ae.address
            }
        }
        return ""
    }

    fun showMissingAddressDialog() {
        val showAddressDialog = SetAddressDialog(this)
        showAddressDialog.show(supportFragmentManager, "Setup Address")
    }

    // initial dialog to set the username
    private fun showMissingUsernameDialog() {

        val setUsernameDialog = SetUsernameDialog(this)
        setUsernameDialog.show(supportFragmentManager, "Input Username")
    }

    // ask for database password
    private fun showDatabasePasswordDialog() {
        val dialog = SetPasswordDialog(this)
        dialog.show(supportFragmentManager, "Show Password Dialog")

    }


}