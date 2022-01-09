package org.rivchain.cuplink

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import org.rivchain.cuplink.MainService.MainBinder
import java.util.*

class SettingsActivity : CupLinkActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = resources.getString(R.string.menu_settings)
        bindService()
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binder != null) {
            unbindService(this)
        }
    }

    private fun bindService() {
        // ask MainService to get us the binder object
        val serviceIntent = Intent(this, MainService::class.java)
        bindService(serviceIntent, this, BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    private fun initViews() {
        if (binder == null) {
            return
        }
        findViewById<View>(R.id.changeNameLayout).setOnClickListener { view: View? -> showChangeNameDialog() }
        findViewById<View>(R.id.changeAddressLayout).setOnClickListener { view: View? ->
            val intent = Intent(this, AddressActivity::class.java)
            startActivity(intent)
        }
        findViewById<View>(R.id.changePasswordLayout).setOnClickListener { view: View? -> showChangePasswordDialog() }
        val username = binder!!.settings.getUsername()
        (findViewById<View>(R.id.nameTv) as TextView).text = if (username.length == 0) resources.getString(R.string.none) else username
        val addresses = binder!!.settings.getAddresses()
        (findViewById<View>(R.id.addressTv) as TextView).text = if (addresses.size == 0) resources.getString(R.string.none) else Utils.join(addresses)
        val password: String? = binder!!.getDatabasePassword()
        (findViewById<View>(R.id.passwordTv) as TextView).text = if (password!!.isEmpty()) resources.getString(R.string.none) else "********"
        val blockUnknown = binder!!.settings.blockUnknown
        val blockUnknownCB = findViewById<CheckBox>(R.id.checkBoxBlockUnknown)
        blockUnknownCB.isChecked = blockUnknown
        blockUnknownCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.setBlockUnknown(isChecked)
            binder!!.saveDatabase()
        }
        val nightMode = binder!!.settings.getNightMode()
        val nightModeCB = findViewById<CheckBox>(R.id.checkBoxNightMode)
        nightModeCB.isChecked = nightMode
        nightModeCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // apply value
            AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )

            // save value
            binder!!.settings.setNightMode(isChecked)
            binder!!.saveDatabase()

            // apply theme
            recreate()
        }
        val developmentMode = binder!!.settings.getDevelopmentMode()
        val developmentModeCB = findViewById<CheckBox>(R.id.checkBoxDevelopmentMode)
        developmentModeCB.isChecked = developmentMode
        developmentModeCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.setDevelopmentMode(isChecked)
            binder!!.saveDatabase()
        }
        locale
    }

    private val locale: Unit
        private get() {
            val config = resources.configuration
            val locale = config.locale
            val language = locale.displayLanguage
            if (binder!!.settings.getLanguage() != language) {
                binder!!.settings.setLanguage(language)
                binder!!.saveDatabase()
            }
            (findViewById<View>(R.id.localeTv) as TextView).text = language
            val locales = arrayOf(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN)
            findViewById<View>(R.id.changeLocaleLayout).setOnClickListener { v: View? ->
                val group = RadioGroup(this)
                val builder = AlertDialog.Builder(this)
                var i = 0
                while (i < locales.size) {
                    val l = locales[i]
                    val button = RadioButton(this)
                    button.id = i
                    button.text = l.displayLanguage
                    if (l.isO3Language == locale.isO3Language) {
                        button.isChecked = true
                    }
                    group.addView(button)
                    i += 1
                }
                builder.setView(group)
                val dialog = builder.show()
                group.setOnCheckedChangeListener { a: RadioGroup?, position: Int ->
                    log("changed locale to " + locales[position].language)
                    val config1 = Configuration()
                    config1.locale = locales[position]
                    resources.updateConfiguration(config1, resources.displayMetrics)
                    binder!!.settings.setLanguage(locale.displayLanguage)
                    binder!!.saveDatabase()
                    finish()
                    startActivity(Intent(applicationContext, this.javaClass))
                    dialog.dismiss()
                }
            }
        }

    private fun showChangeNameDialog() {
        val username = binder!!.settings.getUsername()
        val et = EditText(this)
        et.setText(username)
        et.setSelection(username.length)
        AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.settings_change_name))
                .setView(et)
                .setPositiveButton(R.string.ok) { dialogInterface, i ->
                    val new_username = et.text.toString().trim { it <= ' ' }
                    if (Utils.isValidName(new_username)) {
                        binder!!.settings.setUsername(new_username)
                        binder!!.saveDatabase()
                        initViews()
                    } else {
                        Toast.makeText(this, resources.getString(R.string.invalid_name), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(resources.getText(R.string.cancel), null)
                .show()
    }

    private fun showChangePasswordDialog() {
        val password: String? = binder!!.getDatabasePassword()
        val et = EditText(this)
        et.setText(password)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setSelection(password!!.length)
        AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.settings_change_password))
                .setView(et)
                .setPositiveButton(R.string.ok) { dialogInterface, i ->
                    val new_password = et.text.toString()
                    binder!!.setDatabasePassword(new_password)
                    binder!!.saveDatabase()
                    initViews()
                }
                .setNegativeButton(resources.getText(R.string.cancel), null)
                .show()
    }

    override fun onResume() {
        super.onResume()
        initViews()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this@SettingsActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
    }

    private fun log(s: String) {
        Log.d(SettingsActivity::class.java.simpleName, s)
    }
}