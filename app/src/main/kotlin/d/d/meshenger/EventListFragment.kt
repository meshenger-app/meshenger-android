package d.d.meshenger

import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper.getMainLooper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.AddressUtils.getEUI64MAC
import d.d.meshenger.Log.d
import d.d.meshenger.Log.w
import d.d.meshenger.Utils.bytesToMacAddress
import d.d.meshenger.call.CallActivity
import d.d.meshenger.call.DirectRTCClient
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*


class EventListFragment: Fragment(), AdapterView.OnItemClickListener {

    companion object {
        private const val TAG = "EventListFragment"

        /*
         * When adding an unknown contact, try to
         * extract a MAC address from the IPv6 address.
         */
        private fun getGeneralizedAddress(address: String): String? {
            val addr: InetAddress = InetSocketAddress.createUnresolved(address, 0).address
            if (addr is Inet6Address) {
                // if the IPv6 address contains a MAC address, take that.
                getEUI64MAC(addr)?.let {
                    return bytesToMacAddress(it)
                }
            }
            return address
        }
    }

    private lateinit var eventListView: ListView
    private var eventListAdapter: EventListAdapter? = null
    private lateinit var fabDelete: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_event_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener {
            MainService.instance!!.getEvents().clear()
            refreshEventList()
        }

        eventListAdapter = EventListAdapter(
            requireContext(),
            R.layout.item_event,
            ArrayList<Event?>(),
            ArrayList<Contact?>()
        )
        eventListView.adapter = eventListAdapter
        eventListView.onItemClickListener = this
        refreshEventList()
    }

    fun refreshEventList() {
        d(TAG, "refreshEventList")
        Handler(getMainLooper()).post {
            if (eventListAdapter == null) {
                return@post
            }
            val events: ArrayList<Event?> =
                MainService.instance!!.getEvents().getEventListCopy()
            val contacts = MainService.instance!!.getContacts().getContactListCopy()
            d(TAG, "refreshEventList update: ${events.size}")
            eventListAdapter!!.update(events, contacts)
            eventListAdapter!!.notifyDataSetChanged()
            eventListView.adapter = eventListAdapter
            eventListView.onItemLongClickListener =
                OnItemLongClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                    val event = events[i]
                    val menu = PopupMenu(this@EventListFragment.requireActivity(), requireView())
                    val res: Resources = resources
                    val add: String = res.getString(R.string.add)
                    val block: String = res.getString(R.string.block)
                    val unblock: String = res.getString(R.string.unblock)
                    val contact =
                        MainService.instance!!.getContacts()
                            .getContactByPublicKey(event!!.publicKey)

                    // allow to add unknown caller
                    if (contact == null) {
                        menu.menu.add(add)
                    }

                    // we can only block/unblock contacts
                    // (or we need to need maintain a separate bocklist)
                    contact?.let {
                        if (it.blocked) {
                            menu.menu.add(unblock)
                        } else {
                            menu.menu.add(block)
                        }
                    }
                    menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val title: String = menuItem.title.toString()
                        when(title) {
                            add -> showAddDialog(event)
                            block -> setBlocked(event, true)
                            unblock -> setBlocked(event, false)
                        }

                        false
                    }
                    menu.show()
                    true
                }
        }
    }

    private fun setBlocked(event: Event?, blocked: Boolean) {
        val contact = MainService.instance!!.getContacts().getContactByPublicKey(event!!.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            MainService.instance!!.saveDatabase()
        } else {
            w(TAG, "Cannot block: no contact found for public key")
        }
    }

    private fun showAddDialog(event: Event?) {
        d(TAG, "showAddDialog")
        val dialog = Dialog(requireActivity())
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText: EditText = dialog.findViewById(R.id.NameEditText)
        val cancelButton: Button = dialog.findViewById(R.id.CancelButton)
        val okButton: Button = dialog.findViewById(R.id.OkButton)
        okButton.setOnClickListener {
            val name = nameEditText.text.toString()
            val addresses = ArrayList<String>()
            if (event!!.publicKey == null) {
                Toast.makeText(activity, "Public key not set.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (MainService.instance!!.getContacts().getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            event.address?.let {
                val address = getGeneralizedAddress(it)
                addresses.add(address!!)
            }
            MainService.instance!!.getContacts().addContact(
                Contact(name, event.publicKey, addresses, false)
            )
            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
            refreshEventList()

            // close dialog
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
        d(TAG, "onItemClick")
        val event = eventListAdapter?.getItem(i)
        event?.let {
            if (it.address == null) {
                Toast.makeText(this.context, "No address set for call!", Toast.LENGTH_SHORT).show()
                return
            }
            var contact = MainService.instance!!.getContacts().getContactByPublicKey(it.publicKey)
            if (contact == null) {
                val address = getGeneralizedAddress(it.address!!)!!
                contact = Contact("", it.publicKey,
                    listOf(address) as ArrayList<String>, false)
                contact.addAddress(address)
            }
            if (DirectRTCClient.createOutgoingCall(contact)) {
                val intent = Intent(context, CallActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }

    }
}