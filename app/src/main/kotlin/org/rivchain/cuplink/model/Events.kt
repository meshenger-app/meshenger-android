package org.rivchain.cuplink.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

class Events {
    var eventList = mutableListOf<Event>()

    fun destroy() {
        // no sensitive data
    }

    fun clearEvents() {
        eventList.clear()
    }

    fun deleteEventsByDate(eventDates: List<Date>) {
        for (date in eventDates) {
            eventList.removeAll {
                it.date in eventDates
            }
        }
    }

    fun deleteEventsByPublicKey(publicKey: ByteArray) {
        eventList.removeAll {
            it.publicKey.contentEquals(publicKey)
        }
    }

    fun addEvent(event: Event) {
        if (event !in eventList) {
            eventList.add(event)

            // sort by date / oldest first
            eventList.sortWith { lhs: Event, rhs: Event -> lhs.date.compareTo(rhs.date) }

            while (eventList.size > 100) {
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
            eventList.sortWith { lhs: Event, rhs: Event -> lhs.date.compareTo(rhs.date) }

            val events = Events()
            events.eventList.addAll(eventList)
            return events
        }

        fun toJSON(events: Events): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (event in events.eventList) {
                array.put(Event.toJSON(event))
            }
            obj.put("entries", array)
            return obj
        }
    }
}