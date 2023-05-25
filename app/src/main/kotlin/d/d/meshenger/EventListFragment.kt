package d.d.meshenger

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventListFragment : Fragment() {
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var eventListView: ListView
    private lateinit var fabDelete: FloatingActionButton

    private val onEventClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
        Log.d(this, "onItemClick")
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return@OnItemClickListener
        val eventGroup = eventListAdapter.getItem(i)
        // get last event that has an address
        val latestEvent = eventGroup.lastOrNull { it.address != null } ?: eventGroup.last()

        val knownContact = binder.getContacts().getContactByPublicKey(latestEvent.publicKey)
        val contact = if (knownContact != null) {
            knownContact
        } else {
            latestEvent.createUnknownContact("")
        }

        if (contact.addresses.isEmpty()) {
            Toast.makeText(activity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
        } else {
            // start call
            Log.d(this, "start CallActivity")
            val intent = Intent(activity, CallActivity::class.java)
            intent.action = "ACTION_OUTGOING_CALL"
            intent.putExtra("EXTRA_CONTACT", contact)
            startActivity(intent)
        }
    }

    private val onContactLongClickListener = AdapterView.OnItemLongClickListener { adapterView, view, i, _ ->
        Log.d(this, "onItemLongClick")
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return@OnItemLongClickListener false

        val eventGroup = eventListAdapter.getItem(i)
        val latestEvent = eventGroup.last()
        val menu = PopupMenu(activity, view)
        val res = resources
        val add = res.getString(R.string.add)
        val block = res.getString(R.string.block)
        val unblock = res.getString(R.string.unblock)
        val contact = binder.getContacts().getContactByPublicKey(latestEvent.publicKey)

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
            when (menuItem.title.toString()) {
                add -> {
                    showAddDialog(eventGroup)
                }
                block -> {
                    setBlocked(latestEvent, true)
                }
                unblock -> {
                    setBlocked(latestEvent, false)
                }
            }
            false
        }
        menu.show()
        true
    }

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
        eventListView.onItemClickListener = onEventClickListener
        eventListView.onItemLongClickListener = onContactLongClickListener

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshEventListReceiver, IntentFilter("refresh_event_list"))

        refreshEventListBroadcast()

        return view
    }

    private val refreshEventListReceiver = object : BroadcastReceiver() {
        //private var lastTimeRefreshed = 0L

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this@EventListFragment, "trigger refreshEventList() from broadcast at ${this@EventListFragment.lifecycle.currentState}")
            // prevent this method from being called too often
            //val now = System.currentTimeMillis()
            //if ((now - lastTimeRefreshed) > 1000) {
            //    lastTimeRefreshed = now
                refreshEventList()
            //}
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(refreshEventListReceiver)
        super.onDestroy()
    }

    private fun refreshEventList() {
        Log.d(this, "refreshEventList")

        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return
        val events = binder.getEvents().eventList
        val contacts = binder.getContacts().contactList

        activity.runOnUiThread {
            eventListAdapter.update(events, contacts)
            eventListAdapter.notifyDataSetChanged()
            eventListView.adapter = eventListAdapter
        }
    }

    private fun refreshEventListBroadcast() {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent("refresh_event_list"))
    }

    // only available for known contacts
    private fun setBlocked(event: Event, blocked: Boolean) {
        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return

        val contact = binder.getContacts().getContactByPublicKey(event.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            binder.saveDatabase()
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent("refresh_contact_list"))
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent("refresh_event_list"))
        } else {
            // ignore - not expected to happen
        }
    }

    // only available for unknown contacts
    private fun showAddDialog(eventGroup: List<Event>) {
        Log.d(this, "showAddDialog")
        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return
        // prefer latest event that has an address
        val latestEvent = eventGroup.lastOrNull { it.address != null } ?: eventGroup.last()

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val exitButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (!Utils.isValidName(name)) {
                Toast.makeText(activity, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binder.getContacts().getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val contact = latestEvent.createUnknownContact(name)
            binder.addContact(contact)

            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()

            // close dialog
            dialog.dismiss()
        }
        exitButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
