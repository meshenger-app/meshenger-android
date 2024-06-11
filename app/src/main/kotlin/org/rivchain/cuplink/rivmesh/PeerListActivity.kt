package org.rivchain.cuplink.rivmesh

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.URLUtil
import android.widget.Button
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hbb20.CountryCodePicker
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rivchain.cuplink.R
import org.rivchain.cuplink.SettingsActivity
import org.rivchain.cuplink.rivmesh.models.PeerInfo
import org.rivchain.cuplink.rivmesh.util.Utils.ping
import java.net.InetAddress
import java.util.Locale
class PeerListActivity : SelectPeerActivity() {

    private var popup: PopupWindow? = null
    private lateinit var adapter: SelectPeerInfoListAdapter
    override fun setAlreadySelectedPeers(alreadySelectedPeers: MutableSet<PeerInfo>) {
        adapter = SelectPeerInfoListAdapter(this, arrayListOf(), alreadySelectedPeers)
        val peerList = findViewById<ListView>(R.id.peerList)
        peerList.adapter = adapter
    }
    override fun addPeer(peerInfo: PeerInfo){
        adapter.addItem(peerInfo)
        if (adapter.count % 5 == 0) {
            adapter.sort()
        }
    }

    override fun addAlreadySelectedPeers(alreadySelectedPeers: ArrayList<PeerInfo>){
        adapter.addAll(0, alreadySelectedPeers)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_peer_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        super.onCreate(savedInstanceState)

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { _ ->
            addNewPeer()
        }
    }

    private fun editPeerListUrl() {
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_edit_peer_list_url, null)
        val ab: AlertDialog.Builder = AlertDialog.Builder(this, R.style.PPTCDialog)
        ab.setCancelable(true).setView(view)
        val ad = ab.show()
        val saveButton = view.findViewById<Button>(R.id.save)
        val urlInput = view.findViewById<TextView>(R.id.urlInput)
        urlInput.text = peerListUrl
        saveButton.setOnClickListener{

            val url = urlInput.text.toString()
            if(!URLUtil.isValidUrl(url)){
                urlInput.error = "The URL is invalid!"
                return@setOnClickListener;
            }
            peerListUrl = url
            val preferences =
                PreferenceManager.getDefaultSharedPreferences(this.baseContext)
            preferences.edit().putString(PEER_LIST, peerListUrl).apply()
            ad.dismiss()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun addNewPeer() {
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_add_peer, null)
        val countryCode: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.resources.configuration.locales[0].country
        } else {
            this.resources.configuration.locale.country
        }
        val schemaInput = view.findViewById<TextView>(R.id.schemaInput)
        val ipInput = view.findViewById<TextView>(R.id.ipInput)
        ipInput.requestFocus()
        schemaInput.showSoftInputOnFocus = false
        schemaInput.setOnFocusChangeListener { v, _ ->
            if(schemaInput.isFocused) {
                onClickSchemaList(v)
            }
        }
        schemaInput.setOnClickListener { v->
            onClickSchemaList(v)
        }
        getPopupWindow(
            R.layout.spinner_item,
            resources.getStringArray(R.array.schemas),
            schemaInput
        );
        view.findViewById<CountryCodePicker>(R.id.ccp).setCountryForNameCode(countryCode)
        val ab: AlertDialog.Builder = AlertDialog.Builder(this, R.style.PPTCDialog)
        ab.setCancelable(true).setView(view)
        val ad = ab.show()
        val addButton = view.findViewById<Button>(R.id.add)
        addButton.setOnClickListener{
            val portInput = view.findViewById<TextView>(R.id.portInput)
            val ccpInput = view.findViewById<CountryCodePicker>(R.id.ccp)
            val schema = schemaInput.text.toString().lowercase(Locale.ROOT)
            if(schema.isEmpty()){
                schemaInput.error = "Schema is required"
                return@setOnClickListener
            }
            val ip = ipInput.text.toString().lowercase(Locale.ROOT)
            if(ip.isEmpty()){
                ipInput.error = "IP address is required"
                return@setOnClickListener
            }
            if(portInput.text.isEmpty()){
                portInput.error = "Port is required"
                return@setOnClickListener
            }
            val port = portInput.text.toString().toInt()
            if(port<=0){
                portInput.error = "Port should be > 0"
                return@setOnClickListener
            }
            if(port>=Short.MAX_VALUE){
                portInput.error = "Port should be < "+Short.MAX_VALUE
                return@setOnClickListener
            }
            val ccp = ccpInput.selectedCountryNameCode
            GlobalScope.launch {
                val pi = PeerInfo(schema,
                    withContext(Dispatchers.IO) {
                        InetAddress.getByName(ip)
                    }, port, ccp, false)
                try {
                    val ping = ping(pi.hostName, pi.port)
                    pi.ping = ping
                } catch (e: Throwable){
                    pi.ping = Int.MAX_VALUE
                }
                withContext(Dispatchers.Main) {
                    val selectAdapter = (findViewById<ListView>(R.id.peerList).adapter as SelectPeerInfoListAdapter)
                    selectAdapter.addItem(0, pi)
                    selectAdapter.notifyDataSetChanged()
                    ad.dismiss()
                }
            }
        }
    }

    private fun onClickSchemaList(v: View) {
        val height = -1 * v.height +30
        getAddressListPopup()?.showAsDropDown(v, -5, height)
    }

    private fun getAddressListPopup(): PopupWindow? {
        return popup
    }

    private fun getPopupWindow(
        textViewResourceId: Int,
        objects: Array<String>,
        editText: TextView
    ): PopupWindow {
        // initialize a pop up window type
        val popupWindow = PopupWindow(this)
        // the drop down list is a list view
        val listView = ListView(this)
        listView.dividerHeight = 0
        // set our adapter and pass our pop up window contents
        val adapter = DropDownAdapter(this, textViewResourceId, objects, popupWindow, editText)
        listView.adapter = adapter
        // set the item click listener
        listView.onItemClickListener = adapter
        // some other visual settings
        popupWindow.isFocusable = true
        popupWindow.width = 320
        popupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        // set the list view as pop up window content
        popupWindow.contentView = listView
        popup = popupWindow
        return popupWindow
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.save_peers, menu)
        val item = menu.findItem(R.id.saveItem) as MenuItem
        item.setActionView(R.layout.menu_save)
        val saveButton = item
            .actionView?.findViewById<Button>(R.id.saveButton)
        saveButton?.setOnClickListener {
            saveButton.isClickable = false
            cancelPeerListPing()
            val result = Intent(this, SettingsActivity::class.java)
            val adapter = findViewById<ListView>(R.id.peerList).adapter as SelectPeerInfoListAdapter

            val selectedPeers = adapter.getSelectedPeers()
            saveSelectedPeers(selectedPeers)
            restartService()
        }

        val editUrl = menu.findItem(R.id.editUrlItem) as MenuItem
        editUrl.setActionView(R.layout.menu_edit_url)
        val editUrlButton = editUrl
            .actionView?.findViewById<Button>(R.id.editUrlButton)
        editUrlButton?.setOnClickListener {
            editPeerListUrl()
        }
        return true
    }

    override fun onServiceRestart() {
        super.onServiceRestart()
        finish()
    }
}