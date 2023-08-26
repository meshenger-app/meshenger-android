package d.d.meshenger

import android.content.*
import android.os.Bundle
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

class ContactListFragment : Fragment() {
    private lateinit var contactListView: ListView
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fabPingAll: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private var fabExpanded = false

    private val onContactClickListener =
        AdapterView.OnItemClickListener { adapterView, _, i, _ ->
            Log.d(this, "onItemClick")
            val activity = requireActivity()
            val contact = adapterView.adapter.getItem(i) as Contact
            if (contact.addresses.isEmpty()) {
                Toast.makeText(activity, R.string.contact_has_no_address_warning, Toast.LENGTH_SHORT).show()
            } else {
                Log.d(this, "start CallActivity")
                val intent = Intent(activity, CallActivity::class.java)
                intent.action = "ACTION_OUTGOING_CALL"
                intent.putExtra("EXTRA_CONTACT", contact)
                startActivity(intent)
            }
    }

    private val onContactLongClickListener =
        AdapterView.OnItemLongClickListener { adapterView, view, i, _ ->
            val contact = adapterView.adapter.getItem(i) as Contact
            val menu = PopupMenu(activity, view)
            val details = getString(R.string.contact_menu_details)
            val delete = getString(R.string.contact_menu_delete)
            val ping = getString(R.string.contact_menu_ping)
            val share = getString(R.string.contact_menu_share)
            val qrcode = getString(R.string.contact_menu_qrcode)
            menu.menu.add(details)
            menu.menu.add(delete)
            menu.menu.add(ping)
            menu.menu.add(share)
            menu.menu.add(qrcode)
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                val title = menuItem.title.toString()
                val publicKey = contact.publicKey
                when (title) {
                    details -> {
                        val intent = Intent(activity, ContactDetailsActivity::class.java)
                        intent.putExtra("EXTRA_CONTACT_PUBLICKEY", contact.publicKey)
                        startActivity(intent)
                    }
                    delete -> showDeleteDialog(publicKey, contact.name)
                    ping -> pingContact(contact)
                    share -> shareContact(contact)
                    qrcode -> {
                        val intent = Intent(activity, QRShowActivity::class.java)
                        intent.putExtra("EXTRA_CONTACT_PUBLICKEY", contact.publicKey)
                        startActivity(intent)
                    }
                }
                false
            }
            menu.show()
            true
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(this, "onCreateView")
        val view: View = inflater.inflate(R.layout.fragment_contact_list, container, false)

        fab = view.findViewById(R.id.fab)
        fabScan = view.findViewById(R.id.fabScan)
        fabGen = view.findViewById(R.id.fabGenerate)
        fabPingAll = view.findViewById(R.id.fabPing)
        contactListView = view.findViewById(R.id.contactList)
        contactListView.onItemClickListener = onContactClickListener
        contactListView.onItemLongClickListener = onContactLongClickListener

        val activity = requireActivity()
        fabScan.setOnClickListener {
            val intent = Intent(activity, QRScanActivity::class.java)
            startActivity(intent)
        }

        fabGen.setOnClickListener {
            val binder = (activity as MainActivity).binder
            if (binder != null) {
                val intent = Intent(activity, QRShowActivity::class.java)
                intent.putExtra("EXTRA_CONTACT_PUBLICKEY", binder.getSettings().publicKey)
                startActivity(intent)
            }
        }

        fabPingAll.setOnClickListener {
            pingAllContacts()
            collapseFab()
        }

        fab.setOnClickListener { fab: View -> runFabAnimation(fab) }

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshContactListReceiver, IntentFilter("refresh_contact_list"))

        refreshContactListBroadcast()

        return view
    }

    private val refreshContactListReceiver = object : BroadcastReceiver() {
        //private var lastTimeRefreshed = 0L

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this@ContactListFragment, "trigger refreshContactList() from broadcast at ${this@ContactListFragment.lifecycle.currentState}")
            // prevent this method from being called too often
            //val now = System.currentTimeMillis()
            //if ((now - lastTimeRefreshed) > 1000) {
            //    lastTimeRefreshed = now
                refreshContactList()
            //}
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(refreshContactListReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        Log.d(this, "onResume()")
        super.onResume()

        val activity = requireActivity() as MainActivity
        val binder = (activity as MainActivity).binder
        if (binder != null) {
            if (binder.getSettings().automaticStatusUpdates) {
                // ping all contacts
                binder.pingContacts(binder.getContacts().contactList)
            }
        }

        MainService.refreshContacts(requireActivity())
    }

    private fun showPingAllButton(): Boolean {
        val binder = (activity as MainActivity).binder
        if (binder != null) {
            return !binder.getSettings().automaticStatusUpdates
        } else {
            // it does not hurt to show the button
            return true
        }
    }

    private fun runFabAnimation(fab: View) {
        Log.d(this, "runFabAnimation")
        val activity = requireActivity()
        val scanSet = AnimationSet(activity, null)
        val showSet = AnimationSet(activity, null)
        val pingSet = AnimationSet(activity, null)
        val distance = 200f
        val duration = 300f
        val scanAnimation: TranslateAnimation
        val showAnimation: TranslateAnimation
        val pingAnimation: TranslateAnimation
        val alphaAnimation: AlphaAnimation

        if (fabExpanded) {
            pingAnimation = TranslateAnimation(0f, 0f, -distance * 1, 0f)
            scanAnimation = TranslateAnimation(0f, 0f, -distance * 2, 0f)
            showAnimation = TranslateAnimation(0f, 0f, -distance * 3, 0f)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabGen.y = fabGen.y + distance * 1
            fabScan.y = fabScan.y + distance * 2
            fabPingAll.y = fabPingAll.y + distance * 3
        } else {
            pingAnimation = TranslateAnimation(0f, 0f, distance * 1, 0f)
            scanAnimation = TranslateAnimation(0f, 0f, distance * 2, 0f)
            showAnimation = TranslateAnimation(0f, 0f, distance * 3, 0f)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabGen.y = fabGen.y - distance * 1
            fabScan.y = fabScan.y - distance * 2
            fabPingAll.y = fabPingAll.y - distance * 3
        }

        scanSet.addAnimation(scanAnimation)
        scanSet.addAnimation(alphaAnimation)
        scanSet.fillAfter = true
        scanSet.duration = duration.toLong()

        showSet.addAnimation(showAnimation)
        showSet.addAnimation(alphaAnimation)
        showSet.fillAfter = true
        showSet.duration = duration.toLong()

        pingSet.addAnimation(pingAnimation)
        pingSet.addAnimation(alphaAnimation)
        pingSet.fillAfter = true
        pingSet.duration = duration.toLong()

        fabGen.visibility = View.VISIBLE
        fabScan.visibility = View.VISIBLE
        if (showPingAllButton()) {
            fabPingAll.visibility = View.VISIBLE
        }

        fabScan.startAnimation(scanSet)
        fabGen.startAnimation(showSet)
        fabPingAll.startAnimation(pingSet)

        fabExpanded = !fabExpanded
    }

    private fun collapseFab() {
        if (fabExpanded) {
            fab.setImageResource(R.drawable.qr_glass)
            fabScan.clearAnimation()
            fabGen.clearAnimation()
            fabPingAll.clearAnimation()

            fabGen.y = fabGen.y + 200 * 1
            fabScan.y = fabScan.y + 200 * 2
            if (showPingAllButton()) {
                fabPingAll.y = fabPingAll.y + 200 * 3
            }
            fabExpanded = false
        }
    }

    private fun pingContact(contact: Contact) {
        val binder = (activity as MainActivity).binder ?: return
        binder.pingContacts(listOf(contact))
        val message = String.format(getString(R.string.ping_contact), contact.name)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun pingAllContacts() {
        val binder = (activity as MainActivity).binder ?: return
        binder.pingContacts(binder.getContacts().contactList)
        val message = String.format(getString(R.string.ping_all_contacts))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteDialog(publicKey: ByteArray, name: String) {
        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(R.string.dialog_title_confirm)
        builder.setMessage(String.format(getString(R.string.contact_remove), name))
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.button_yes) { dialog: DialogInterface, _: Int ->
                binder.deleteContact(publicKey)
                dialog.cancel()
            }

        builder.setNegativeButton(R.string.button_no) { dialog: DialogInterface, _: Int ->
            dialog.cancel() }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    private fun refreshContactList() {
        Log.d(this, "refreshContactList")

        val activity = requireActivity()
        val binder = (activity as MainActivity).binder ?: return
        val contacts = binder.getContacts().contactList

        activity.runOnUiThread {
            contactListView.adapter = ContactListAdapter(activity, R.layout.item_contact, contacts)
        }
    }

    private fun refreshContactListBroadcast() {
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent("refresh_contact_list"))
    }

    private fun shareContact(contact: Contact) {
        Log.d(this, "shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, Contact.toJSON(contact, false).toString())
            startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        collapseFab()
    }
}
