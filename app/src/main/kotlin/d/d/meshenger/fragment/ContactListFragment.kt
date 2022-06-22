package d.d.meshenger.fragment

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.*
import d.d.meshenger.utils.Log.d
import d.d.meshenger.utils.Utils.isValidContactName
import d.d.meshenger.activity.MainActivity
import d.d.meshenger.activity.QRScanActivity
import d.d.meshenger.adapter.ContactListAdapter
import d.d.meshenger.base.QRShowActivity
import d.d.meshenger.call.CallActivity
import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.model.Contact
import d.d.meshenger.service.MainService
import org.json.JSONException

class ContactListFragment: Fragment(), AdapterView.OnItemClickListener {

    companion object {
        private const val TAG = "ContactListFragment"

    }

    private var contactListView: ListView? = null
    private var fabExpanded = false
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View?  = inflater.inflate(R.layout.fragment_contact_list, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mainActivity = (activity as MainActivity?)!!
        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        contactListView = view.findViewById(R.id.contactList)

        fabScan.setOnClickListener { v: View? ->
            startActivity(
                Intent(
                    mainActivity,
                    QRScanActivity::class.java
                )
            )
        }
        fabGen.setOnClickListener { v: View? ->
            startActivity(
                Intent(
                    mainActivity,
                    QRShowActivity::class.java
                )
            )
        }
        fab.setOnClickListener { fab: View ->
            this.runFabAnimation(
                fab
            )
        }

        refreshContactList()

    }


    private fun runFabAnimation(fab: View) {
        d(TAG, "runFabAnimation")
        val scanSet = AnimationSet(mainActivity, null)
        val generateSet = AnimationSet(mainActivity, null)
        val distance = 200
        val duration = 300
        val scanAnimation: TranslateAnimation
        val generateAnimnation: TranslateAnimation
        val alphaAnimation: AlphaAnimation
        if (fabExpanded) {
            scanAnimation = TranslateAnimation(0F, 0F, (-distance).toFloat(), 0F)
            generateAnimnation = TranslateAnimation(0F, 0F, (-distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabScan.y = fabScan.y + distance
            fabGen.y = fabGen.y + distance * 2
        } else {
            scanAnimation = TranslateAnimation(0F, 0F, distance.toFloat(), 0F)
            generateAnimnation = TranslateAnimation(0F, 0F, (distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabScan.y = fabScan.y - distance
            fabGen.y = fabGen.y - distance * 2
        }
        scanSet.addAnimation(scanAnimation)
        scanSet.addAnimation(alphaAnimation)
        scanSet.fillAfter = true
        generateSet.addAnimation(generateAnimnation)
        generateSet.addAnimation(alphaAnimation)
        generateSet.fillAfter = true
        scanSet.duration = duration.toLong()
        generateSet.duration = duration.toLong()
        fabScan.visibility = View.VISIBLE
        fabGen.visibility = View.VISIBLE
        fabScan.startAnimation(scanSet)
        fabGen.startAnimation(generateSet)
        fabExpanded = !fabExpanded
    }

    private fun collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass)
            fabScan.clearAnimation()
            fabGen.clearAnimation()
            fabScan.y = fabScan.y + 200
            fabGen.y = fabGen.y + 200 * 2
            fabExpanded = false
        }
    }

    private fun showDeleteDialog(publicKey: ByteArray, name: String) {
        val builder = AlertDialog.Builder(mainActivity)
        builder.setTitle(R.string.confirm)
        builder.setMessage(resources.getString(R.string.contact_remove) + " " + name)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
            MainService.instance!!.getContacts()!!.deleteContact(publicKey)
            refreshContactListBroadcast()
            dialog.cancel()
        }
        builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int -> dialog.cancel() }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    fun refreshContactList() {
        d(TAG, "refreshContactList")
        Handler(Looper.getMainLooper()).post {
            val contacts =
                MainService.instance!!.getContacts()!!.getContactListCopy()
            contactListView?.let {
                it.adapter = ContactListAdapter(
                    mainActivity,
                    R.layout.item_contact,
                    contacts
                )
                it.onItemClickListener = this@ContactListFragment
                it.onItemLongClickListener =
                    OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                        val contact = contacts!![i]
                        val menu =
                            PopupMenu(mainActivity, view)
                        val res = resources
                        val delete = res.getString(R.string.delete)
                        val rename = res.getString(R.string.rename)
                        val block = res.getString(R.string.block)
                        val unblock = res.getString(R.string.unblock)
                        val share = res.getString(R.string.share)
                        val ping = "Ping" //res.getString(R.string.ping);
                        val qr = "QR-ify"
                        menu.menu.add(delete)
                        menu.menu.add(rename)
                        menu.menu.add(share)
                        menu.menu.add(ping)
                        if (contact!!.blocked) {
                            menu.menu.add(unblock)
                        } else {
                            menu.menu.add(block)
                        }
                        menu.menu.add(qr)
                        menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                            val title = menuItem.title.toString()
                            val publicKey = contact.publicKey
                            if (title == delete) {
                                showDeleteDialog(publicKey, contact.name)
                            } else if (title == rename) {
                                showContactEditDialog(publicKey, contact.name)
                            } else if (title == share) {
                                shareContact(contact)
                            } else if (title == block) {
                                setBlocked(publicKey, true)
                            } else if (title == unblock) {
                                setBlocked(publicKey, false)
                            } else if (title == ping) {
                                // TODO: ping contact
                                d(
                                    TAG,
                                    "Ping not implemented here"
                                )
                            } else if (title == qr) {
                                val intent =
                                    Intent(
                                        mainActivity,
                                        QRShowActivity::class.java
                                    )
                                intent.putExtra("EXTRA_CONTACT", contact.publicKey)
                                startActivity(intent)
                            }
                            false
                        }
                        menu.show()
                        true
                    }
            }

        }
    }

    private fun refreshContactListBroadcast() {
        LocalBroadcastManager.getInstance(activity!!).sendBroadcast(Intent("contact_changed"))
    }

    private fun setBlocked(publicKey: ByteArray, blocked: Boolean) {
        val contacts = MainService.instance!!.getContacts()
        val contact = contacts!!.getContactByPublicKey(publicKey)
        if (contact != null) {
            contact.blocked = blocked
            contacts.addContact(contact)
            refreshContactListBroadcast()
        }
    }

    private fun shareContact(contact: Contact?) {
        d(TAG, "shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, Contact.toJSON(contact!!).toString())
            startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showContactEditDialog(publicKey: ByteArray, name: String) {
        d(TAG, "showContactEditDialog")
        val contact = MainService.instance!!.getContacts()!!.getContactByPublicKey(publicKey)
        val et = EditText(mainActivity)
        et.setText(name)
        val dialog = AlertDialog.Builder(mainActivity)
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val newName = et.text.toString().trim { it <= ' ' }
                if (newName == contact!!.name) {
                    // nothing to do
                    return@setPositiveButton
                }
                if (!isValidContactName(newName)) {
                    Toast.makeText(context, "Invalid name.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (null != MainService.instance!!.getContacts()!!.getContactByName(newName)) {
                    Toast.makeText(
                        context,
                        "A contact with that name already exists.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // rename contact
                contact.name = newName
                MainService.instance!!.saveDatabase()
                refreshContactListBroadcast()
            }.show()
    }

    override fun onPause() {
        super.onPause()
        collapseFab()
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
        d(TAG, "onItemClick")
        val contact = adapterView.adapter.getItem(i) as Contact
        if (DirectRTCClient.createOutgoingCall(contact)) {
            val intent = Intent(context, CallActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

}