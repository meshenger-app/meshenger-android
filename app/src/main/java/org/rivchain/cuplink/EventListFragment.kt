package org.rivchain.cuplink

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
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class EventListFragment : Fragment(), OnItemClickListener {
    private var mainActivity: MainActivity? = null
    private lateinit var eventListView: ListView
    private var eventListAdapter: EventListAdapter? = null
    private lateinit var fabDelete: FloatingActionButton
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_event_list, container, false)
        mainActivity = activity as MainActivity?
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener {
            mainActivity!!.binder!!.clearEvents()
            refreshEventList()
        }
        eventListAdapter =
            EventListAdapter(mainActivity!!, R.layout.item_event, emptyList(), emptyList())
        eventListView.adapter = eventListAdapter
        eventListView.onItemClickListener = this
        return view
    }

    private fun refreshEventList() {
        log("refreshEventList")
        if (mainActivity == null || mainActivity!!.binder == null) {
            log("refreshEventList early return")
            return
        }
        val events = mainActivity!!.binder!!.eventsCopy
        val contacts = mainActivity!!.binder!!.contactsCopy
        Handler(Looper.getMainLooper()).post {
            log("refreshEventList update: " + events.size)
            eventListAdapter!!.update(events, contacts)
            eventListAdapter!!.notifyDataSetChanged()
            eventListView.adapter = eventListAdapter
            eventListView.onItemLongClickListener =
                OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                    val event = events[i]
                    val menu = PopupMenu(mainActivity!!, requireView())
                    val res = resources
                    val add = res.getString(R.string.add)
                    val block = res.getString(R.string.block)
                    val unblock = res.getString(R.string.unblock)
                    val qr = "QR-ify"
                    val contact = mainActivity!!.binder!!.getContactByIp(event.address.hostAddress)

                    // allow to add unknown caller
                    if (contact == null) {
                        menu.menu.add(add)
                    }

                    // we can only block/unblock contacts
                    // (or we need to need maintain a separate bocklist)
                    if (contact != null) {
                        if (contact.getBlocked()) {
                            menu.menu.add(unblock)
                        } else {
                            menu.menu.add(block)
                        }
                    }
                    menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val title = menuItem.title.toString()
                        when (title) {
                            add -> {
                                showAddDialog(event)
                            }
                            block -> {
                                setBlocked(event, true)
                            }
                            unblock -> {
                                setBlocked(event, false)
                            }
                        }
                        false
                    }
                    menu.show()
                    true
                }
        }
    }

    private fun setBlocked(event: CallEvent, blocked: Boolean) {
        val contact = mainActivity!!.binder!!.getContactByIp(event.address.hostAddress)
        if (contact != null) {
            contact.setBlocked(blocked)
            mainActivity!!.binder!!.saveDatabase()
        } else {
            // unknown contact
        }
    }

    private fun showAddDialog(event: CallEvent) {
        log("showAddDialog")
        val dialog = Dialog(mainActivity!!)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.nameEditText)
        val exitButton = dialog.findViewById<Button>(R.id.ExitButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        okButton.setOnClickListener { v: View? ->
            val name = nameEditText.text.toString()
            if (name.isEmpty()) {
                Toast.makeText(mainActivity, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mainActivity!!.binder!!.getContactByName(name) != null) {
                Toast.makeText(mainActivity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val address = Utils.getGeneralizedAddress(event.address)
            mainActivity!!.binder!!.addContact(
                Contact(name, listOf(address))
            )
            Toast.makeText(mainActivity, R.string.done, Toast.LENGTH_SHORT).show()
            refreshEventList()

            // close dialog
            dialog.dismiss()
        }
        exitButton.setOnClickListener { v: View? -> dialog.dismiss() }
        dialog.show()
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        log("onItemClick")
        val event = eventListAdapter!!.getItem(i)
        val address = Utils.getGeneralizedAddress(event.address)
        val contact = Contact("", listOf(address))
        contact.setLastWorkingAddress(
            Utils.parseInetSocketAddress(
                address,
                MainService.serverPort
            )!!
        )
        val intent = Intent(mainActivity, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }

    fun onServiceConnected() {
        refreshEventList()
    }

    companion object {
        private fun log(s: String) {
            Log.d(EventListFragment::class.java.simpleName, s)
        }
    }
}