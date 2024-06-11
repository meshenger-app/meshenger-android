package org.rivchain.cuplink

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import org.rivchain.cuplink.MainService.MainBinder
import org.rivchain.cuplink.model.AddressEntry
import org.rivchain.cuplink.util.AddressUtils
import org.rivchain.cuplink.util.AddressUtils.AddressType
import java.util.Locale

class AddressManagementActivity : BaseActivity(), ServiceConnection {
    private var service: MainService? = null
    private lateinit var addressListView: ListView
    private lateinit var customAddressTextEdit: TextInputEditText
    private lateinit var addressListViewAdapter: AddressListAdapter
    private var systemAddresses = mutableListOf<AddressEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_management)
        setTitle(R.string.address_management)

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

        addressListViewAdapter = AddressListAdapter(this)
        addressListView = findViewById(R.id.AddressListView)

        addressListView.adapter = addressListViewAdapter
        addressListView.setOnItemClickListener { _, _, i, _ ->
            addressListViewAdapter.toggle(i)
        }

        customAddressTextEdit = findViewById(R.id.CustomAddressEditText)
        systemAddresses = AddressUtils.collectAddresses().toMutableList()

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    private fun initViews() {
        if (service == null) {
            return
        }

        val saveButton = findViewById<Button>(R.id.save_button)
        val resetButton = findViewById<Button>(R.id.reset_button)
        val addButton = findViewById<View>(R.id.AddCustomAddressButton)

        saveButton.setOnClickListener {
            service!!.getSettings().addresses = addressListViewAdapter.storedAddresses.map { it.address }.toMutableList()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            service!!.saveDatabase()
        }

        addButton.setOnClickListener {
            var address = AddressUtils.stripInterface(customAddressTextEdit.text!!.toString())
            address = if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                address.lowercase(Locale.ROOT)
            } else if (AddressUtils.isMACAddress(address)) {
                address.uppercase(Locale.ROOT)
            } else {
                Toast.makeText(this, R.string.error_address_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // multicast addresses are not supported yet
            if (AddressUtils.getAddressType(address) in listOf(AddressType.MULTICAST_MAC, AddressType.MULTICAST_IP)) {
                Toast.makeText(this, R.string.error_address_multicast_not_supported, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ae = AddressEntry(address, "")

            if (ae in addressListViewAdapter.allAddresses) {
                Toast.makeText(applicationContext, R.string.error_address_exists, Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            addressListViewAdapter.allAddresses.add(ae)
            addressListViewAdapter.storedAddresses.add(ae)

            addressListViewAdapter.notifyDataSetChanged()

            customAddressTextEdit.setText("")
        }

        resetButton.setOnClickListener {
            initAddressList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        service = (iBinder as MainBinder).getService()

        // add extra information to stored addresses from system addresses
        val addresses = mutableListOf<AddressEntry>()
        for (address in service!!.getSettings().addresses) {
            val ae = systemAddresses.firstOrNull { it.address == address }
            if (ae != null) {
                addresses.add(AddressEntry(address, ae.device))
            } else {
                addresses.add(AddressEntry(address, ""))
            }
        }

        initAddressList()
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    inner class AddressListAdapter(private val context: Activity): BaseAdapter() {
        // hack, we want android:textColorPrimary
        private val defaultColor = Color.parseColor(if (isNightmodeEnabled(context)) "#EC3E3E" else "#000000")
        private val markColor = Color.parseColor("#39b300")
        var allAddresses = mutableListOf<AddressEntry>()
        private var systemAddresses = mutableListOf<AddressEntry>()
        var storedAddresses = mutableListOf<AddressEntry>()

        override fun isEmpty(): Boolean {
            return allAddresses.isEmpty()
        }

        fun init(systemAddresses: List<AddressEntry>, storedAddresses: List<AddressEntry>) {
            this.allAddresses = (storedAddresses + systemAddresses).distinct().toMutableList()
            this.systemAddresses = ArrayList(systemAddresses)
            this.storedAddresses = ArrayList(storedAddresses)
        }

        override fun getCount(): Int {
            return if (isEmpty) {
                1
            } else allAddresses.size
        }

        override fun getItem(position: Int): AddressEntry? {
            return if (isEmpty) {
                null
            } else allAddresses[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = convertView ?: context.layoutInflater.inflate(
                    R.layout.item_address, parent, false)
            val label = item.findViewById<TextView>(R.id.label)
            val icon = item.findViewById<ImageView>(R.id.icon)
            if (isEmpty) {
                // no addresses
                label.text = getString(R.string.empty_list_item)
                label.textAlignment = View.TEXT_ALIGNMENT_CENTER
                label.setTextColor(defaultColor)
                icon.isVisible = false
            } else {
                val ae = allAddresses[position]

                val isCustom = ae !in this.systemAddresses
                icon.isVisible = isCustom
                if (isCustom) {
                    icon.setOnClickListener {
                        storedAddresses.remove(ae)
                        if (ae !in systemAddresses) {
                            allAddresses.remove(ae)
                        }
                        notifyDataSetChanged()
                    }
                } else {
                    icon.setOnClickListener(null)
                }

                val info = ArrayList<String>()

                // add device name in brackets
                if (ae.device.isNotEmpty() && !ae.address.endsWith("%${ae.device}")) {
                    info.add(ae.device)
                }

                when (AddressUtils.getAddressType(ae.address)) {
                    AddressType.GLOBAL_MAC -> info.add("<hardware>")
                    AddressType.MULTICAST_MAC,
                    AddressType.MULTICAST_IP -> info.add("<multicast>")
                    else -> {}
                }

                label.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                if (info.isNotEmpty()) {
                    label.text = "${ae.address} (${info.joinToString()})"
                } else {
                    label.text = "${ae.address}"
                }

                if (ae in storedAddresses) {
                    label.setTextColor(markColor)
                } else {
                    label.setTextColor(defaultColor)
                }
            }

            return item
        }

        fun toggle(pos: Int) {
            if (pos > -1 && pos < allAddresses.count()) {
                val entry = allAddresses[pos]

                if (entry in storedAddresses) {
                    storedAddresses.remove(entry)
                } else {
                    storedAddresses.add(entry)
                }

                if (entry !in systemAddresses) {
                    allAddresses.remove(entry)
                }

                notifyDataSetChanged()
            }
        }
    }

    private fun initAddressList() {
        // add extra information to stored addresses
        val storedAddresses = mutableListOf<AddressEntry>()
        for (address in service!!.getSettings().addresses) {
            val ae = systemAddresses.firstOrNull { it.address == address }
            if (ae != null) {
                storedAddresses.add(AddressEntry(address, ae.device))
            } else {
                storedAddresses.add(AddressEntry(address, ""))
            }
        }

        addressListViewAdapter.init(systemAddresses, storedAddresses)
        addressListViewAdapter.notifyDataSetChanged()
        addressListView.adapter = addressListViewAdapter
    }
}
