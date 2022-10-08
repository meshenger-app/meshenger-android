package d.d.meshenger

import android.app.Dialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import d.d.meshenger.MainService.MainBinder
import java.util.*

class SettingsActivity : MeshengerActivity(), ServiceConnection {
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

        val settings = binder!!.getSettings()

        findViewById<View>(R.id.nameLayout).setOnClickListener { showChangeNameDialog() }
        findViewById<View>(R.id.passwordLayout).setOnClickListener { showChangePasswordDialog() }
        findViewById<View>(R.id.iceServersLayout).setOnClickListener { showChangeIceServersDialog() }
        findViewById<View>(R.id.addressLayout).setOnClickListener {
            val intent = Intent(this, AddressActivity::class.java)
            startActivity(intent)
        }

        val username = settings.username
        (findViewById<View>(R.id.nameTv) as TextView).text =
            if (username.isEmpty()) getString(R.string.none) else username

        val addresses = settings.addresses
        (findViewById<View>(R.id.addressTv) as TextView).text =
            if (addresses.size == 0) getString(R.string.none) else addresses.joinToString()

        val password = binder!!.getService().database_password
        (findViewById<View>(R.id.passwordTv) as TextView).text =
            if (password.isEmpty()) getString(R.string.none) else "*".repeat(password.length)

        val iceServers = settings.iceServers
        (findViewById<View>(R.id.iceServersTv) as TextView).text =
            if (iceServers.isEmpty()) getString(R.string.none) else iceServers.joinToString()

        val blockUnknown = settings.blockUnknown
        val blockUnknownCB = findViewById<SwitchMaterial>(R.id.switchBlockUnknown)
        blockUnknownCB.apply {
            isChecked = blockUnknown
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                settings.blockUnknown = isChecked
                binder!!.saveDatabase()
            }
        }
        val nightMode = settings.nightMode
        val nightModeCB = findViewById<SwitchMaterial>(R.id.switchButtonNightMode)
        nightModeCB.apply {
            isChecked = nightMode
            setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                try {
                    // apply value
                    if (isChecked) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                    // save value
                    settings.nightMode = isChecked
                    binder!!.saveDatabase()
                    // apply theme
                    recreate()
                } finally {
                    finish()
                }
            }
        }

        val selectedColor = ContextCompat.getColor(this, R.color.selectedColor)
        val unselectedColor = ContextCompat.getColor(this, R.color.platform_grey)
        val basicRadioButton = findViewById<RadioButton>(R.id.basic_radio_button)
        val advancedRadioButton = findViewById<RadioButton>(R.id.advanced_radio_button)
        val expertRadioButton = findViewById<RadioButton>(R.id.expert_radio_button)

        applySettingsMode("basic");
        basicRadioButton.isChecked = true
        basicRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("basic")
                // settingsViewModel.currentSettingsMode = 0
            } else {
                (compoundButton as RadioButton).setTextColor(unselectedColor)
            }
        }

        advancedRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("advanced")
                //settingsViewModel.currentSettingsMode = 1
            } else {
                (compoundButton as RadioButton).setTextColor(unselectedColor)
            }
        }

        expertRadioButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                (compoundButton as RadioButton).setTextColor(selectedColor)
                setServiceAndSettings("expert")
                // settingsViewModel.currentSettingsMode = 2
            } else {
                (compoundButton as RadioButton).setTextColor(unselectedColor)
            }
        }

        setupSpinner(settings.videoCodec,
            R.id.spinnerVideoCodecs,
            R.array.videoCodecs,
            R.array.videoCodecs,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        settings.videoCodec = it
                        binder!!.saveDatabase()
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
                        binder!!.saveDatabase()
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
                        settings.videoResolution = it
                        binder!!.saveDatabase()
                    }
                }
            })
        val playAudio = settings.playAudio
        val playAudioCB = findViewById<CheckBox>(R.id.checkBoxPlayAudio)
        playAudioCB.isChecked = playAudio
        playAudioCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.playAudio = isChecked
            binder!!.saveDatabase()
        }

        val playVideo = settings.playVideo
        val playVideoCB = findViewById<CheckBox>(R.id.checkBoxPlayVideo)
        playVideoCB.isChecked = playVideo
        playVideoCB.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            // save value
            settings.playVideo = isChecked
            binder!!.saveDatabase()
        }

        val ignoreBatteryOptimizations = getIgnoreBatteryOptimizations()
        val ignoreBatteryOptimizationsCB =
            findViewById<CheckBox>(R.id.checkBoxIgnoreBatteryOptimizations)
        ignoreBatteryOptimizationsCB.isChecked = ignoreBatteryOptimizations
        ignoreBatteryOptimizationsCB.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean ->
            // Only required for Android 6 or later
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent =
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:" + this.packageName)
                this.startActivity(intent)
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
                val new_username = et.text.toString().trim { it <= ' ' }
                if (Utils.isValidName(new_username)) {
                    settings.username = new_username
                    binder!!.saveDatabase()
                    initViews()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.invalid_name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val password = binder!!.getService().database_password
        val et = EditText(this)
        et.setText(password)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        et.setSelection(password.length)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_change_password))
            .setView(et)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                val new_password = et.text.toString()
                binder!!.getService().database_password = new_password
                binder!!.saveDatabase()
                Toast.makeText(this@SettingsActivity, R.string.done, Toast.LENGTH_SHORT).show()
                initViews()
            }
            .setNegativeButton(resources.getText(R.string.cancel), null)
            .show()
    }

    private fun showChangeIceServersDialog() {
        val settings = binder!!.getSettings()
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_set_ice_server)
        val iceServersTextView = dialog.findViewById<TextView>(R.id.iceServersEditText)
        val saveButton = dialog.findViewById<Button>(R.id.SaveButton)
        val abortButton = dialog.findViewById<Button>(R.id.AbortButton)
        iceServersTextView.text = settings.iceServers.joinToString()
        saveButton.setOnClickListener {
            val iceServers = ArrayList<String>()
            Utils.split(iceServersTextView.text.toString()).let {
                iceServers.addAll(it)
            }
            settings.iceServers = iceServers
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
            else -> Log.e(
                ContentValues.TAG,
                "Invalid settings mode: $settingsMode"
            )
        }
    }

    fun setServiceAndSettings(str: String) {
        //settings.settingsMode = str
        // binder!!.saveDatabase()
        applySettingsMode(str)
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
        settingsMode: String?,
        spinnerId: Int,
        entriesId: Int,
        entryValuesId: Int,
        callback: SpinnerItemSelected,
    ) {
        val spinner = findViewById<Spinner>(spinnerId)
        val spinnerAdapter: ArrayAdapter<*> =
            ArrayAdapter.createFromResource(this, entriesId, R.layout.spinner_item_settings)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_settings)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(
            (spinner.adapter as ArrayAdapter<CharSequence?>).getPosition(settingsMode)
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            var check = 0
            override fun onItemSelected(parent: AdapterView<*>?, view: View, pos: Int, id: Long) {
                if (check++ > 0) {
                    val selectedValues = resources.obtainTypedArray(entryValuesId)
                    val settingsModeValue = selectedValues.getString(pos)
                    callback.call(settingsModeValue)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // ignore
            }
        }
    }
}