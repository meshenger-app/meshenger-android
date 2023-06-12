package d.d.meshenger

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class Events {
    val eventList = mutableListOf<Event>()
    var eventsMissed = 0

    fun destroy() {
        // no sensitive data
    }

    fun clearEvents() {
        eventList.clear()
    }

    fun deleteEvents(eventDates: List<Date>) {
        for (date in eventDates) {
            eventList.removeAll {
                it.date in eventDates
            }
        }
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

            val events = Events()
            events.eventList.addAll(eventList)
            events.eventsMissed = obj.getInt("events_missed")
            return events
        }

        fun toJSON(events: Events): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (event in events.eventList) {
                array.put(Event.toJSON(event))
            }
            obj.put("entries", array)
            obj.put("events_missed", events.eventsMissed)
            return obj
        }
    }
}