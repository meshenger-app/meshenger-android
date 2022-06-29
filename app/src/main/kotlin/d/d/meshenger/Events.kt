package d.d.meshenger

import d.d.meshenger.Event.CallType
import d.d.meshenger.call.DirectRTCClient.CallDirection
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.InetAddress
import java.util.*
import kotlin.collections.ArrayList


class Events constructor() {
    private var events: ArrayList<Event>
    private var eventsViewed: Date? = null

    init {
        events = ArrayList()
        eventsViewed = Date()
    }

    companion object {

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Events {
            val events: ArrayList<Event> = ArrayList()
            val array = obj.getJSONArray("entries")
            var i = 0
            while (i < array.length()) {
                events.add(
                    Event.fromJSON(array.getJSONObject(i))
                )
                i += 1
            }

            // sort by date / oldest first
            events.sortWith { lhs: Event, rhs: Event ->
                lhs.date.compareTo(
                    rhs.date
                )
            }
            val eventsViewed = Date(obj.getString("events_viewed").toLong(10))
            return Events(events, eventsViewed)
        }

        @Throws(JSONException::class)
        fun toJSON(events: Events): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (event in events.events) {
                array.put(Event.toJSON(event))
            }
            obj.put("entries", array)
            obj.put(
                "events_viewed",
                events.eventsViewed!!.time.toString()
            )
            return obj
        }
    }

    constructor(events: ArrayList<Event>, eventsViewed: Date?) : this() {
        this.events = events
        this.eventsViewed = eventsViewed
    }

    fun getEventList(): List<Event> = events

    fun getEventListCopy(): ArrayList<Event?> = ArrayList(events)

    fun clear() {
        events.clear()
    }

    fun addEvent(contact: Contact, callDirection: CallDirection?, callType: CallType?) {
        val address: InetAddress? = contact.lastWorkingAddress
        val addressStr: String? = address?.toString()
        val event = Event(
            contact.publicKey!!, addressStr,
            callDirection!!, callType!!, Date()
        )
        if (events.size > 100) {
            // remove first item
            events.removeAt(0)
        }
        events.add(event)
    }

    fun setEventsViewedDate() {
        eventsViewed = Date()
    }

    fun getMissedCalls(): List<Event> {
        val calls: MutableList<Event> = ArrayList()
        for (event in events) {
            if (event.isMissedCall() && event.date.time >= eventsViewed!!.time) {
                calls.add(event)
            }
        }
        return calls
    }
}