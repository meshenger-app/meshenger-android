package d.d.meshenger

import d.d.meshenger.Utils.isMAC
import d.d.meshenger.Utils.getAddressPermutations
import d.d.meshenger.Utils.parseInetSocketAddress
import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Utils.hexStringToByteArray
import d.d.meshenger.Utils.isValidName
import d.d.meshenger.MainService
import kotlin.Throws
import d.d.meshenger.Contact
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import org.libsodium.jni.Sodium
import java.io.Serializable
import java.lang.Exception
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.ArrayList

class Contact : Serializable {
    enum class State {
        ONLINE, OFFLINE, PENDING
    }

    private var name: String
    var publicKey: ByteArray?
        private set
    private var blocked: Boolean
    private var addresses: MutableList<String>

    // contact state
    var state = State.PENDING

    // last working address (use this address next connection and for unknown contact initialization)
    var lastWorkingAddress: InetSocketAddress? = null
        private set

    constructor(name: String, pubkey: ByteArray?, addresses: MutableList<String>) {
        this.name = name
        publicKey = pubkey
        blocked = false
        this.addresses = addresses
    }

    private constructor() {
        name = ""
        publicKey = null
        blocked = false
        addresses = ArrayList()
    }

    fun getAddresses(): List<String> {
        return addresses
    }

    fun addAddress(address: String) {
        if (address.isEmpty()) {
            return
        }
        for (addr in addresses) {
            if (addr.equals(address, ignoreCase = true)) {
                return
            }
        }
        addresses.add(address)
    }

    // also resolves domains
    val allSocketAddresses: List<InetSocketAddress?>
        get() {
            val addrs: MutableList<InetSocketAddress?> = ArrayList()
            for (address in addresses) {
                try {
                    if (isMAC(address)) {
                        addrs.addAll(getAddressPermutations(address, MainService.serverPort))
                    } else {
                        // also resolves domains
                        addrs.add(parseInetSocketAddress(address, MainService.serverPort))
                    }
                } catch (e: Exception) {
                    log("invalid address: $address")
                    e.printStackTrace()
                }
            }
            return addrs
        }

    fun getName(): String {
        return name
    }

    fun setName(name: String) {
        this.name = name
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
            log("try latest address: " + lastWorkingAddress!!)
            socket = establishConnection(lastWorkingAddress!!, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }
        for (address in allSocketAddresses) {
            log("try address: '" + address!!.hostName + "', port: " + address.port)
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

    companion object {
        private fun establishConnection(address: InetSocketAddress, timeout: Int): Socket? {
            val socket = Socket()
            try {
                // timeout to establish connection
                socket.connect(address, timeout)
                return socket
            } catch (e: SocketTimeoutException) {
                // ignore
            } catch (e: ConnectException) {
                // device is online, but does not listen on the given port
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

        @JvmStatic
        @Throws(JSONException::class)
        fun exportJSON(contact: Contact, all: Boolean): JSONObject {
            val `object` = JSONObject()
            val array = JSONArray()
            `object`.put("name", contact.name)
            `object`.put("public_key", byteArrayToHexString(contact.publicKey))
            for (address in contact.getAddresses()) {
                array.put(address)
            }
            `object`.put("addresses", array)
            if (all) {
                `object`.put("blocked", contact.blocked)
            }
            return `object`
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun importJSON(`object`: JSONObject, all: Boolean): Contact {
            val contact = Contact()
            contact.name = `object`.getString("name")
            contact.publicKey = hexStringToByteArray(`object`.getString("public_key"))
            if (!isValidName(contact.name)) {
                throw JSONException("Invalid Name.")
            }
            if (contact.publicKey == null || contact.publicKey!!.size != Sodium.crypto_sign_publickeybytes()) {
                throw JSONException("Invalid Public Key.")
            }
            val array = `object`.getJSONArray("addresses")
            var i = 0
            while (i < array.length()) {
                contact.addAddress(array.getString(i).toUpperCase().trim { it <= ' ' })
                i += 1
            }
            if (all) {
                contact.blocked = `object`.getBoolean("blocked")
            }
            return contact
        }
    }
}