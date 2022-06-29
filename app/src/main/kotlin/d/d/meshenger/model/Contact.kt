package d.d.meshenger.model

import d.d.meshenger.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.net.InetAddress

data class Contact(var name: String, val publicKey: ByteArray, val addresses: ArrayList<String>, var blocked: Boolean): Serializable {

        companion object {
        private const val TAG = "Contact"
        var STATE_TIMEOUT = (60 * 1000).toLong()


        @Throws(JSONException::class)
        fun toJSON(contact: Contact): JSONObject {
            val `object` = JSONObject()
            val array = JSONArray()
            `object`.put("name", contact.name)
            `object`.put("public_key", Utils.byteArrayToHexString(contact.publicKey))
            `object`.put("blocked", contact.blocked)
            for (address in contact.addresses) {
                array.put(address)
            }
            `object`.put("addresses", array)
            return `object`
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Contact {
            val name = obj.getString("name")
            val publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))!! //TODO: Null check?
            val blocked = obj.getBoolean("blocked")
            val addresses = ArrayList<String>()
            val array = obj.getJSONArray("addresses")
            var i = 0
            while (i < array.length()) {
                addresses.add(array.getString(i).toUpperCase().trim { it <= ' ' })
                i += 1
            }
            return Contact(name, publicKey, addresses, blocked)
        }

    }

        enum class State {
        ONLINE, OFFLINE, UNKNOWN
    }

    // contact state
    private var state: State = State.UNKNOWN
    var state_last_updated = System.currentTimeMillis()


    // last working address (use this address next connection
    // and for unknown contact initialization)
    var last_working_address: InetAddress? = null;

    fun getState(): State? {
        if (state_last_updated + STATE_TIMEOUT > System.currentTimeMillis()) {
            state = State.UNKNOWN
        }
        return state
    }

    fun setState(state: State) {
        state_last_updated = System.currentTimeMillis()
        this.state = state
    }


    fun getStateLastUpdated(): Long {
        return state_last_updated
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



}