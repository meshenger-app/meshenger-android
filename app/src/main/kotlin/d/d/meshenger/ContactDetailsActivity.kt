package d.d.meshenger

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.switchmaterial.SwitchMaterial
import d.d.meshenger.MainService.MainBinder
import d.d.meshenger.AddressUtils.AddressType
import org.libsodium.jni.Sodium
import java.util.*
import kotlin.collections.ArrayList

class ContactDetailsActivity : BaseActivity(), ServiceConnection {
    private lateinit var publicKey: ByteArray

    private lateinit var contactNameEdit: TextView
    private lateinit var contactPublicKeyEdit: TextView
    private lateinit var contactBlockedSwitch : SwitchMaterial
    private lateinit var addressEditText: EditText

    private lateinit var addressListView: ListView
    private lateinit var addressListViewAdapter: AddressListAdapter

    private var binder: MainBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_details)
        setTitle(R.string.title_contact_details)

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
        addressEditText = findViewById(R.id.AddressEditText)

        contactNameEdit = findViewById(R.id.contactNameTv)
        contactPublicKeyEdit = findViewById(R.id.contactPublicKeyTv)
        contactBlockedSwitch = findViewById(R.id.contactBlockedSwitch)

        addressListViewAdapter = AddressListAdapter(this)
        addressListView.adapter = addressListViewAdapter

        bindService(Intent(this, MainService::class.java), this, 0)
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        try {
            val mainBinder = iBinder as MainBinder
            val publicKey = intent.extras!!["EXTRA_CONTACT_PUBLICKEY"] as ByteArray
            val contact = mainBinder.getContacts().getContactByPublicKey(publicKey)!!
            binder = mainBinder
            updateContact(contact)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        // nothing to do
    }

    private fun updateContact(newContact: Contact) {
        Log.d(this, "updateContact")

        publicKey = newContact.publicKey

        contactNameEdit.text = newContact.name
        findViewById<View>(R.id.contactNameLayout).setOnClickListener { showChangeNameDialog() }

        contactPublicKeyEdit.text = Utils.byteArrayToHexString(newContact.publicKey)
        findViewById<View>(R.id.contactPublicKeyLayout).setOnClickListener { showChangePublicKeyDialog() }

        contactBlockedSwitch.isChecked = newContact.blocked

        addressListView.setOnItemClickListener { _, _, i, _ ->
            val address = addressListViewAdapter.getAddressAt(i)
            if (address != null) {
                addressEditText.setText(address)
            }
        }

        addressListViewAdapter.setAddresses(newContact.addresses)

        addressEditText.setText("")

        findViewById<Button>(R.id.AddAddressButton).setOnClickListener {
            var address = addressEditText.text!!.toString()
            address = if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                address.lowercase(Locale.ROOT)
            } else {
                Toast.makeText(this, R.string.error_address_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // multicast addresses are not supported yet
            if (AddressUtils.getAddressType(address) in listOf(AddressType.MULTICAST_IP)) {
                Toast.makeText(this, R.string.error_address_multicast_not_supported, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (address in addressListViewAdapter.getAddresses()) {
                Toast.makeText(this, R.string.error_address_exists, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            addressListViewAdapter.addAddress(address)
        }

        findViewById<Button>(R.id.SaveButton).setOnClickListener {
            val contact = getOriginalContact()
            if (contact != null) {
                contact.name = contactNameEdit.text.toString()
                contact.blocked = contactBlockedSwitch.isChecked
                contact.addresses = addressListViewAdapter.getAddresses()
                contact.publicKey = publicKey

                binder!!.saveDatabase()
                Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.ResetButton).setOnClickListener {
            val contact = getOriginalContact()
            if (contact != null) {
                updateContact(contact)
            } else {
                Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOriginalContact(): Contact? {
        return if (publicKey.size == Sodium.crypto_sign_publickeybytes()) {
            binder!!.getContacts().getContactByPublicKey(publicKey)
        } else {
            null
        }
    }

    private fun showChangePublicKeyDialog() {
        Log.d(this, "showChangePublicKeyDialog()")

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_contact_change_public_key)
        val publicKeyInput = dialog.findViewById<EditText>(R.id.PublicKeyEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        publicKeyInput.setText(contactPublicKeyEdit.text, TextView.BufferType.EDITABLE)

        okButton.setOnClickListener {
            val newPublicKey = Utils.hexStringToByteArray(
                publicKeyInput.text.toString()
            )

            if (newPublicKey == null || (newPublicKey.size != Sodium.crypto_sign_publickeybytes())) {
                Toast.makeText(this, R.string.contact_public_key_invalid, Toast.LENGTH_SHORT).show()
            } else if (binder!!.getContacts().getContactByPublicKey(newPublicKey) != null) {
                Toast.makeText(this, R.string.contact_public_key_already_exists, Toast.LENGTH_LONG).show()
            } else {
                contactPublicKeyEdit.text = Utils.byteArrayToHexString(newPublicKey)
                dialog.dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showChangeNameDialog() {
        Log.d(this, "showChangeNameDialog()")

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_contact_change_name)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)

        nameEditText.setText(contactNameEdit.text, TextView.BufferType.EDITABLE)

        okButton.setOnClickListener {
            val newName = nameEditText.text.toString().trim { it <= ' ' }
            val existingContact = binder!!.getContacts().getContactByName(newName)

            if (!Utils.isValidName(newName)) {
                Toast.makeText(this, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
            } else if ((existingContact != null) && !existingContact.publicKey.contentEquals(publicKey)) {
                Toast.makeText(this, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
            } else {
                contactNameEdit.text = newName
                dialog.dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (binder != null) {
            unbindService(this)
        }
    }

    inner class AddressListAdapter(private val context: Activity): BaseAdapter() {
        private val addresses = mutableListOf<AddressEntry>()

        override fun isEmpty(): Boolean {
            return addresses.isEmpty()
        }

        override fun getCount(): Int {
            return if (isEmpty) {
                1
            } else addresses.size
        }

        override fun getItem(position: Int): AddressEntry? {
            return if (isEmpty) {
                null
            } else addresses[position]
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
                icon.isVisible = false
            } else {
                val ae = addresses[position]

                icon.isVisible = true
                icon.setOnClickListener {
                    addresses.remove(ae)
                    notifyDataSetChanged()
                }

                val info = ArrayList<String>()

                // add device name in brackets
                if (ae.device.isNotEmpty() && !ae.address.endsWith("%${ae.device}")) {
                    info.add(ae.device)
                }

                when (AddressUtils.getAddressType(ae.address)) {
                    //AddressType.GLOBAL_MAC -> info.add("<hardware>")
                    //AddressType.MULTICAST_MAC,
                    AddressType.MULTICAST_IP -> info.add("<multicast>")
                    else -> {}
                }

                label.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                if (info.isNotEmpty()) {
                    label.text = "${ae.address} (${info.joinToString()})"
                } else {
                    label.text = "${ae.address}"
                }
            }

            return item
        }

        fun getAddressAt(pos: Int): String? {
            if (pos > -1 && pos < addresses.count()) {
                return addresses[pos].address
            } else {
                return null
            }
        }

        fun addAddress(address: String)  {
            val ae = AddressEntry(address, "")
            if (ae !in addresses) {
                this.addresses.add(ae)
                notifyDataSetChanged()
            }
        }

        fun getAddresses(): List<String> {
            return addresses.map { it.address }
        }

        fun setAddresses(addresses: List<String>) {
            this.addresses.clear()
            addresses.map { this.addresses.add(AddressEntry(it, "")) }
            notifyDataSetChanged()
        }
    }
}
