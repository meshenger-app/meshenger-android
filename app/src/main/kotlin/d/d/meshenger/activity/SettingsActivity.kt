package d.d.meshenger.activity

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import d.d.meshenger.R
import d.d.meshenger.utils.Log.e
import d.d.meshenger.utils.Utils.isValidContactName
import d.d.meshenger.utils.Utils.join
import d.d.meshenger.utils.Utils.split
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.service.MainService

class SettingsActivity: MeshengerActivity() {

    companion object {
        private const val TAG = "SettingsActivity"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = resources.getString(R.string.menu_settings)
        initViews()


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

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getIgnoreBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pMgr = this.getSystemService(POWER_SERVICE) as PowerManager
            return pMgr.isIgnoringBatteryOptimizations(this.packageName)
        }
        return false
    }

    private fun initViews() {
        val settings = MainService.instance!!.getSettings()!!
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        toolbar.apply {
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_ios_new_24)
            setNavigationOnClickListener {
                finish()
            }
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        findViewById<View>(R.id.nameLayout).setOnClickListener { view: View? -> showChangeNameDialog() }
        findViewById<View>(R.id.addressLayout).setOnClickListener { view: View? ->
            val intent = Intent(this, AddressActivity::class.java)
            startActivity(intent)
        }
        findViewById<View>(R.id.passwordLayout).setOnClickListener { view: View? -> showChangePasswordDialog() }
        findViewById<View>(R.id.iceServersLayout).setOnClickListener { view: View? -> showChangeIceServersDialog() }
        val username = settings.username
        (findViewById<View>(R.id.nameTv) as TextView).text =
            if (username.isEmpty()) resources.getString(R.string.none) else username
        val addresses: List<String?> = settings.addresses
        (findViewById<View>(R.id.addressTv) as TextView).text =
            if (addresses.isEmpty()) resources.getString(R.string.none) else join(addresses)
        val password = MainService.instance!!.database_password
        (findViewById<View>(R.id.passwordTv) as TextView).text =
            if (password.isEmpty()) resources.getString(R.string.none) else "********"
        val iceServers: List<String?> = settings.iceServers
        (findViewById<View>(R.id.iceServersTv) as TextView).text =
            if (iceServers.isEmpty()) resources.getString(R.string.none) else join(iceServers)
        val blockUnknown = settings.blockUnknown
        val blockUnknownCB = findViewById<SwitchMaterial>(R.id.switchBlockUnknown)
        blockUnknownCB.apply{
            isChecked = blockUnknown
            setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
                // save value
                settings.blockUnknown = isChecked
                MainService.instance!!.saveDatabase()
            }
        }
        val nightMode = MainService.instance!!.getSettings()?.nightMode
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
                    settings.nightMode = isChecked
                    MainService.instance!!.saveDatabase()

                    // apply theme
                    recreate()
                }
        }


        val recordAudio = settings.recordAudio
        val recordAudioCB = findViewById<CheckBox>(R.id.checkBoxSendAudio)
        recordAudioCB.isChecked = recordAudio
        recordAudioCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.recordAudio = isChecked
            MainService.instance!!.saveDatabase()
        }
        val playAudio = settings.playAudio
        val playAudioCB = findViewById<CheckBox>(R.id.checkBoxPlayAudio)
        playAudioCB.isChecked = playAudio
        playAudioCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.playAudio = isChecked
            MainService.instance!!.saveDatabase()
        }
        val recordVideo = settings.recordVideo
        val recordVideoCB = findViewById<CheckBox>(R.id.checkBoxRecordVideo)
        recordVideoCB.isChecked = recordVideo
        recordVideoCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.recordVideo = isChecked
            MainService.instance!!.saveDatabase()
        }
        val playVideo = settings.playVideo
        val playVideoCB = findViewById<CheckBox>(R.id.checkBoxPlayVideo)
        playVideoCB.isChecked = playVideo
        playVideoCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.playVideo = isChecked
            MainService.instance!!.saveDatabase()
        }
        val autoAcceptCall = settings.autoAcceptCall
        val autoAcceptCallCB = findViewById<CheckBox>(R.id.checkBoxAutoAcceptCall)
        autoAcceptCallCB.isChecked = autoAcceptCall
        autoAcceptCallCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.autoAcceptCall = isChecked
            MainService.instance!!.saveDatabase()
        }
        val autoConnectCall = settings.autoConnectCall
        val autoConnectCallCB = findViewById<CheckBox>(R.id.checkBoxAutoConnectCall)
        autoConnectCallCB.isChecked = autoConnectCall
        autoConnectCallCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.autoConnectCall = isChecked
            MainService.instance!!.saveDatabase()
        }
        val ignoreBatteryOptimizations = getIgnoreBatteryOptimizations()
        val ignoreBatteryOptimizationsCB =
            findViewById<CheckBox>(R.id.checkBoxIgnoreBatteryOptimizations)
        ignoreBatteryOptimizationsCB.isChecked = ignoreBatteryOptimizations
        ignoreBatteryOptimizationsCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // Only required for Android 6 or later
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + this.packageName)
                this.startActivity(intent)
            }
        }

        fun setServiceAndSettings(str: String) {
            settings.settingsMode = str
            MainService.instance!!.saveDatabase()
            applySettingsMode(str)
        }

        val selectedColor = ContextCompat.getColor(this, R.color.selectedColor)

        val settingsRadioGroup = findViewById<RadioGroup>(R.id.settings_mode_radio_group)
        val basicRadioButton = findViewById<RadioButton>(R.id.basic_radio_button)
        val advancedRadioButton = findViewById<RadioButton>(R.id.advanced_radio_button)
        val expertRadioButton = findViewById<RadioButton>(R.id.expert_radio_button)
//        val settingsModeArrayValues = resources.getStringArray(R.array.settingsModeValues

        basicRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("basic")
            } else {
                (compoundButton as RadioButton).setTextColor(Color.BLACK)

            }
        }

        advancedRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("advanced")
            } else {
                (compoundButton as RadioButton).setTextColor(Color.BLACK)

            }
        }

        expertRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("expert")
            } else {
                (compoundButton as RadioButton).setTextColor(Color.BLACK)

            }
        }

        basicRadioButton.apply {
            isSelected = true
            setTextColor(selectedColor)
            setServiceAndSettings("basic")
        }

        setupSpinner(settings.videoCodec,
            R.id.spinnerVideoCodecs,
            R.array.videoCodecs,
            R.array.videoCodecs,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let{
                        settings.videoCodec = it
                        MainService.instance!!.saveDatabase()
                    }

                }
            })
        setupSpinner(settings.audioCodec,
            R.id.spinnerAudioCodecs,
            R.array.audioCodecs,
            R.array.audioCodecs,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        settings.audioCodec = it
                        MainService.instance!!.saveDatabase()
                    }

                }
            })
        setupSpinner(settings.videoResolution,
            R.id.spinnerVideoResolutions,
            R.array.videoResolutions,
            R.array.videoResolutionsValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        settings.audioCodec = it
                        MainService.instance!!.saveDatabase()
                    }

                }
            })
//        setupSpinner(settings.speakerphone,
//            R.id.spinnerSpeakerphone,
//            R.array.speakerphone,
//            R.array.speakerphoneValues,
//            object : SpinnerItemSelected {
//                override fun call(newValue: String?) {
//                    newValue?.let {
//                        settings.speakerphone = it
//                        MainService.instance!!.saveDatabase()
//                    }
//
//                }
//            })

        val speakerSwitch = findViewById<SwitchMaterial>(R.id.speaker_switch)
        speakerSwitch.apply {
            setOnCheckedChangeListener { compoundButton, b ->
                if (b) {
                    settings.speakerphone = "true"
                    MainService.instance!!.saveDatabase()
                } else {
                    settings.speakerphone = "false"
                    MainService.instance!!.saveDatabase()
                }
            }
        }
        applySettingsMode(settings.settingsMode)
    }


    private interface SpinnerItemSelected {
        fun call(newValue: String?)
    }

    // allow for a customized spinner
    private fun setupSpinner(
        settingsMode: String?,
        spinnerId: Int,
        entriesId: Int,
        entryValuesId: Int,
        callback: SpinnerItemSelected
    ) {
        val spinner = findViewById<Spinner>(spinnerId)
        val spinnerAdapter: ArrayAdapter<*> =
            ArrayAdapter.createFromResource(this, entriesId, R.layout.spinner_item_settings)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(
            (spinner.adapter as ArrayAdapter<CharSequence?>).getPosition(
                settingsMode
            )
        )
        spinner.onItemSelectedListener = object : OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (check++ > 0) {
                    val selectedValues = resources.obtainTypedArray(entryValuesId)
                    val settingsMode = selectedValues.getString(pos)
                    callback.call(settingsMode)
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
            else -> e(
                TAG,
                "Invalid settings mode: $settingsMode"
            )
        }
    }

    private fun showChangeNameDialog() {
        val settings = MainService.instance!!.getSettings()!!
        val username = settings.username
        val et = EditText(this)
        et.setText(username)
        et.setSelection(username.length)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_name))
            .setView(et)
            .setPositiveButton(R.string.ok) { dialogInterface, i ->
                val new_username = et.text.toString().trim { it <= ' ' }
                if (isValidContactName(new_username)) {
                    settings.username = new_username
                    MainService.instance!!.saveDatabase()
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
        val password = MainService.instance!!.database_password
        val et = EditText(this)
        et.setText(password)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setSelection(password.length)
        AlertDialog.Builder(this)
            .setTitle(resources.getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton(R.string.ok) { dialogInterface, i ->
                val new_password = et.text.toString()
                MainService.instance!!.database_password = new_password
                MainService.instance!!.saveDatabase()
                initViews()
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeIceServersDialog() {
        val settings = MainService.instance!!.getSettings()!!
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_set_ice_server)
        val iceServersTextView = dialog.findViewById<TextView>(R.id.iceServersEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        iceServersTextView.text = join(settings.iceServers)
        saveButton.setOnClickListener { v: View? ->
            val iceServers =
                split(iceServersTextView.text.toString()) as ArrayList<String>
            settings.iceServers = iceServers

            // done
            Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }
        abortButton.setOnClickListener { v: View? -> dialog.cancel() }
        dialog.show()
    }

}