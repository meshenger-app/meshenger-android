package d.d.meshenger

import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class EventListFragment : Fragment() {
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var eventListView: ListView
    private lateinit var fabClear: FloatingActionButton

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

    private val onEventLongClickListener = AdapterView.OnItemLongClickListener { _, view, i, _ ->
        Log.d(this, "onItemLongClick")
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return@OnItemLongClickListener false

        val eventGroup = eventListAdapter.getItem(i)
        val latestEvent = eventGroup.last()
        val menu = PopupMenu(activity, view)
        val contact = binder.getContacts().getContactByPublicKey(latestEvent.publicKey)
        val titles = mutableListOf<Int>()

        // allow to add unknown caller
        if (contact == null) {
            titles.add(R.string.contact_menu_add)
        }

        if (contact != null) {
            if (contact.blocked) {
                titles.add(R.string.contact_menu_unblock)
            } else {
                titles.add(R.string.contact_menu_block)
            }
        }

        titles.add(R.string.contact_menu_delete)

        for (title in titles) {
            menu.menu.add(0, title, 0, title)
        }

        menu.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.string.contact_menu_add -> {
                    showAddDialog(eventGroup)
                }
                R.string.contact_menu_block -> {
                    setBlocked(latestEvent, true)
                }
                R.string.contact_menu_unblock -> {
                    setBlocked(latestEvent, false)
                }
                R.string.contact_menu_delete -> {
                    deleteEventGroup(eventGroup)
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
        fabClear = view.findViewById(R.id.fabClear)

        val activity = requireActivity()

        fabClear.setOnClickListener {
            Log.d(this, "fabClear")
            showClearEventsDialog()
        }

        eventListAdapter = EventListAdapter(activity, R.layout.item_event, emptyList(), emptyList())
        eventListView.adapter = eventListAdapter
        eventListView.onItemClickListener = onEventClickListener

        val binder = (activity as MainActivity).binder!!
        if (!binder.getSettings().hideMenus) {
            eventListView.onItemLongClickListener = onEventLongClickListener
        }

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

        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return
        val events = binder.getEvents().eventList
        val contacts = binder.getContacts().contactList

        activity.runOnUiThread {
            activity.updateEventTabTitle()

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

    override fun onResume() {
        Log.d(this, "onResume()")
        super.onResume()

        val activity = requireActivity() as MainActivity
        activity.binder?. let {
            it.getEvents().eventsMissed = 0
            it.updateNotification();
        }

        MainService.refreshEvents(requireActivity())
    }

    private fun deleteEventGroup(eventGroup: List<Event>) {
        Log.d(this, "removeEventGroup()")
        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return

        binder.deleteEvents(eventGroup.map { it.date })
    }

    private fun showClearEventsDialog() {
        Log.d(this, "showClearEventsDialog()")

        val activity = requireActivity() as MainActivity
        val binder = activity.binder ?: return
        val builder = AlertDialog.Builder(activity, R.style.AlertDialogTheme)
        builder.setTitle(R.string.clear_events)
        builder.setMessage(R.string.remove_all_events)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.button_yes) { dialog: DialogInterface, _: Int ->
            binder.clearEvents()
            binder.saveDatabase()

            refreshEventList()
            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
            dialog.cancel()
        }

        builder.setNegativeButton(R.string.button_no) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
        }

        // create dialog box
        val alert = builder.create()
        alert.show()
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
