package d.d.meshenger

import d.d.meshenger.Contact.Companion.exportJSON
import android.widget.AdapterView.OnItemClickListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import d.d.meshenger.MainActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import d.d.meshenger.ContactListFragment
import d.d.meshenger.R
import android.content.Intent
import d.d.meshenger.QRScanActivity
import d.d.meshenger.QRShowActivity
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.view.animation.AlphaAnimation
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import d.d.meshenger.Contact
import d.d.meshenger.ContactListAdapter
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import d.d.meshenger.CallActivity
import org.json.JSONException

class ContactListFragment : Fragment(), OnItemClickListener {
    private var contactListView: ListView? = null
    private var fabExpanded = false
    private var fabScan: FloatingActionButton? = null
    private var fabGen: FloatingActionButton? = null
    private var fab: FloatingActionButton? = null
    private var mainActivity: MainActivity? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        log("onCreateView")
        val view = inflater.inflate(R.layout.fragment_contact_list, container, false)
        mainActivity = activity as MainActivity?
        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        contactListView = view.findViewById(R.id.contactList)
        fabScan?.setOnClickListener(View.OnClickListener { v: View? ->
            startActivity(
                Intent(
                    mainActivity, QRScanActivity::class.java
                )
            )
        })
        fabGen?.setOnClickListener(View.OnClickListener { v: View? ->
            startActivity(
                Intent(
                    mainActivity, QRShowActivity::class.java
                )
            )
        })
        fab?.setOnClickListener(View.OnClickListener { fab: View -> runFabAnimation(fab) })
        return view
    }

    fun onServiceConnected() {
        refreshContactList()
    }

    private fun runFabAnimation(fab: View) {
        log("runFabAnimation")
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
            fabScan!!.y = fabScan!!.y + distance
            fabGen!!.y = fabGen!!.y + distance * 2
        } else {
            scanAnimation = TranslateAnimation(0F, 0F, distance.toFloat(), 0F)
            generateAnimnation = TranslateAnimation(0F, 0F, (distance * 2).toFloat(), 0F)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabScan!!.y = fabScan!!.y - distance
            fabGen!!.y = fabGen!!.y - distance * 2
        }
        scanSet.addAnimation(scanAnimation)
        scanSet.addAnimation(alphaAnimation)
        scanSet.fillAfter = true
        generateSet.addAnimation(generateAnimnation)
        generateSet.addAnimation(alphaAnimation)
        generateSet.fillAfter = true
        scanSet.duration = duration.toLong()
        generateSet.duration = duration.toLong()
        fabScan!!.visibility = View.VISIBLE
        fabGen!!.visibility = View.VISIBLE
        fabScan!!.startAnimation(scanSet)
        fabGen!!.startAnimation(generateSet)
        fabExpanded = !fabExpanded
    }

    private fun collapseFab() {
        if (fabExpanded) {
            fab!!.setImageResource(R.drawable.qr_glass)
            fabScan!!.clearAnimation()
            fabGen!!.clearAnimation()
            fabScan!!.y = fabScan!!.y + 200
            fabGen!!.y = fabGen!!.y + 200 * 2
            fabExpanded = false
        }
    }

    private fun showDeleteDialog(publicKey: ByteArray?, name: String) {
        val builder = AlertDialog.Builder(
            mainActivity!!
        )
        builder.setTitle(R.string.confirm)
        builder.setMessage(resources.getString(R.string.contact_remove) + " " + name)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
            mainActivity!!.binder!!.deleteContact(publicKey)
            dialog.cancel()
        }
        builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int -> dialog.cancel() }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    fun refreshContactList() {
        log("refreshContactList")
        if (mainActivity == null || mainActivity!!.binder == null) {
            log("refreshContactList early return")
            return
        }
        Handler(Looper.getMainLooper()).post {
            val contacts = mainActivity!!.binder!!.contactsCopy
            contactListView!!.adapter =
                ContactListAdapter(mainActivity!!, R.layout.item_contact, contacts)
            contactListView!!.onItemClickListener = this@ContactListFragment
            contactListView!!.onItemLongClickListener =
                OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                    val contact = contacts[i]
                    val menu = PopupMenu(mainActivity, view)
                    val res = resources
                    val delete = res.getString(R.string.delete)
                    val rename = res.getString(R.string.rename)
                    val block = res.getString(R.string.block)
                    val unblock = res.getString(R.string.unblock)
                    val share = res.getString(R.string.share)
                    val qr = "QR-ify"
                    menu.menu.add(delete)
                    menu.menu.add(rename)
                    menu.menu.add(share)
                    if (contact.getBlocked()) {
                        menu.menu.add(unblock)
                    } else {
                        menu.menu.add(block)
                    }
                    menu.menu.add(qr)
                    menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                        val title = menuItem.title.toString()
                        val publicKey = contact.publicKey
                        if (title == delete) {
                            showDeleteDialog(publicKey, contact.getName())
                        } else if (title == rename) {
                            showContactEditDialog(publicKey, contact.getName())
                        } else if (title == share) {
                            shareContact(contact)
                        } else if (title == block) {
                            setBlocked(publicKey, true)
                        } else if (title == unblock) {
                            setBlocked(publicKey, false)
                        } else if (title == qr) {
                            val intent = Intent(mainActivity, QRShowActivity::class.java)
                            intent.putExtra("EXTRA_CONTACT", contact)
                            startActivity(intent)
                        }
                        false
                    }
                    menu.show()
                    true
                }
        }
    }

    private fun setBlocked(publicKey: ByteArray?, blocked: Boolean) {
        val contact = mainActivity!!.binder!!.getContactByPublicKey(publicKey)
        if (contact != null) {
            contact.setBlocked(blocked)
            mainActivity!!.binder!!.addContact(contact)
        }
    }

    private fun shareContact(c: Contact) {
        log("shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, exportJSON(c, false).toString())
            startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showContactEditDialog(publicKey: ByteArray?, name: String) {
        log("showContactEditDialog")
        val et = EditText(mainActivity)
        et.setText(name)
        val dialog = AlertDialog.Builder(
            mainActivity!!
        )
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val newName = et.text.toString()
                if (newName.length > 0) {
                    val contact = mainActivity!!.binder!!.getContactByPublicKey(publicKey)
                    if (contact != null) {
                        contact.setName(newName)
                        mainActivity!!.binder!!.addContact(contact)
                    }
                }
            }.show()
    }

    override fun onPause() {
        super.onPause()
        collapseFab()
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        log("onItemClick")
        val contact = mainActivity!!.binder!!.contactsCopy[i]
        val intent = Intent(mainActivity, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }

    companion object {
        private fun log(s: String) {
            Log.d(ContactListFragment::class.java.simpleName, s)
        }
    }
}