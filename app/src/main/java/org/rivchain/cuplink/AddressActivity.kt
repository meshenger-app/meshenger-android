package org.rivchain.cuplink

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.rivchain.cuplink.MainService.MainBinder
import java.lang.Exception
import java.util.*

class AddressActivity : CupLinkActivity(), ServiceConnection {
    lateinit var storedAddressSpinner: Spinner
    lateinit var systemAddressSpinner: Spinner
    lateinit var pickStoredAddressButton: Button
    lateinit var pickSystemAddressButton: Button
    lateinit var addressEditText: EditText
    lateinit var addButton: Button
    lateinit var removeButton: Button
    lateinit var saveButton: Button
    lateinit var abortButton: Button
    lateinit var systemAddressList: List<AddressEntry>
    lateinit var storedAddressList: MutableList<AddressEntry>
    var storedAddressListAdapter: AddressListAdapter? = null
    var systemAddressListAdapter: AddressListAdapter? = null
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        setTitle(R.string.address_management)
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
        storedAddressListAdapter = AddressListAdapter(this, Color.parseColor("#39b300")) //dark green
        systemAddressListAdapter = AddressListAdapter(this, Color.parseColor("#b3b7b2")) //light grey
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
        addButton.setOnClickListener(View.OnClickListener { v: View? ->
            val address = addressEditText.getText().toString()
            if (address.isEmpty()) {
                return@OnClickListener
            }
            val entry = parseAddress(address)
            if (entry.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            if (Utils.isIP(address) && !systemAddressList.contains(entry)) {
                Toast.makeText(this, "You can only choose a IP address that is used by the system.", Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            storedAddressList.add(entry)
            updateSpinners()

            // select the added element
            val idx = AddressEntry.listIndexOf(storedAddressList, entry)
            storedAddressSpinner.setSelection(idx)
        })
        removeButton.setOnClickListener(View.OnClickListener { v: View? ->
            val address = addressEditText.getText().toString()
            if (address.isEmpty()) {
                return@OnClickListener
            }
            val idx = AddressEntry.listIndexOf(storedAddressList, AddressEntry(address, "", false))
            if (idx > -1) {
                storedAddressList.removeAt(idx)
                updateSpinners()
            }
        })
        pickSystemAddressButton.setOnClickListener(View.OnClickListener { v: View? ->
            val pos = systemAddressSpinner.getSelectedItemPosition()
            if (pos > -1 && !systemAddressListAdapter!!.isEmpty) {
                addressEditText.setText(systemAddressList.get(pos).address)
            }
        })
        pickStoredAddressButton.setOnClickListener { v: View? ->
            val pos = storedAddressSpinner.getSelectedItemPosition()
            if (pos > -1 && !storedAddressListAdapter!!.isEmpty) {
                addressEditText.setText(storedAddressList.get(pos).address)
            }
        }
        saveButton.setOnClickListener { v: View? ->
            val addresses = ArrayList<String>()
            for (ae in storedAddressList) {
                addresses.add(ae.address)
            }
            binder!!.settings.addresses = addresses
            initKeyPair()
            binder!!.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        abortButton.setOnClickListener { v: View? -> finish() }
        bindService()
    }

    private fun initKeyPair() {
        val ae = storedAddressList!![storedAddressList!!.size - 1]
        // create secret/public key pair
        val publicKey = Utils.parseInetSocketAddress(ae.address, 0)!!.address.address
        val secretKey: ByteArray? = null
        val settings = binder!!.settings
        settings.publicKey = publicKey
        settings.secretKey = secretKey
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

    private fun updateAddressEditTextButtons() {
        val address = addressEditText!!.text.toString()
        val exists = AddressEntry.findAddressEntry(storedAddressList, address) != null
        if (exists) {
            addButton!!.isEnabled = false
            removeButton!!.isEnabled = true
        } else {
            removeButton!!.isEnabled = false
            val valid = Utils.isMAC(address) || Utils.isDomain(address) || Utils.isIP(address)
            addButton!!.isEnabled = valid
        }
        val nightMode = binder!!.settings.nightMode
        if (nightMode) {
            addressEditText!!.setTextColor(Color.WHITE)
        } else {
            addressEditText!!.setTextColor(Color.BLACK)
        }
    }

    private fun updateSpinners() {
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
        storedAddressListAdapter!!.update(storedAddressList, systemAddressList)
        storedAddressListAdapter!!.notifyDataSetChanged()
        systemAddressListAdapter!!.update(systemAddressList, storedAddressList)
        systemAddressListAdapter!!.notifyDataSetChanged()
        systemAddressSpinner!!.adapter = storedAddressListAdapter
        systemAddressSpinner!!.adapter = systemAddressListAdapter
        pickStoredAddressButton!!.isEnabled = !storedAddressListAdapter!!.isEmpty
        pickSystemAddressButton!!.isEnabled = !systemAddressListAdapter!!.isEmpty
        updateAddressEditTextButtons()
    }

    /*
     * Create AddressEntry from address string.
     * Do not perform any domain lookup
     */
    fun parseAddress(address: String?): AddressEntry {
        // instead of parsing, lookup in known addresses first
        val ae = AddressEntry.findAddressEntry(systemAddressList, address!!)
        return if (ae != null) {
            // known address
            AddressEntry(address!!, ae.device, ae.multicast)
        } else if (Utils.isMAC(address)) {
            // MAC address
            val mc = Utils.isMulticastMAC(Utils.macAddressToBytes(address))
            AddressEntry(address!!, "", mc)
        } else if (Utils.isIP(address)) {
            // IP address
            var mc = false
            try {
                mc = Utils.parseInetSocketAddress(address, 0)!!.address.isMulticastAddress
            } catch (e: Exception) {
                // ignore
            }
            AddressEntry(address!!, "", mc)
        } else {
            // domain
            AddressEntry(address!!, "", false)
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun log(s: String) {
        Log.d(this, s)
    }

    inner class AddressListAdapter(private val context: Activity, private val markColor: Int) : BaseAdapter() {
        private var addressEntries: List<AddressEntry>?
        private var addressEntriesMarked: List<AddressEntry>?
        override fun isEmpty(): Boolean {
            return addressEntries!!.isEmpty()
        }

        fun update(addressEntries: List<AddressEntry>?, addressEntriesMarked: List<AddressEntry>?) {
            this.addressEntries = addressEntries
            this.addressEntriesMarked = addressEntriesMarked
        }

        override fun getCount(): Int {
            return if (isEmpty) {
                1
            } else addressEntries!!.size
        }

        override fun getItem(position: Int): Any? {
            return if (isEmpty) {
                null
            } else addressEntries!![position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, view: View?, parent: ViewGroup): View {
            var view = view
            if (view == null) {
                val inflater = context.layoutInflater
                view = inflater.inflate(R.layout.activity_address_item, parent, false)
            }
            val label = view!!.findViewById<TextView>(R.id.label)
            if (isEmpty) {
                label.text = resources.getString(R.string.empty_list_item)
                label.setTextColor(Color.GRAY)
            } else {
                val ae = addressEntries!![position]
                val info = ArrayList<String>()
                if (ae.device.length > 0) {
                    info.add(ae.device)
                }
                if (ae.multicast) {
                    info.add("multicast")
                }
                label.text = ae.address + if (info.isEmpty()) "" else " (" + Utils.join(info) + ")"
                if (AddressEntry.listIndexOf(addressEntriesMarked!!, ae) < 0) {
                    label.setTextColor(Color.GRAY)
                } else {
                    label.setTextColor(markColor)
                }
            }
            return view
        }

        init {
            addressEntries = ArrayList()
            addressEntriesMarked = ArrayList()
        }
    }
}