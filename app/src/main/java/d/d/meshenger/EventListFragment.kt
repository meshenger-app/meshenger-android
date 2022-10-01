package d.d.meshenger

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventListFragment : Fragment(), OnItemClickListener {
    private var mainActivity: MainActivity? = null
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var eventListView: ListView
    private lateinit var fabDelete: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_event_list, container, false)
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener(View.OnClickListener { _: View? ->
            mainActivity!!.binder!!.clearEvents()
            Log.d(this, "fabDelete")
            refreshEventList()
        })
        eventListAdapter = EventListAdapter(mainActivity!!, R.layout.item_event, emptyList(), emptyList())
        eventListView.setAdapter(eventListAdapter)
        eventListView.setOnItemClickListener(this)

        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            mainActivity = context as MainActivity
        } catch (e: ClassCastException) {
            Log.e(this, "MainActivity expected")
            throw RuntimeException()
        }
    }

    fun refreshEventList() {
        Log.d(this, "refreshEventList")
        if (mainActivity == null) {
            return
        }

        val binder = mainActivity!!.binder ?: return
        val events = binder.getEvents().eventList
        val contacts = binder.getContacts().contactList

        Handler(Looper.getMainLooper()).post {
            Log.d(this, "refreshEventList update: ${events.size}")
            eventListAdapter.update(events, contacts)
            eventListAdapter.notifyDataSetChanged()
            eventListView.adapter = eventListAdapter
            eventListView.onItemLongClickListener =
                OnItemLongClickListener { _: AdapterView<*>?, view: View?, i: Int, _: Long ->
                    val event = events[i]
                    val menu = PopupMenu(mainActivity, view)
                    val res = resources
                    val add = res.getString(R.string.add)
                    val block = res.getString(R.string.block)
                    val unblock = res.getString(R.string.unblock)
                    val qr = "QR-ify"
                    val contact = mainActivity!!.binder!!.getContactByPublicKey(event.publicKey)

                    // allow to add unknown caller
                    if (contact == null) {
                        menu.menu.add(add)
                    }

                    // we can only block/unblock contacts
                    // (or we need to need maintain a separate blocklist)
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

    private fun setBlocked(event: Event, blocked: Boolean) {
        val contact = mainActivity!!.binder!!.getContactByPublicKey(event.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            mainActivity!!.binder!!.saveDatabase()
        } else {
            // unknown contact
        }
    }

    private fun showAddDialog(event: Event) {
        Log.d(this, "showAddDialog")
        val dialog = Dialog(mainActivity!!)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val exitButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(mainActivity!!, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mainActivity!!.binder!!.getContactByName(name) != null) {
                Toast.makeText(mainActivity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val address = Utils.getGeneralizedAddress(event.address?.address)
            mainActivity!!.binder!!.addContact(
                Contact(
                    name,
                    event.publicKey,
                    if (address != null) listOf(address) else listOf()
                )
            )

            Toast.makeText(mainActivity, R.string.done, Toast.LENGTH_SHORT).show()
            refreshEventList()

            // close dialog
            dialog.dismiss()
        }
        exitButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        Log.d(this, "onItemClick")
        val event = eventListAdapter.getItem(i)
        val address = Utils.getGeneralizedAddress(event.address?.address)
        if (address == null) {
            Toast.makeText(mainActivity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
            return
        }
        val contact = Contact("", event.publicKey, listOf(address))
        contact.lastWorkingAddress = Utils.parseInetSocketAddress(address, MainService.serverPort)!!
        val intent = Intent(mainActivity, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }
}