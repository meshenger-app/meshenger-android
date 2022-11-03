package d.d.meshenger

import android.content.Context
import d.d.meshenger.Contact
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.ViewGroup
import d.d.meshenger.R
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.ImageView

internal class ContactListAdapter(
    context: Context,
    resource: Int,
    private val contacts: List<Contact>
) : ArrayAdapter<Contact?>(
    context, resource, contacts
) {
    private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView_: View?, parent: ViewGroup): View {
        val convertView = convertView_ ?: inflater.inflate(R.layout.item_contact, null)
        val contact = contacts[position]

        convertView.findViewById<TextView>(R.id.contact_name).text = contact.name

        convertView.findViewById<View>(R.id.contact_waiting).visibility = View.GONE
        val state = convertView.findViewById<ImageView>(R.id.contact_state)
        state.visibility = View.VISIBLE
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint()
        p.color = when (contact.state) {
            Contact.State.ONLINE -> Color.parseColor("#00ff0a") // green
            Contact.State.OFFLINE -> Color.parseColor("#ff0000") // red
            Contact.State.PENDING -> Color.parseColor("#ff7000") // orange
            Contact.State.BROKEN -> Color.parseColor("#612c00") // brown
        }
        canvas.drawCircle(100f, 100f, 100f, p)
        if (contact.blocked) {
            // draw smaller red circle on top
            p.color = -0x13c1c2 // red
            canvas.drawCircle(100f, 100f, 70f, p)
        }
        state.setImageBitmap(bitmap)

        return convertView
    }
}