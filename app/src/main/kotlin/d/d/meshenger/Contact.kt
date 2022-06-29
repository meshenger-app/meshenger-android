package d.d.meshenger

import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Utils.hexStringToByteArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.net.InetAddress
import java.util.*
import kotlin.collections.ArrayList


class Contact(var name: String, val publicKey: ByteArray?, val addresses: ArrayList<String>, var blocked: Boolean): Serializable {

    enum class State {
        ONLINE, OFFLINE, UNKNOWN
    }

    // contact state
    var state = State.UNKNOWN
        get(){
            if (stateLastUpdated + STATE_TIMEOUT > System.currentTimeMillis()) {
            field = State.UNKNOWN
            }
            return field
        }
        set (state) {
            this.stateLastUpdated = System.currentTimeMillis()
            field = state
        }

    private var stateLastUpdated = System.currentTimeMillis()

    companion object{
        const val TAG = "Contact"
        const val STATE_TIMEOUT = 60*1000L


        @Throws(JSONException::class)
        fun toJSON(contact: Contact): JSONObject {
            return JSONObject().apply {
                val array = JSONArray()
                this.put("name", contact.name)
                this.put("public_key", byteArrayToHexString(contact.publicKey))
                this.put("blocked", contact.blocked)
                for (address in contact.addresses) {
                    array.put(address)
                }
                this.put("addresses", array)

            }
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Contact {
            val name = obj.getString("name")
            val publicKey = hexStringToByteArray(obj.getString("public_key"))
            val blocked = obj.getBoolean("blocked")
            val addresses: ArrayList<String> = ArrayList()
            val array = obj.getJSONArray("addresses")
            var i = 0
            while (i < array.length()) {
                addresses.add(array.getString(i).uppercase(Locale.getDefault()).trim { it <= ' ' })
                i += 1
            }
            return Contact(name, publicKey, addresses, blocked)
        }
    }

    // last working address (use this address next connection
    // and for unknown contact initialization)
    // set good address to try first next time,
    // this is not stored in the database
    var lastWorkingAddress: InetAddress? = null


    fun getStateLastUpdated(): Long = this.stateLastUpdated


    fun addAddress(address: String) {
        if (address.isEmpty()) {
            return
        }
        for (addr in this.addresses) {
            if (addr.equals(address, ignoreCase = true)) {
                return
            }
        }
        this.addresses.add(address)
    }

}