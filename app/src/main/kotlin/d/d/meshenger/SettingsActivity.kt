package d.d.meshenger

import android.app.Dialog
import android.content.*
import android.graphics.Color
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
        // nothing to do
    }

    private fun initViews() {
        val binder = binder ?: return
        val settings = binder.getSettings()

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

        val databasePassword = binder.getService().databasePassword
        findViewById<TextView>(R.id.databasePasswordTv)
            .text = if (databasePassword.isEmpty()) getString(R.string.none) else "*".repeat(databasePassword.length)
        findViewById<View>(R.id.databasePasswordLayout)
            .setOnClickListener { showDatabasePasswordDialog() }

        findViewById<TextView>(R.id.publicKeyTv)
            .text = Utils.byteArrayToHexString(settings.publicKey)
        findViewById<View>(R.id.publicKeyLayout)
            .setOnClickListener { Toast.makeText(this@SettingsActivity, R.string.read_only, Toast.LENGTH_SHORT).show() }

        findViewById<SwitchMaterial>(R.id.blockUnknownSwitch).apply {
            isChecked = settings.blockUnknown
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.blockUnknown = isChecked
                    binder.saveDatabase()
                }
            }
        }

        setupSpinner(settings.nightMode,
            R.id.spinnerNightModes,
            R.array.nightModeLabels,
            R.array.nightModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    val binder = binder
                    if (newValue != null && binder != null) {
                        settings.nightMode = newValue
                        binder.saveDatabase()
                        setDefaultNightMode(newValue)
                        applyNightMode()
                        startActivity(Intent(this@SettingsActivity, SettingsActivity::class.java))
                        finish()
                    }
                }
            })

        setupSpinner(settings.speakerphoneMode,
            R.id.spinnerSpeakerphoneModes,
            R.array.speakerphoneModeLabels,
            R.array.speakerphoneModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    val binder = binder
                    if (newValue != null && binder != null) {
                        settings.speakerphoneMode = newValue
                        binder.saveDatabase()
                    }
                }
            })

        findViewById<TextView>(R.id.connectRetriesTv)
            .text = "${settings.connectRetries}"
        findViewById<View>(R.id.connectRetriesLayout)
            .setOnClickListener { showChangeConnectRetriesDialog() }

        findViewById<TextView>(R.id.connectTimeoutTv)
            .text = "${settings.connectTimeout}"
        findViewById<View>(R.id.connectTimeoutLayout)
            .setOnClickListener { showChangeConnectTimeoutDialog() }

        findViewById<SwitchMaterial>(R.id.promptOutgoingCallsSwitch).apply {
            isChecked = settings.promptOutgoingCalls
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.promptOutgoingCalls = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.disableCallHistorySwitch).apply {
            isChecked = settings.disableCallHistory
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.disableCallHistory = isChecked
                    if (isChecked) {
                        binder.clearEvents()
                    }
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.startOnBootupSwitch).apply {
            isChecked = settings.startOnBootup
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.startOnBootup = isChecked
                    BootUpReceiver.setEnabled(this@SettingsActivity, isChecked) // apply setting
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.pushToTalkSwitch).apply {
            isChecked = settings.pushToTalk
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.pushToTalk = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.disableProximitySensorSwitch).apply {
            isChecked = settings.disableProximitySensor
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.disableProximitySensor = isChecked
                    binder.saveDatabase()
                }
            }
        }

        setupSpinner(settings.videoDegradationMode,
            R.id.spinnerVideoDegradationModes,
            R.array.videoDegradationModeLabels,
            R.array.videoDegradationModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.videoDegradationMode = newValue
                        applyVideoDegradationMode(newValue)
                    }
                }
            })

        setupSpinner(settings.cameraResolution,
            R.id.spinnerCameraResolution,
            R.array.cameraResolutionLabels,
            R.array.cameraResolutionValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.cameraResolution = newValue
                    }
                }
            })

        setupSpinner(settings.cameraFramerate,
            R.id.spinnerCameraFramerate,
            R.array.cameraFramerateLabels,
            R.array.cameraFramerateValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.cameraFramerate = newValue
                    }
                }
            })

        setupSpinner(settingsMode,
            R.id.spinnerSettingsModes,
            R.array.settingsModeLabels,
            R.array.settingsModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settingsMode = newValue
                        applySettingsMode(newValue)
                    }
                }
            })

        findViewById<SwitchMaterial>(R.id.useNeighborTableSwitch).apply {
            isChecked = settings.useNeighborTable
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.useNeighborTable = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.videoHardwareAccelerationSwitch).apply {
            isChecked = settings.videoHardwareAcceleration
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.videoHardwareAcceleration = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.disableAudioProcessingSwitch).apply {
            isChecked = settings.disableAudioProcessing
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.disableAudioProcessing = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.showUsernameAsLogoSwitch).apply {
            isChecked = settings.showUsernameAsLogo
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.showUsernameAsLogo = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.enableMicrophoneByDefaultSwitch).apply {
            isChecked = settings.enableMicrophoneByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.enableMicrophoneByDefault = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.enableCameraByDefaultSwitch).apply {
            isChecked = settings.enableCameraByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.enableCameraByDefault = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.selectFrontCameraByDefaultSwitch).apply {
            isChecked = settings.selectFrontCameraByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.selectFrontCameraByDefault = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.disableCpuOveruseDetectionSwitch).apply {
            isChecked = settings.disableCpuOveruseDetection
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.disableCpuOveruseDetection = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.autoAcceptCallsSwitch).apply {
            isChecked = settings.autoAcceptCalls
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.autoAcceptCalls = isChecked
                    binder.saveDatabase()
                }
            }
        }

        findViewById<SwitchMaterial>(R.id.automaticStatusUpdatesSwitch).apply {
            isChecked = settings.automaticStatusUpdates
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                val binder = binder
                if (binder != null) {
                    settings.automaticStatusUpdates = isChecked
                    binder.saveDatabase()
                }
            }
        }

        val menuPassword = settings.menuPassword
        findViewById<TextView>(R.id.menuPasswordTv)
            .text = if (menuPassword.isEmpty()) getString(R.string.none) else "*".repeat(menuPassword.length)
        findViewById<View>(R.id.menuPasswordLayout)
            .setOnClickListener { showMenuPasswordDialog() }

        applySettingsMode(settingsMode)
        applyVideoDegradationMode(settings.videoDegradationMode)

    }

    private fun showChangeNameDialog() {
        val binder = binder ?: return
        val settings = binder.getSettings()
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
                    binder.saveDatabase()
                    initViews()
                } else {
                    Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeConnectRetriesDialog() {
        val binder = binder ?: return
        val settings = binder.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_connect_retries)
        val connectRetriesEditText = dialog.findViewById<TextView>(R.id.connectRetriesEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        connectRetriesEditText.text = "${settings.connectRetries}"
        saveButton.setOnClickListener {
            val minValue = 0
            val maxValue = 4
            var connectRetries = -1
            val text = connectRetriesEditText.text.toString()
            try {
                connectRetries = parseInt(text)
            } catch (e: Exception) {
                // ignore
            }

            if (connectRetries in minValue..maxValue) {
                settings.connectRetries = connectRetries
                binder.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                val message = String.format(getString(R.string.invalid_number_value), minValue, maxValue)
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showChangeConnectTimeoutDialog() {
        val binder = binder ?: return
        val settings = binder.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_connect_timeout)
        val connectTimeoutEditText = dialog.findViewById<TextView>(R.id.connectTimeoutEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        connectTimeoutEditText.text = "${settings.connectTimeout}"
        saveButton.setOnClickListener {
            val minValue = 20
            val maxValue = 4000
            var connectTimeout = -1
            val text = connectTimeoutEditText.text.toString()
            try {
                connectTimeout = parseInt(text)
            } catch (e: Exception) {
                // ignore
            }

            if (connectTimeout in minValue..maxValue) {
                settings.connectTimeout = connectTimeout
                binder.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                val message = String.format(getString(R.string.invalid_number_value), minValue, maxValue)
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showDatabasePasswordDialog() {
        val binder = binder ?: return
        val databasePassword = binder.getService().databasePassword

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_database_password)
        val passwordInput = dialog.findViewById<TextInputEditText>(R.id.change_password_edit_textview)
        val abortButton = dialog.findViewById<Button>(R.id.change_password_cancel_button)
        val changeButton = dialog.findViewById<Button>(R.id.change_password_ok_button)

        passwordInput.setText(databasePassword)
        changeButton.setOnClickListener {
            val newPassword = passwordInput.text.toString()
            binder.getService().databasePassword = newPassword
            binder.saveDatabase()
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            initViews()
            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showMenuPasswordDialog() {
        val binder = binder ?: return
        val menuPassword = binder.getSettings().menuPassword

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_menu_password)
        val passwordInput = dialog.findViewById<TextInputEditText>(R.id.change_password_edit_textview)
        val abortButton = dialog.findViewById<Button>(R.id.change_password_cancel_button)
        val changeButton = dialog.findViewById<Button>(R.id.change_password_ok_button)

        passwordInput.setText(menuPassword)
        changeButton.setOnClickListener {
            val newPassword = passwordInput.text.toString()
            binder.getSettings().menuPassword = newPassword
            binder.saveDatabase()
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

    // grey out resolution/framerate spinners that are not considered by certain
    // degradation modes. We still allow those two values to be changed though.
    private fun applyVideoDegradationMode(degradation: String) {
        val videoDegradationModeText = findViewById<TextView>(R.id.videoDegradationModeText)
        val cameraResolutionText = findViewById<TextView>(R.id.cameraResolutionText)
        val cameraFramerateText = findViewById<TextView>(R.id.cameraFramerateText)
        val enabledColor = videoDegradationModeText.currentTextColor
        val disabledColor = Color.parseColor("#d3d3d3")

        when (degradation) {
            "balanced" -> {
                cameraResolutionText.setTextColor(disabledColor)
                cameraFramerateText.setTextColor(disabledColor)
            }
            "maintain_resolution" -> {
                cameraResolutionText.setTextColor(enabledColor)
                cameraFramerateText.setTextColor(disabledColor)
            }
            "maintain_framerate" -> {
                cameraResolutionText.setTextColor(disabledColor)
                cameraFramerateText.setTextColor(enabledColor)
            }
            "disabled" -> {
                cameraResolutionText.setTextColor(enabledColor)
                cameraFramerateText.setTextColor(enabledColor)
            }
            else -> {
                Log.w(this, "applyVideoDegradationMode() unhandled degradation=$degradation")
            }
        }
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

    companion object {
        // not stored in the database
        private var settingsMode = "basic"
    }
}
