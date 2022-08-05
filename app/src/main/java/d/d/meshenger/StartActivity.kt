package d.d.meshenger

import android.app.Dialog
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import d.d.meshenger.MainService
import d.d.meshenger.MainService.MainBinder
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import java.util.*
import java.util.regex.Pattern

/*
 * Show splash screen, name setup dialog, database password dialog and
 * start background service before starting the MainActivity.
 */
class StartActivity : MeshengerActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    private var startState = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // load libsodium for JNI access
        sodium = NaCl.sodium()
        val type = Typeface.createFromAsset(assets, "rounds_black.otf")
        (findViewById<View>(R.id.splashText) as TextView).setTypeface(type)
//MainService.start(this)
        // start MainService and call back via onServiceConnected()
        startService(Intent(this, MainService::class.java))
    }

    private fun continueInit() {
        startState += 1
        when (startState) {
            1 -> {
                log("init 1: load database")
                // open without password
                binder!!.loadDatabase()
                continueInit()
            }
            2 -> {
                log("init 2: check database")
                if (binder!!.getService().database == null) {
                    // database is probably encrypted
                    showDatabasePasswordDialog()
                } else {
                    continueInit()
                }
            }
            3 -> {
                log("init 3: check username")
                if (binder!!.settings.username.isEmpty()) {
                    // set username
                    showMissingUsernameDialog()
                } else {
                    continueInit()
                }
            }
            4 -> {
                log("init 4: check key pair")
                if (binder!!.settings.publicKey == null) {
                    // generate key pair
                    initKeyPair()
                }
                continueInit()
            }
            5 -> {
                log("init 5: check addresses")
                if (binder!!.getService()!!.isFirstStart) {
                    showMissingAddressDialog()
                } else {
                    continueInit()
                }
            }
            6 -> {
                log("init 6: start contact list")
                // set night mode
                val nightMode = binder!!.settings.nightMode
                AppCompatDelegate.setDefaultNightMode(
                    if (nightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )

                // all done - show contact list
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        log("onServiceConnected")
        if (startState == 0) {
            if (binder!!.getService().isFirstStart) {
                // show delayed splash page
                Handler().postDelayed({ continueInit() }, 1000)
            } else {
                // show contact list as fast as possible
                continueInit()
            }
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, MainService::class.java), this, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        unbindService(this)
    }

    private fun initKeyPair() {
        // create secret/public key pair
        val publicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val secretKey = ByteArray(Sodium.crypto_sign_secretkeybytes())
        Sodium.crypto_sign_keypair(publicKey, secretKey)
        val settings = binder!!.settings
        settings.publicKey = publicKey
        settings.secretKey = secretKey
        try {
            binder!!.saveDatabase()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMacOfDevice(device: String): String {
        for (ae in Utils.collectAddresses()) {
            // only MAC addresses
            if (ae.device == "wlan0" && Utils.isMAC(ae.address)) {
                return ae.address
            }
        }
        return ""
    }

    private fun showMissingAddressDialog() {
        val mac = getMacOfDevice("wlan0")
//        if (mac.isEmpty()) {
//            val builder = AlertDialog.Builder(this)
//            builder.setTitle("Setup Address")
//            builder.setMessage("No address of your WiFi card found. Enable WiFi now (not Internet needed) or skip to configure later.")
//            builder.setPositiveButton(R.string.ok) { dialog: DialogInterface, id: Int ->
//                showMissingAddressDialog()
//                dialog.cancel()
//            }
//            builder.setNegativeButton(R.string.skip) { dialog: DialogInterface, id: Int ->
//                dialog.cancel()
//                // continue with out address configuration
//                continueInit()
//            }
//            builder.show()
//        } else {
//            binder!!.settings.addAddress(mac)
//            try {
//                binder!!.saveDatabase()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
        continueInit()
        // }
    }

    // initial dialog to set the username
    private fun showMissingUsernameDialog() {
        val tw = TextView(this)
        tw.setText(R.string.name_prompt)
        //tw.setTextColor(Color.BLACK);
        tw.textSize = 20f
        tw.gravity = Gravity.CENTER_HORIZONTAL
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(tw)
        val et = EditText(this)
        et.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        et.isSingleLine = true
        layout.addView(et)
        layout.setPadding(40, 80, 40, 40)
        //layout.setGravity(Gravity.CENTER_HORIZONTAL);
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.hello)
        builder.setView(layout)
        builder.setNegativeButton(R.string.cancel) { dialogInterface: DialogInterface?, i: Int ->


            binder!!.shutdown()
            finish()
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        builder.setPositiveButton(R.string.next) { dialogInterface: DialogInterface?, i: Int -> }
        val dialog = builder.create()
        dialog.setOnShowListener { newDialog: DialogInterface ->
            val okButton = (newDialog as AlertDialog).getButton(
                AlertDialog.BUTTON_POSITIVE
            )
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                    // nothing to do
                }

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    // nothing to do
                }

                override fun afterTextChanged(editable: Editable) {
                    okButton.isClickable = editable.length > 0
                    okButton.alpha = if (editable.length > 0) 1.0f else 0.5f
                }
            })
            okButton.isClickable = false
            okButton.alpha = 0.5f
            if (et.requestFocus()) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        }
        dialog.show()

        // override handler (to be able to dismiss the dialog manually)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { v: View? ->
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY)
            val username = et.text.toString()
            if (Utils.isValidName(username)) {
                binder!!.settings.username = username
                try {
                    binder!!.saveDatabase()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // close dialog
                dialog.dismiss()
                //dialog.cancel(); // needed?
                continueInit()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { v: View? ->
            imm.toggleSoftInput(0, InputMethodManager.HIDE_IMPLICIT_ONLY)
            val username = generateRandomUserName()
            if (Utils.isValidName(username)) {
                binder!!.settings.username = username
                try {
                    binder!!.saveDatabase()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // close dialog
                dialog.dismiss()
                //dialog.cancel(); // needed?
                continueInit()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ask for database password
    private fun showDatabasePasswordDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_database_password)
        val passwordEditText = dialog.findViewById<EditText>(R.id.change_password_edit_textview)
        val exitButton = dialog.findViewById<Button>(R.id.change_password_cancel_button)
        val okButton = dialog.findViewById<Button>(R.id.change_password_ok_button)
        okButton.setOnClickListener { v: View? ->
            val password = passwordEditText.text.toString()
            binder!!.getService().databasePassword = password
            binder!!.loadDatabase()
            if (binder!!.getService().database == null) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            } else {
                // close dialog
                dialog.dismiss()
                continueInit()
            }
        }
        exitButton.setOnClickListener { v: View? ->
            // shutdown app
            dialog.dismiss()
            binder!!.shutdown()
            finish()
        }
        dialog.show()
    }

    private fun log(s: String) {
        Log.d(this, s)
    }

    companion object {
        private var sodium: Sodium? = null
    }
    fun generateRandomUserName() = //foreach loop?
        "User${UUID.randomUUID().toString().substring(0..7).replace(Pattern.quote("-"), "")}"

}