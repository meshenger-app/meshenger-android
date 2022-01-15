package org.rivchain.cuplink

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONException
import org.rivchain.cuplink.MainService.MainBinder

// the main view with tabs
class MainActivity : CupLinkActivity(), ServiceConnection, OnItemClickListener {
    var binder: MainService.MainBinder? = null
    private var contactListView: ListView? = null
    private var fabExpanded = false
    private lateinit var fabScan: FloatingActionButton
    private lateinit var fabGen: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private val refreshContactListReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //refreshContactList();
            val adapter = contactListView!!.adapter as ContactListAdapter
            adapter.notifyDataSetChanged()
        }
    }

    override fun onItemClick(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        log("onItemClick")
        val contact = binder!!.contactsCopy[i]
        val intent = Intent(this, CallActivity::class.java)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        log("onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fab = findViewById(R.id.fab)
        fabScan = findViewById(R.id.fabScan)
        fabGen = findViewById(R.id.fabGenerate)
        contactListView = findViewById(R.id.contactList)
        fabScan.setOnClickListener(View.OnClickListener { v: View? ->
            startActivity(
                Intent(
                    this,
                    QRScanActivity::class.java
                )
            )
        })
        fabGen.setOnClickListener(View.OnClickListener { v: View? ->
            startActivity(
                Intent(
                    this,
                    QRShowActivity::class.java
                )
            )
        })
        fab.setOnClickListener(View.OnClickListener { fab: View -> runFabAnimation(fab) })

        // ask for audio recording permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(refreshContactListReceiver, IntentFilter("refresh_contact_list"))
    }

    private fun refreshContactList() {
        log("refreshContactList")
        Handler(mainLooper).post {
            val contacts = binder!!.contactsCopy
            contactListView!!.adapter = ContactListAdapter(this, R.layout.item_contact, contacts)
            contactListView!!.onItemClickListener = this
            contactListView!!.onItemLongClickListener =
                OnItemLongClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                    val contact = contacts[i]
                    val menu = PopupMenu(this, view)
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
                            showDeleteDialog(publicKey, contact.name)
                        } else if (title == rename) {
                            showContactEditDialog(publicKey, contact.name)
                        } else if (title == share) {
                            shareContact(contact)
                        } else if (title == block) {
                            setBlocked(publicKey, true)
                        } else if (title == unblock) {
                            setBlocked(publicKey, false)
                        } else if (title == qr) {
                            val intent = Intent(this, QRShowActivity::class.java)
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

    private fun showDeleteDialog(publicKey: ByteArray?, name: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.confirm)
        builder.setMessage(resources.getString(R.string.contact_remove) + " " + name)
        builder.setCancelable(false) // prevent key shortcut to cancel dialog
        builder.setPositiveButton(R.string.yes) { dialog: DialogInterface, id: Int ->
            binder!!.deleteContact(publicKey)
            dialog.cancel()
        }
        builder.setNegativeButton(R.string.no) { dialog: DialogInterface, id: Int -> dialog.cancel() }

        // create dialog box
        val alert = builder.create()
        alert.show()
    }

    private fun setBlocked(publicKey: ByteArray?, blocked: Boolean) {
        val contact = binder!!.getContactByPublicKey(publicKey)
        if (contact != null) {
            contact.setBlocked(blocked)
            binder!!.addContact(contact)
        }
    }

    private fun shareContact(c: Contact) {
        log("shareContact")
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, Contact.exportJSON(c, false).toString())
            startActivity(intent)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun showContactEditDialog(publicKey: ByteArray?, name: String) {
        log("showContactEditDialog")
        val et = EditText(this)
        et.setText(name)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.contact_edit)
            .setView(et)
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(R.string.ok) { dialogInterface: DialogInterface?, i: Int ->
                val newName = et.text.toString()
                if (newName.isNotEmpty()) {
                    val contact = binder!!.getContactByPublicKey(publicKey)
                    if (contact != null) {
                        contact.name = newName
                        binder!!.addContact(contact)
                    }
                }
            }.show()
    }

    private fun runFabAnimation(fab: View) {
        log("runFabAnimation")
        val scanSet = AnimationSet(this, null)
        val generateSet = AnimationSet(this, null)
        val distance = 200
        val duration = 300
        val scanAnimation: TranslateAnimation
        val generateAnimnation: TranslateAnimation
        val alphaAnimation: AlphaAnimation
        if (fabExpanded) {
            scanAnimation = TranslateAnimation(0f, 0f, (-distance).toFloat(), 0f)
            generateAnimnation = TranslateAnimation(0f, 0f, (-distance * 2).toFloat(), 0f)
            alphaAnimation = AlphaAnimation(1.0f, 0.0f)
            (fab as FloatingActionButton).setImageResource(R.drawable.qr_glass)
            fabScan!!.y = fabScan!!.y + distance
            fabGen!!.y = fabGen!!.y + distance * 2
        } else {
            scanAnimation = TranslateAnimation(0f, 0f, distance.toFloat(), 0f)
            generateAnimnation = TranslateAnimation(0f, 0f, (distance * 2).toFloat(), 0f)
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
        fabScan!!.show()
        fabGen!!.show()
        fabScan!!.startAnimation(scanSet)
        fabGen!!.startAnimation(generateSet)
        fabExpanded = !fabExpanded
    }

    override fun onDestroy() {
        log("onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshContactListReceiver)
        (this.getSystemService(AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = false
        super.onDestroy()
    }

    override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
        log("OnServiceConnected")
        binder = iBinder as MainBinder
        refreshContactList()
        if(binder!!.settings == null){
            Toast.makeText(this, R.string.choose_address, Toast.LENGTH_LONG).show()
            return;
        }
        // call it here because EventListFragment.onResume is triggered twice
        if (binder!!.settings?.publicKey != null) {
            binder!!.pingContacts()
        }
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
        log("OnServiceDisconnected")
        binder = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        log("onOptionsItemSelected")
        val id = item.itemId
        when (id) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }
            R.id.action_backup -> {}
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MainService::class.java), this, BIND_AUTO_CREATE)
    }

    override fun onResume() {
        log("OnResume")
        super.onResume()
    }

    override fun onPause() {
        log("onPause")
        collapseFab()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        unbindService(this)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.permission_mic, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        log("onCreateOptionsMenu")
        menuInflater.inflate(R.menu.menu_main_activity, menu)
        return true
    }

    private fun log(s: String) {
        Log.d(this, s)
    }
}