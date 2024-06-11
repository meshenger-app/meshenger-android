package org.rivchain.cuplink.rivmesh

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.rivchain.cuplink.R

class ConfigurePublicPeerActivity: TestPortActivity() {

    private var port = 0
    private lateinit var publicPeerAgreementDialog: AlertDialog
    private lateinit var portInfoText: TextView
    private lateinit var portStatus: ImageButton
    private lateinit var portStatusText: TextView
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

        okButton = dialogView.findViewById(R.id.OkButton)
        cancelButton = dialogView.findViewById(R.id.CancelButton)
        portStatus.setOnClickListener {
            portStatus.setImageResource(R.drawable.ic_status_unknown)
            // test port manually
            showCountdownDialog()
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
    }

    override fun portClosed(port: Int) {
        // change port image and text status
        portStatus.setImageResource(R.drawable.ic_status_red)
        portStatusText.setTextColor(Color.RED)
        portStatusText.text = "Closed"
        // deactivate Ok button
        okButton.isEnabled = false
        okButton.setOnClickListener {  }
    }

    override fun connectedAsPublicPeer(port: Int) {
        // notify success
        finish()
    }

    override fun notConnectedAsPublicPeer(port: Int) {
        //do nothing and retry to connect from dialog
    }

}