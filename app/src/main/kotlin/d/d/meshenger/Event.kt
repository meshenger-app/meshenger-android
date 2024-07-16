package d.d.meshenger

import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.*

class Event(
    val publicKey: ByteArray, val address: InetSocketAddress?, val type: Type, val date: Date
) {
    enum class Type {
        UNKNOWN,

        INCOMING_ACCEPTED, // call was successful
        INCOMING_MISSED, // call did not start
        INCOMING_ERROR, // some error happened

        OUTGOING_ACCEPTED, // call was successful
        OUTGOING_MISSED, // call did not start
        OUTGOING_ERROR // some error happened
    }

    fun createUnknownContact(name: String): Contact {
        val addresses = mutableListOf<String>()
        val address = address?.address
        if (address != null) {
            addresses.add(address.toString().removePrefix("/"))
        }
        return Contact(name, publicKey, addresses)
    }

    companion object {
        private fun eventTypeToString(value: Type): String {
            return when (value) {
                Type.UNKNOWN -> "UNKNOWN"
                Type.INCOMING_ACCEPTED -> "INCOMING_ACCEPTED"
                Type.INCOMING_MISSED -> "INCOMING_MISSED"
                Type.INCOMING_ERROR -> "INCOMING_ERROR"
                Type.OUTGOING_ACCEPTED -> "OUTGOING_ACCEPTED"
                Type.OUTGOING_MISSED -> "OUTGOING_MISSED"
                Type.OUTGOING_ERROR -> "OUTGOING_ERROR"
            }
        }

        private fun eventTypeFromString(value: String): Type {
            return when (value) {
                "UNKNOWN" -> Type.UNKNOWN
                "INCOMING_ACCEPTED" -> Type.INCOMING_ACCEPTED
                "INCOMING_MISSED" -> Type.INCOMING_MISSED
                "INCOMING_ERROR" -> Type.INCOMING_ERROR
                "OUTGOING_ACCEPTED" -> Type.OUTGOING_ACCEPTED
                "OUTGOING_MISSED" -> Type.OUTGOING_MISSED
                "OUTGOING_ERROR" -> Type.OUTGOING_ERROR
                else -> {
                    Log.w(this, "Invalid call event type: $value")
                    Type.UNKNOWN
                }
            }
        }

        fun toJSON(event: Event): JSONObject {
            val obj = JSONObject()
            obj.put("public_key", Utils.byteArrayToHexString(event.publicKey))
            obj.put("address", AddressUtils.inetSocketAddressToString(event.address))
            obj.put("type", eventTypeToString(event.type))
            obj.put("date", event.date.time.toString())
            return obj
        }

        fun fromJSON(obj: JSONObject): Event {
            val publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))!!
            val address = AddressUtils.stringToInetSocketAddress(obj.optString("address"), MainService.serverPort)
            val type = eventTypeFromString(obj.getString("type"))
            val date = Date(obj.getString("date").toLong(10))
            return Event(publicKey, address, type, date)
        }
    }
}