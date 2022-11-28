package d.d.meshenger

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
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import d.d.meshenger.MainService.MainBinder
import java.util.*
import kotlin.collections.ArrayList

class AddressActivity : BaseActivity(), ServiceConnection {
    private var binder: MainBinder? = null
    private lateinit var addressListView: ListView
    private lateinit var customAddressTextEdit: EditText
    private var systemAddresses = mutableListOf<AddressEntry>()
    private val addressListViewAdapter = AddressListAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
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

        addressListView = findViewById(R.id.AddressListView)

        addressListView.adapter = addressListViewAdapter
        addressListView.setOnItemClickListener { _, _, i, _ ->
            addressListViewAdapter.toggle(i)
        }

        customAddressTextEdit = findViewById(R.id.CustomAddressEditText)
        systemAddresses = AddressUtils.collectAddresses().toMutableList()

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    fun initViews() {
        if (binder == null) {
            return
        }

        val saveButton = findViewById<Button>(R.id.save_button)
        val resetButton = findViewById<Button>(R.id.reset_button)
        val addButton = findViewById<View>(R.id.AddCustomAddressButton)

        saveButton.setOnClickListener(View.OnClickListener {
            binder!!.getSettings().addresses = addressListViewAdapter.storedAddresses.map { it.address }.toMutableList()
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            binder!!.saveDatabase()
        })

        addButton.setOnClickListener {
            var address = customAddressTextEdit.text?.toString() ?: return@setOnClickListener
            if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                address = address.lowercase(Locale.ROOT)
            } else if (AddressUtils.isMACAddress(address)) {
                address = address.uppercase(Locale.ROOT)
            } else {
                Toast.makeText(this, "Please enter a valid MAC/IP address or domain name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ae = AddressEntry(address, "", false)

            if (ae in addressListViewAdapter.allAddresses) {
                Toast.makeText(applicationContext, "THIS ADDRESS ALREADY EXISTS", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            addressListViewAdapter.allAddresses.add(ae)
            addressListViewAdapter.storedAddresses.add(ae)

            addressListViewAdapter.notifyDataSetChanged()
            addressListView.adapter = addressListViewAdapter

            customAddressTextEdit.setText("")
        }

        resetButton.setOnClickListener(View.OnClickListener { initAddressList() })
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(this)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        binder = iBinder as MainBinder

        // add extra information to stored addresses from system addresses
        val addresses = mutableListOf<AddressEntry>()
        for (address in binder!!.getSettings().addresses) {
            val ae = systemAddresses.firstOrNull { it.address == address }
            if (ae != null) {
                addresses.add(AddressEntry(address, ae.device, ae.multicast))
            } else {
                addresses.add(AddressEntry(address, "", false))
            }
        }

        initAddressList()
        initViews()
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        binder = null
    }

    inner class AddressListAdapter(private val context: Activity) : BaseAdapter() {
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
            val x = convertView ?: context.layoutInflater.inflate(
                R.layout.activity_address_item,
                parent,
                false
            )
            val label = x.findViewById<TextView>(R.id.label)
            val icon = x.findViewById<ImageView>(R.id.icon)
            label.let {
                if (isEmpty) {
                    label.text = getString(R.string.empty_list_item)
                    label.setTextColor(Color.BLACK)
                    icon.isVisible = false
                } else {
                    val ae = allAddresses[position]

                    val isCustom = !(ae in this.systemAddresses)
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

                    if (ae.multicast) {
                        info.add("<multicast>")
                    }

                    if (info.isNotEmpty()) {
                        label.text = "${ae.address} (${info.joinToString()})"
                    } else {
                        label.text = "${ae.address}"
                    }

                    if (ae in storedAddresses) {
                        label.setTextColor(markColor)
                    } else {
                        label.setTextColor(Color.BLACK)
                    }
                }
            }

            return x
        }

        fun toggle(pos: Int) {
            if (pos > -1 && pos < allAddresses.count()) {
                val entry = allAddresses[pos]

                if (entry in storedAddresses) {
                    storedAddresses.remove(entry)
                } else {
                    storedAddresses.add(entry)
                }

                if (!(entry in systemAddresses)) {
                    allAddresses.remove(entry)
                }

                notifyDataSetChanged()
            }
        }
    }

    private fun initAddressList() {
        // add extra information to stored addresses
        val storedAddresses = mutableListOf<AddressEntry>()
        for (address in binder!!.getSettings().addresses) {
            val ae = systemAddresses.firstOrNull { it.address == address }
            if (ae != null) {
                storedAddresses.add(AddressEntry(address, ae.device, ae.multicast))
            } else {
                storedAddresses.add(AddressEntry(address, "", false))
            }
        }

        addressListViewAdapter.init(systemAddresses, storedAddresses)
        addressListViewAdapter.notifyDataSetChanged()
        addressListView.adapter = addressListViewAdapter
    }
}