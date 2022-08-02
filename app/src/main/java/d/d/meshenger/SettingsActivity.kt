package d.d.meshenger

import android.app.Dialog
import d.d.meshenger.MeshengerActivity
import android.content.ServiceConnection
import d.d.meshenger.MainService.MainBinder
import android.os.Bundle
import d.d.meshenger.R
import android.content.Intent
import d.d.meshenger.MainService
import android.content.ComponentName
import android.os.IBinder
import d.d.meshenger.AddressActivity
import android.content.DialogInterface
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import d.d.meshenger.MainActivity
import d.d.meshenger.SettingsActivity

class SettingsActivity : MeshengerActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        toolbar.apply {
            setNavigationOnClickListener {
                finish()
            }
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
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
        findViewById<View>(R.id.nameLayout).setOnClickListener { view: View? -> showChangeNameDialog() }
        findViewById<View>(R.id.addressLayout).setOnClickListener { view: View? ->
            val intent = Intent(this, AddressActivity::class.java)
            startActivity(intent)
        }
        findViewById<View>(R.id.passwordLayout).setOnClickListener { view: View? -> showChangePasswordDialog() }
        findViewById<View>(R.id.iceServersLayout).setOnClickListener { view: View? -> showChangeIceServersDialog() }
        val username = binder!!.settings.username
        (findViewById<View>(R.id.nameTv) as TextView).text =
            if (username.length == 0) resources.getString(R.string.none) else username
        val addresses = binder!!.settings.addresses
        (findViewById<View>(R.id.addressTv) as TextView).text =
            if (addresses.size == 0) resources.getString(R.string.none) else Utils.join(addresses)
        val password = binder!!.getService().databasePassword
        (findViewById<View>(R.id.passwordTv) as TextView).text =
            if (password.isEmpty()) resources.getString(R.string.none) else "********"
        val iceServers = binder!!.settings.iceServers
        (findViewById<View>(R.id.iceServersTv) as TextView).text =
            if (iceServers.isEmpty()) resources.getString(R.string.none) else Utils.join(iceServers)
        val blockUnknown = binder!!.settings.blockUnknown
        val blockUnknownCB = findViewById<SwitchMaterial>(R.id.switchBlockUnknown)
        blockUnknownCB.apply{
            isChecked = blockUnknown
            setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
                binder!!.settings.blockUnknown = isChecked
                binder!!.saveDatabase()
            }
        }
        val nightMode = binder!!.settings.nightMode
        val nightModeCB = findViewById<SwitchMaterial>(R.id.switchButtonNightMode)
        nightModeCB.apply {
            isChecked = nightMode!!
            setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->

                // apply value
                if (isChecked) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }

                // save value
                binder!!.settings.nightMode = isChecked
                binder!!.saveDatabase()

                // apply theme
                recreate()
            }
        }
    }

    private fun showChangeNameDialog() {
        val username = binder!!.settings.username
        val et = EditText(this)
        et.setText(username)
        et.setSelection(username.length)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val new_username = et.text.toString().trim { it <= ' ' }
                if (Utils.isValidName(new_username)) {
                    binder!!.settings.username = new_username
                    binder!!.saveDatabase()
                    initViews()
                } else {
                    Toast.makeText(
                        this,
                        resources.getString(R.string.invalid_name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val password = binder!!.getService().databasePassword
        val et = EditText(this)
        et.setText(password)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setSelection(password.length)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val new_password = et.text.toString()
                binder!!.getService().databasePassword = new_password
                binder!!.saveDatabase()
                initViews()
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeIceServersDialog() {
        val settings = binder!!.settings
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_set_ice_server)
        val iceServersTextView = dialog.findViewById<TextView>(R.id.iceServersEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        iceServersTextView.text = Utils.join(settings.iceServers)
        saveButton.setOnClickListener { v: View? ->
            var list = ArrayList<String>()
            Utils.split(iceServersTextView.text.toString()).let {
                list.addAll(it)
            }
            val iceServers = list
            settings.iceServers = iceServers

            // done
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }
        abortButton.setOnClickListener { v: View? -> dialog.cancel() }
        dialog.show()
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