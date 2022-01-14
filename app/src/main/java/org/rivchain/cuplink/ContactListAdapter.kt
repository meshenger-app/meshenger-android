package org.rivchain.cuplink

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

internal class ContactListAdapter(
    context: Context,
    resource: Int,
    private val contacts: List<Contact>
) : ArrayAdapter<Contact?>(context, resource, contacts) {
    private val inflater: LayoutInflater
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val contact = contacts[position]
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_contact, null)
        }
        (convertView!!.findViewById<View>(R.id.contact_name) as TextView).text = contact.name
        if (contact.state !== Contact.State.PENDING) {
            convertView.findViewById<View>(R.id.contact_waiting).visibility = View.GONE
            val state = convertView.findViewById<ImageView>(R.id.contact_state)
            state.visibility = View.VISIBLE
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val p = Paint()
            if (contact.state === Contact.State.ONLINE) {
                p.color = -0x851ed3 // green
            } else {
                p.color = -0x13c1c2 // red
            }
            canvas.drawCircle(100f, 100f, 100f, p)
            if (contact.getBlocked()) {
                // draw smaller red circle on top
                p.color = -0x13c1c2 // red
                canvas.drawCircle(100f, 100f, 70f, p)
            }
            state.setImageBitmap(bitmap)
        }
        /*
        if (contact.recent) {
            contact.recent = false;
            ScaleAnimation anim = new ScaleAnimation(0f, 1f, 0f, 1f);
            anim.setDuration(1000);
            convertView.setAnimation(anim);
        }
*/return convertView
    }

    private fun log(s: String) {
        Log.d(this, s)
    }

    init {
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }
}