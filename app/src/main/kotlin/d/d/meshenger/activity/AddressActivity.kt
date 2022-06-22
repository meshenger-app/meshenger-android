package d.d.meshenger.activity

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import d.d.meshenger.*
import d.d.meshenger.base.MeshengerActivity
import d.d.meshenger.model.AddressEntry
import d.d.meshenger.service.MainService
import d.d.meshenger.utils.AddressUtils
import d.d.meshenger.utils.Utils
import java.net.InetSocketAddress
import java.util.*
import kotlin.Comparator


class AddressActivity: MeshengerActivity() {

    companion object {
        private const val TAG = "AddressActivity"

    }

    private lateinit var storedAddressSpinner: Spinner
    private lateinit var systemAddressSpinner: Spinner
    private lateinit var pickStoredAddressButton: Button
    private lateinit var pickSystemAddressButton: Button
    private lateinit var addressEditText: EditText
    private lateinit var addButton: Button
    private lateinit var removeButton: Button
    private lateinit var saveButton: Button
    private lateinit var abortButton: Button

    private var systemAddressList = ArrayList<AddressEntry>()
    private var storedAddressList = ArrayList<AddressEntry>()
    private lateinit var storedAddressListAdapter: AddressListAdapter
    private lateinit var systemAddressListAdapter: AddressListAdapter

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
        systemAddressList = AddressUtils.getOwnAddresses()
        storedAddressList = ArrayList()
        storedAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#39b300")) //dark green
        systemAddressListAdapter =
            AddressListAdapter(this, Color.parseColor("#b3b7b2")) //light grey
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
        addButton.setOnClickListener { v: View? ->
            val address = addressEditText.text.toString()
            if (address.isEmpty()) {
                return@setOnClickListener
            }
            val entry: AddressEntry = parseAddress(address)
            if (entry.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
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
                return@setOnClickListener
            }
            storedAddressList.add(entry)
            updateSpinners()

            // select the added element
            val idx = AddressEntry.listIndexOf(storedAddressList, entry)
            storedAddressSpinner.setSelection(idx)
        }
        removeButton.setOnClickListener { v: View? ->
            val address = addressEditText.text.toString()
            if (address.isEmpty()) {
                return@setOnClickListener
            }
            val idx =
                AddressEntry.listIndexOf(storedAddressList, AddressEntry(address, "", false))
            if (idx > -1) {
                storedAddressList.removeAt(idx)
                updateSpinners()
            }
        }
        pickSystemAddressButton.setOnClickListener { v: View? ->
            val pos = systemAddressSpinner.selectedItemPosition
            if (pos > -1 && !systemAddressListAdapter.isEmpty()) {
                addressEditText.setText(systemAddressList[pos].address)
            }
        }
        pickStoredAddressButton.setOnClickListener { v: View? ->
            val pos = storedAddressSpinner.selectedItemPosition
            if (pos > -1 && !storedAddressListAdapter.isEmpty()) {
                addressEditText.setText(storedAddressList[pos].address)
            }
        }
        saveButton.setOnClickListener { v: View? ->
            val addresses: ArrayList<String> = ArrayList()
            for (ae in storedAddressList) {
                addresses.add(ae.address)
            }
            MainService.instance!!.getSettings()?.addresses = addresses
            MainService.instance!!.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        abortButton.setOnClickListener { v: View? -> finish() }

        // get from settings
        for (address in MainService.instance!!.getSettings()?.addresses!!) {
            storedAddressList.add(parseAddress(address))
        }
        updateSpinners()
    }

    inner class AddressListAdapter(private val context: Activity, private val markColor: Int) :
        BaseAdapter() {
        private var addressEntries: List<AddressEntry>
        private var addressEntriesMarked: List<AddressEntry>
        override fun isEmpty(): Boolean {
            return addressEntries.isEmpty()
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

        override fun getItem(position: Int): Any? {
            return if (isEmpty) {
                null
            } else addressEntries[position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, view: View, parent: ViewGroup): View {
            var view = view
            if (view == null) {
                val inflater = context.layoutInflater
                view = inflater.inflate(R.layout.activity_address_item, parent, false)
            }
            val label = view.findViewById<TextView>(R.id.label)
            if (isEmpty) {
                label.setText(getResources().getString(R.string.empty_list_item))
                label.setTextColor(Color.BLACK)
            } else {
                val ae = addressEntries[position]
                val info = java.util.ArrayList<String>()
                if (ae.device.length > 0) {
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
            return view
        }

        init {
            addressEntries = java.util.ArrayList()
            addressEntriesMarked = java.util.ArrayList()
        }
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

    private fun updateSpinners() {
        // compare by device first, address second
        val compareAddressEntries = Comparator<AddressEntry> { p0, p1 ->
            val dd = p0.device.compareTo(p1.device)
            if (dd == 0) {
                return@Comparator p0.address.compareTo(p1.address)
            } else return@Comparator dd
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
    private fun parseAddress(address: String): AddressEntry {
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
                mc = InetSocketAddress.createUnresolved(address, 0).address.isMulticastAddress
            } catch (e: Exception) {
                // ignore
            }
            AddressEntry(address, "", mc)
        } else {
            // domain
            AddressEntry(address, "", false)
        }
    }


}