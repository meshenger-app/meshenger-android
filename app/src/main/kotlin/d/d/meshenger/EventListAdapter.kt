package d.d.meshenger

import android.content.Context
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.size
import java.text.DateFormat
import kotlin.math.abs

internal class EventListAdapter(
    ctx: Context,
    resource: Int,
    private var events: List<Event>,
    private var contacts: List<Contact>
) : ArrayAdapter<List<Event>?>(
    ctx, resource
) {
    // group consecutive events from the same contact
    private var eventGroups = compactEventList(events)
    private val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    private fun appendIcon(iconsView: LinearLayout, eventType: Event.Type) {
        Log.d(this, "appendIcon() eventType: $eventType")

        val imageView = ImageView(iconsView.context)
        val iconResource = when (eventType) {
            Event.Type.UNKNOWN -> R.drawable.ic_incoming_call_error
            Event.Type.INCOMING_ACCEPTED -> R.drawable.ic_incoming_call_accepted
            Event.Type.INCOMING_MISSED -> R.drawable.ic_incoming_call_missed
            Event.Type.INCOMING_ERROR -> R.drawable.ic_incoming_call_error
            Event.Type.OUTGOING_ACCEPTED -> R.drawable.ic_outgoing_call_accepted
            Event.Type.OUTGOING_MISSED -> R.drawable.ic_outgoing_call_missed
            Event.Type.OUTGOING_ERROR -> R.drawable.ic_outgoing_call_error
        }

        imageView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        imageView.setImageResource(iconResource)
        iconsView.addView(imageView)
    }

    fun update(events: List<Event>, contacts: List<Contact>) {
        Log.d(this, "update() events=${events.size}, contacts=${contacts.size}")
        this.eventGroups = compactEventList(events)
        this.contacts = contacts
    }

    override fun getCount(): Int {
        return eventGroups.size
    }

    override fun getItem(position: Int): List<Event> {
        return eventGroups[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, v: View?, parent: ViewGroup): View {
        // show list in reverse, latest element first
        val view = v ?: inflater.inflate(R.layout.item_event, null)
        val eventGroup = eventGroups[eventGroups.size - position - 1]
        val latestEvent = eventGroup.last()

        // find name
        val name = contacts.find { it.publicKey.contentEquals(latestEvent.publicKey) }?.name

        val nameTv = view.findViewById<TextView>(R.id.call_name)
        if (name == null || name.isEmpty()) {
            nameTv.text = view.context.getString(R.string.unknown_caller)
        } else {
            nameTv.text = name
        }

        // time how long ago the latest event happened
        val dateTV = view.findViewById<TextView>(R.id.call_date)
        val now = System.currentTimeMillis()
        if (abs(now - latestEvent.date.time) < DateUtils.HOUR_IN_MILLIS) {
            dateTV.text = DateUtils.getRelativeTimeSpanString(latestEvent.date.time, now, DateUtils.MINUTE_IN_MILLIS)
        } else if (DateUtils.isToday(latestEvent.date.time)) {
            val tf = DateFormat.getTimeInstance(DateFormat.SHORT)
            dateTV.text = tf.format(latestEvent.date)
        } else {
            val df = DateFormat.getDateInstance(DateFormat.SHORT)
            dateTV.text = df.format(latestEvent.date)
        }

        // add one icon for each event type add one extra for the last event
        val iconsView = view.findViewById<LinearLayout>(R.id.call_icons)
        val iconSet = mutableSetOf<Event.Type>()
        iconsView.removeAllViews()
        for (e in eventGroup) {
            if (!iconSet.contains(e.type) || e === latestEvent) {
                appendIcon(iconsView, e.type)
                iconSet.add(e.type)
            }
        }

        val addressTV = view.findViewById<TextView>(R.id.call_address)
        val address = latestEvent.address ?. address

        if (address != null) {
            addressTV.text = address.toString().removePrefix("/")
        } else {
            addressTV.text = ""
        }

        // show counter if not all calls have an icon
        val eventCount = eventGroup.size
        val iconCount = iconsView.size
        val counterTV = view.findViewById<TextView>(R.id.call_counter)
        if (eventCount > iconCount) {
            counterTV.text = "(${eventCount})"
        } else {
            counterTV.text = ""
        }

        return view
    }

    companion object {
        // group consecutive events from a contact
        fun compactEventList(events: List<Event>): List<List<Event>> {
            val compactEventList = mutableListOf<List<Event>>()
            var lastEvent: Event? = null
            var lastEventList: MutableList<Event>? = null
            for (event in events) {
                if (lastEvent == null || lastEventList == null) {
                    lastEventList = mutableListOf(event)
                } else if (lastEvent.publicKey.contentEquals(event.publicKey)) {
                    lastEventList.add(event)
                } else {
                    compactEventList.add(lastEventList)
                    lastEventList = mutableListOf(event)
                }
                lastEvent = event
            }

            if (lastEventList != null) {
                compactEventList.add(lastEventList)
            }

            return compactEventList
        }
    }
}
