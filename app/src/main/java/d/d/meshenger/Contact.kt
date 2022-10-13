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

    /*
    * Create a connection to the contact.
    * Must not be called on the GUI thread.
    */
    fun createSocket(): Socket? {
        val connectionTimeout = 500

        val addresses = AddressUtils.getAllSocketAddresses(addresses, lastWorkingAddress, MainService.serverPort)
        Log.d(this, "addresses to try: " + addresses.joinToString())

        for (address in addresses) {
            Log.d(this, "try address: $address")

            val socket = Socket()

            try {
                // timeout to establish connection
                socket.connect(address, connectionTimeout)
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
        }

        return null
    }

    companion object {
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
            if (all && contact.blocked) {
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
                throw JSONException("Invalid Public Key")
            }

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (AddressUtils.isMACAddress(address)) {
                    address = address.uppercase(Locale.ROOT)
                } else {
                    throw JSONException("Invalid Address $address")
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }

            val blocked = if (all) {
                obj.optBoolean("blocked", false)
            } else false

            return Contact(name, publicKey, addresses.toList(), blocked)
        }
    }
}