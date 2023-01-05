package d.d.meshenger

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class Events {
    var eventsViewed = Date()
    val eventList = mutableListOf<Event>()

    fun destroy() {
        // no sensitive data
    }

    fun clearEvents() {
        eventList.clear()
    }

    fun addEvent(event: Event) {
        if (event !in eventList) {
            eventList.add(event)

            // sort by date / oldest first
            eventList.sortWith(Comparator { lhs: Event, rhs: Event -> lhs.date.compareTo(rhs.date) })

            if (eventList.size > 100) {
                // remove first item
                eventList.removeAt(0)
            }
        }
    }

    fun setEventsViewedDate() {
        eventsViewed = Date()
    }

    fun getMissedCalls(): List<Event> {
        val calls = mutableListOf<Event>()
        for (event in eventList) {
            if (event.isMissedCall() && event.date.time >= eventsViewed.time) {
                calls.add(event)
            }
        }
        return calls
    }

    companion object {
        fun fromJSON(obj: JSONObject): Events {
            val eventList = mutableListOf<Event>()
            val array = obj.getJSONArray("entries")
            for (i in 0 until array.length()) {
                eventList.add(
                    Event.fromJSON(array.getJSONObject(i))
                )
            }

            // sort by date / oldest first
            eventList.sortWith(Comparator { lhs: Event, rhs: Event -> lhs.date.compareTo(rhs.date) })
            val eventsViewed = Date(obj.getString("events_viewed").toLong(10))

            val events = Events()
            events.eventList.addAll(eventList)
            events.eventsViewed = eventsViewed
            return events
        }

        fun toJSON(events: Events): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (event in events.eventList) {
                array.put(Event.toJSON(event))
            }
            obj.put("entries", array)
            obj.put(
                "events_viewed",
                events.eventsViewed.time.toString()
            )
            return obj
        }
    }
}