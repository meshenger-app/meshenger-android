package org.rivchain.cuplink.rivmesh

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mm2d.upnp.Adapter.discoveryListener
import net.mm2d.upnp.Adapter.iconFilter
import net.mm2d.upnp.ControlPoint
import net.mm2d.upnp.ControlPointFactory
import net.mm2d.upnp.Protocol
import org.rivchain.cuplink.R
import org.rivchain.cuplink.util.Log
import org.rivchain.cuplink.util.NetworkUtils
import org.rivchain.cuplink.util.ServiceUtil
import java.io.IOException

class ConfigurePublicPeerActivity: TestPortActivity() {

    private lateinit var lock: WifiManager.MulticastLock
    private lateinit var controlPoint: ControlPoint
    private var port = 0
    private lateinit var publicPeerAgreementDialog: AlertDialog
    private lateinit var portInfoText: TextView
    private lateinit var portStatus: ImageButton
    private lateinit var portStatusText: TextView
    private lateinit var upnpCheckbox: CheckBox
    private lateinit var okButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty)
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        super.onServiceConnected(name, iBinder)
        port = getPublicPeerPort()
        showPortStatusDialog()
    }

    private fun showPortStatusDialog(){
        // Inflate the layout for the dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_configure_public_peer_activity, null)

        // Create the AlertDialog
        publicPeerAgreementDialog = AlertDialog.Builder(this, R.style.PPTCDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        portInfoText = dialogView.findViewById(R.id.portInfoText)
        portStatus = dialogView.findViewById(R.id.portStatus)
        portStatusText = dialogView.findViewById(R.id.portStatusText)
        val colorStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked), // checked state
                intArrayOf(-android.R.attr.state_checked) // unchecked state
            ),
            intArrayOf(
                ContextCompat.getColor(this, R.color.light_light_grey), // color for checked state
                ContextCompat.getColor(this, R.color.light_grey) // color for unchecked state
            )
        )
        upnpCheckbox = dialogView.findViewById(R.id.upnpCheckBox)
        upnpCheckbox.setTextColor(colorStateList)

        okButton = dialogView.findViewById(R.id.OkButton)
        cancelButton = dialogView.findViewById(R.id.CancelButton)
        portStatus.setOnClickListener {
            portStatus.setImageResource(R.drawable.ic_status_unknown)
            // test port manually
            showCountdownDialog()
            if(upnpCheckbox.isChecked){
                val ips = NetworkUtils.getLocalInterfaceIPs()
                val wifi = ServiceUtil.getWifiManager(this)
                lock = wifi.createMulticastLock("ssdp")
                lock.acquire()
                // Initialize mmupnp ControlPoint
                controlPoint = ControlPointFactory.create(protocol = Protocol.IP_V4_ONLY).also {
                    it.setIconFilter(iconFilter { list -> list })
                    it.initialize()
                }
                controlPoint.setSsdpMessageFilter { ssdpMessage ->
                    Log.d(this@ConfigurePublicPeerActivity, ssdpMessage.toString()); true
                }
                try {
                    CoroutineScope(Dispatchers.Main).launch {
                        withContext(Dispatchers.IO) {
                            for (ip in ips) {
                                controlPoint.addDiscoveryListener(discoveryListener({
                                    //discover
                                    Log.d(this, "Devices found: " + controlPoint.deviceListSize.toString())
                                    for(device in controlPoint.deviceList){
                                        NetworkUtils.openPortWithUPnP(device, ip, port, port)
                                    }
                                }, {
                                    //lost
                                }))
                            }
                        }
                    }
                    controlPoint.start()
                    controlPoint.search()
                } catch (e: IOException){
                    e.printStackTrace()
                }
            }
            portTest(port)
        }
        cancelButton.setOnClickListener {
            // skip public peer connection
            publicPeerAgreementDialog.dismiss()
            finish()
        }
        portInfoText.text = "TCP $port"
        // Show the dialog
        publicPeerAgreementDialog.show()
    }

    override fun portOpen(port: Int) {
        // change port image and text status
        portStatus.setImageResource(R.drawable.ic_status_green)
        portStatusText.setTextColor(Color.GREEN)
        portStatusText.text = "Open"
        // activate Ok button
        okButton.isEnabled = true
        okButton.setOnClickListener {
            // confirm public peer creation
            connectAsPublicPeer(port)
        }
        cleanUp()
    }

    override fun portClosed(port: Int) {
        // change port image and text status
        portStatus.setImageResource(R.drawable.ic_status_red)
        portStatusText.setTextColor(Color.RED)
        portStatusText.text = "Closed"
        // deactivate Ok button
        okButton.isEnabled = false
        okButton.setOnClickListener { }
        cleanUp()
    }

    private fun cleanUp(){
        if(this::lock.isInitialized && lock.isHeld) {
            lock.release();
        }
        if(this::controlPoint.isInitialized){
            controlPoint.terminate()
        }
    }

    override fun connectedAsPublicPeer(port: Int) {
        // notify success
        finish()
    }

    override fun notConnectedAsPublicPeer(port: Int) {
        //do nothing and retry to connect from dialog
    }

}