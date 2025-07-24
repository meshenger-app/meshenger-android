/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.app.Dialog
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import d.d.meshenger.MainService.MainBinder
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import java.util.UUID

/*
 * Show splash screen, name setup dialog, database password dialog and
 * start background service before starting the MainActivity.
 */
class StartActivity : BaseActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    private var dialog : Dialog? = null
    private var startState = 0
    private var isStartOnBootup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(this, "onCreate() Meshenger version ${BuildConfig.VERSION_NAME}")
        Log.d(this, "Android SDK: ${Build.VERSION.SDK_INT}, "
                    + "Release: ${Build.VERSION.RELEASE}, "
                    + "Brand: ${Build.BRAND}, "
                    + "Device: ${Build.DEVICE}, "
                    + "Id: ${Build.ID}, "
                    + "Hardware: ${Build.HARDWARE}, "
                    + "Manufacturer: ${Build.MANUFACTURER}, "
                    + "Model: ${Build.MODEL}, "
                    + "Product: ${Build.PRODUCT}"
        )

        // set by BootUpReceiver
        isStartOnBootup = intent.getBooleanExtra(BootUpReceiver.IS_START_ON_BOOTUP, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val type = Typeface.createFromAsset(assets, "rounds_black.otf")
        findViewById<TextView>(R.id.splashText).typeface = type

        // start MainService and call back via onServiceConnected()
        MainService.start(this)

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    private fun continueInit() {
        startState += 1
        when (startState) {
            1 -> {
                Log.d(this, "init 1: load database")
                // open without password
                try {
                    binder!!.getService().loadDatabase()
                } catch (e: Database.WrongPasswordException) {
                    // ignore and continue with initialization,
                    // the password dialog comes on the next startState
                } catch (e: Exception) {
                    Log.e(this, "${e.message}")
                    Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                continueInit()
            }
            2 -> {
                Log.d(this, "init 2: check database")
                if (!binder!!.isDatabaseLoaded()) {
                    // database is probably encrypted
                    showDatabasePasswordDialog()
                } else {
                    continueInit()
                }
            }
            3 -> {
                Log.d(this, "init 3: check username")
                if (binder!!.getSettings().username.isEmpty()) {
                    // set username
                    showMissingUsernameDialog()
                } else {
                    continueInit()
                }
            }
            4 -> {
                Log.d(this, "init 4: check key pair")
                if (binder!!.getSettings().publicKey.isEmpty()) {
                    // generate key pair
                    initKeyPair()
                }
                continueInit()
            }
            5 -> {
                Log.d(this, "init 5: check addresses")
                if (binder!!.getService().firstStart) {
                    showMissingAddressDialog()
                } else {
                    continueInit()
                }
            }
            6 -> {
                Log.d(this, "init 6: start MainActivity")
                val settings = binder!!.getSettings()

                // set in case we just updated the app
                BootUpReceiver.setEnabled(this, settings.startOnBootup)

                // set theme
                setDefaultThemeName(settings.themeName)

                // set night mode
                setDefaultNightMode(settings.nightMode)

                if (!isStartOnBootup) {
                    val firstStart = binder!!.getService().firstStart
                    val permissionsToAsk = AskForMissingPermissionsActivity.permissionsToAsk(applicationContext, firstStart)
                    if (settings.skipStartupPermissionCheck || !permissionsToAsk) {
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        // ask for permissions first
                        startActivity(Intent(this, AskForMissingPermissionsActivity::class.java))
                    }
                }

                finish()
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        Log.d(this, "onServiceConnected")
        binder = iBinder as MainBinder

        if (startState == 0) {
            if (binder!!.getService().firstStart) {
                // show delayed splash page
                Handler(Looper.getMainLooper()).postDelayed({
                    continueInit()
                }, 1000)
            } else {
                // show contact list as fast as possible
                continueInit()
            }
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    override fun onDestroy() {
        dialog?.dismiss()
        unbindService(this)
        super.onDestroy()
    }

    private fun initKeyPair() {
        // create secret/public key pair
        val publicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val secretKey = ByteArray(Sodium.crypto_sign_secretkeybytes())
        Sodium.crypto_sign_keypair(publicKey, secretKey)
        val settings = binder!!.getSettings()
        settings.publicKey = publicKey
        settings.secretKey = secretKey
        binder!!.saveDatabase()
    }

    private fun getDefaultAddress(): AddressEntry? {
        val addresses = AddressUtils.collectAddresses()

        // any EUI-64 address
        val addressEUI64 = addresses.firstOrNull { it.device.startsWith("wlan") && it.address.contains(":fffe:") }
        if (addressEUI64 != null) {
            return addressEUI64
        }

        // any IPv6 address
        val addressLinkLocal = addresses.firstOrNull { it.device.startsWith("wlan") && it.address.contains(":") }
        if (addressLinkLocal != null) {
            return addressLinkLocal
        }

        return null
    }

    private fun showMissingAddressDialog() {
        val defaultAddress = getDefaultAddress()
        if (defaultAddress == null) {
            val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            builder.setTitle(getString(R.string.setup_address))
            builder.setMessage(getString(R.string.setup_no_address_found))
            builder.setPositiveButton(R.string.button_ok) { dialog: DialogInterface, _: Int ->
                showMissingAddressDialog()
                dialog.cancel()
            }
            builder.setNegativeButton(R.string.button_skip) { dialog: DialogInterface, _: Int ->
                dialog.cancel()
                // continue with out address configuration
                continueInit()
            }
            val adialog = builder.create()
            adialog.setCancelable(false)
            adialog.setCanceledOnTouchOutside(false)

            this.dialog?.dismiss()
            this.dialog = adialog

            adialog.show()
        } else {
            binder!!.getSettings().addresses = mutableListOf(defaultAddress.address)
            binder!!.saveDatabase()
            continueInit()
        }
    }

    // initial dialog to set the username
    private fun showMissingUsernameDialog() {
        val tw = TextView(this)
        tw.setText(R.string.startup_prompt_name)
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

        val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder.setTitle(R.string.startup_hello)
        builder.setView(layout)
        builder.setNegativeButton(R.string.button_skip) { dialog: DialogInterface?, _: Int ->
            val username = generateRandomUserName()
            if (Utils.isValidName(username)) {
                binder!!.getSettings().username = username
                binder!!.saveDatabase()
                // close dialog
                dialog?.dismiss()
                continueInit()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }
        }
        builder.setPositiveButton(R.string.button_next) { dialog: DialogInterface?, _: Int ->
            val username = et.text.toString().trim { it <= ' ' }
            if (Utils.isValidName(username)) {
                binder!!.getSettings().username = username
                binder!!.saveDatabase()
                // close dialog
                dialog?.dismiss()
                continueInit()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }
        }

        val adialog = builder.create()
        adialog.setCancelable(false)
        adialog.setCanceledOnTouchOutside(false)

        adialog.setOnShowListener { dialog: DialogInterface ->
            val okButton = (dialog as AlertDialog).getButton(
                AlertDialog.BUTTON_POSITIVE
            )
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int,
                ) {
                    // nothing to do
                }

                override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                    // nothing to do
                }

                override fun afterTextChanged(et: Editable) {
                    val username = et.toString().trim { it <= ' ' }
                    val ok = Utils.isValidName(username)
                    okButton.isClickable = ok
                    okButton.alpha = if (ok) 1.0f else 0.5f
                }
            })
            okButton.isClickable = false
            okButton.alpha = 0.5f
        }

        this.dialog?.dismiss()
        this.dialog = adialog

        adialog.show()
    }

    // ask for database password
    private fun showDatabasePasswordDialog() {
        val ddialog = Dialog(this)
        ddialog.setContentView(R.layout.dialog_enter_database_password)
        ddialog.setCancelable(false)
        ddialog.setCanceledOnTouchOutside(false)

        val passwordEditText = ddialog.findViewById<EditText>(R.id.change_password_edit_textview)
        val exitButton = ddialog.findViewById<Button>(R.id.change_password_cancel_button)
        val okButton = ddialog.findViewById<Button>(R.id.change_password_ok_button)
        okButton.setOnClickListener {
            val password = passwordEditText.text.toString()
            binder!!.getService().databasePassword = password
            try {
                binder!!.getService().loadDatabase()
            } catch (e: Database.WrongPasswordException) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }

            if (binder!!.isDatabaseLoaded()) {
                // close dialog
                ddialog.dismiss()
                continueInit()
            }
        }
        exitButton.setOnClickListener {
            // shutdown app
            ddialog.dismiss()
            binder!!.shutdown()
            finish()
        }

        this.dialog?.dismiss()
        this.dialog = ddialog

        ddialog.show()
    }

    companion object {
        // load libsodium for JNI access
        private var sodium = NaCl.sodium()
    }

    private fun generateRandomUserName(): String {
        val user = getString(R.string.startup_name_prefix)
        val id = UUID.randomUUID().toString().substring(0..6)
        return "$user-$id"
    }
}