package d.d.meshenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;


class ContactListAdapter extends ArrayAdapter<Contact> {
    private List<Contact> contacts;
    private Context context;

    private LayoutInflater inflater;

    public ContactListAdapter(@NonNull Context context, int resource, @NonNull List<Contact> contacts) {
        super(context, resource, contacts);
        this.contacts = contacts;
        this.context = context;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Contact contact = contacts.get(position);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_contact, null);
        }

        ((TextView) convertView.findViewById(R.id.contact_name)).setText(contact.getName());

        if (contact.getState() != Contact.State.PENDING) {
            convertView.findViewById(R.id.contact_waiting).setVisibility(View.GONE);
            ImageView state = convertView.findViewById(R.id.contact_state);
            state.setVisibility(View.VISIBLE);
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint p = new Paint();

            if (contact.getState() == Contact.State.ONLINE) {
                p.setColor(0xFF7AE12D); // green
            } else {
                p.setColor(0xFFEC3E3E); // red
            }

            canvas.drawCircle(100, 100, 100, p);

            if (contact.getBlocked()) {
                // draw smaller red circle on top
                p.setColor(0xFFEC3E3E); // red
                canvas.drawCircle(100, 100, 70, p);
            }

            state.setImageBitmap(bitmap);
        }
/*
        if (contact.recent) {
            contact.recent = false;
            ScaleAnimation anim = new ScaleAnimation(0f, 1f, 0f, 1f);
            anim.setDuration(1000);
            convertView.setAnimation(anim);
        }
*/
        return convertView;
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
