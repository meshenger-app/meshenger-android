package org.rivchain.cuplink.rivmesh

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rivchain.cuplink.MainService
import org.rivchain.cuplink.R
import org.rivchain.cuplink.rivmesh.util.Utils
import org.rivchain.cuplink.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

open class TestPortActivity: AppCompatActivity(), ServiceConnection {

    protected var service: MainService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(Intent(this, MainService::class.java), this, 0)
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        Log.d(this, "onServiceConnected")
        service = (iBinder as MainService.MainBinder).getService()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        // nothing todo
    }

    /**
     * Run te after the onServiceConnected!
     */
    protected fun getPublicPeerPort(): Int {
        val listenArray = service!!.getMesh().getListen()
        val port: Int
        if(listenArray.length() == 0){
            // Generate a random port and continue
            port = Utils.generateRandomPort()
        } else {
            val listen = service!!.getMesh().getListen().get(0).toString()
            port = URI(listen).port
        }
        return port
    }

    protected fun portTest(port: Int) {

        val url = "https://map.rivchain.org/rest/peer/$port"
        Log.d(this, "Sending GET $url")
        // Perform network request in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                try {
                    val responseCode = sendGet(url)
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        portOpen(port)
                    } else {
                        portClosed(port)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showToast("An error occurred: ${e.message}")
                }
            }
        }
    }

    protected open fun portOpen(port: Int) {

    }

    protected open fun portClosed(port: Int) {

    }

    protected fun connectAsPublicPeer(port: Int) {
        // Public peers have been already assigned on MainService start
        // Now we check the status of the public ip:port
        val url = "https://map.rivchain.org/rest/peer"
        val requestBody = "{\"port\":$port}"
        // Start countdown
        showCountdownDialog()
        // Perform network request in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                try {
                    val responseCode = sendPost(url, requestBody)
                    if (responseCode == HttpURLConnection.HTTP_CREATED) {
                        showToast("Your peer has been registered successfully!")
                        connectedAsPublicPeer(port)
                    } else {
                        showToast("Port $port is unreachable")
                        notConnectedAsPublicPeer(port)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    showToast("An error occurred: ${e.message}")
                }
            }
        }
    }

    private fun sendGet(url: String): Int {
        val urlObject = URL(url)
        val connection = urlObject.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.doOutput = false // Ensure this is set to false for GET requests

        // Get the response code
        val responseCode = connection.responseCode

        // Close the connection
        connection.disconnect()

        return responseCode
    }

    private fun sendPost(url: String, requestBody: String): Int {
        val urlObject = URL(url)
        val connection = urlObject.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.doOutput = true

        // Write the request body
        val outputStream = connection.outputStream
        val writer = OutputStreamWriter(outputStream, "UTF-8")
        writer.write(requestBody)
        writer.flush()
        writer.close()

        // Get the response code
        val responseCode = connection.responseCode

        // Close the connection
        connection.disconnect()

        return responseCode
    }

    protected open fun connectedAsPublicPeer(port: Int) {

    }

    protected open fun notConnectedAsPublicPeer(port: Int) {

    }

    protected fun showCountdownDialog() {
        // Inflate the dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_countdown, null)

        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Get the TextView from the dialog layout
        val countdownTextView: TextView = dialogView.findViewById(R.id.countdownTextView)

        // Start the countdown timer
        object : CountDownTimer(6000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                countdownTextView.text = "$secondsLeft sec left"
            }

            override fun onFinish() {
                // Dismiss the dialog when the countdown finishes
                alertDialog.dismiss()
            }
        }.start()

        // Show the dialog
        alertDialog.show()
    }

    protected fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@TestPortActivity, message, Toast.LENGTH_LONG).show()
        }
    }

}