package d.d.meshenger

import android.app.Dialog
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import d.d.meshenger.MainService.MainBinder
import java.lang.Integer.parseInt

class SettingsActivity : BaseActivity(), ServiceConnection {
    private var binder: MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setTitle(R.string.menu_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
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

        bindService(Intent(this, MainService::class.java), this, 0)
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
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

        val settings = binder!!.getSettings()

        findViewById<TextView>(R.id.nameTv)
            .text = settings.username.ifEmpty { getString(R.string.none) }
        findViewById<View>(R.id.nameLayout)
            .setOnClickListener { showChangeNameDialog() }

        findViewById<TextView>(R.id.addressTv)
            .text = if (settings.addresses.isEmpty()) getString(R.string.none) else settings.addresses.joinToString()
        findViewById<View>(R.id.addressLayout)
            .setOnClickListener {
                startActivity(Intent(this@SettingsActivity, AddressActivity::class.java))
            }

        val password = binder!!.getService().databasePassword
        findViewById<TextView>(R.id.passwordTv)
            .text = if (password.isEmpty()) getString(R.string.none) else "*".repeat(password.length)
        findViewById<View>(R.id.passwordLayout)
            .setOnClickListener { showChangePasswordDialog() }

        findViewById<TextView>(R.id.publicKeyTv)
            .text = Utils.byteArrayToHexString(settings.publicKey)
        findViewById<View>(R.id.publicKeyLayout)
            .setOnClickListener { Toast.makeText(this@SettingsActivity, R.string.read_only, Toast.LENGTH_SHORT).show() }

        findViewById<SwitchMaterial>(R.id.blockUnknownSwitch).apply {
            isChecked = settings.blockUnknown
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.blockUnknown = isChecked
                binder!!.saveDatabase()
            }
        }

        setupSpinner(settings.nightMode,
            R.id.spinnerNightModes,
            R.array.nightModes,
            R.array.nightModesValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        settings.nightMode = it
                        binder!!.saveDatabase()
                        setDefaultNightMode(newValue)
                        applyNightMode()
                        startActivity(Intent(this@SettingsActivity, SettingsActivity::class.java))
                        finish()
                    }
                }
            })

        findViewById<TextView>(R.id.connectTimeoutTv)
            .text = "${settings.connectTimeout}"
        findViewById<View>(R.id.connectTimeoutLayout)
            .setOnClickListener { showChangeConnectTimeoutDialog() }

        findViewById<SwitchMaterial>(R.id.promptOutgoingCallsSwitch).apply {
            isChecked = settings.promptOutgoingCalls
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.promptOutgoingCalls = isChecked
                binder!!.saveDatabase()
            }
        }

        val basicRadioButton = findViewById<RadioButton>(R.id.basic_radio_button)
        val advancedRadioButton = findViewById<RadioButton>(R.id.advanced_radio_button)
        val expertRadioButton = findViewById<RadioButton>(R.id.expert_radio_button)

        applySettingsMode("basic")

        basicRadioButton.isChecked = true
        basicRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                applySettingsMode("basic")
            }
        }

        advancedRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                applySettingsMode("advanced")
            }
        }

        expertRadioButton.setOnCheckedChangeListener { _, b ->
            if (b) {
                applySettingsMode("expert")
            }
        }

        findViewById<SwitchMaterial>(R.id.useNeighborTableSwitch).apply {
            isChecked = settings.useNeighborTable
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.useNeighborTable = isChecked
                binder!!.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.videoHardwareAccelerationSwitch).apply {
            isChecked = settings.videoHardwareAcceleration
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.videoHardwareAcceleration = isChecked
                binder!!.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.noAudioProcessingSwitch).apply {
            isChecked = settings.noAudioProcessing
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.noAudioProcessing = isChecked
                binder!!.saveDatabase()
            }
        }
    }

    private fun showChangeNameDialog() {
        val settings = binder!!.getSettings()
        val username = settings.username
        val et = EditText(this)
        et.setText(username)
        et.setSelection(username.length)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                val newUsername = et.text.toString().trim { it <= ' ' }
                if (Utils.isValidName(newUsername)) {
                    settings.username = newUsername
                    binder!!.saveDatabase()
                    initViews()
                } else {
                    Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeConnectTimeoutDialog() {
        val settings = binder!!.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_connect_timeout)
        val connectTimeoutEditText = dialog.findViewById<TextView>(R.id.connectTimeoutEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        connectTimeoutEditText.text = "${settings.connectTimeout}"
        saveButton.setOnClickListener {
            var connectTimeout = -1
            val text = connectTimeoutEditText.text.toString()
            try {
                connectTimeout = parseInt(text)
            } catch (e: Exception) {
                // ignore
            }

            if (connectTimeout in 20..4000) {
                settings.connectTimeout = connectTimeout
                binder!!.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SettingsActivity, R.string.invalid_timeout, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showChangePasswordDialog() {
        val password = binder!!.getService().databasePassword

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_database_password)
        val passwordInput = dialog.findViewById<TextInputEditText>(R.id.change_password_edit_textview)
        val abortButton = dialog.findViewById<Button>(R.id.change_password_cancel_button)
        val changeButton = dialog.findViewById<Button>(R.id.change_password_ok_button)

        passwordInput.setText(password)
        changeButton.setOnClickListener {
            val newPassword = passwordInput.text.toString()
            binder!!.getService().databasePassword = newPassword
            binder!!.saveDatabase()
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            initViews()
            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        initViews()
    }

    private fun applySettingsMode(settingsMode: String) {
        val basicSettingsLayout = findViewById<View>(R.id.basicSettingsLayout)
        val advancedSettingsLayout = findViewById<View>(R.id.advancedSettingsLayout)
        val expertSettingsLayout = findViewById<View>(R.id.expertSettingsLayout)

        when (settingsMode) {
            "basic" -> {
                basicSettingsLayout.visibility = View.VISIBLE
                advancedSettingsLayout.visibility = View.INVISIBLE
                expertSettingsLayout.visibility = View.INVISIBLE
            }
            "advanced" -> {
                basicSettingsLayout.visibility = View.VISIBLE
                advancedSettingsLayout.visibility = View.VISIBLE
                expertSettingsLayout.visibility = View.INVISIBLE
            }
            "expert" -> {
                basicSettingsLayout.visibility = View.VISIBLE
                advancedSettingsLayout.visibility = View.VISIBLE
                expertSettingsLayout.visibility = View.VISIBLE
            }
            else -> Log.e(this, "Invalid settings mode: $settingsMode")
        }
    }

    private fun getIgnoreBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pMgr = this.getSystemService(POWER_SERVICE) as PowerManager
            return pMgr.isIgnoringBatteryOptimizations(this.packageName)
        }
        return false
    }

    private interface SpinnerItemSelected {
        fun call(newValue: String?)
    }

    private fun setupSpinner(
        currentValue: String,
        spinnerId: Int,
        arrayId: Int,
        arrayValuesId: Int,
        callback: SpinnerItemSelected,
    ) {
        val arrayValues = resources.getStringArray(arrayValuesId)
        val spinner = findViewById<Spinner>(spinnerId)
        val spinnerAdapter = ArrayAdapter.createFromResource(this, arrayId, R.layout.spinner_item_settings)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)

        spinner.adapter = spinnerAdapter
        spinner.setSelection(arrayValues.indexOf(currentValue))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (check++ > 0) {
                    callback.call(arrayValues[pos])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // ignore
            }
        }
    }
}