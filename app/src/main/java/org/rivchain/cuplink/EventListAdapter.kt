package org.rivchain.cuplink

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

internal class EventListAdapter(
    context: Context,
    resource: Int,
    private var events: List<CallEvent>,
    private var contacts: List<Contact>
) : ArrayAdapter<CallEvent>(context, resource, events) {
    private val inflater: LayoutInflater
    fun update(events: List<CallEvent>, contacts: List<Contact>) {
        this.events = events
        this.contacts = contacts
    }

    override fun getCount(): Int {
        return events.size
    }

    override fun getItem(position: Int): CallEvent {
        return events[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
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
            if (contact.getAddresses()?.get(0)?.address?.hostAddress?.equals(event.address.hostAddress)!!) {
                name = contact.name
                break
            }
        }
        val name_tv = view!!.findViewById<TextView>(R.id.call_name)
        if (name.isEmpty()) {
            name_tv.text = context.resources.getString(R.string.unknown_caller)
        } else {
            name_tv.text = name
        }
        val date_tv = view.findViewById<TextView>(R.id.call_date)
        if (DateUtils.isToday(event.date.time)) {
            val ft = SimpleDateFormat("'Today at' hh:mm:ss")
            date_tv.text = ft.format(event.date)
        } else {
            val ft = SimpleDateFormat("yyyy.MM.dd 'at' hh:mm:ss")
            date_tv.text = ft.format(event.date)
        }
        val type_iv = view.findViewById<ImageView>(R.id.call_type)
        when (event.type) {
            CallEvent.Type.INCOMING_ACCEPTED, CallEvent.Type.INCOMING_DECLINED -> type_iv.setImageResource(
                R.drawable.call_incoming
            )
            CallEvent.Type.INCOMING_UNKNOWN, CallEvent.Type.INCOMING_MISSED, CallEvent.Type.INCOMING_ERROR -> type_iv.setImageResource(
                R.drawable.call_incoming_missed
            )
            CallEvent.Type.OUTGOING_ACCEPTED, CallEvent.Type.OUTGOING_DECLINED -> type_iv.setImageResource(
                R.drawable.call_outgoing
            )
            CallEvent.Type.OUTGOING_UNKNOWN, CallEvent.Type.OUTGOING_MISSED, CallEvent.Type.OUTGOING_ERROR -> type_iv.setImageResource(
                R.drawable.call_outgoing_missed
            )
        }
        val address_tv = view.findViewById<TextView>(R.id.call_address)
        if (event.address != null) {
            address_tv.text = "(" + event.address.hostAddress + ")"
        } else {
            address_tv.text = ""
        }
        return view
    }

    companion object {
        private fun log(s: String) {
            Log.d(EventListAdapter::class.java.simpleName, s)
        }
    }

    init {
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
}