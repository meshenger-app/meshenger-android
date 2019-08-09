package d.d.meshenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.ScaleAnimation;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;


class ContactListAdapter extends ArrayAdapter<Contact> {
    private List<Contact> contacts;
    private Context context;

    private LayoutInflater inflater;

    public ContactListAdapter(@NonNull Context context, int resource, @NonNull List<Contact> objects) {
        super(context, resource, objects);
        this.contacts = objects;
        this.context = context;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Contact c = contacts.get(position);
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.contact_item, null);
        }

        ((TextView)convertView.findViewById(R.id.contact_item_name)).setText(c.getName());
        ((TextView)convertView.findViewById(R.id.contact_item_info)).setText(c.getInfo());

        if (c.getState() != Contact.State.PENDING) {
            Log.d(ContactListActivity.class.getSimpleName(), c.getName() + " online");
            convertView.findViewById(R.id.contact_item_waiting).setVisibility(View.GONE);
            ImageView state = convertView.findViewById(R.id.contact_item_state);
            state.setVisibility(View.VISIBLE);
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint p = new Paint();
            p.setColor(c.getState() == Contact.State.ONLINE ? Color.GREEN : 0xFFEC3E3E);
            canvas.drawCircle(100, 100, 100, p);
            state.setImageBitmap(bitmap);
        } else {
            Log.d(ContactListActivity.class.getSimpleName(), c.getName() + " offline");
        }

        if (c.recent) {
            c.recent = false;
            ScaleAnimation anim = new ScaleAnimation(0f, 1f, 0f, 1f);
            anim.setDuration(1000);
            convertView.setAnimation(anim);
        }

        return convertView;
    }
}
