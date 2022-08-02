package d.d.meshenger

import d.d.meshenger.Utils.hexStringToByteArray
import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Contact
import kotlin.Throws
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.util.ArrayList

class Settings {
    var username = ""
    var secretKey: ByteArray? = null
    var publicKey: ByteArray? = null
    var nightMode = false
    var language = ""
    var blockUnknown = false
    var developmentMode = false
    var addresses: ArrayList<String>

    // ICE (Interactive Connectivity Establishment) servers implement STUN and TURN
    var iceServers: ArrayList<String>


    fun getAddresses(): List<String> {
        return addresses
    }


    fun addAddress(address: String) {
        for (addr in getAddresses()) {
            if (addr.equals(address, ignoreCase = true)) {
                return
            }
        }
        addresses.add(address)
    }


    val ownContact: Contact
        get() = Contact(username, publicKey, addresses)

    companion object {
        @Throws(JSONException::class)
        fun importJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = hexStringToByteArray(obj.getString("secret_key"))
            s.publicKey = hexStringToByteArray(obj.getString("public_key"))
            s.language = obj.getString("language")
            s.nightMode = obj.getBoolean("night_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.developmentMode = obj.getBoolean("development_mode")
            val addresses = obj.getJSONArray("addresses")
            run {
                var i = 0
                while (i < addresses.length()) {
                    s.addresses.add(addresses.getString(i))
                    i += 1
                }
            }
            val iceServers = obj.getJSONArray("ice_servers")
            var i = 0
            while (i < iceServers.length()) {
                s.iceServers.add(iceServers.getString(i))
                i += 1
            }
            return s
        }

        @Throws(JSONException::class)
        fun exportJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", byteArrayToHexString(s.secretKey))
            obj.put("public_key", byteArrayToHexString(s.publicKey))
            obj.put("language", s.language)
            obj.put("night_mode", s.nightMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("development_mode", s.developmentMode)
            val addresses = JSONArray()
            run {
                var i = 0
                while (i < s.addresses.size) {
                    addresses.put(s.addresses[i])
                    i += 1
                }
            }
            obj.put("addresses", addresses)
            val iceServers = JSONArray()
            var i = 0
            while (i < s.iceServers.size) {
                iceServers.put(s.iceServers[i])
                i += 1
            }
            obj.put("ice_servers", iceServers)
            return obj
        }
    }

    init {
        addresses = ArrayList()
        iceServers = ArrayList()
    }
}