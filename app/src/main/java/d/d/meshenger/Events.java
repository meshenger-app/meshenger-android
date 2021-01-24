package d.d.meshenger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import d.d.meshenger.call.DirectRTCClient;


public class Events {
    private List<Event> events;
    private Date eventsViewed;

    public Events() {
        this.events = new ArrayList<>();
        this.eventsViewed = new Date();
    }

    public Events(List<Event> events, Date eventsViewed) {
        this.events = events;
        this.eventsViewed = eventsViewed;
    }

    public List<Event> getEventList() {
        return events;
    }

    public List<Event> getEventListCopy() {
        return new ArrayList<>(events);
    }

    public void clear() {
        events.clear();
    }

    public void addEvent(Contact contact, DirectRTCClient.CallDirection callDirection, Event.CallType callType) {
        InetAddress address = contact.getLastWorkingAddress();
        String address_str = (address != null) ? address.toString() : null;
        Event event = new Event(contact.getPublicKey(), address_str, callDirection, callType, new Date());

        if (events.size() > 100) {
            // remove first item
            events.remove(0);
        }

        events.add(event);
    }

    public void setEventsViewedDate() {
        eventsViewed = new Date();
    }

    public List<Event> getMissedCalls() {
        List<Event> calls = new ArrayList<>();
        for (Event event : events) {
            if (event.isMissedCall() && event.date.getTime() >= eventsViewed.getTime()) {
                calls.add(event);
            }
        }
        return calls;
    }

    public static Events fromJSON(JSONObject obj) throws JSONException {
        List<Event> events = new ArrayList<Event>();
        JSONArray array = obj.getJSONArray("entries");
        for (int i = 0; i < array.length(); i += 1) {
            events.add(
                Event.fromJSON(array.getJSONObject(i))
            );
        }

        // sort by date / oldest first
        Collections.sort(events, (Event lhs, Event rhs) -> {
            return lhs.date.compareTo(rhs.date);
        });

        Date eventsViewed = new Date(
            Long.parseLong(obj.getString("events_viewed"), 10)
        );

        return new Events(events, eventsViewed);
    }

    public static JSONObject toJSON(Events events) throws JSONException {
        JSONObject obj = new JSONObject();

        JSONArray array = new JSONArray();
        for (Event event : events.events) {
            array.put(Event.toJSON(event));
        }

        obj.put("entries", array);
        obj.put("events_viewed", String.valueOf(
            events.eventsViewed.getTime())
        );

        return obj;
    }
}
