package org.rivchain.cuplink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException
import org.rivchain.cuplink.adapter.ContactListAdapter
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.util.Log

class ContactListFragment() : Fragment() {
    private lateinit var service: MainService
    private lateinit var contactListView: ListView
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fabPingAll: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private var fabExpanded = false

    fun setService(service: MainService){
        this.service = service
    }

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
        AdapterView.OnItemLongClickListener { adapterView, _, i, _ ->
            val contact = adapterView.adapter.getItem(i) as Contact
            val options = listOf(
                getString(R.string.contact_menu_details),
                getString(R.string.contact_menu_delete),
                getString(R.string.contact_menu_ping),
                getString(R.string.contact_menu_share),
                getString(R.string.contact_menu_qrcode)
            )

            val inflater = LayoutInflater.from(activity)
            val dialogView = inflater.inflate(R.layout.dialog_select_one_listview_item, null)
            val listViewContactOptions: ListView = dialogView.findViewById(R.id.listView)

            val adapter = ArrayAdapter(this.requireContext(), R.layout.spinner_item, options)
            listViewContactOptions.adapter = adapter

            val dialog = AlertDialog.Builder(this.requireContext(), R.style.PPTCDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            listViewContactOptions.setOnItemClickListener { _, _, position, _ ->
                val selectedOption = options[position]
                val publicKey = contact.publicKey
                when (selectedOption) {
                    getString(R.string.contact_menu_details) -> {
                        val intent = Intent(activity, ContactDetailsActivity::class.java)
                        intent.putExtra("EXTRA_CONTACT_PUBLICKEY", contact.publicKey)
                        startActivity(intent)
                    }
                    getString(R.string.contact_menu_delete) -> showDeleteDialog(publicKey, contact.name)
                    getString(R.string.contact_menu_ping) -> pingContact(contact)
                    getString(R.string.contact_menu_share) -> shareContact(contact)
                    getString(R.string.contact_menu_qrcode) -> {
                        val intent = Intent(activity, QRShowActivity::class.java)
                        intent.putExtra("EXTRA_CONTACT_PUBLICKEY", contact.publicKey)
                        startActivity(intent)
                    }
                }
                dialog.dismiss()
            }

            dialog.show()
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
            val intent = Intent(activity, QRShowActivity::class.java)
            intent.putExtra("EXTRA_CONTACT_PUBLICKEY", service.getSettings().publicKey)
            startActivity(intent)
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

        if (service.getSettings().automaticStatusUpdates) {
            // ping all contacts
            service.pingContacts(service.getContacts().contactList)
        }

        MainService.refreshContacts(requireActivity())
    }

    private fun showPingAllButton(): Boolean {
        return !service.getSettings().automaticStatusUpdates
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
            fabGen.y += distance * 1
            fabScan.y += distance * 2
            fabPingAll.y += distance * 3
        } else {
            pingAnimation = TranslateAnimation(0f, 0f, distance * 1, 0f)
            scanAnimation = TranslateAnimation(0f, 0f, distance * 2, 0f)
            showAnimation = TranslateAnimation(0f, 0f, distance * 3, 0f)
            alphaAnimation = AlphaAnimation(0.0f, 1.0f)
            (fab as FloatingActionButton).setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            fabGen.y -= distance * 1
            fabScan.y -= distance * 2
            fabPingAll.y -= distance * 3
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

            fabGen.y += 200 * 1
            fabScan.y += 200 * 2
            if (showPingAllButton()) {
                fabPingAll.y += 200 * 3
            }
            fabExpanded = false
        }
    }

    private fun pingContact(contact: Contact) {
        service.pingContacts(listOf(contact))
        val message = String.format(getString(R.string.ping_contact), contact.name)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun pingAllContacts() {
        service.pingContacts(service.getContacts().contactList)
        val message = String.format(getString(R.string.ping_all_contacts))
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteDialog(publicKey: ByteArray, name: String) {
        val activity = requireActivity()
        val builder = AlertDialog.Builder(activity, R.style.FullPPTCDialog)
        builder.setTitle(R.string.dialog_title_delete_contact)
        builder.setMessage(name)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.button_yes) { dialog: DialogInterface, _: Int ->
                service.deleteContact(publicKey)
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
        val contacts = service.getContacts().contactList

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
