package org.rivchain.cuplink.rivmesh

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import org.rivchain.cuplink.R
import org.rivchain.cuplink.rivmesh.models.PeerInfo
import org.rivchain.cuplink.rivmesh.models.Status
import kotlin.math.exp
import kotlin.math.pow

class AutoSelectPeerActivity: SelectPeerActivity() {

    private var addedPeers = 0

    private var maxPeersSelection = 3

    private lateinit var currentStageTextView: MaterialTextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressDialog: AlertDialog

    private val selectedPeers = ArrayList<PeerInfo>()

    override fun peersMap(peersMap: Map<String, Map<String, Status>>) {
        // Calculate total peers with up status
        var totalProbablyOnlinePeers = 0
        for ((country, peers) in peersMap.entries) {
            for ((peer, status) in peers) {
                if (status.up) {
                    totalProbablyOnlinePeers++
                }
            }
        }
        if(totalProbablyOnlinePeers == 0){
            // TODO show up a warning here
            finish()
        }
        if(totalProbablyOnlinePeers < this@AutoSelectPeerActivity.maxPeersSelection){
            this@AutoSelectPeerActivity.maxPeersSelection = totalProbablyOnlinePeers
        }
    }

    override fun addPeer(peerInfo: PeerInfo){
        addedPeers++
        selectedPeers.add(peerInfo)
        // Update progress
        updateProgress()
    }

    override fun addAlreadySelectedPeers(alreadySelectedPeers: ArrayList<PeerInfo>){
        selectedPeers.addAll(alreadySelectedPeers)

        if (selectedPeers.size == 0) {
            // TODO show up a warning here
            finish()
        }
        // Do peers selection
        super.saveSelectedPeers(selectPeers(selectedPeers))
        // TODO show success status
        progressDialog.dismiss()
        finish()
    }

    private fun selectPeers(currentPeers: ArrayList<PeerInfo>): Set<PeerInfo> {
        val selectedPeers = mutableSetOf<PeerInfo>()
        val size = currentPeers.size
        if (size <= maxPeersSelection) {
            return currentPeers.toSet()
        } else {
            // Calculate selection probabilities for the top 3 peers
            val topProbabilities = ArrayList<Double>()
            for (i in 0 until maxPeersSelection) {
                topProbabilities.add(1.0)
            }
            // Calculate total probability for normalization
            val totalProbability = topProbabilities.sum()

            // Select the top 3 peers
            for (i in 0 until maxPeersSelection) {
                var selection = (totalProbability * Math.random()).toFloat()
                for (j in topProbabilities.indices) {
                    selection -= topProbabilities[j].toFloat()
                    if (selection <= 0) {
                        selectedPeers.add(currentPeers[j])
                        break
                    }
                }
            }

            // If more peers need to be selected, consider the remaining peers
            if (size > maxPeersSelection) {
                val remainingPeers = currentPeers.subList(maxPeersSelection, size)
                val remainingSize = remainingPeers.size
                // Calculate selection probabilities for the remaining peers based on normal distribution
                val remainingProbabilities = ArrayList<Double>()
                for (i in maxPeersSelection until size) {
                    val probability = exp(-((i - maxPeersSelection).toDouble() / remainingSize.toDouble()).pow(2))
                    remainingProbabilities.add(probability)
                }
                val maxRemainingProbability = remainingProbabilities.maxOrNull() ?: 1.0
                for (i in remainingProbabilities.indices) {
                    remainingProbabilities[i] /= maxRemainingProbability // Normalize probabilities
                }

                // Select remaining peers based on probabilities until 3 peers are selected
                for (i in 0 until (maxPeersSelection - selectedPeers.size)) {
                    var selection = (totalProbability * Math.random()).toFloat()
                    for (j in remainingProbabilities.indices) {
                        selection -= remainingProbabilities[j].toFloat()
                        if (selection <= 0) {
                            selectedPeers.add(remainingPeers[j])
                            break
                        }
                    }
                }
            }
        }
        return selectedPeers
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_empty)
        // Inflate the layout for the dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)

        // Create the AlertDialog
        progressDialog = AlertDialog.Builder(this, R.style.PPTCDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        currentStageTextView = dialogView.findViewById(R.id.current_stage)
        progressBar = dialogView.findViewById(R.id.progress_bar)

        // Show the dialog
        progressDialog.show()

        // Initialize progress
        updateProgress()
    }

    private fun updateProgress() {
        if (maxPeersSelection > 0) {
            val progress = (addedPeers.toFloat() / maxPeersSelection) * 100
            progressBar.progress = progress.toInt()
            currentStageTextView.text = "Adding Peers ($addedPeers / $maxPeersSelection)"
        }
    }
}