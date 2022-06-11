package org.rivchain.cuplink

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.AbsListView.CHOICE_MODE_SINGLE
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import org.rivchain.cuplink.MainService.MainBinder
import java.util.*


class AddressActivity : CupLinkActivity(), ServiceConnection {

    private lateinit var systemAddressSpinner: ListView
    private lateinit var systemAddressList: List<AddressEntry>
    private var storedAddressList: MutableList<AddressEntry> = ArrayList<AddressEntry>()
    private var systemAddressListAdapter: ArrayAdapter<AddressEntry>? = null
    private var binder: MainBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        setTitle(R.string.choose_network)
        systemAddressList = Utils.collectAddresses()
        systemAddressSpinner = findViewById(R.id.addressListView)
        systemAddressSpinner.choiceMode = CHOICE_MODE_SINGLE
        systemAddressListAdapter =
            ArrayAdapter(this, R.layout.activity_address_item, systemAddressList)
        systemAddressSpinner.adapter = systemAddressListAdapter
        systemAddressSpinner.setOnItemClickListener { adapterView, view, i, l ->
            val addresses = ArrayList<String>()
            addresses.add(systemAddressList[i].address)
            binder!!.settings.addresses = addresses
            initKeyPair(systemAddressList[i])
            binder!!.saveDatabase()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
        }
        bindService()
    }

    private fun initKeyPair(addressEntry: AddressEntry) {
        val ae = addressEntry
        // create secret/public key pair
        val publicKey = Utils.parseInetSocketAddress(ae.address, 0)!!.address.address
        //val secretKey: ByteArray? = null
        val settings = binder!!.settings
        //settings.secretKey = secretKey
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
            storedAddressList.add(parseAddress(address))
        }
        updateSpinners()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
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
        Collections.sort(systemAddressList, compareAddressEntries)
        systemAddressSpinner.adapter = systemAddressListAdapter
        systemAddressListAdapter!!.notifyDataSetChanged()

    }

    /*
     * Create AddressEntry from address string.
     * Do not perform any domain lookup
     */
    private fun parseAddress(address: String?): AddressEntry {
        // instead of parsing, lookup in known addresses first
        val ae = AddressEntry.findAddressEntry(systemAddressList, address!!)
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
                mc = Utils.parseInetSocketAddress(address, 0)!!.address.isMulticastAddress
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