package d.d.meshenger.fragment

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.utils.AddressUtils.getEUI64MAC
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Log.w
import d.d.meshenger.service.MainService
import d.d.meshenger.R
import d.d.meshenger.utils.Utils.bytesToMacAddress
import d.d.meshenger.adapter.EventListAdapter
import d.d.meshenger.call.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.model.Contact
import d.d.meshenger.model.Event
import java.net.Inet6Address
import java.net.InetSocketAddress
import kotlin.collections.ArrayList

class EventListFragment: Fragment(), AdapterView.OnItemClickListener{

    companion object {
        private const val TAG = "EventListFragment"


        /*
     * When adding an unknown contact, try to
     * extract a MAC address from the IPv6 address.
     */
        private fun getGeneralizedAddress(address: String): String {
            val addr = InetSocketAddress.createUnresolved(address, 0).address
            if (addr is Inet6Address) {
                // if the IPv6 address contains a MAC address, take that.
                val mac = getEUI64MAC(addr)
                if (mac != null) {
                    return bytesToMacAddress(mac)
                }
            }
            return address
        }

    }

    private var eventListView: ListView? = null
    private var eventListAdapter: EventListAdapter? = null
    private lateinit var fabDelete: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_event_list, container, false);

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener { v: View? ->
            MainService.instance!!.getEvents()!!.clear()
            refreshEventList()
        }

        eventListAdapter =
            EventListAdapter(activity!!, R.layout.item_event, ArrayList<Event>(), ArrayList<Contact>())

        eventListView?.apply {
            adapter = eventListAdapter
            onItemClickListener = this@EventListFragment

            refreshEventList()
        }
    }


    fun refreshEventList() {
        d(TAG, "refreshEventList")
        Handler(Looper.getMainLooper()).post {
            val events =
                MainService.instance!!.getEvents()!!
                    .getEventListCopy()
            val contacts =
                MainService.instance!!.getContacts()!!.getContactListCopy()
            d(
                TAG,
                "refreshEventList update: " + events!!.size
            )
            eventListAdapter?.let {
                it.update(events, contacts)
                it.notifyDataSetChanged()
                eventListView?.adapter = it
            }

            eventListView?.onItemLongClickListener =
                OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                    val event = events[i]
                    val menu =
                        PopupMenu(this@EventListFragment.activity, view)
                    val res = resources
                    val add = res.getString(R.string.add)
                    val block = res.getString(R.string.block)
                    val unblock = res.getString(R.string.unblock)
                    val contact =
                        MainService.instance!!.getContacts()!!
                            .getContactByPublicKey(event!!.publicKey)

                    // allow to add unknown caller
                    if (contact == null) {
                        menu.menu.add(add)
                    }

                    // we can only block/unblock contacts
                    // (or we need to need maintain a separate bocklist)
                    if (contact != null) {
                        if (contact.blocked) {
                            menu.menu.add(unblock)
                        } else {
                            menu.menu.add(block)
                        }
                    }
                    menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val title = menuItem.title.toString()
                        if (title == add) {
                            showAddDialog(event)
                        } else if (title == block) {
                            setBlocked(event, true)
                        } else if (title == unblock) {
                            setBlocked(event, false)
                        }
                        false
                    }
                    menu.show()
                    true
                }
        }
    }

    private fun setBlocked(event: Event?, blocked: Boolean) {
        val contact =
            MainService.instance!!.getContacts()!!.getContactByPublicKey(event!!.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            MainService.instance!!.saveDatabase()
        } else {
            w(TAG, "Cannot block: no contact found for public key")
        }
    }

    private fun showAddDialog(event: Event?) {
        d(TAG, "showAddDialog")
        val activity: Activity? = activity
        val dialog = Dialog(activity!!)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener { v: View? ->
            val name = nameEditText.text.toString()
            val addresses= ArrayList<String> ()
            if (event!!.publicKey == null) {
                Toast.makeText(activity, "Public key not set.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (MainService.instance!!.getContacts()!!.getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (event.address != null) {
                val address =
                    getGeneralizedAddress(event.address)
                addresses.add(address)
            }
            MainService.instance!!.getContacts()!!.addContact(
                Contact(name, event.publicKey, addresses, false)
            )
            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
            refreshEventList()

            // close dialog
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { v: View? -> dialog.dismiss() }
        dialog.show()
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
        d(TAG, "onItemClick")
        val event = eventListAdapter?.getItem(i)
        if (event!!.address == null) {
            Toast.makeText(this.context, "No address set for call!", Toast.LENGTH_SHORT).show()
            return
        }
        var contact = MainService.instance!!.getContacts()!!.getContactByPublicKey(event.publicKey)
        if (contact == null) {
            val address = getGeneralizedAddress(event.address)
            contact = Contact("", event.publicKey, arrayListOf(address), false)
            contact.addAddress(address!!)
        }
        if (DirectRTCClient.createOutgoingCall(contact)) {
            val intent = Intent(context, CallActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }


}