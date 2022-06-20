package d.d.meshenger

import android.app.Dialog
import android.app.Service
import android.content.*
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import d.d.meshenger.AddressUtils.getOwnAddresses
import d.d.meshenger.Log.d
import d.d.meshenger.Utils.isMAC
import d.d.meshenger.Utils.isValidContactName
import d.d.meshenger.dialog.SetUsernameDialog
import d.d.meshenger.dialog.SetupAddressDialog
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium


class StartActivity: MeshengerActivity(), ServiceConnection {

    companion object {
        private const val TAG = "StartActivity"
        private const val IGNORE_BATTERY_OPTIMIZATION_REQUEST = 5223
        private var sodium: Sodium? = null
    }

    private var startState = 0
    private var requestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
            if (result.resultCode== IGNORE_BATTERY_OPTIMIZATION_REQUEST) {
                // resultCode: -1 (Allow), 0 (Deny)
                continueInit()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d(TAG, "onCreate")
        setContentView(R.layout.activity_splash)

        // load libsodium for JNI access
        sodium = NaCl.sodium()
        val type = Typeface.createFromAsset(assets, "rounds_black.otf")
        (findViewById<View>(R.id.splashText) as TextView).typeface = type

        // start MainService and call back via onServiceConnected()
        MainService.start(this)
//        val myService = startService(Intent(this, MainService::class.java))
        bindService(Intent(this, MainService::class.java), this, Service.BIND_AUTO_CREATE)
    }

    fun continueInit() { //TODO: While loop?
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
                if (MainService.instance!!.database == null) {
                    // database is probably encrypted
                    showDatabasePasswordDialog()
                } else {
                    continueInit()
                }
            }
            3 -> {
                d(TAG, "init 3: check username")
                if (MainService.instance?.getSettings()?.username!!.isEmpty()) {
                    // set username
                    showMissingUsernameDialog()
                } else {
                    continueInit()
                }
            }
            4 -> {
                d(TAG, "init 4: check key pair")
                if (MainService.instance?.getSettings()?.publicKey == null) {
                    // generate key pair
                    initKeyPair()
                }
                continueInit()
            }
            5 -> {
                d(TAG, "init 5: check addresses")
                if (MainService.instance?.isFirstStart() == true) {
                    showMissingAddressDialog()
                } else {
                    continueInit()
                }
            }
            6 -> {
                d(TAG, "init 6: battery optimizations")
                if (MainService.instance?.isFirstStart() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val pMgr = this.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (!pMgr.isIgnoringBatteryOptimizations(this.packageName)) {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) //TODO(IODevBlue): Device alternate Intent request. Violation alert
                            intent.data = Uri.parse("package:" + this.packageName)
                            requestLauncher.launch(intent)
//                            startActivityForResult(intent, IGNORE_BATTERY_OPTIMIZATION_REQUEST)
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
                val nightMode = MainService.instance?.getSettings()?.nightMode?:false
                AppCompatDelegate.setDefaultNightMode(
                    if (nightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )

                // all done - show contact list
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {
        //this.binder = (MainService.MainBinder) iBinder;
        d(TAG, "onServiceConnected")
        if (startState == 0) {
            if (MainService.instance?.isFirstStart() == true) {
                // show delayed splash page
                Handler(Looper.myLooper()!!).postDelayed({ continueInit() }, 1000)
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
        val settings = MainService.instance?.getSettings()
        settings?.publicKey = publicKey
        settings?.secretKey = secretKey
        try {
            MainService.instance?.saveDatabase()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // for the database initialization
    private fun getMacOfDevice(device: String): String {
        for (ae in getOwnAddresses()) {
            // only MAC addresses
            if (ae.device == "wlan0" && isMAC(ae.address)) {
                return ae.address
            }
        }
        return ""
    }

    fun showMissingAddressDialog() {
        val mac = getMacOfDevice("wlan0")
        if (mac.isEmpty()) {
            val setupAddressDialog = SetupAddressDialog(this)
            setupAddressDialog.show(supportFragmentManager, "Setup Address")
        } else {
            MainService.instance?.getSettings()?.addAddress(mac)
            MainService.instance?.saveDatabase()
            continueInit()
        }
    }

    // initial dialog to set the username
    private fun showMissingUsernameDialog() {
        val setUsernameDialog = SetUsernameDialog(this)
        setUsernameDialog.show(supportFragmentManager, "SetUsername")

    }

    // ask for database password
    private fun showDatabasePasswordDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_database_password)
        dialog.setCancelable(false)
        val passwordEditText: EditText = dialog.findViewById(R.id.PasswordEditText)
        val exitButton: Button = dialog.findViewById(R.id.ExitButton)
        val okButton: Button = dialog.findViewById(R.id.OkButton)
        okButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            MainService.instance?.databasePassword = password
            MainService.instance?.loadDatabase()
            if (MainService.instance?.database == null) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            } else {
                // close dialog
                dialog.dismiss()
                continueInit()
            }
        }
        exitButton.setOnClickListener {
            // shutdown app
            dialog.dismiss()
            stopService(Intent(this, MainService::class.java))
            finish()
        }
        dialog.show()
    }
}