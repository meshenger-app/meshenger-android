package d.d.meshenger

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import d.d.meshenger.Log.e
import d.d.meshenger.Utils.isValidContactName
import d.d.meshenger.Utils.join
import d.d.meshenger.Utils.split
import kotlin.collections.ArrayList


class SettingsActivity: MeshengerActivity() {

    companion object{
        private const val TAG = "SettingsActivity"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = resources.getString(R.string.menu_settings)
        initViews()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun getIgnoreBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pMgr = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pMgr.isIgnoringBatteryOptimizations(packageName)
        }
        return false
    }

    private fun initViews() {
        val settings = MainService.instance?.getSettings()
        settings?.let {
            findViewById<View>(R.id.nameLayout).setOnClickListener { showChangeNameDialog() }
            findViewById<View>(R.id.addressLayout).setOnClickListener {
                val intent = Intent(this, AddressActivity::class.java)
                startActivity(intent)
            }
            findViewById<View>(R.id.passwordLayout).setOnClickListener { showChangePasswordDialog() }
            findViewById<View>(R.id.iceServersLayout).setOnClickListener { showChangeIceServersDialog() }
            val username = it.username
            (findViewById<View>(R.id.nameTv) as TextView).text =
                if (username.isEmpty()) resources.getString(R.string.none) else username
            val addresses = it.addresses
            (findViewById<View>(R.id.addressTv) as TextView).text =
                if (addresses.isEmpty()) resources.getString(R.string.none) else join(addresses)
            val password = MainService.instance?.databasePassword
            (findViewById<View>(R.id.passwordTv) as TextView).text =
                if (password?.isEmpty() == true) resources.getString(R.string.none) else "********"
            val iceServers = it.iceServers
            (findViewById<View>(R.id.iceServersTv) as TextView).text =
                if (iceServers.isEmpty()) resources.getString(R.string.none) else join(iceServers)
            val blockUnknown = it.blockUnknown
            val blockUnknownCB = findViewById<CheckBox>(R.id.checkBoxBlockUnknown)
            blockUnknownCB.isChecked = blockUnknown?: false
            blockUnknownCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.blockUnknown = isChecked
                MainService.instance?.saveDatabase()
            }
            val nightMode = it.nightMode
            val nightModeCB = findViewById<CheckBox>(R.id.checkBoxNightMode)
            nightModeCB.isChecked = nightMode
            nightModeCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // apply value
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )

                // save value
                it.nightMode = isChecked
                MainService.instance?.saveDatabase()

                // apply theme
                recreate()
            }
            val recordAudio = it.recordAudio
            val recordAudioCB = findViewById<CheckBox>(R.id.checkBoxSendAudio)
            recordAudioCB.isChecked = recordAudio
            recordAudioCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.recordAudio = isChecked
                MainService.instance?.saveDatabase()
            }
            val playAudio = it.playAudio
            val playAudioCB = findViewById<CheckBox>(R.id.checkBoxPlayAudio)
            playAudioCB.isChecked = playAudio
            playAudioCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.playAudio = isChecked
                MainService.instance?.saveDatabase()
            }
            val recordVideo = it.recordVideo
            val recordVideoCB = findViewById<CheckBox>(R.id.checkBoxRecordVideo)
            recordVideoCB.isChecked = recordVideo
            recordVideoCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.recordVideo = isChecked
                MainService.instance?.saveDatabase()
            }
            val playVideo = it.playVideo
            val playVideoCB = findViewById<CheckBox>(R.id.checkBoxPlayVideo)
            playVideoCB.isChecked = playVideo
            playVideoCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.playVideo = isChecked
                MainService.instance?.saveDatabase()
            }
            val autoAcceptCall = it.autoAcceptCall
            val autoAcceptCallCB = findViewById<CheckBox>(R.id.checkBoxAutoAcceptCall)
            autoAcceptCallCB.isChecked = autoAcceptCall
            autoAcceptCallCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.autoAcceptCall = isChecked
                MainService.instance?.saveDatabase()
            }
            val autoConnectCall = it.autoConnectCall
            val autoConnectCallCB = findViewById<CheckBox>(R.id.checkBoxAutoConnectCall)
            autoConnectCallCB.isChecked = autoConnectCall
            autoConnectCallCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                // save value
                it.autoConnectCall = isChecked
                MainService.instance?.saveDatabase()
            }
            val ignoreBatteryOptimizations = getIgnoreBatteryOptimizations()
            val ignoreBatteryOptimizationsCB =
                findViewById<CheckBox>(R.id.checkBoxIgnoreBatteryOptimizations)
            ignoreBatteryOptimizationsCB.isChecked = ignoreBatteryOptimizations
            ignoreBatteryOptimizationsCB.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean ->
                // Only required for Android 6 or later
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent =
                        Intent(Settings.
                        ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) //TODO: Dangerous Operation
                    intent.data = Uri.parse("package: ${this.packageName}")
                    this.startActivity(intent)
                }
            }
            setupSpinner(it.settingsMode,
                R.id.spinnerSettingsMode,
                R.array.settingsMode,
                R.array.settingsModeValues,
                object : SpinnerItemSelected {
                    override fun call(newValue: String?) {
                        it.settingsMode = newValue!!
                        MainService.instance?.saveDatabase()
                        applySettingsMode(newValue)
                    }
                })
            setupSpinner(it.videoCodec,
                R.id.spinnerVideoCodecs,
                R.array.videoCodecs,
                R.array.videoCodecs,
                object : SpinnerItemSelected {
                    override fun call(newValue: String?) {
                        it.videoCodec = newValue!!
                        MainService.instance?.saveDatabase()
                    }
                })
            setupSpinner(it.audioCodec,
                R.id.spinnerAudioCodecs,
                R.array.audioCodecs,
                R.array.audioCodecs,
                object : SpinnerItemSelected {
                    override fun call(newValue: String?) {
                        it.audioCodec = newValue!!
                        MainService.instance?.saveDatabase()
                    }
                })
            setupSpinner(it.videoResolution,
                R.id.spinnerVideoResolutions,
                R.array.videoResolutions,
                R.array.videoResolutionsValues,
                object : SpinnerItemSelected {
                    override fun call(newValue: String?) {
                        it.audioCodec = newValue!!
                        MainService.instance?.saveDatabase()
                    }
                })
            setupSpinner(it.speakerphone,
                R.id.spinnerSpeakerphone,
                R.array.speakerphone,
                R.array.speakerphoneValues,
                object : SpinnerItemSelected {
                    override fun call(newValue: String?) {
                        it.speakerphone = newValue!!
                        MainService.instance?.saveDatabase()
                    }
                })
            applySettingsMode(it.settingsMode)
        }

    }

    private interface SpinnerItemSelected {
        fun call(newValue: String?)
    }

    // allow for a customized spinner
    private fun setupSpinner(
        settingsMode: String,
        spinnerId: Int,
        entriesId: Int,
        entryValuesId: Int,
        callback: SpinnerItemSelected
    ) {
        val spinner = findViewById<Spinner>(spinnerId)
        val spinnerAdapter: ArrayAdapter<CharSequence?> =
            ArrayAdapter.createFromResource(this, entriesId, R.layout.spinner_item_settings)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(
            (spinner.adapter as ArrayAdapter<String>).getPosition( //TODO(IODevBlue): Fix ArrayAdapter Lint
                settingsMode
            )
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (check++ > 0) {
                    val selectedValues = resources.obtainTypedArray(entryValuesId)
                    callback.call(selectedValues.getString(pos))
                    selectedValues.recycle() //TODO(IODevBlue): Added Recycle method
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // ignore
            }
        }
    }

    private fun applySettingsMode(settingsMode: String) {
        when (settingsMode) {
            "basic" -> {
                findViewById<View>(R.id.basicSettingsLayout).visibility = View.VISIBLE
                findViewById<View>(R.id.advancedSettingsLayout).visibility = View.INVISIBLE
                findViewById<View>(R.id.expertSettingsLayout).visibility = View.INVISIBLE
            }
            "advanced" -> {
                findViewById<View>(R.id.basicSettingsLayout).visibility = View.VISIBLE
                findViewById<View>(R.id.advancedSettingsLayout).visibility = View.VISIBLE
                findViewById<View>(R.id.expertSettingsLayout).visibility = View.INVISIBLE
            }
            "expert" -> {
                findViewById<View>(R.id.basicSettingsLayout).visibility = View.VISIBLE
                findViewById<View>(R.id.advancedSettingsLayout).visibility = View.VISIBLE
                findViewById<View>(R.id.expertSettingsLayout).visibility = View.VISIBLE
            }
            else -> e(TAG, "Invalid settings mode: $settingsMode")
        }
    }

    private fun showChangeNameDialog() {
        val settings = MainService.instance?.getSettings()
        val username = settings?.username
        val et = EditText(this)
        et.setText(username)
        et.setSelection(username?.length?:0)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newUserName = et.text.toString().trim { it <= ' ' }
                if (isValidContactName(newUserName)) {
                    settings?.username = newUserName
                    MainService.instance?.saveDatabase()
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
        val password = MainService.instance?.databasePassword
        val et = EditText(this)
        et.setText(password)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setSelection(password?.length?:0)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newPassword = et.text.toString()
                MainService.instance?.databasePassword = newPassword
                MainService.instance?.saveDatabase()
                initViews()
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeIceServersDialog() {
        val settings = MainService.instance?.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_set_ice_server)
        val iceServersTextView: TextView = dialog.findViewById(R.id.iceServersEditText)
        val saveButton: Button = dialog.findViewById(R.id.SaveButton)
        val abortButton: Button = dialog.findViewById(R.id.AbortButton)
        iceServersTextView.text = join(settings?.iceServers)
        saveButton.setOnClickListener {
            val iceServers =
                split(iceServersTextView.text.toString()) as ArrayList<String>
            settings?.iceServers = iceServers

            // done
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }
        abortButton.setOnClickListener { dialog.cancel() }
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
}