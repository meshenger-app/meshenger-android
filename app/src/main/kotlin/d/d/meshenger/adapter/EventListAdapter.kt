package d.d.meshenger.adapter

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.activity.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.model.Event.CallType
import d.d.meshenger.utils.Utils.getUnknownCallerName
import d.d.meshenger.call.DirectRTCClient.CallDirection
import d.d.meshenger.fragment.EventListFragment
import d.d.meshenger.mock.MockContacts
import d.d.meshenger.model.Contact
import d.d.meshenger.model.Event
import d.d.meshenger.service.MainService
import d.d.meshenger.utils.Log
import java.text.SimpleDateFormat
import java.util.*

class EventListAdapter (
    private val mContext: Context,
    var events: ArrayList<Event>,
    private val eventListFragment: EventListFragment,
    var contacts: ArrayList<Contact>): RecyclerView.Adapter<EventListAdapter.EventListAdapterViewHolder>() {
    //: ArrayAdapter<Event>(mContext, resource, events){

    val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val activity = mContext as AppCompatActivity

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventListAdapterViewHolder {
        val itemView = inflater.inflate(R.layout.item_event, parent, false)
        return EventListAdapterViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: EventListAdapterViewHolder, position: Int) {
            // show list in reverse, latest element first

        val event = events[events.size - position - 1]


            // find name
            val name = ""
            for (contact in contacts) {
                if (Arrays.equals(contact.publicKey, event.publicKey)) {
                    contact.name = name
                    break
                }
            }
            holder.contactNameTextView.text = if (name.isEmpty()) {
                getUnknownCallerName(mContext, event.publicKey)
            } else {
                name
            }

            holder.callDateTextView.text = if (DateUtils.isToday(event.date.time)) { //TODO: Rectify date format
                val ft = SimpleDateFormat("'Today at' hh:mm:ss")
                ft.format(event.date)
            } else {
                val ft = SimpleDateFormat("yyyy.MM.dd 'at' hh:mm")
                ft.format(event.date)
            }

        val resourceInt =
            if (event.callDirection === CallDirection.INCOMING) {
                when (event.callType) {
                CallType.ACCEPTED -> R.drawable.ic_incoming_call_accepted
                CallType.DECLINED -> R.drawable.ic_incoming_call_declined
                CallType.MISSED -> R.drawable.ic_incoming_call_missed
                CallType.ERROR -> R.drawable.ic_incoming_call_error
                else -> CallType.UNKNOWN
            }
        }
        else {
            when (event.callType) {
                CallType.ACCEPTED -> R.drawable.ic_outgoing_call_accepted
                CallType.DECLINED -> R.drawable.ic_outgoing_call_declined
                CallType.MISSED -> R.drawable.ic_outgoing_call_missed
                CallType.ERROR -> R.drawable.ic_outgoing_call_error
                else -> CallType.UNKNOWN
            }
        }
        holder.callTypeImageView.setImageResource(resourceInt as Int) //Problematic?

        holder.callAddressTextView.text = formatAddress(event.address)

        holder.callEventRoot.setOnClickListener {
            val event = getItem(position)
            var contact = MainService.instance!!.getContacts()!!.getContactByPublicKey(event.publicKey)
            if (contact == null) {
                val address = EventListFragment.getGeneralizedAddress(event.address)
                contact = Contact("", event.publicKey, arrayListOf(address), false)
                contact.addAddress(address)
            }
            if (DirectRTCClient.createOutgoingCall(contact)) {
                val intent = Intent(mContext, CallActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(intent)
            }
        }

        holder.callEventRoot.setOnLongClickListener {
            val event = events[position]
            val menu =
                PopupMenu(mContext, it)
            val res = mContext.resources
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
                when (menuItem.title.toString()) {
                    add -> showAddDialog(event)
                    block -> setBlocked(event, true)
                    unblock -> setBlocked(event, false)
                }

                false
            }
            menu.show()
            true
        }

        holder.callCloseEventImageView.setOnClickListener {
            events.removeAt(position)
            notifyItemRemoved(position)
        }

    }

    private fun setBlocked(event: Event?, blocked: Boolean) {
        val contact =
            MainService.instance!!.getContacts()!!.getContactByPublicKey(event!!.publicKey)
        if (contact != null) {
            contact.blocked = blocked
            MainService.instance!!.saveDatabase()
        } else {
            Log.w(EventListFragment.TAG, "Cannot block: no contact found for public key")
        }
    }

    private fun showAddDialog(event: Event?) {
        Log.d(EventListFragment.TAG, "showAddDialog")
        val activity = activity
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_add_contact)
        val nameEditText = dialog.findViewById<EditText>(R.id.NameEditText)
        val cancelButton = dialog.findViewById<Button>(R.id.CancelButton)
        val okButton = dialog.findViewById<Button>(R.id.OkButton)
        PushDownAnim.setPushDownAnimTo(cancelButton, okButton)
        okButton.setOnClickListener { v: View? ->
            val name = nameEditText.text.toString()
            val addresses= ArrayList<String> ()
            if (name.isEmpty()) {
                Toast.makeText(activity, R.string.contact_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (MainService.instance!!.getContacts()!!.getContactByName(name) != null) {
                Toast.makeText(activity, R.string.contact_name_exists, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val address =
                EventListFragment.getGeneralizedAddress(event!!.address)
            addresses.add(address)
            MainService.instance!!.getContacts()!!.addContact(
                Contact(name, event.publicKey, addresses, false)
            )
            Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show()
            eventListFragment.refreshEventList()

            // close dialog
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun update(events: ArrayList<Event>, contacts: ArrayList<Contact>) {
        this.events = events
        this.contacts = contacts
    }

    private fun formatAddress(address: String): String {
        return if (address.startsWith("/")) {
            address.substring(1)
        } else {
            address
        }
    }

    override fun getItemCount():Int {
        return events.size
    }

    fun getItem(position: Int): Event {
        return events[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    inner class EventListAdapterViewHolder(val itemView: View): RecyclerView.ViewHolder(itemView) {

        var contactNameTextView: TextView = itemView.findViewById<TextView>(R.id.contact_name)
        var callTypeImageView: ImageView = itemView.findViewById<ImageView>(R.id.call_type)
        var callDateTextView: TextView = itemView.findViewById<TextView>(R.id.call_date)
        var callAddressTextView: TextView = itemView.findViewById<TextView>(R.id.call_address)
        val callCloseEventImageView: ImageView = itemView.findViewById<ImageView>(R.id.call_close_event)
        val callEventRoot: RelativeLayout = itemView.findViewById(R.id.event_item_root)

        init {
            PushDownAnim.setPushDownAnimTo(callCloseEventImageView)
        }

    }

}