/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

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
        setTitle(R.string.title_settings)

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
        val settings = Database.getSettings()

        findViewById<TextView>(R.id.nameTv)
            .text = settings.username.ifEmpty { getString(R.string.no_value) }
        findViewById<View>(R.id.nameLayout)
            .setOnClickListener { showChangeUsernameDialog() }

        findViewById<TextView>(R.id.addressTv)
            .text = if (settings.addresses.isEmpty()) getString(R.string.no_value) else settings.addresses.joinToString()
        findViewById<View>(R.id.addressLayout)
            .setOnClickListener {
                startActivity(Intent(this@SettingsActivity, AddressManagementActivity::class.java))
            }

        val databasePassword = Database.databasePassword
        findViewById<TextView>(R.id.databasePasswordTv)
            .text = if (databasePassword.isEmpty()) getString(R.string.no_value) else "*".repeat(databasePassword.length)
        findViewById<View>(R.id.databasePasswordLayout)
            .setOnClickListener { showDatabasePasswordDialog() }

        findViewById<TextView>(R.id.publicKeyTv)
            .text = Utils.byteArrayToHexString(settings.publicKey)
        findViewById<View>(R.id.publicKeyLayout)
            .setOnClickListener { Toast.makeText(this@SettingsActivity, R.string.setting_read_only, Toast.LENGTH_SHORT).show() }

        findViewById<SwitchMaterial>(R.id.blockUnknownSwitch).apply {
            isChecked = settings.blockUnknown
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.blockUnknown = isChecked
                Database.saveDatabase()
            }
        }

        setupSpinner(settings.nightMode,
            R.id.spinnerNightModes,
            R.array.nightModeLabels,
            R.array.nightModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.nightMode = newValue
                        Database.saveDatabase()
                        setDefaultNightMode(newValue)

                        // reload activity
                        val intent = Intent(this@SettingsActivity, SettingsActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        finish()
                        overridePendingTransition(0, 0)
                        startActivity(intent)
                    }
                }
            })

        setupSpinner(settings.themeName,
            R.id.themeName,
            R.array.themeNameLabels,
            R.array.themeNameValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.themeName = newValue
                        Database.saveDatabase()
                        setDefaultThemeName(newValue)

                        // reload activity
                        val intent = Intent(this@SettingsActivity, SettingsActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        finish()
                        overridePendingTransition(0, 0)
                        startActivity(intent)
                    }
                }
            })

        setupSpinner(settings.speakerphoneMode,
            R.id.spinnerSpeakerphoneModes,
            R.array.speakerphoneModeLabels,
            R.array.speakerphoneModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.speakerphoneMode = newValue
                        Database.saveDatabase()
                    }
                }
            })

        findViewById<TextView>(R.id.connectRetriesTv)
            .text = settings.connectRetries.toString()
        findViewById<View>(R.id.connectRetriesLayout)
            .setOnClickListener { showChangeConnectRetriesDialog() }

        findViewById<TextView>(R.id.connectTimeoutTv)
            .text = settings.connectTimeout.toString()
        findViewById<View>(R.id.connectTimeoutLayout)
            .setOnClickListener { showChangeConnectTimeoutDialog() }

        findViewById<TextView>(R.id.serverPortTv)
            .text = settings.serverPort.toString()
        findViewById<View>(R.id.serverPortLayout)
            .setOnClickListener { showChangeServerPortDialog() }

        findViewById<SwitchMaterial>(R.id.promptOutgoingCallsSwitch).apply {
            isChecked = settings.promptOutgoingCalls
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.promptOutgoingCalls = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.disableCallHistorySwitch).apply {
            isChecked = settings.disableCallHistory
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.disableCallHistory = isChecked
                if (isChecked) {
                    binder.clearEvents()
                }
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.startOnBootupSwitch).apply {
            isChecked = settings.startOnBootup
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.startOnBootup = isChecked
                BootUpReceiver.setEnabled(this@SettingsActivity, isChecked) // apply setting
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.pushToTalkSwitch).apply {
            isChecked = settings.pushToTalk
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.pushToTalk = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.disableProximitySensorSwitch).apply {
            isChecked = settings.disableProximitySensor
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.disableProximitySensor = isChecked
                Database.saveDatabase()
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

        setupSpinner(settings.settingsMode,
            R.id.spinnerSettingsModes,
            R.array.settingsModeLabels,
            R.array.settingsModeValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.settingsMode = newValue
                        Database.saveDatabase()
                        applySettingsMode(newValue)
                    }
                }
            })

        findViewById<SwitchMaterial>(R.id.guessEUI64AddressSwitch).apply {
            isChecked = settings.guessEUI64Address
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.guessEUI64Address = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.useNeighborTableSwitch).apply {
            isChecked = settings.useNeighborTable
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.useNeighborTable = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.videoHardwareAccelerationSwitch).apply {
            isChecked = settings.videoHardwareAcceleration
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.videoHardwareAcceleration = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.disableAudioProcessingSwitch).apply {
            isChecked = settings.disableAudioProcessing
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.disableAudioProcessing = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.showUsernameAsLogoSwitch).apply {
            isChecked = settings.showUsernameAsLogo
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.showUsernameAsLogo = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.enableMicrophoneByDefaultSwitch).apply {
            isChecked = settings.enableMicrophoneByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.enableMicrophoneByDefault = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.enableCameraByDefaultSwitch).apply {
            isChecked = settings.enableCameraByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.enableCameraByDefault = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.selectFrontCameraByDefaultSwitch).apply {
            isChecked = settings.selectFrontCameraByDefault
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.selectFrontCameraByDefault = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.disableCpuOveruseDetectionSwitch).apply {
            isChecked = settings.disableCpuOveruseDetection
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.disableCpuOveruseDetection = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.autoAcceptCallsSwitch).apply {
            isChecked = settings.autoAcceptCalls
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.autoAcceptCalls = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.automaticStatusUpdatesSwitch).apply {
            isChecked = settings.automaticStatusUpdates
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.automaticStatusUpdates = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.skipStartupPermissionCheckSwitch).apply {
            isChecked = settings.skipStartupPermissionCheck
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.skipStartupPermissionCheck = isChecked
                Database.saveDatabase()
            }
        }

        findViewById<SwitchMaterial>(R.id.hideMenusSwitch).apply {
            isChecked = settings.hideMenus
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.hideMenus = isChecked
                if (isChecked) {
                    binder.clearEvents()
                }
                Database.saveDatabase()
            }
        }

        setupSpinner(settings.audioBitrateMax,
            R.id.spinnerAudioBitrateMax,
            R.array.audioBitrateMaxLabels,
            R.array.audioBitrateMaxValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.audioBitrateMax = newValue
                        Database.saveDatabase()
                    }
                }
            })

        setupSpinner(settings.videoBitrateMax,
            R.id.spinnerVideoBitrateMax,
            R.array.videoBitrateMaxLabels,
            R.array.videoBitrateMaxValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    if (newValue != null) {
                        settings.videoBitrateMax = newValue
                        Database.saveDatabase()
                    }
                }
            })

        val menuPassword = settings.menuPassword
        findViewById<TextView>(R.id.menuPasswordTv)
            .text = if (menuPassword.isEmpty()) getString(R.string.no_value) else "*".repeat(menuPassword.length)
        findViewById<View>(R.id.menuPasswordLayout)
            .setOnClickListener { showMenuPasswordDialog() }

        applySettingsMode(settings.settingsMode)
        applyVideoDegradationMode(settings.videoDegradationMode)

    }

    private fun showChangeUsernameDialog() {
        Log.d(this, "showChangeUsernameDialog()")

        val settings = Database.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_name)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        nameEditText.setText(settings.username, TextView.BufferType.EDITABLE)

        okButton.setOnClickListener {
            val newUsername = nameEditText.text.toString().trim { it <= ' ' }
            if (Utils.isValidName(newUsername)) {
                settings.username = newUsername
                Database.saveDatabase()
                initViews()
            } else {
                Toast.makeText(this, R.string.invalid_name, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showChangeConnectRetriesDialog() {
        val settings = Database.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_connect_retries)
        val connectRetriesEditText = dialog.findViewById<TextView>(R.id.ConnectRetriesEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        connectRetriesEditText.text = "${settings.connectRetries}"
        okButton.setOnClickListener {
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
                Database.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                val message = String.format(getString(R.string.invalid_number), minValue, maxValue)
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }
        cancelButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showChangeConnectTimeoutDialog() {
        val settings = Database.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_connect_timeout)
        val connectTimeoutEditText = dialog.findViewById<TextView>(R.id.ConnectTimeoutEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        connectTimeoutEditText.text = "${settings.connectTimeout}"
        okButton.setOnClickListener {
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
                Database.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                val message = String.format(getString(R.string.invalid_number), minValue, maxValue)
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }
        cancelButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showChangeServerPortDialog() {
        val settings = Database.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_server_port)

        val serverPortEditText = dialog.findViewById<TextView>(R.id.ServerPortEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        serverPortEditText.text = "${settings.serverPort}"

        okButton.setOnClickListener {
            val minValue = 1
            val maxValue = 65535
            var serverPort = -1
            val text = serverPortEditText.text.toString()
            try {
                serverPort = parseInt(text)
            } catch (e: Exception) {
                // ignore parse error
            }

            if (serverPort in minValue..maxValue) {
                settings.serverPort = serverPort
                Database.saveDatabase()
                initViews()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                val message = String.format(getString(R.string.invalid_number), minValue, maxValue)
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_SHORT).show()
            }

            dialog.cancel()
        }

        cancelButton.setOnClickListener {
            dialog.cancel()
        }

        dialog.show()
    }


    private fun showDatabasePasswordDialog() {
        val databasePassword = Database.databasePassword

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_database_password)
        val passwordEditText = dialog.findViewById<TextInputEditText>(R.id.DatabasePasswordEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        passwordEditText.setText(databasePassword)
        okButton.setOnClickListener {
            val newPassword = passwordEditText.text.toString()
            Database.databasePassword = newPassword
            Database.saveDatabase()
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            initViews()
            dialog.cancel()
        }
        cancelButton.setOnClickListener { dialog.cancel() }
        dialog.show()
    }

    private fun showMenuPasswordDialog() {
        val menuPassword = Database.getSettings().menuPassword

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_change_menu_password)
        val passwordEditText = dialog.findViewById<TextInputEditText>(R.id.MenuPasswordEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        passwordEditText.setText(menuPassword)
        okButton.setOnClickListener {
            val newPassword = passwordEditText.text.toString()
            Database.getSettings().menuPassword = newPassword
            Database.saveDatabase()
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            initViews()
            dialog.cancel()
        }
        cancelButton.setOnClickListener { dialog.cancel() }
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
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos >= arrayValues.size) {
                    Toast.makeText(this@SettingsActivity,
                        "pos out of bounds: $arrayValues", Toast.LENGTH_SHORT).show()
                    return
                }
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
