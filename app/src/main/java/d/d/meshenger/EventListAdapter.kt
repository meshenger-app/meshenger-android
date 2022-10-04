package d.d.meshenger

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import d.d.meshenger.Event
import java.net.InetAddress
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.*

internal class EventListAdapter(
    private val ctx: Context,
    resource: Int,
    private var events: List<Event>,
    private var contacts: List<Contact>
) : ArrayAdapter<Event?>(
    ctx, resource, events
) {
    private val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    fun update(events: List<Event>, contacts: List<Contact>) {
        this.events = events
        this.contacts = contacts
    }

    override fun getCount(): Int {
        return events.size
    }

    override fun getItem(position: Int): Event {
        return events[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, v: View?, parent: ViewGroup): View {
        // show list in reverse, latest element first
        val view = v ?: inflater.inflate(R.layout.item_event, null)
        val event = events[events.size - position - 1]

        // find name
        val name = contacts.find { it.publicKey.contentEquals(event.publicKey) }?.name ?: ""

        val name_tv = view.findViewById<TextView>(R.id.call_name)
        if (name.isEmpty()) {
            name_tv.text = Utils.getUnknownCallerName(ctx, event.publicKey)
        } else {
            name_tv.text = name
        }

        val date_tv = view.findViewById<TextView>(R.id.call_date)
        if (DateUtils.isToday(event.date.time)) {
            val ft = SimpleDateFormat("'Today at' hh:mm:ss", Locale.ROOT)
            date_tv.text = ft.format(event.date)
        } else {
            val ft = SimpleDateFormat("yyyy.MM.dd 'at' hh:mm", Locale.ROOT)
            date_tv.text = ft.format(event.date)
        }

        val type_iv = view.findViewById<ImageView>(R.id.call_type)
        when (event.type) {
            Event.Type.UNKNOWN -> type_iv.setImageResource(R.drawable.ic_incoming_call_error)
            Event.Type.INCOMING_UNKNOWN -> type_iv.setImageResource(R.drawable.ic_incoming_call_error)
            Event.Type.INCOMING_ACCEPTED -> type_iv.setImageResource(R.drawable.ic_incoming_call_accepted)
            Event.Type.INCOMING_DECLINED -> type_iv.setImageResource(R.drawable.ic_incoming_call_declined)
            Event.Type.INCOMING_MISSED -> type_iv.setImageResource(R.drawable.ic_incoming_call_missed)
            Event.Type.INCOMING_ERROR -> type_iv.setImageResource(R.drawable.ic_incoming_call_error)
            Event.Type.OUTGOING_UNKNOWN -> type_iv.setImageResource(R.drawable.ic_incoming_call_error)
            Event.Type.OUTGOING_ACCEPTED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_accepted)
            Event.Type.OUTGOING_DECLINED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_declined)
            Event.Type.OUTGOING_MISSED -> type_iv.setImageResource(R.drawable.ic_outgoing_call_missed)
            Event.Type.OUTGOING_ERROR -> type_iv.setImageResource(R.drawable.ic_outgoing_call_error)
        }

        val address_tv = view.findViewById<TextView>(R.id.call_address)
        if (event.address != null) {
            val text = event.address.address.toString()
            address_tv.text = "(${text})"
        } else {
            address_tv.text = ""
        }

        return view
    }
}