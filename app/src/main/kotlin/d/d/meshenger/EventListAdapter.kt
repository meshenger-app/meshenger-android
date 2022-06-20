package d.d.meshenger

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import d.d.meshenger.Event.CallType
import d.d.meshenger.Utils.getUnknownCallerName
import d.d.meshenger.call.DirectRTCClient
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class EventListAdapter(context: Context, val resource: Int, var events: ArrayList<Event?>, var contacts: ArrayList<Contact?> )
    : ArrayAdapter<Event>(context, resource, events) {

    private var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater


    fun update(events: ArrayList<Event?>, contacts: ArrayList<Contact?>) {
        this.events = events
        this.contacts = contacts
    }

    private fun formatAddress(address: String?): String {
        return if (address!!.startsWith("/")) {
            address.substring(1)
        } else {
            address
        }
    }

    override fun getCount(): Int {
        return events.size
    }

    override fun getItem(position: Int): Event? = events[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        // show list in reverse, latest element first
        val event: Event? = events[events.size - position - 1]
        // find name
        var name = ""

        for (contact in contacts) {
            if (Arrays.equals(contact!!.publicKey, event!!.publicKey)) {
                name = contact.name
                break
            }
        }
        val nameTv: TextView = view?.findViewById(R.id.call_name)!!
        if (name.isEmpty()) {
            val unknownCaller = event!!.publicKey?.let { getUnknownCallerName(this.context, it) }
            nameTv.text = unknownCaller
        } else {
            nameTv.text = name
        }


        val dateTv: TextView = view.findViewById(R.id.call_date) //TODO(IODevBlue): SimpleDateFormat uses outdated constructor
        if (DateUtils.isToday(event!!.date.time)) {
            val ft = SimpleDateFormat("'Today at' hh:mm:ss")
            dateTv.text = ft.format(event.date)
        } else {
            val ft = SimpleDateFormat("yyyy.MM.dd 'at' hh:mm")
            dateTv.text = ft.format(event.date)
        }
        val typeIv: ImageView = view.findViewById(R.id.call_type)
        if (event.callDirection === DirectRTCClient.CallDirection.INCOMING) {
            when (event.callType) {
                CallType.ACCEPTED -> typeIv.setImageResource(R.drawable.ic_incoming_call_accepted)
                CallType.DECLINED -> typeIv.setImageResource(R.drawable.ic_incoming_call_declined)
                CallType.MISSED -> typeIv.setImageResource(R.drawable.ic_incoming_call_missed)
                CallType.ERROR -> typeIv.setImageResource(R.drawable.ic_incoming_call_error)
                else -> CallType.UNKNOWN
            }
        }
        if (event.callDirection === DirectRTCClient.CallDirection.OUTGOING) {
            when (event.callType) {
                CallType.ACCEPTED -> typeIv.setImageResource(R.drawable.ic_outgoing_call_accepted)
                CallType.DECLINED -> typeIv.setImageResource(R.drawable.ic_outgoing_call_declined)
                CallType.MISSED -> typeIv.setImageResource(R.drawable.ic_outgoing_call_missed)
                CallType.ERROR -> typeIv.setImageResource(R.drawable.ic_outgoing_call_error)
                else -> CallType.UNKNOWN
            }
        }
        val addressTv: TextView = view.findViewById(R.id.call_address)
        if (event.address != null) {
            addressTv.text = formatAddress(event.address)
        } else {
            addressTv.text = ""
        }
        return view
    }

}