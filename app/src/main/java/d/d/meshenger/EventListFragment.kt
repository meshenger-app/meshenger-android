package d.d.meshenger

import android.app.Dialog
import d.d.meshenger.Utils.getGeneralizedAddress
import d.d.meshenger.Utils.parseInetSocketAddress
import android.widget.AdapterView.OnItemClickListener
import d.d.meshenger.MainActivity
import d.d.meshenger.EventListAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import d.d.meshenger.R
import d.d.meshenger.EventListFragment
import d.d.meshenger.CallEvent
import d.d.meshenger.Contact
import android.os.Looper
import android.widget.AdapterView.OnItemLongClickListener
import d.d.meshenger.MainService
import android.content.Intent
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import d.d.meshenger.CallActivity
import java.util.*

class EventListFragment : Fragment(), OnItemClickListener {
    private var mainActivity: MainActivity? = null
    lateinit var eventListView: ListView
    private lateinit var eventListAdapter: EventListAdapter
    lateinit var fabDelete: FloatingActionButton
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_event_list, container, false)
        mainActivity = activity as MainActivity?
        eventListView = view.findViewById(R.id.eventList)
        fabDelete = view.findViewById(R.id.fabDelete)
        fabDelete.setOnClickListener(View.OnClickListener { v: View? ->
            mainActivity!!.binder!!.clearEvents()
            refreshEventList()
        })
        eventListAdapter =
            EventListAdapter(mainActivity!!, R.layout.item_event, emptyList(), emptyList())
        eventListView.setAdapter(eventListAdapter)
        eventListView.setOnItemClickListener(this)
        return view
    }

    fun refreshEventList() {
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
            eventListView!!.adapter = eventListAdapter
            eventListView!!.onItemLongClickListener =
                OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                    val event = events[i]
                    val menu = PopupMenu(mainActivity, view)
                    val res = resources
                    val add = res.getString(R.string.add)
                    val block = res.getString(R.string.block)
                    val unblock = res.getString(R.string.unblock)
                    val qr = "QR-ify"
                    val contact = mainActivity!!.binder!!.getContactByPublicKey(event.pubKey)

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

    private fun setBlocked(event: CallEvent, blocked: Boolean) {
        val contact = mainActivity!!.binder!!.getContactByPublicKey(event.pubKey)
        if (contact != null) {
            contact.blocked = blocked
            mainActivity!!.binder!!.saveDatabase()
        } else {
            // unknown contact
        }
    }

    private fun showAddDialog(event: CallEvent) {
        log("showAddDialog")
        val dialog = Dialog(mainActivity!!)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val exitButton = dialog.findViewById<Button>(R.id.CancelButton)
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
            val address = getGeneralizedAddress(event.address)
            mainActivity!!.binder!!.addContact(
                Contact(name, event.pubKey, Arrays.asList(address))
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
        val address = getGeneralizedAddress(event.address)
        val contact = Contact("", event.pubKey, Arrays.asList(address))
        contact.setLastWorkingAddress(parseInetSocketAddress(address, MainService.serverPort)!!)
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