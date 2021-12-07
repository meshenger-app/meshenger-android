package org.rivchain.cuplink;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;


class EventListAdapter extends ArrayAdapter<CallEvent> {
    private LayoutInflater inflater;
    private List<CallEvent> events;
    private List<Contact> contacts;
    private Context context;

    public EventListAdapter(@NonNull Context context, int resource, List<CallEvent> events, List<Contact> contacts) {
        super(context, resource, events);

        this.events = events;
        this.contacts = contacts;
        this.context = context;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private static void log(String s) {
        Log.d(EventListAdapter.class.getSimpleName(), s);
    }

    public void update(List<CallEvent> events, List<Contact> contacts) {
        this.events = events;
        this.contacts = contacts;
    }

    @Override
    public int getCount() {
        return events.size();
    }

    @Override
    public CallEvent getItem(int position) {
        return events.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View view, @NonNull ViewGroup parent) {
        // show list in reverse, latest element first
        CallEvent event = this.events.get(this.events.size() - position - 1);

        if (view == null) {
            view = inflater.inflate(R.layout.item_event, null);
        }

        // find name
        String name = "";
        for (Contact contact : this.contacts) {
            if (Arrays.equals(contact.getPublicKey(), event.pubKey)) {
                name = contact.getName();
                break;
            }
        }

        TextView name_tv = view.findViewById(R.id.call_name);
        if (name.isEmpty()) {
            name_tv.setText(this.context.getResources().getString(R.string.unknown_caller));
        } else {
            name_tv.setText(name);
        }

        TextView date_tv = view.findViewById(R.id.call_date);
        if (DateUtils.isToday(event.date.getTime())) {
            SimpleDateFormat ft = new SimpleDateFormat("'Today at' hh:mm:ss");
            date_tv.setText(ft.format(event.date));
        } else {
            SimpleDateFormat ft = new SimpleDateFormat("yyyy.MM.dd 'at' hh:mm:ss");
            date_tv.setText(ft.format(event.date));
        }

        ImageView type_iv = view.findViewById(R.id.call_type);
        switch (event.type) {
            case INCOMING_ACCEPTED:
            case INCOMING_DECLINED:
                type_iv.setImageResource(R.drawable.call_incoming);
                break;
            case INCOMING_UNKNOWN:
            case INCOMING_MISSED:
            case INCOMING_ERROR:
                type_iv.setImageResource(R.drawable.call_incoming_missed);
                break;
            case OUTGOING_ACCEPTED:
            case OUTGOING_DECLINED:
                type_iv.setImageResource(R.drawable.call_outgoing);
                break;
            case OUTGOING_UNKNOWN:
            case OUTGOING_MISSED:
            case OUTGOING_ERROR:
                type_iv.setImageResource(R.drawable.call_outgoing_missed);
                break;
        }

        TextView address_tv = view.findViewById(R.id.call_address);
        if (event.address != null) {
            address_tv.setText("(" + event.address.getHostAddress() + ")");
        } else {
            address_tv.setText("");
        }
        return view;
    }
}
