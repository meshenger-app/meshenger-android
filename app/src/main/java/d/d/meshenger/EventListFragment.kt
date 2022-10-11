package d.d.meshenger

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventListFragment : Fragment(), AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var eventListView: ListView
    private lateinit var fabDelete: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_event_list, container, false)
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)

        val activity = requireActivity()

        fabDelete.setOnClickListener {
            Log.d(this, "fabDelete")
            val binder = (activity as MainActivity).binder!!

            binder.clearEvents()
            binder.saveDatabase()

            refreshEventList()
        }

        eventListAdapter = EventListAdapter(activity, R.layout.item_event, emptyList(), emptyList())
        eventListView.adapter = eventListAdapter
        eventListView.onItemClickListener = this
        eventListView.onItemLongClickListener = this

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshEventListReceiver, IntentFilter("refresh_event_list"))

        refreshEventListBroadcast()

        return view
    }

    private val refreshEventListReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this, "trigger refreshEventList() from broadcast")
            refreshEventList()
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(refreshEventListReceiver)
        super.onDestroy()
    }

    fun refreshEventList() {
        Log.d(this, "refreshEventList")

        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return

        val events = binder.getEvents().eventList
        val contacts = binder.getContacts().contactList

        Log.d(this, "refreshEventList update: ${events.size}")

        Handler(Looper.getMainLooper()).post {
            Log.d(this, "refreshEventList looper: ${events.size}")
            eventListAdapter.update(events, contacts)
            eventListAdapter.notifyDataSetChanged()
            eventListView.adapter = eventListAdapter
        }
    }

    private fun refreshEventListBroadcast() {
        LocalBroadcastManager.getInstance(requireActivity().applicationContext).sendBroadcast(Intent("refresh_event_list"))
    }

    // only available for known contacts
    private fun setBlocked(event: Event, blocked: Boolean) {
        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return

        val contact = binder.getContacts().getContactByPublicKey(event.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            binder.saveDatabase()
        } else {
            // ignore - not expected to happen
        }
    }

    // only available for unknown contacts
    private fun showAddDialog(event: Event) {
        Log.d(this, "showAddDialog")
        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val exitButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binder.getContacts().getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val contact = event.createUnknownContact(name)
            binder.addContact(contact)

            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()

            // close dialog
            dialog.dismiss()
        }
        exitButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onItemLongClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long): Boolean {
        Log.d(this, "onItemLongClick")
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return false

        val event = adapterView.adapter.getItem(i) as Event
        val menu = PopupMenu(activity, view)
        val res = resources
        val add = res.getString(R.string.add)
        val block = res.getString(R.string.block)
        val unblock = res.getString(R.string.unblock)
        val contact = binder.getContacts().getContactByPublicKey(event.publicKey)

        // allow to add unknown caller
        if (contact == null) {
            menu.menu.add(add)
        }

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
        return true
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        Log.d(this, "onItemClick")
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return
        val event = eventListAdapter.getItem(i)

        val known_contact = binder.getContacts().getContactByPublicKey(event.publicKey)
        val contact = if (known_contact != null) {
            known_contact
        } else {
            event.createUnknownContact("")
        }

        if (contact.addresses.isEmpty()) {
            Toast.makeText(activity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
            return
        }

        // start call
        val intent = Intent(activity, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }
}