package d.d.meshenger

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONException


class ContactListAdapter(val context: Context, val contacts: ArrayList<Contact?>)
    : RecyclerView.Adapter<ContactListAdapter.ContactListAdapterViewHolder>() {
//    : ArrayAdapter<Contact>(context, resource, contacts){

    private var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    companion object {
            private const val  TAG = "ContactListAdapter"
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ContactListAdapterViewHolder = ContactListAdapterViewHolder(
        inflater.inflate(R.layout.item_contact, parent, false)
    )

    override fun onBindViewHolder(holder: ContactListAdapterViewHolder, position: Int) {

        val contact: Contact = contacts[position]!!

        holder.let{
            it.contactNameTv.apply {
                text = contact.name
                setOnLongClickListener { it ->
                    val menu = it?.let { PopupMenu(context, it) }!!
                    val res: Resources = resources
                    val delete: String = res.getString(R.string.delete)
                    val rename: String = res.getString(R.string.rename)
                    val block: String = res.getString(R.string.block)
                    val unblock: String = res.getString(R.string.unblock)
                    val share: String = res.getString(R.string.share)
//                    val ping = "Ping" //res.getString(R.string.ping);
                    val qr = "QR-ify"
                    menu.menu.add(delete)
                    menu.menu.add(rename)
                    menu.menu.add(share)
//                    menu.menu.add(ping)
                    if (contact.blocked) {
                        menu.menu.add(unblock)
                    } else {
                        menu.menu.add(block)
                    }
                    menu.menu.add(qr)
                    menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val title: String = menuItem.title.toString()
                        val publicKey = contact.publicKey
                        when(title) {
                            delete -> showDeleteDialog(position, publicKey, contact.name)
                            rename -> showContactEditDialog(position, publicKey, contact.name)
                            share -> shareContact(contact)
                            block -> setBlocked(position, publicKey, true)
                            unblock -> setBlocked(position, publicKey, false)
//                            ping -> Log.d(TAG, "Ping not implemented here")// TODO: ping contact
                            qr ->  {
                                val intent = Intent(context, QRShowActivity::class.java)
                                intent.putExtra("EXTRA_CONTACT", contact.publicKey)
                                context.startActivity(intent)
                            }
                        }
                        false
                    }
                    menu.show()
                    true
                }

            }
            it.stateView.let { it2 ->
                it2.setOnClickListener {
                    contact.state = Contact.State.UNKNOWN
                    // TODO: send ping
                }

                //stateView.setVisibility(View.VISIBLE);
                val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val p = Paint()
                when (contact.state) {
                    Contact.State.ONLINE -> {
                        p.color = -0x851ed3 // green
                        val pc =
                            ((System.currentTimeMillis() - contact.getStateLastUpdated()) / Contact.STATE_TIMEOUT).toFloat()
                        if (pc in 0.0..1.0) {
                            p.color = -0x345fd // orange
                            //canvas.drawCircle(100, 100, 70, p);
                            canvas.drawArc(
                                100F,  // left
                                100F,  // top
                                100F,  // right
                                100F,  // bottom
                                0F,  // startAngle
                                360 * pc,  // sweepAngle
                                false,  // useCenter
                                p
                            )
                        }
                    }
                    Contact.State.OFFLINE -> p.color = -0x13c1c2 // red
                    else -> p.color = -0x4a4a4b // gray
                }
                canvas.drawCircle(100F, 100F, 100F, p)
                if (contact.blocked) {
                    // draw smaller red circle on top
                    p.color = -0x13c1c2 // red
                    canvas.drawCircle(100F, 100F, 70F, p)
                }
                it2.setImageBitmap(bitmap)
                //}
            }

            }


        }

    override fun getItemCount(): Int = contacts.size


    private fun setBlocked(position: Int, publicKey: ByteArray?, blocked: Boolean) {
        val contacts = MainService.instance?.getContacts()
        val contact = contacts?.getContactByPublicKey(publicKey)
        contact?.let {
            it.blocked = blocked
            contacts.addContact(it)
            notifyItemChanged(position)
            refreshContactListBroadcast()
        }
    }

    private fun showDeleteDialog(position: Int, publicKey: ByteArray?, name: String) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.confirm)
        builder.setMessage( "${context.resources.getString(R.string.contact_remove)} $name" )
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
            MainService.instance?.getContacts()?.deleteContact(publicKey)
            refreshContactListBroadcast()
            dialog.cancel()
            notifyItemRemoved(position)


        }
        builder.setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int -> dialog.cancel() }

        // create dialog box
        val alert: AlertDialog = builder.create()
        alert.show()
    }

    private fun showContactEditDialog(position: Int, publicKey: ByteArray?, name: String) {
        Log.d(TAG, "showContactEditDialog")
        val contact = MainService.instance?.getContacts()?.getContactByPublicKey(publicKey)
        val et = EditText(context)
        et.setText(name)
        AlertDialog.Builder(context)
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(context.resources.getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                val newName = et.text.toString().trim { it <= ' ' }
                if (newName == contact!!.name) {
                    // nothing to do
                    return@setPositiveButton
                }
                if (!Utils.isValidContactName(newName)) {
                    Toast.makeText(context, "Invalid name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (null != MainService.instance?.getContacts()?.getContactByName(newName)) {
                    Toast.makeText(
                        context,
                        "A contact with that name already exists.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // rename contact
                contact.name = newName
                MainService.instance?.saveDatabase()
                notifyItemChanged(position)
                refreshContactListBroadcast()
            }.show()
    }

    private fun shareContact(contact: Contact?) {
        Log.d(TAG, "shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, Contact.toJSON(contact!!).toString())
            context.startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }



    private fun refreshContactListBroadcast() {
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent("contact_changed"))
    }


    inner class ContactListAdapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var contactNameTv: TextView = itemView.findViewById(R.id.contact_name)
    var stateView: ImageView = itemView.findViewById(R.id.contact_state)

    }

}