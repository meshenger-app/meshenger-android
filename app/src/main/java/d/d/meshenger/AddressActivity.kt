package d.d.meshenger

import d.d.meshenger.MeshengerActivity
import android.content.ServiceConnection
import d.d.meshenger.MainService.MainBinder
import d.d.meshenger.AddressEntry
import d.d.meshenger.AddressActivity.AddressListAdapter
import android.os.Bundle
import d.d.meshenger.R
import android.text.TextWatcher
import android.text.Editable
import android.content.Intent
import d.d.meshenger.MainService
import android.content.ComponentName
import android.os.IBinder
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import java.lang.Exception
import java.util.*

class AddressActivity : MeshengerActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    private lateinit var storedAddressSpinner: Spinner
    private lateinit var systemAddressSpinner: Spinner
    private lateinit var pickStoredAddressButton: Button
    private lateinit var pickSystemAddressButton: Button
    private lateinit var addressEditText: EditText
    private lateinit var addButton: Button
    private lateinit var removeButton: ImageButton
    private lateinit var saveButton: Button
    private lateinit var abortButton: Button
    private lateinit var systemAddressList: List<AddressEntry>
    private var storedAddressList = ArrayList<AddressEntry>()
    private lateinit var storedAddressListAdapter: AddressListAdapter
    private lateinit var systemAddressListAdapter: AddressListAdapter
    private fun initToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.address_toolbar)
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
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        setTitle(R.string.address_management)
        initToolbar()
        val sharedPreferences: SharedPreferences = this.getSharedPreferences("MESHENGER_PREF",
            MODE_PRIVATE)
        val editor: SharedPreferences.Editor =  sharedPreferences.edit()

        storedAddressSpinner = findViewById(R.id.StoredAddressSpinner)
        systemAddressSpinner = findViewById(R.id.SystemAddressSpinner)
        pickStoredAddressButton = findViewById(R.id.PickStoredAddressButton)
        pickSystemAddressButton = findViewById(R.id.PickSystemAddressButton)
        addressEditText = findViewById(R.id.AddressEditText)
        addButton = findViewById(R.id.AddButton)
        removeButton = findViewById(R.id.RemoveButton)
        saveButton = findViewById(R.id.SaveButton)
        abortButton = findViewById(R.id.AbortButton)
        systemAddressList = Utils.collectAddresses()
        storedAddressList = ArrayList()
        storedAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#39b300")) //dark green
        systemAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#b3b7b2")) //light grey
        storedAddressSpinner.setAdapter(storedAddressListAdapter)
        systemAddressSpinner.setAdapter(systemAddressListAdapter)
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
        addButton.setOnClickListener { v: View? ->
            val address = addressEditText.getText().toString()
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
            storedAddressList.add(entry)
            updateSpinners()

            // select the added element
            val idx = AddressEntry.listIndexOf(storedAddressList, entry)
            storedAddressSpinner.setSelection(idx)
        }
        removeButton.setOnClickListener { v: View? ->
            val address = addressEditText.getText().toString()
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
                    Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT)
                        .show()
                    return@OnClickListener
                }
                if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(entry)) {
                    Toast.makeText(
                        this,
                        "You can only choose a MAC/IP address that is used by the system.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@OnClickListener
                }
                storedAddressList.add(entry)
//                updateSpinners()

                // select the added element
                val idx = AddressEntry.listIndexOf(storedAddressList, entry)
//                systemAddressSpinner.setSelection(idx)

                editor.putInt("indx_no", pos)
                editor.apply()
            }
        })

        pickStoredAddressButton.setOnClickListener(View.OnClickListener { v: View? ->
            val pos = storedAddressSpinner.getSelectedItemPosition()
            if (pos > -1 && !storedAddressListAdapter!!.isEmpty) {
                addressEditText.setText(storedAddressList.get(pos).address)
            }
        })
        saveButton.setOnClickListener(View.OnClickListener { v: View? ->
            /*val addresses = ArrayList<String>()
            for (ae in storedAddressList) {
                addresses.add(ae.address)
            }*/

            if (storedAddressList.size > 0){
                val lastIndx = storedAddressList.size - 1

                binder!!.settings.addresses.add(storedAddressList[lastIndx].address)
                binder!!.saveDatabase()
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            }

        })
        abortButton.setOnClickListener(View.OnClickListener { v: View? -> finish() })
        bindService()

        val indx = sharedPreferences.getInt("indx_no", 0)
        systemAddressSpinner.setSelection(indx)

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
            storedAddressList!!.add(parseAddress(address))
        }
        updateSpinners()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    inner class AddressListAdapter(private val context: Activity, private val markColor: Int) :
        BaseAdapter() {
        private var addressEntries: List<AddressEntry> = ArrayList()
        private var addressEntriesMarked: List<AddressEntry> = ArrayList()
        override fun isEmpty(): Boolean {
            return addressEntries!!.isEmpty()
        }

        fun update(addressEntries: List<AddressEntry>, addressEntriesMarked: List<AddressEntry>) {
            this.addressEntries = addressEntries
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
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val x = convertView?: context.layoutInflater.inflate(R.layout.activity_address_item, parent, false)
            val label = x as TextView
            label?.let {
                if (isEmpty) {
                    label.text = context.resources.getString(R.string.empty_list_item)
                    label.setTextColor(Color.BLACK)
                } else {
                    val ae = addressEntries[position]
                    val info = ArrayList<String>()
                    if (ae.device.isNotEmpty()) {
                        info.add(ae.device)
                    }
                    if (ae.multicast) {
                        info.add("multicast")
                    }
                    label.text = ae.address + if (info.isEmpty()) "" else " (" + Utils.join(info) + ")"
                    if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                        label.setTextColor(Color.BLACK)
                    } else {
                        label.setTextColor(markColor)
                    }
                }

            }
            return x
        }

        init {
            addressEntries = ArrayList()
            addressEntriesMarked = ArrayList()
        }
    }

    private fun updateAddressEditTextButtons() {
        val address = addressEditText!!.text.toString()
        val exists = AddressEntry.findAddressEntry(storedAddressList, address) != null
        if (exists) {
            addButton!!.isEnabled = false
            removeButton!!.isEnabled = true
        } else {
            removeButton!!.isEnabled = false
            val valid = Utils.isMAC(address) || Utils.isDomain(address) || Utils.isIP(address)
            if (valid) {
                addButton!!.isEnabled = true
            } else {
                addButton!!.isEnabled = false
            }
        }
    }

    private fun updateSpinners() {
        // compare by device first, address second
        val compareAddressEntries =  Comparator { o1: AddressEntry, o2: AddressEntry ->
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
        systemAddressSpinner.adapter = storedAddressListAdapter
        systemAddressSpinner.adapter = systemAddressListAdapter
        pickStoredAddressButton.isEnabled = !storedAddressListAdapter.isEmpty
        pickSystemAddressButton.isEnabled = !systemAddressListAdapter.isEmpty
        updateAddressEditTextButtons()
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

    override fun onResume() {
        super.onResume()
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}