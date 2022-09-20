package d.d.meshenger

import d.d.meshenger.Utils
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
import java.util.*

class Contact(
    var name: String,
    var publicKey: ByteArray,
    var addresses: List<String>,
    var blocked: Boolean = false
) : Serializable {
    enum class State {
        ONLINE, OFFLINE, PENDING
    }

    // contact state
    var state = State.PENDING

    // last working address (use this address next connection and for unknown contact initialization)
    var lastWorkingAddress: InetSocketAddress? = null

    // also resolves domains
    fun getAllSocketAddresses(): List<InetSocketAddress> {
        val addrs = mutableListOf<InetSocketAddress>()
        for (address in this.addresses) {
            try {
                if (Utils.isMACAddress(address)) {
                    addrs.addAll(Utils.getAddressPermutations(address, MainService.serverPort))
                } else {
                    // also resolves domains
                    val addr = Utils.parseInetSocketAddress(address, MainService.serverPort)
                    if (addr != null) {
                        addrs.add(addr)
                    }
                }
            } catch (e: Exception) {
                log("invalid address: $address")
                e.printStackTrace()
            }
        }
        return addrs
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
        for (address in getAllSocketAddresses()) {
            log("try address: '" + address!!.hostName + "', port: " + address.port)
            socket = establishConnection(address, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }
        return null
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

            try {
                socket.close()
            } catch (e: Exception) {
                // ignore
            }

            return null
        }

        @Throws(JSONException::class)
        fun exportJSON(contact: Contact, all: Boolean): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            obj.put("name", contact.name)
            val pubKey = contact.publicKey
            if (pubKey != null) {
                obj.put("public_key", Utils.byteArrayToHexString(pubKey))
            }
            for (address in contact.addresses) {
                array.put(address)
            }
            obj.put("addresses", array)
            if (all) {
                obj.put("blocked", contact.blocked)
            }
            return obj
        }

        @Throws(JSONException::class)
        fun importJSON(obj: JSONObject, all: Boolean): Contact {
            val name = obj.getString("name")
            if (!Utils.isValidName(name)) {
                throw JSONException("Invalid Name.")
            }
            val publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))
            if (publicKey == null || publicKey.size != Sodium.crypto_sign_publickeybytes()) {
                throw JSONException("Invalid Public Key.")
            }

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (Utils.isIPAddress(address) || Utils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (Utils.isMACAddress(address)) {
                    address = address.uppercase(Locale.ROOT)
                } else {
                    Log.d(this, "invalid address ${address}")
                    continue
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }

            val blocked = obj.getBoolean("blocked") ?: false

            return Contact(name, publicKey, addresses.toList(), blocked)
        }
    }
}