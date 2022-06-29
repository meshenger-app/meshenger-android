package d.d.meshenger.adapter

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.thekhaeng.pushdownanim.PushDownAnim
import d.d.meshenger.R
import d.d.meshenger.dialog.QRShowDialog
import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService
import d.d.meshenger.utils.Utils
import org.json.JSONException


class ContactListAdapter(val context: Context, val contacts: ArrayList<Contact>)
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

        val contact: Contact = contacts[position]

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
                                val intent = Intent("NONE")
                                intent.putExtra("EXTRA_CONTACT", contact.publicKey)
                                QRShowDialog(context, intent).show((context as AppCompatActivity).supportFragmentManager, "QRShow Fragment")
                            }
                        }
                        false
                    }
                    menu.show()
                    true
                }

            }

        }


    }

    override fun getItemCount(): Int = contacts.size


    private fun setBlocked(position: Int, publicKey: ByteArray, blocked: Boolean) {
        val contacts = MainService.instance?.getContacts()!!
        val contact = publicKey.let { contacts.getContactByPublicKey(it) }
        contact?.let {
            it.blocked = blocked
            contacts.addContact(it)
            notifyItemChanged(position)
            refreshContactListBroadcast()
        }
    }

    private fun showDeleteDialog(position: Int, publicKey: ByteArray, name: String) {

        // create dialog box
        val alert = Dialog(context)
        alert.setContentView(R.layout.dialog_delete_contact)
        val noButton = alert.findViewById<Button>(R.id.noButton)
        val yesButton = alert.findViewById<Button>(R.id.yesButton)
        yesButton.setOnClickListener {
            MainService.instance?.getContacts()?.deleteContact(publicKey)
            refreshContactListBroadcast()
            alert.cancel()
            notifyItemRemoved(position)
        }
        noButton.setOnClickListener{
            alert.cancel()
        }
        PushDownAnim.setPushDownAnimTo(noButton, yesButton)
        alert.show()
    }

    private fun showContactEditDialog(position: Int, publicKey: ByteArray, name: String) {
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

    }

}