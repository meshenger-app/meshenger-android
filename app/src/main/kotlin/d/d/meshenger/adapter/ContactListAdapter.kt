package d.d.meshenger.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import d.d.meshenger.R
import d.d.meshenger.model.Contact

//TODO:RecyclerView Adapter?
class ContactListAdapter(val mContext: Context, resource: Int, val contacts: List<Contact>)
    : ArrayAdapter<Contact>(mContext, resource, contacts) {

    companion object {
        private const val TAG = "ContactListAdapter"

    }

    private var inflater =  context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val contact = contacts[position]
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_contact, null)
        }
        (convertView!!.findViewById<View>(R.id.contact_name) as TextView).text =
            contact.name

        //if (contact.getState() != Contact.State.UNKNOWN) {
        //convertView.findViewById(R.id.contact_waiting).setVisibility(View.GONE); // no animation
        val stateView = convertView.findViewById<ImageView>(R.id.contact_state)
        stateView.setOnClickListener {
            contact.setState(Contact.State.UNKNOWN)
            // TODO: send ping
        }

        //stateView.setVisibility(View.VISIBLE);
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint()
        when (contact.getState()) {
            Contact.State.ONLINE -> {
                p.color = -0x851ed3 // green
                val pc =
                    ((System.currentTimeMillis() - contact.getStateLastUpdated()) / Contact.STATE_TIMEOUT).toFloat()
                if (pc >= 0.0 && pc <= 1.0) {
                    p.color = -0x345fd // orange
                    //canvas.drawCircle(100, 100, 70, p);
                    canvas.drawArc(
                        100f, 100f, 100f, 100f, 0f,  // startAngle
                        360 * pc,  // sweepAngle
                        false,  // useCenter
                        p
                    )
                }
            }
            Contact.State.OFFLINE -> p.color = -0x13c1c2 // red
            else -> p.color = -0x4a4a4b // gray
        }
        canvas.drawCircle(100f, 100f, 100f, p)
        if (contact.blocked) {
            // draw smaller red circle on top
            p.color = -0x13c1c2 // red
            canvas.drawCircle(100f, 100f, 70f, p)
        }
        stateView.setImageBitmap(bitmap)
        //}
/*
        if (contact.recent) {
            contact.recent = false;
            ScaleAnimation anim = new ScaleAnimation(0f, 1f, 0f, 1f);
            anim.setDuration(1000);
            convertView.setAnimation(anim);
        }
*/return convertView
    }

}