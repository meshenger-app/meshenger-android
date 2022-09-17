package d.d.meshenger

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.androidbuts.multispinnerfilter.KeyPairBoolData
import com.androidbuts.multispinnerfilter.MultiSpinnerSearch
import d.d.meshenger.MainService
import d.d.meshenger.MainService.MainBinder
import java.util.*

class AddressActivity : MeshengerActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    private lateinit var storedAddressSpinner: Spinner
    private lateinit var systemAddressSpinner: Spinner
    private lateinit var pickStoredAddressButton: Button
    private lateinit var pickSystemAddressButton: Button
    private lateinit var addressEditText: EditText
    private lateinit var addButton: Button
    private lateinit var removeBtn: Button
    private lateinit var removeButton: ImageButton
    private lateinit var ipField: EditText
    private lateinit var saveButton: Button
    private lateinit var abortButton: Button
    private val systemAddressList: ArrayList<AddressEntry> = arrayListOf()
    private var storedAddressList = ArrayList<AddressEntry>()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var storedAddressListAdapter: AddressListAdapter
    private lateinit var systemAddressListAdapter: AddressListAdapter
    private fun initToolbar() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    fun saveCustomIpList(pref: SharedPreferences, items: List<String>) {
        pref.edit()
            .putStringSet("custom_ip_list", items.toSet())
            .commit()
    }

    val customIpAddress = ArrayList<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        setTitle(R.string.address_management)
        initToolbar()

        sharedPreferences = this.getSharedPreferences(
            "MESHENGER_PREF",
            MODE_PRIVATE
        )
        editor = sharedPreferences.edit()

        storedAddressSpinner = findViewById(R.id.StoredAddressSpinner)
        systemAddressSpinner = findViewById(R.id.SystemAddressSpinner)
        pickStoredAddressButton = findViewById(R.id.PickStoredAddressButton)
        pickSystemAddressButton = findViewById(R.id.PickSystemAddressButton)
        removeBtn = findViewById(R.id.removeBtn1)
        addressEditText = findViewById(R.id.AddressEditText)
        addButton = findViewById(R.id.AddButton)
        removeButton = findViewById(R.id.RemoveButton)
        saveButton = findViewById(R.id.SaveButton)
        abortButton = findViewById(R.id.AbortButton)
        systemAddressList.addAll(Utils.collectAddresses())
        val cache = sharedPreferences.getStringSet("custom_ip_list", setOf())
        if (cache != null) {
            customIpAddress.addAll(cache.toList())
            systemAddressList.addAll(cache.map {
                AddressEntry(it.toString(), "Custom", false, true)
            })
        }
        storedAddressList = ArrayList()
        storedAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#39b300")) //dark green
        systemAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#39b300")) //light green
        storedAddressSpinner.adapter = storedAddressListAdapter
        systemAddressSpinner.adapter = systemAddressListAdapter
        addressEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                updateAddressEditTextButtons()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // nothing to do
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateAddressEditTextButtons()
            }
        })

        removeBtn.setOnClickListener {
            val pos = systemAddressSpinner.selectedItemPosition
            if (pos > -1 && !systemAddressListAdapter.isEmpty) {
//                addressEditText.setText(systemAddressList.get(pos).address)
                val address = systemAddressList.get(pos).address
                val entry = parseAddress(address)
                if (storedAddressList.contains(entry)) {
                    storedAddressList.remove(entry)
                    binder!!.settings.addresses.remove(entry.address)
                    binder!!.saveDatabase()
                    updateSpinners()
                    Toast.makeText(this, "$entry is removed!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "This Entry is not saved!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        addButton.setOnClickListener { v: View? ->
            val address = addressEditText.text.toString()
            if (address.isEmpty()) {
                return@setOnClickListener
            }
            val entry = parseAddress(address)
            if (entry.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(entry)) {
                Toast.makeText(
                    this,
                    "You can only choose a MAC/IP address that is used by the system.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (!storedAddressList.contains(entry)) {
                storedAddressList.add(entry)
            }
            updateSpinners()
            // select the added element
            val idx = AddressEntry.listIndexOf(storedAddressList, entry)
            storedAddressSpinner.setSelection(idx)
        }
        removeButton.setOnClickListener {
            val address = addressEditText.text.toString()
            if (address.isEmpty()) {
                return@setOnClickListener
            }
            val idx = AddressEntry.listIndexOf(storedAddressList, AddressEntry(address, "", false))
            if (idx > -1) {
                storedAddressList.removeAt(idx)
                updateSpinners()
            }
        }

        pickSystemAddressButton.setOnClickListener(View.OnClickListener { v: View? ->
            val pos = systemAddressSpinner.selectedItemPosition
            if (pos > -1 && !systemAddressListAdapter.isEmpty) {
//                addressEditText.setText(systemAddressList.get(pos).address)
                val address = systemAddressList.get(pos).address
                val entry = parseAddress(address)
                if (entry.multicast) {
                    Toast.makeText(
                        this,
                        "Multicast addresses are not supported.",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    return@OnClickListener
                }
                if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(
                        entry
                    )
                ) {
                    Toast.makeText(
                        this,
                        "You can only choose a MAC/IP address that is used by the system.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@OnClickListener
                }
                if (!storedAddressList.contains(entry)) {
                    storedAddressList.add(entry)
                }
//                updateSpinners()
                // select the added element
                val idx = AddressEntry.listIndexOf(storedAddressList, entry)
//                systemAddressSpinner.setSelection(idx)
                editor.putString("selected_ip_address", entry.address)
                editor.apply()
                systemAddressListAdapter.notifyDataSetChanged()
            }
        })

        pickStoredAddressButton.setOnClickListener(View.OnClickListener { v: View? ->
            val pos = storedAddressSpinner.selectedItemPosition
            if (pos > -1 && !storedAddressListAdapter.isEmpty) {
                addressEditText.setText(storedAddressList.get(pos).address)
            }
        })

        saveButton.setOnClickListener(View.OnClickListener { v: View? ->
            val pos = systemAddressSpinner.selectedItemPosition
            if (pos > -1 && !systemAddressListAdapter.isEmpty) {
                val address = systemAddressList.get(pos).address
                val entry = parseAddress(address)
                if (entry.multicast) {
                    Toast.makeText(
                        this,
                        "Multicast addresses are not supported.",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    return@OnClickListener
                }
                if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(
                        entry
                    )
                ) {
                    Toast.makeText(
                        this,
                        "You can only choose a MAC/IP address that is used by the system.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@OnClickListener
                }

                if (!storedAddressList.contains(entry)) {
                    storedAddressList.add(entry)
                } else {
                    Toast.makeText(applicationContext,
                        "THIS ADDRESS ALREADY EXISTS",
                        Toast.LENGTH_LONG).show()
                }
//                updateSpinners()
                // select the added element
                val idx = AddressEntry.listIndexOf(storedAddressList, entry)
//                systemAddressSpinner.setSelection(idx)
                editor.putString("selected_ip_address", entry.address)
                editor.apply()
            }

            if (storedAddressList.size > 0) {
                val lastIndx = storedAddressList.size - 1

                if (!binder!!.settings.addresses.contains(storedAddressList[lastIndx].address)) {
                    binder!!.settings.addresses.add(storedAddressList[lastIndx].address)
                    Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
                    binder!!.saveDatabase()
//                    updateSpinners()
                }
            }
        })


        ipField = findViewById(R.id.ipAdressField)
        findViewById<View>(R.id.addIpBtn).setOnClickListener {
            val text = ipField.text?.toString() ?: return@setOnClickListener
            if (text.isEmpty() || text.split(".").size < 2) {
                Toast.makeText(this, "Please enter a valid domain name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val entry = AddressEntry(text, "Custom", false, true)

            if (!systemAddressList.contains(entry)) {
                systemAddressList.add(entry)
                customIpAddress.add(text)

                if (!binder!!.settings.addresses.contains(text)) {
                    binder!!.settings.addresses.add(text)
                }
                updateSpinners(true)
                saveCustomIpList(sharedPreferences, customIpAddress)
                ipField.setText("")
            } else {
                Toast.makeText(applicationContext, "THIS ADDRESS ALREADY EXISTS", Toast.LENGTH_LONG)
                    .show()
            }
        }
        abortButton.setOnClickListener(View.OnClickListener { v: View? -> finish() })
        bindService()
    }

    private fun saveOperation(i: Int) {
        val address = systemAddressList.get(i).address
        val entry = parseAddress(address)
        if (entry.multicast) {
            Toast.makeText(
                this,
                "Multicast addresses are not supported.",
                Toast.LENGTH_SHORT
            )
                .show()
            return
        }
        if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(entry)
        ) {
            Toast.makeText(
                this,
                "You can only choose a MAC/IP address that is used by the system.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!storedAddressList.contains(entry)) {
            storedAddressList.add(entry)
        } else {
            Toast.makeText(applicationContext,
                "THIS ADDRESS ALREADY EXISTS",
                Toast.LENGTH_LONG).show()
        }
//                updateSpinners()
        // select the added element
        val idx = AddressEntry.listIndexOf(storedAddressList, entry)
//                systemAddressSpinner.setSelection(idx)
        editor.putString("selected_ip_address", entry.address)
        editor.apply()


        if (storedAddressList.size > 0) {
            val lastIndx = storedAddressList.size - 1

            if (!binder!!.settings.addresses.contains(storedAddressList[lastIndx].address)) {
                binder!!.settings.addresses.add(storedAddressList[lastIndx].address)
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
                binder!!.saveDatabase()
                //updateSpinners()
            }
        }
    }

    private fun initiateSpinner() {
        /**
         * Search MultiSelection Spinner (With Search/Filter Functionality)
         *
         *  Using MultiSpinnerSearch class
         */
        /**
         * Search MultiSelection Spinner (With Search/Filter Functionality)
         *
         * Using MultiSpinnerSearch class
         */
        val multiSelectSpinnerWithSearch =
            findViewById<MultiSpinnerSearch>(R.id.multipleItemSelectionSpinner)
        // Pass true If you want searchView above the list. Otherwise false. default = true.
        // Pass true If you want searchView above the list. Otherwise false. default = true.
        multiSelectSpinnerWithSearch.isSearchEnabled = true
        // A text that will display in search hint.
        // A text that will display in search hint.
        multiSelectSpinnerWithSearch.setSearchHint("Select your ip")
        // Set text that will display when search result not found...
        // Set text that will display when search result not found...
        multiSelectSpinnerWithSearch.setEmptyTitle("Not Data Found!")
        // If you will set the limit, this button will not display automatically.
        // If you will set the limit, this button will not display automatically.
        multiSelectSpinnerWithSearch.isShowSelectAllButton = true
        //A text that will display in clear text button
        //A text that will display in clear text button
        multiSelectSpinnerWithSearch.setClearText("Close & Clear")
        // Removed second parameter, position. Its not required now..
        // If you want to pass preselected items, you can do it while making listArray,
        // Pass true in setSelected of any item that you want to preselect
        // Removed second parameter, position. Its not required now..
        // If you want to pass preselected items, you can do it while making listArray,
        // Pass true in setSelected of any item that you want to preselect
        val listArray1: MutableList<KeyPairBoolData> = ArrayList()
        for (i in systemAddressList.indices) {
            val h = KeyPairBoolData()
            h.id = (i + 1).toLong()
            h.name = systemAddressList.get(i).address
            h.isSelected = storedAddressList.contains(systemAddressList[i])
            listArray1.add(h)
        }
        multiSelectSpinnerWithSearch.setItems(listArray1) { it ->
            removeItemsDB()
            for (i in it.indices) {
                if (it[i].isSelected) {
                    Log.d("Address",
                        i.toString() + " : " + it[i].name + " : " + it[i].isSelected)
//                    saveOperation(i)
                    if (!binder!!.settings.addresses.contains(it[i].name)) {
                        binder!!.settings.addresses.add(it[i].name)
//                        Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
                        binder!!.saveDatabase()
                    }
                }
            }
//            updateSpinners()
        }
    }

    private fun removeItemsDB() {
        storedAddressList = ArrayList()
        binder!!.settings.addresses = ArrayList()
        binder!!.saveDatabase()
        updateSpinners()
//
////        val dummyList = ArrayList<String>()
//        dummyList.addAll(binder!!.settings.addresses)
//
//        for (item in dummyList) {
//            binder!!.settings.addresses.remove(item)
//        }
//        binder!!.saveDatabase()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    private fun bindService() {
        // ask MainService to get us the binder object
        val serviceIntent = Intent(this, MainService::class.java)
        bindService(serviceIntent, this, BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder
        // get from settings
        for (address in binder!!.settings.addresses) {
            if (!storedAddressList.contains(parseAddress(address))) {
                storedAddressList.add(parseAddress(address))
            }
        }
        initiateSpinner()
        updateSpinners(true)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    inner class AddressListAdapter(private val context: Activity, private val markColor: Int) :
        BaseAdapter() {
        private var addressEntries: ArrayList<AddressEntry> = ArrayList()
        private var addressEntriesMarked: List<AddressEntry> = ArrayList()
        override fun isEmpty(): Boolean {
            return addressEntries.isEmpty()
        }

        fun update(
            addressEntries: ArrayList<AddressEntry>,
            addressEntriesMarked: List<AddressEntry>,
        ) {
            this.addressEntries.clear()


            this.addressEntries.addAll(addressEntries)
            this.addressEntriesMarked = addressEntriesMarked
        }

        override fun getCount(): Int {
            return if (isEmpty) {
                1
            } else addressEntries.size
        }

        override fun getItem(position: Int): AddressEntry? {
            return if (isEmpty) {
                null
            } else addressEntries[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val itemView = super.getDropDownView(position, convertView, parent)
            val selectedIp = sharedPreferences.getString("selected_ip_address", null)
            if (selectedIp == getItem(position)?.address) {
//                itemView.setBackgroundColor(Color.parseColor("#d3d3d3"))
            }

            return itemView
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val x = convertView ?: context.layoutInflater.inflate(
                R.layout.activity_address_item,
                parent,
                false
            )
            val label = x.findViewById<TextView>(R.id.label)
            val icon = x.findViewById<ImageView>(R.id.icon)
            label.let {
                if (isEmpty) {
                    label.text = context.resources.getString(R.string.empty_list_item)
                    label.setTextColor(Color.BLACK)
                    icon.isVisible = false
                } else {
                    val ae = addressEntries[position]
                    icon.isVisible = ae.isCustom
                    if (ae.isCustom) {
                        icon.setOnClickListener {
                            onDeleteCustomAdress(ae)
                        }
                    } else {
                        icon.setOnClickListener(null)
                    }
                    val info = ArrayList<String>()
                    if (ae.device.isNotEmpty()) {
                        info.add(ae.device)
                    }
                    if (ae.multicast) {
                        info.add("multicast")
                    }
                    label.text =
                        ae.address + if (info.isEmpty()) "" else " (" + Utils.join(info) + ")"

                    if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                        label.setTextColor(Color.BLACK)
                    } else {
                        label.setTextColor(markColor)
                    }
                }
            }

            return x
        }

        fun addEntry(entry: AddressEntry) {
            addressEntries.add(entry)
            notifyDataSetChanged()
        }

        fun removeEntry(entry: AddressEntry) {
            addressEntries.remove(entry)
            notifyDataSetChanged()
        }

        init {
            addressEntries = ArrayList()
            addressEntriesMarked = ArrayList()
        }
    }

    private fun onDeleteCustomAdress(ae: AddressEntry) {
        for (i in 0 until systemAddressList.size) {
            if (systemAddressList[i].address == ae.address) {
                systemAddressList.removeAt(i)
                break
            }
        }
        customIpAddress.remove(ae.address)
        saveCustomIpList(sharedPreferences, customIpAddress)
        updateSpinners(true)
    }

    private fun updateAddressEditTextButtons() {
        val address = addressEditText.text.toString()
        val exists = AddressEntry.findAddressEntry(storedAddressList, address) != null
        if (exists) {
            addButton.isEnabled = false
            removeButton.isEnabled = true
        } else {
            removeButton.isEnabled = false
            val valid = Utils.isMAC(address) || Utils.isDomain(address) || Utils.isIP(address)
            addButton.isEnabled = valid
        }
    }

    private fun updateSpinners(selectWlan0: Boolean = false) {
        // compare by device first, address second
        val compareAddressEntries = Comparator { o1: AddressEntry, o2: AddressEntry ->
            val dd = o1.device.compareTo(o2.device)
            if (dd == 0) {
                return@Comparator o1.address.compareTo(o2.address)
            } else {
                return@Comparator dd
            }
        }
        Collections.sort(storedAddressList, compareAddressEntries)
        Collections.sort(systemAddressList, compareAddressEntries)
        storedAddressListAdapter.update(storedAddressList, systemAddressList)
        storedAddressListAdapter.notifyDataSetChanged()
        systemAddressListAdapter.update(systemAddressList, storedAddressList)
        systemAddressListAdapter.notifyDataSetChanged()
        storedAddressSpinner.adapter = storedAddressListAdapter
        systemAddressSpinner.adapter = systemAddressListAdapter
        pickStoredAddressButton.isEnabled = !storedAddressListAdapter.isEmpty
        pickSystemAddressButton.isEnabled = !systemAddressListAdapter.isEmpty

        if (selectWlan0) {
            var fe890Wlan = systemAddressList.indexOfFirst {
                it.address.startsWith("fe80::") && it.device.startsWith("wlan")
            }
            if (fe890Wlan < 0) {
                fe890Wlan =
                    systemAddressList.indexOfFirst { it.device.startsWith("wlan") }.coerceAtLeast(0)
            }
            sharedPreferences.edit {
                putString("selected_ip_address", systemAddressList[fe890Wlan].address)
            }
            systemAddressSpinner.setSelection(fe890Wlan)
            updateAddressEditTextButtons()
//            initiateSpinner()
        }
    }

    /*
     * Create AddressEntry from address string.
     * Do not perform any domain lookup
    */
    fun parseAddress(address: String): AddressEntry {
        // instead of parsing, lookup in known addresses first
        val ae = AddressEntry.findAddressEntry(systemAddressList, address)
        return if (ae != null) {
            // known address
            AddressEntry(address, ae.device, ae.multicast)
        } else if (Utils.isMAC(address)) {
            // MAC address
            val mc = Utils.isMulticastMAC(Utils.macAddressToBytes(address))
            AddressEntry(address, "", mc)
        } else if (Utils.isIP(address)) {
            // IP address
            var mc = false
            try {
                Utils.parseInetSocketAddress(address, 0)?.let {
                    mc = it.address.isMulticastAddress
                }
            } catch (e: Exception) {
                // ignore
            }
            AddressEntry(address, "", mc)
        } else {
            // domain
            AddressEntry(address, "", false)
        }
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}