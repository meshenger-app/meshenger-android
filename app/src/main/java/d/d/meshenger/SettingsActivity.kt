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
import d.d.meshenger.MainService
import d.d.meshenger.MainService.MainBinder

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
        binder = iBinder!! as MainBinder
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
            if (addresses.size == 0) resources.getString(R.string.none) else addresses[addresses.size-1]
        val password = binder!!.getService().databasePassword
        (findViewById<View>(R.id.passwordTv) as TextView).text =
            if (password.isEmpty()) resources.getString(R.string.none) else "********"
        val iceServers = binder!!.settings.iceServers
        (findViewById<View>(R.id.iceServersTv) as TextView).text =
            if (iceServers.isEmpty()) resources.getString(R.string.none) else Utils.join(iceServers)
        val blockUnknown = binder!!.settings.blockUnknown
        val blockUnknownCB = findViewById<SwitchMaterial>(R.id.switchBlockUnknown)
        blockUnknownCB.apply {
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
                try {


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
        setupSpinner(binder!!.settings.videoCodec,
            R.id.spinnerVideoCodecs,
            R.array.videoCodecs,
            R.array.videoCodecs,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        binder!!.settings.videoCodec = it
                        binder!!.saveDatabase()
                    }

                }
            })
        setupSpinner(binder!!.settings.audioCodec,
            R.id.spinnerAudioCodecs,
            R.array.audioCodecs,
            R.array.audioCodecs,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        binder!!.settings.audioCodec = it
                        binder!!.saveDatabase()
                    }

                }
            })
        setupSpinner(binder!!.settings.videoResolution,
            R.id.spinnerVideoResolutions,
            R.array.videoResolutions,
            R.array.videoResolutionsValues,
            object : SpinnerItemSelected {
                override fun call(newValue: String?) {
                    newValue?.let {
                        binder!!.settings.videoResolution = it
                        binder!!.saveDatabase()
                    }

                }
            })

        val recordAudio = binder!!.settings.recordAudio
        val recordAudioCB = findViewById<CheckBox>(R.id.checkBoxSendAudio)
        recordAudioCB.isChecked = recordAudio
        recordAudioCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.recordVideo = isChecked
            binder!!.saveDatabase()
        }
        val playAudio = binder!!.settings.playAudio
        val playAudioCB = findViewById<CheckBox>(R.id.checkBoxPlayAudio)
        playAudioCB.isChecked = playAudio
        playAudioCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.playAudio = isChecked
            binder!!.saveDatabase()
        }
        val recordVideo = binder!!.settings.recordVideo
        val recordVideoCB = findViewById<CheckBox>(R.id.checkBoxRecordVideo)
        recordVideoCB.isChecked = recordVideo
        recordVideoCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.recordVideo = isChecked
            binder!!.saveDatabase()
        }
        val playVideo = binder!!.settings.playVideo
        val playVideoCB = findViewById<CheckBox>(R.id.checkBoxPlayVideo)
        playVideoCB.isChecked = playVideo
        playVideoCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.playVideo = isChecked
            binder!!.saveDatabase()
        }
        val autoAcceptCall = binder!!.settings.autoAcceptCall
        val autoAcceptCallCB = findViewById<CheckBox>(R.id.checkBoxAutoAcceptCall)
        autoAcceptCallCB.isChecked = autoAcceptCall
        autoAcceptCallCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.autoAcceptCall = isChecked
            binder!!.saveDatabase()
        }
        val autoConnectCall = binder!!.settings.autoConnectCall
        val autoConnectCallCB = findViewById<CheckBox>(R.id.checkBoxAutoConnectCall)
        autoConnectCallCB.isChecked = autoConnectCall
        autoConnectCallCB.setOnCheckedChangeListener { compoundButton: CompoundButton?, isChecked: Boolean ->
            // save value
            binder!!.settings.autoConnectCall = isChecked
            binder!!.saveDatabase()
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
            else -> android.util.Log.e(
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
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
}