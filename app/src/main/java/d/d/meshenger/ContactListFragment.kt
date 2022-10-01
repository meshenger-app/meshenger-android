package d.d.meshenger

import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException

class ContactListFragment : Fragment(), AdapterView.OnItemClickListener {
    private var mainActivity: MainActivity? = null
    private lateinit var contactListView: ListView
    private var fabExpanded = false
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fab: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(this, "onCreateView")
        val view: View = inflater.inflate(R.layout.fragment_contact_list, container, false)

        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        contactListView = view.findViewById(R.id.contactList)
        fabScan.setOnClickListener(View.OnClickListener { _: View? ->
            startActivity(
                Intent(
                    mainActivity!!, QRScanActivity::class.java
                )
            )
        })
        fabGen.setOnClickListener(View.OnClickListener { _: View? ->
            startActivity(
                Intent(
                    mainActivity!!, QRShowActivity::class.java
                )
            )
        })
        fab.setOnClickListener(View.OnClickListener { fab: View -> runFabAnimation(fab) })
        //refreshContactList() // TODO
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            mainActivity = context as MainActivity
        } catch (e: ClassCastException) {
            Log.e(this, "MainActivity expected")
            throw RuntimeException()
        }
    }

    private fun runFabAnimation(fab: View) {
        Log.d(this, "runFabAnimation")
        val scanSet = AnimationSet(mainActivity!!, null)
        val generateSet = AnimationSet(mainActivity!!, null)
        val distance = 200f
        val duration = 300f
        val scanAnimation: TranslateAnimation
        val generateAnimnation: TranslateAnimation
        val alphaAnimation: AlphaAnimation
        if (fabExpanded) {
            scanAnimation = TranslateAnimation(0f, 0f, -distance, 0f)
            generateAnimnation = TranslateAnimation(0f, 0f, -distance * 2, 0f)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabScan.setY(fabScan.getY() + distance)
            fabGen.setY(fabGen.getY() + distance * 2)
        } else {
            scanAnimation = TranslateAnimation(0f, 0f, distance, 0f)
            generateAnimnation = TranslateAnimation(0f, 0f, distance * 2, 0f)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabScan.setY(fabScan.getY() - distance)
            fabGen.setY(fabGen.getY() - distance * 2)
        }
        scanSet.addAnimation(scanAnimation)
        scanSet.addAnimation(alphaAnimation)
        scanSet.setFillAfter(true)
        generateSet.addAnimation(generateAnimnation)
        generateSet.addAnimation(alphaAnimation)
        generateSet.setFillAfter(true)
        scanSet.setDuration(duration.toLong())
        generateSet.setDuration(duration.toLong())
        fabScan.setVisibility(View.VISIBLE)
        fabGen.setVisibility(View.VISIBLE)
        fabScan.startAnimation(scanSet)
        fabGen.startAnimation(generateSet)
        fabExpanded = !fabExpanded
    }

    private fun collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass)
            fabScan.clearAnimation()
            fabGen.clearAnimation()
            fabScan.setY(fabScan.getY() + 200)
            fabGen.setY(fabGen.getY() + 200 * 2)
            fabExpanded = false
        }
    }

    private fun showDeleteDialog(publicKey: ByteArray, name: String) {
        val builder = AlertDialog.Builder(
            mainActivity!!
        )
        builder.setTitle(R.string.confirm)
        builder.setMessage(resources.getString(R.string.contact_remove) + " ${name}")
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(
            R.string.yes,
            DialogInterface.OnClickListener { dialog: DialogInterface, _: Int ->
                mainActivity!!.binder!!.getContacts().deleteContact(publicKey)
                refreshContactListBroadcast()
                dialog.cancel()
            })
        builder.setNegativeButton(
            R.string.no,
            DialogInterface.OnClickListener { dialog: DialogInterface, _: Int -> dialog.cancel() })

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    fun refreshContactList() {
        Log.d(this, "refreshContactList")
        if (mainActivity == null) {
            return
        }

        val binder = mainActivity!!.binder ?: return

        Handler(Looper.getMainLooper()).post(Runnable {
            val contacts = binder.getContacts().contactList
            contactListView.adapter =
                ContactListAdapter(mainActivity!!, R.layout.item_contact, contacts)
            contactListView.setOnItemClickListener(this@ContactListFragment)
            contactListView.onItemLongClickListener =
                AdapterView.OnItemLongClickListener { _: AdapterView<*>?, view: View?, i: Int, _: Long ->
                    val contact = contacts[i]
                    val menu = PopupMenu(mainActivity!!, view)
                    val delete = getString(R.string.delete)
                    val rename = getString(R.string.rename)
                    val block = getString(R.string.block)
                    val unblock = getString(R.string.unblock)
                    val share = getString(R.string.share)
                    val ping = getString(R.string.ping)
                    val qr = "QR-ify"
                    menu.menu.add(delete)
                    menu.menu.add(rename)
                    menu.menu.add(share)
                    menu.menu.add(ping)
                    if (contact.blocked) {
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
                            Log.d(this, "Ping not implemented here")
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
        })
    }

    private fun refreshContactListBroadcast() {
        LocalBroadcastManager.getInstance(requireActivity().applicationContext).sendBroadcast(Intent("refresh_contact_list"))
    }

    private fun setBlocked(publicKey: ByteArray, blocked: Boolean) {
        val contacts: Contacts = mainActivity!!.binder!!.getContacts()
        val contact = contacts.getContactByPublicKey(publicKey)
        if (contact != null) {
            contact.blocked = blocked
            contacts.addContact(contact)
            refreshContactListBroadcast()
        }
    }

    private fun shareContact(contact: Contact) {
        Log.d(this, "shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.setType("text/plain")
            intent.putExtra(Intent.EXTRA_TEXT, Contact.toJSON(contact, false).toString())
            startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showContactEditDialog(publicKey: ByteArray, name: String) {
        Log.d(this, "showContactEditDialog")
        val contact = mainActivity!!.binder!!.getContactByPublicKey(publicKey) ?: return
        val et = EditText(mainActivity!!)
        et.setText(name)
        val dialog = AlertDialog.Builder(
            mainActivity!!
        )
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(
                R.string.ok,
                DialogInterface.OnClickListener setPositiveButton@{ _: DialogInterface?, _: Int ->
                    val newName: String = et.text.toString().trim { it <= ' ' }
                    if (newName == contact.name) {
                        // nothing to do
                        return@setPositiveButton
                    }
                    if (!Utils.isValidName(newName)) {
                        Toast.makeText(context, "Invalid name.", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (null != mainActivity!!.binder!!.getContacts().getContactByName(newName)) {
                        Toast.makeText(
                            context,
                            "A contact with that name already exists.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    // rename contact
                    contact.name = newName
                    mainActivity!!.binder!!.saveDatabase()

                    refreshContactListBroadcast()
                }).show()
    }

    override fun onPause() {
        super.onPause()
        collapseFab()
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        Log.d(this, "onItemClick")
        val contact = adapterView.adapter.getItem(i) as Contact
        if (contact.addresses.isEmpty()) {
            Toast.makeText(mainActivity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(mainActivity, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }
}