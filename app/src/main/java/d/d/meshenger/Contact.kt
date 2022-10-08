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
    private fun getAllSocketAddresses(): List<InetSocketAddress> {
        val socketAddresses = mutableListOf<InetSocketAddress>()

        for (address in addresses) {
            try {
                if (AddressUtils.isMACAddress(address)) {
                    socketAddresses.addAll(AddressUtils.getOwnAddressesWithMAC(address, MainService.serverPort))
                } else {
                    val socketAddress = AddressUtils.stringToInetSocketAddress(address, MainService.serverPort.toUShort())
                    if (socketAddress != null) {
                        socketAddresses.add(socketAddress)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return socketAddresses
    }

    /*
    * Create a connection to the contact.
    * Try/Remember the last successful address.
    */
    fun createSocket(): Socket? {
        var socket: Socket?
        val connectionTimeout = 500

        // try last successful address first
        val lastAddress = lastWorkingAddress
        if (lastAddress != null) {
            Log.d(this, "try latest address: $lastWorkingAddress")
            socket = establishConnection(lastAddress, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }

        for (address in getAllSocketAddresses()) {
            Log.d(this, "try address: ${address.toString()}")
            socket = establishConnection(address, connectionTimeout)
            if (socket != null) {
                return socket
            }
        }

        return null
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
        fun toJSON(contact: Contact, all: Boolean): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            obj.put("name", contact.name)
            obj.put("public_key", Utils.byteArrayToHexString(contact.publicKey))
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
        fun fromJSON(obj: JSONObject, all: Boolean): Contact {
            val name = obj.getString("name")
            if (!Utils.isValidName(name)) {
                throw JSONException("Invalid Name")
            }

            val publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))
            if ((publicKey == null) || (publicKey.size != Sodium.crypto_sign_publickeybytes())) {
                throw JSONException("Invalid Public Key.")
            }

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (AddressUtils.isIPv4Address(address)|| AddressUtils.isIPv6Address(address) || AddressUtils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (AddressUtils.isMACAddress(address)) {
                    address = address.uppercase(Locale.ROOT)
                } else {
                    Log.d(this, "invalid address $address")
                    continue
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }

            val blocked = if (all) {
                obj.getBoolean("blocked")
            } else false

            return Contact(name, publicKey, addresses.toList(), blocked)
        }
    }
}