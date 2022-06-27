package d.d.meshenger.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintSet
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.*
import d.d.meshenger.adapter.AddressListAdapter
import d.d.meshenger.adapter.AddressListAdapterFix
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
    private lateinit var removeButton: ImageButton
    private lateinit var saveButton: Button
    private lateinit var abortButton: Button

    private var systemAddressList = ArrayList<AddressEntry>()
    private var storedAddressList = ArrayList<AddressEntry>()
    private lateinit var storedAddressListAdapter: AddressListAdapterFix
    private lateinit var systemAddressListAdapter: AddressListAdapterFix

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        initToolbar()
       initViews()
    }

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

    private fun initSpinners() {
        storedAddressListAdapter =
            AddressListAdapterFix(this,
                R.layout.activity_address_item,
                R.id.label,
                Color.parseColor("#39b300"), storedAddressList) //dark green
        systemAddressListAdapter =
            AddressListAdapterFix(this,
                R.layout.activity_address_item,
                R.id.label,
                Color.parseColor("#39b300"), systemAddressList) //dark green
        storedAddressSpinner.adapter = storedAddressListAdapter

        systemAddressSpinner.adapter = systemAddressListAdapter

//        val storedAddressArrayAdapter: ArrayAdapter<AddressEntry> = ArrayAdapter(this, android.R.layout.simple_spinner_item, storedAddressList)
//        storedAddressArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
//        storedAddressSpinner.adapter = storedAddressArrayAdapter
//
//        val systemAddressArrayAdapter: ArrayAdapter<AddressEntry> = ArrayAdapter(this, android.R.layout.simple_spinner_item, storedAddressList)
//        systemAddressArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item)
//        systemAddressSpinner.adapter = systemAddressArrayAdapter

    }

    private fun initViews() {
        storedAddressSpinner = findViewById(R.id.StoredAddressSpinner)
        systemAddressSpinner = findViewById(R.id.SystemAddressSpinner)
        pickStoredAddressButton = findViewById(R.id.PickStoredAddressButton)
        pickSystemAddressButton = findViewById(R.id.PickSystemAddressButton)
        addressEditText = findViewById(R.id.AddressEditText)
        addButton = findViewById(R.id.AddButton)
        removeButton = findViewById(R.id.RemoveButton)
        saveButton = findViewById(R.id.SaveButton)
        abortButton = findViewById(R.id.AbortButton)
        PushDownAnim.setPushDownAnimTo(addButton, removeButton, saveButton, abortButton,
            pickStoredAddressButton, pickSystemAddressButton)

        systemAddressList = AddressUtils.getOwnAddresses()
        storedAddressList = ArrayList()
        initSpinners()


        //val x = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, storedAddressList)
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
            MainService.instance!!.getSettings()!!.addresses = addresses
            MainService.instance!!.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        abortButton.setOnClickListener { v: View? -> finish() }

        // get from settings
        for (address in MainService.instance!!.getSettings()!!.addresses) {
            storedAddressList.add(parseAddress(address))
        }
        //updateSpinners()

    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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