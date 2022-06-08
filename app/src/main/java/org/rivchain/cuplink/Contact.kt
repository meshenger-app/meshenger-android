package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.*

class Contact : Serializable {
    var name: String
    var publicKey: ByteArray?
        private set
    private var blocked: Boolean
    private var addresses: MutableList<InetSocketAddress>

    // contact state
    var state = State.PENDING

    // last working address (use this address next connection and for unknown contact initialization)
    var lastWorkingAddress: InetSocketAddress? = null
        private set

    constructor(name: String, pubkey: ByteArray?, addresses: MutableList<String>) {
        this.name = name
        publicKey = pubkey
        blocked = false
        this.addresses = ArrayList()
        for (addr in addresses) {
            addAddress(addr)
        }
    }

    private constructor() {
        name = ""
        publicKey = null
        blocked = false
        addresses = ArrayList()
    }

    fun getAddresses(): List<InetSocketAddress>? {
        if (addresses.size==0){
            return null
        }
        return addresses
    }

    fun addAddress(address: String) {
        if (address.isEmpty()) {
            return
        }
        for (addr in addresses) {
            if (addr.address.hostAddress.equals(address, ignoreCase = true)) {
                return
            }
        }
        addresses.add(Utils.parseInetSocketAddress(address, MainService.serverPort)!!)
    }

    fun getBlocked(): Boolean {
        return blocked
    }

    fun setBlocked(blocked: Boolean) {
        this.blocked = blocked
    }

    /*
     * Create a connection to the contact.
     * Try/Remember the last successful address.
     */
    fun createSocket(): Socket? {
        var socket: Socket? = null
        val connectionTimeout = 300

        // try last successful address first
        if (lastWorkingAddress != null) {
            log("try latest address: " + lastWorkingAddress)
            socket = establishConnection(lastWorkingAddress!!, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }
        for (address in addresses) {
            log("try address: '" + address.hostName + "', port: " + address.port)
            socket = establishConnection(address, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }
        return null
    }

    // set good address to try first next time
    fun setLastWorkingAddress(address: InetSocketAddress) {
        log("setLatestWorkingAddress: $address")
        lastWorkingAddress = address
    }

    private fun log(s: String) {
        Log.d(this, s)
    }

    enum class State {
        ONLINE, OFFLINE, PENDING
    }

    companion object {
        private fun establishConnection(address: InetSocketAddress, timeout: Int): Socket? {
            val socket = Socket()
            try {
                // timeout to establish connection
                socket.connect(address, timeout)
                return socket
            } catch (e: SocketTimeoutException) {
                // ignore
                e.printStackTrace()
            } catch (e: ConnectException) {
                // device is online, but does not listen on the given port
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (socket != null) {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
            return null
        }

        @Throws(JSONException::class)
        fun exportJSON(contact: Contact, all: Boolean): JSONObject {
            val `object` = JSONObject()
            val array = JSONArray()
            `object`.put("name", contact.name)
            `object`.put("public_key", Utils.byteArrayToHexString(contact.publicKey))
            for (address in contact.getAddresses()!!) {
                array.put(address.address.hostAddress)
            }
            `object`.put("addresses", array)
            if (all) {
                `object`.put("blocked", contact.blocked)
            }
            return `object`
        }

        @Throws(Exception::class)
        fun importJSON(`object`: JSONObject, all: Boolean): Contact {
            val contact = Contact()
            contact.name = `object`.getString("name")
            contact.publicKey = Utils.hexStringToByteArray(`object`.getString("public_key"))
            if (!Utils.isValidName(contact.name)) {
                throw JSONException("Invalid Name.")
            }
            if (contact.publicKey == null) {
                throw JSONException("Invalid Public Key.")
            }
            val array = `object`.getJSONArray("addresses")
            var i = 0
            while (i < array.length()) {
                contact.addAddress(array.getString(i).toLowerCase(Locale.ROOT).trim { it <= ' ' })
                i += 1
            }
            if (all) {
                contact.blocked = `object`.getBoolean("blocked")
            }
            return contact
        }
    }
}