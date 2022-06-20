package d.d.meshenger

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import d.d.meshenger.Utils.join
import java.net.InetSocketAddress
import java.util.*
import java.util.ArrayList


class AddressActivity: MeshengerActivity() {

   companion object {
    val TAG = "AddressActivity"
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

    private lateinit var systemAddressList: List<AddressEntry>
    private lateinit var storedAddressList: List<AddressEntry>
    private lateinit var storedAddressListAdapter: AddressListAdapter
    private lateinit var systemAddressListAdapter: AddressListAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_address)
        title = resources.getString(R.string.address_management)

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
        storedAddressList = ArrayList<AddressEntry>()
        storedAddressListAdapter = AddressListAdapter(this, Color.parseColor("#39b300")) //dark green
        systemAddressListAdapter = AddressListAdapter(this, Color.parseColor("#b3b7b2")) //light grey

        storedAddressSpinner.adapter = storedAddressListAdapter
        systemAddressSpinner.adapter = systemAddressListAdapter

        addressEditText.addTextChangedListener(object: TextWatcher {

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

        addButton.setOnClickListener {
            val address = addressEditText.text.toString()
            if (address.isEmpty()) {
                return@setOnClickListener
            }

            val entry = parseAddress(address)

            if (entry.multicast) {
                Toast.makeText(this, "Multicast addresses are not supported.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if ((Utils.isMAC(address) || Utils.isIP(address)) && !systemAddressList.contains(entry)) {
                Toast.makeText(this, "You can only choose a MAC/IP address that is used by the system.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            (storedAddressList as ArrayList<AddressEntry>).add(entry)
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
                (storedAddressList as ArrayList<AddressEntry>).removeAt(idx)
                updateSpinners()
            }
        }

        pickSystemAddressButton.setOnClickListener {
            val pos = systemAddressSpinner.selectedItemPosition
            if (pos > -1 && !systemAddressListAdapter.isEmpty) {
                addressEditText.setText(systemAddressList[pos].address)
            }
        }

        pickStoredAddressButton.setOnClickListener {
            val pos = storedAddressSpinner.selectedItemPosition
            if (pos > -1 && !storedAddressListAdapter.isEmpty) {
                addressEditText.setText(storedAddressList[pos].address)
            }
        }

        saveButton.setOnClickListener {
            val addresses = ArrayList<String>()
            for (ae in this.storedAddressList) {
                addresses.add(ae.address)
            }

            MainService.instance?.getSettings()?.addresses = addresses
            MainService.instance?.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }

        abortButton.setOnClickListener {
            finish()
        }

        // get from settings
        for (address in MainService.instance?.getSettings()?.addresses!!) {
            (this.storedAddressList as ArrayList<AddressEntry>).add(parseAddress(address))
        }

        updateSpinners()
    }

    inner class AddressListAdapter(context: Activity , markColor: Int ): BaseAdapter() {
        private var context: Activity
        private var markColor = 0
        private var addressEntries: List<AddressEntry>
        private var addressEntriesMarked: List<AddressEntry>

        init {
            this.context = context
            this.markColor = markColor
            this.addressEntries = ArrayList<AddressEntry>()
            this.addressEntriesMarked = ArrayList<AddressEntry>()
        }

        override fun isEmpty(): Boolean = this.addressEntries.isEmpty()

        fun update(addressEntries: List<AddressEntry>, addressEntriesMarked: List<AddressEntry>) {
            this.addressEntries = addressEntries
            this.addressEntriesMarked = addressEntriesMarked
        }


        override fun getCount()  =
            if (isEmpty) {
                1
            }
            else addressEntries.size

        override fun getItem(position: Int): Any? =
            if (isEmpty) {
                 null
            } else addressEntries[position]


        override fun getItemId(position: Int): Long = 0

        override fun getView(position: Int, view: View, parent: ViewGroup?): View { //TODO: Nullable View

            val label = view.findViewById(R.id.label) as TextView

            if (isEmpty) {
                label.text = resources.getString(R.string.empty_list_item)
                label.setTextColor(Color.BLACK)
            } else {
                val ae = this.addressEntries[position]

                val info = ArrayList<String>()

                if (ae.device.isNotEmpty()) {
                    info.add(ae.device)
                }

                if (ae.multicast) {
                    info.add("multicast")
                }
                label.text = ae.address + (if (info.isEmpty()) "" else " (" + join(info) + ")") //TODO: Placeholder xml resources

                if (AddressEntry.listIndexOf(addressEntriesMarked, ae) < 0) {
                    label.setTextColor(Color.BLACK)
                } else {
                    label.setTextColor(this.markColor)
                }
            }

            return view
        }
    }

    private fun updateAddressEditTextButtons() {
        val address = addressEditText.text.toString()

        if (AddressEntry.findAddressEntry(storedAddressList, address) != null) {
            addButton.isEnabled = false
            removeButton.isEnabled = true
        } else {
            removeButton.isEnabled = false
            addButton.isEnabled = (Utils.isMAC(address) || Utils.isDomain(address) || Utils.isIP(address))
        }
    }

    private fun updateSpinners() {
        // compare by device first, address second
        val compareAddressEntries  = Comparator<AddressEntry> { o1, o2 -> //TODO: Validate Comparator function
            val dd = o1?.device?.compareTo(o2?.device!!)
            if (dd == 0) {
                o1.address.compareTo(o2?.address!!)
            } else 0
        }

        Collections.sort(storedAddressList, compareAddressEntries)
        Collections.sort(systemAddressList, compareAddressEntries)

        storedAddressListAdapter.apply{
            this.update(storedAddressList, systemAddressList)
            this.notifyDataSetChanged()

        }
        systemAddressListAdapter.apply{
            this.update(systemAddressList, storedAddressList)
            this.notifyDataSetChanged()

        }

        systemAddressSpinner.apply{
            this.adapter = storedAddressListAdapter
            this.adapter = systemAddressListAdapter
        }

        pickStoredAddressButton.isEnabled = !storedAddressListAdapter.isEmpty
        pickSystemAddressButton.isEnabled = !systemAddressListAdapter.isEmpty

        updateAddressEditTextButtons()
    }

    /*
     * Create AddressEntry from address string.
     * Do not perform any domain lookup
    */
    private fun parseAddress(address: String ): AddressEntry {
        // instead of parsing, lookup in known addresses first
        val ae = AddressEntry.findAddressEntry(systemAddressList, address)
        if (ae != null) {
            // known address
            return AddressEntry(address, ae.device, ae.multicast)
        } else if (Utils.isMAC(address)) {
            // MAC address
            val mc = Utils.macAddressToBytes(address)?.let { Utils.isMulticastMAC(it) }
            return AddressEntry(address, "", mc!!)
        } else if (Utils.isIP(address)) {
            // IP address
            var mc = false
            try {
                mc = InetSocketAddress.createUnresolved(address, 0).address.isMulticastAddress
            } catch (e: Exception) {
                // ignore
            }
            return AddressEntry(address, "", mc)
        } else {
            // domain
            return AddressEntry(address, "", false)
        }
    }
}
