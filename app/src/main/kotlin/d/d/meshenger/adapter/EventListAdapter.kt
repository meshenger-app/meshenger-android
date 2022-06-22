package d.d.meshenger.adapter

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import d.d.meshenger.R
import d.d.meshenger.model.Event.CallType
import d.d.meshenger.utils.Utils.getUnknownCallerName
import d.d.meshenger.call.DirectRTCClient.CallDirection
import d.d.meshenger.model.Contact
import d.d.meshenger.model.Event
import java.text.SimpleDateFormat
import java.util.*

class EventListAdapter (val mContext: Context, resource: Int,
                        var events: ArrayList<Event>,
                        var contacts: ArrayList<Contact>): ArrayAdapter<Event>(mContext, resource, events){

    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater


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

    override fun getCount(): Int {
        return events.size
    }

    override fun getItem(position: Int): Event? {
        return events[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        // show list in reverse, latest element first
        var view = view
        val event = events[events.size - position - 1]
        if (view == null) {
            view = inflater.inflate(R.layout.item_event, null)
        }

        // find name
        var name = ""
        for (contact in contacts) {
            if (Arrays.equals(contact.publicKey, event.publicKey)) {
                contact.name = name
                break
            }
        }
        val name_tv = view!!.findViewById<TextView>(R.id.call_name)
        if (name.isEmpty()) {
            val unknown_caller = getUnknownCallerName(context, event.publicKey)
            name_tv.text = unknown_caller
        } else {
            name_tv.text = name
        }
        val date_tv = view.findViewById<TextView>(R.id.call_date)
        if (DateUtils.isToday(event.date.time)) {
            val ft = SimpleDateFormat("'Today at' hh:mm:ss")
            date_tv.text = ft.format(event.date)
        } else {
            val ft = SimpleDateFormat("yyyy.MM.dd 'at' hh:mm")
            date_tv.text = ft.format(event.date)
        }
        val type_iv = view.findViewById<ImageView>(R.id.call_type)
        if (event.callDirection === CallDirection.INCOMING) {
            when (event.callType) {
                CallType.ACCEPTED -> type_iv.setImageResource(R.drawable.ic_incoming_call_accepted)
                CallType.DECLINED -> type_iv.setImageResource(R.drawable.ic_incoming_call_declined)
                CallType.MISSED -> type_iv.setImageResource(R.drawable.ic_incoming_call_missed)
                CallType.ERROR -> type_iv.setImageResource(R.drawable.ic_incoming_call_error)
                else -> CallType.UNKNOWN
            }
        }
        if (event.callDirection === CallDirection.OUTGOING) {
            when (event.callType) {
                CallType.ACCEPTED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_accepted)
                CallType.DECLINED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_declined)
                CallType.MISSED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_missed)
                CallType.ERROR -> type_iv.setImageResource(R.drawable.ic_outgoing_call_error)
                else -> CallType.UNKNOWN
            }
        }
        val address_tv = view.findViewById<TextView>(R.id.call_address)
        if (event.address != null) {
            address_tv.text = "(" + formatAddress(event.address) + ")"
        } else {
            address_tv.text = ""
        }
        return view
    }

}