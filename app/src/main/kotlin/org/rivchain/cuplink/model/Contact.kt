package org.rivchain.cuplink.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.libsodium.jni.Sodium
import org.rivchain.cuplink.util.NetworkUtils
import org.rivchain.cuplink.util.Utils
import org.tdf.rlp.RLPIgnored
import java.io.Serializable
import java.net.InetSocketAddress
import java.util.Locale

class Contact(
    var name: String,
    var publicKey: ByteArray,
    var addresses: List<String>,

    @RLPIgnored
    var blocked: Boolean = false
) : Serializable {
    enum class State {
        CONTACT_ONLINE,
        CONTACT_OFFLINE,
        NETWORK_UNREACHABLE,
        APP_NOT_RUNNING, // host is online, but CupLink does not run
        AUTHENTICATION_FAILED, // authentication failed, key might have changed
        COMMUNICATION_FAILED, // something went wrong during communication
        PENDING, // temporary state until the contact has been pinged
    }

    constructor() : this("", ByteArray(0), emptyList(), false)

    // contact state
    @RLPIgnored
    var state = State.CONTACT_OFFLINE

    // last working address (use this address next connection and for unknown contact initialization)
    @RLPIgnored
    var lastWorkingAddress: InetSocketAddress? = null

    companion object {
        fun toJSON(contact: Contact, all: Boolean): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            obj.put("name", contact.name)
            obj.put("public_key", Utils.byteArrayToHexString(contact.publicKey))
            for (address in contact.addresses) {
                array.put(NetworkUtils.stripInterface(address))
            }
            obj.put("addresses", array)
            if (all && contact.blocked) {
                obj.put("blocked", contact.blocked)
            }
            return obj
        }

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
                var address = NetworkUtils.stripInterface(array[i].toString())
                if (NetworkUtils.isIPAddress(address) || NetworkUtils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (NetworkUtils.isMACAddress(address)) {
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