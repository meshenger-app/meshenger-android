package org.rivchain.cuplink.rivmesh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import org.rivchain.cuplink.R
import org.rivchain.cuplink.rivmesh.models.PeerInfo

class SelectPeerInfoListAdapter(
    private val mContext: Context,
    private var allPeers: MutableList<PeerInfo>,
    private var currentPeers: MutableSet<PeerInfo>
) : ArrayAdapter<PeerInfo?> (mContext, 0, allPeers as List<PeerInfo?>) {

    override fun getItem(position: Int): PeerInfo {
        return allPeers[position]
    }

    override fun getCount(): Int {
        return allPeers.size
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var peerInfoHolder = PeerInfoHolder()
        var listItem: View? = convertView
        if (listItem == null) {
            listItem = LayoutInflater.from(mContext).inflate(R.layout.host_list_item_edit, parent, false)
            peerInfoHolder.checkbox = listItem?.findViewById(R.id.checkbox)!!
            peerInfoHolder.countryFlag = listItem.findViewById(R.id.countryFlag)!!
            peerInfoHolder.peerInfoText = listItem.findViewById(R.id.hostInfoText)!!
            peerInfoHolder.ping = listItem.findViewById(R.id.ping)!!
            listItem.tag = peerInfoHolder
        } else {
            peerInfoHolder = listItem.tag as PeerInfoHolder
        }
        val currentPeer = allPeers[position]
        peerInfoHolder.countryFlag.setImageResource(currentPeer.getCountry(mContext)!!.flagID)
        val peerId = currentPeer.toString()
        peerInfoHolder.peerInfoText.text = peerId
        if(currentPeer.ping == Int.MAX_VALUE){
            peerInfoHolder.ping.text=""
            peerInfoHolder.peerInfoText.setTextColor(Color.RED)
        } else {
            peerInfoHolder.ping.text = currentPeer.ping.toString() + " ms"
            peerInfoHolder.peerInfoText.setTextColor(Color.LTGRAY)
        }
        peerInfoHolder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!currentPeers.contains(currentPeer)) {
                    currentPeers.add(currentPeer)
                }
            } else {
                if (currentPeers.contains(currentPeer)) {
                    currentPeers.remove(currentPeer)
                }
            }
        }
        peerInfoHolder.peerInfoText.setOnClickListener {
            val clipboard: ClipboardManager =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip =
                ClipData.newPlainText("Peer info", peerId)
            clipboard.setPrimaryClip(clip)
            showToast(peerId + " " + context.getString(R.string.node_info_copied))
        }
        peerInfoHolder.checkbox.isChecked = this.currentPeers.contains(currentPeer)
        return listItem
    }

    fun getSelectedPeers(): Set<PeerInfo> {
        return currentPeers
    }

    fun getAllPeers(): List<PeerInfo> {
        return allPeers
    }

    fun addItem(peerInfo: PeerInfo){
        if(!allPeers.contains(peerInfo)){
            allPeers.add(peerInfo)
        }
    }

    fun addItem(index: Int, peerInfo: PeerInfo){
        allPeers.add(index, peerInfo)
    }

    fun addAll(index: Int, peerInfo: ArrayList<PeerInfo>){
        currentPeers.addAll(peerInfo)
        allPeers.removeAll(peerInfo)
        allPeers.addAll(index, peerInfo)
        this.notifyDataSetChanged()
    }

    fun sort(){
        allPeers = ArrayList(allPeers.sortedWith(compareBy { it.ping }))
        this.notifyDataSetChanged()
    }

    class PeerInfoHolder {
        lateinit var checkbox: CheckBox
        lateinit var countryFlag: ImageView
        lateinit var peerInfoText: TextView
        lateinit var ping: TextView
    }

    private fun showToast(text: String){
        val duration = Toast.LENGTH_SHORT
        val toast = Toast.makeText(context, text, duration)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }
}