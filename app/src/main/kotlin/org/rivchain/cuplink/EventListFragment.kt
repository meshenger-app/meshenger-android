package org.rivchain.cuplink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.rivchain.cuplink.adapter.EventListAdapter
import org.rivchain.cuplink.model.Event
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.Utils

class EventListFragment() : Fragment() {
    private lateinit var service: MainService
    private lateinit var eventListAdapter: EventListAdapter
    private lateinit var eventListView: ListView
    private lateinit var fabClear: FloatingActionButton

    fun setService(service: MainService){
        this.service = service
    }

    private val onEventClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
        Log.d(this, "onItemClick")
        val activity = requireActivity()
        val eventGroup = eventListAdapter.getItem(i)
        // get last event that has an address
        val latestEvent = eventGroup.lastOrNull { it.address != null } ?: eventGroup.last()

        val contact = service.getContacts().getContactByPublicKey(latestEvent.publicKey)
            ?: latestEvent.createUnknownContact("")

        if (contact.addresses.isEmpty()) {
            Toast.makeText(activity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT)
                .show()
        } else {
            // start call
            Log.d(this, "start CallActivity")
            val intent = Intent(activity, CallActivity::class.java)
            intent.action = "ACTION_OUTGOING_CALL"
            intent.putExtra("EXTRA_CONTACT", contact)
            startActivity(intent)
        }
    }

    private val onEventLongClickListener = AdapterView.OnItemLongClickListener { _, _, i, _ ->
        Log.d(this, "onItemLongClick")
        val activity = requireActivity()

        val eventGroup = eventListAdapter.getItem(i)
        val latestEvent = eventGroup.last()
        val res = resources
        val add = res.getString(R.string.contact_menu_add)
        val delete = res.getString(R.string.contact_menu_delete)
        val block = res.getString(R.string.contact_menu_block)
        val unblock = res.getString(R.string.contact_menu_unblock)
        val contact = service.getContacts().getContactByPublicKey(latestEvent.publicKey)

        // Create a list of options
        val options = mutableListOf<String>()

        // Allow to add unknown caller
        if (contact == null) {
            options.add(add)
        }

        if (contact != null) {
            if (contact.blocked) {
                options.add(unblock)
            } else {
                options.add(block)
            }
        }

        options.add(delete)

        // Inflate the dialog layout
        val inflater = LayoutInflater.from(activity)
        val dialogView = inflater.inflate(R.layout.dialog_select_one_listview_item, null)
        val listViewEventOptions: ListView = dialogView.findViewById(R.id.listView)

        // Create an ArrayAdapter
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, options)
        listViewEventOptions.adapter = adapter

        // Create and show the dialog
        val dialog = AlertDialog.Builder(activity, R.style.PPTCDialog)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        listViewEventOptions.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = options[position]
            when (selectedOption) {
                add -> {
                    showAddDialog(eventGroup)
                }
                block -> {
                    setBlocked(latestEvent, true)
                }
                unblock -> {
                    setBlocked(latestEvent, false)
                }
                delete -> {
                    deleteEventGroup(eventGroup)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
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
        eventListView.onItemLongClickListener = onEventLongClickListener

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
        val events = service.getEvents().eventList
        val contacts = service.getContacts().contactList

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

        val contact = service.getContacts().getContactByPublicKey(event.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            service.saveDatabase()
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
        //service.updateNotification()
        MainService.refreshEvents(requireActivity())
    }

    private fun deleteEventGroup(eventGroup: List<Event>) {
        Log.d(this, "removeEventGroup()")
        service.deleteEvents(eventGroup.map { it.date })
    }

    private fun showClearEventsDialog() {
        Log.d(this, "showClearEventsDialog()")

        val activity = requireActivity() as MainActivity
        val builder = AlertDialog.Builder(activity, R.style.FullPPTCDialog)
        builder.setTitle(R.string.clear_events)
        builder.setMessage(R.string.remove_all_events)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.button_yes) { dialog: DialogInterface, _: Int ->
            service.clearEvents()
            service.saveDatabase()

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
        // prefer latest event that has an address
        val latestEvent = eventGroup.lastOrNull { it.address != null } ?: eventGroup.last()
        val view: View = LayoutInflater.from(activity).inflate(R.layout.dialog_add_contact, null)
        val b = AlertDialog.Builder(activity, R.style.PPTCDialog)
        val dialog = b.setView(view).create()
        val nameEditText = view.findViewById<TextInputEditText>(R.id.NameEditText)
        val exitButton = view.findViewById<Button>(R.id.CancelButton)
        val okButton = view.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener {
            val name = nameEditText.text.toString()
            if (!Utils.isValidName(name)) {
                Toast.makeText(activity, R.string.contact_name_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (service.getContacts().getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val contact = latestEvent.createUnknownContact(name)
            service.addContact(contact)

            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()

            // close dialog
            dialog.dismiss()
        }
        exitButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
