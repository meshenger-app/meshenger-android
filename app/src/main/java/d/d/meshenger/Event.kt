package d.d.meshenger

import org.json.JSONException
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

class Event(
    val publicKey: ByteArray, val address: InetSocketAddress?, val type: Type, val date: Date
) {
    enum class Type {
        UNKNOWN,
        OUTGOING_UNKNOWN, OUTGOING_ACCEPTED, OUTGOING_DECLINED, OUTGOING_MISSED, OUTGOING_ERROR,
        INCOMING_UNKNOWN, INCOMING_ACCEPTED, INCOMING_DECLINED, INCOMING_MISSED, INCOMING_ERROR
    }

    fun isMissedCall(): Boolean {
        return when (type) {
                Type.UNKNOWN -> false
                Type.OUTGOING_UNKNOWN -> false
                Type.OUTGOING_ACCEPTED -> false
                Type.OUTGOING_DECLINED -> false
                Type.OUTGOING_MISSED -> false
                Type.OUTGOING_ERROR -> false
                Type.INCOMING_UNKNOWN -> true
                Type.INCOMING_ACCEPTED -> false
                Type.INCOMING_DECLINED -> true
                Type.INCOMING_MISSED -> true
                Type.INCOMING_ERROR -> true
            }
    }

    companion object {
        private fun eventTypeToString(value: Type): String {
            return when (value) {
                Type.UNKNOWN -> "UNKNOWN"
                Type.OUTGOING_UNKNOWN -> "OUTGOING_UNKNOWN"
                Type.OUTGOING_ACCEPTED -> "OUTGOING_ACCEPTED"
                Type.OUTGOING_DECLINED -> "OUTGOING_DECLINED"
                Type.OUTGOING_MISSED -> "OUTGOING_MISSED"
                Type.OUTGOING_ERROR -> "OUTGOING_ERROR"
                Type.INCOMING_UNKNOWN -> "INCOMING_UNKNOWN"
                Type.INCOMING_ACCEPTED -> "INCOMING_ACCEPTED"
                Type.INCOMING_DECLINED -> "INCOMING_DECLINED"
                Type.INCOMING_MISSED -> "INCOMING_MISSED"
                Type.INCOMING_ERROR -> "INCOMING_ERROR"
            }
        }

        private fun eventTypeFromString(value: String): Type {
            return when (value) {
                "UNKNOWN" -> Type.UNKNOWN
                "OUTGOING_UNKNOWN" -> Type.OUTGOING_UNKNOWN
                "OUTGOING_ACCEPTED" -> Type.OUTGOING_ACCEPTED
                "OUTGOING_DECLINED" -> Type.OUTGOING_DECLINED
                "OUTGOING_MISSED" -> Type.OUTGOING_MISSED
                "OUTGOING_ERROR" -> Type.OUTGOING_ERROR
                "INCOMING_UNKNOWN" -> Type.INCOMING_UNKNOWN
                "INCOMING_ACCEPTED" -> Type.INCOMING_ACCEPTED
                "INCOMING_DECLINED" -> Type.INCOMING_DECLINED
                "INCOMING_MISSED" -> Type.INCOMING_MISSED
                "INCOMING_ERROR" -> Type.INCOMING_ERROR
                else -> {
                    Log.w(this, "Invalid call event type: $value")
                    Type.OUTGOING_UNKNOWN
                }
            }
        }

        @Throws(JSONException::class)
        fun toJSON(event: Event): JSONObject {
            val obj = JSONObject()
            obj.put("public_key", Utils.byteArrayToHexString(event.publicKey))
            obj.put("address", event.address.toString())
            obj.put("type", eventTypeToString(event.type))
            obj.put("date", event.date.time.toString())
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Event {
            val publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))!!
            val address = InetSocketAddress.createUnresolved(obj.getString("address"), MainService.serverPort)
            val type = eventTypeFromString(obj.getString("type"))
            val date = Date(obj.getString("date").toLong(10))
            return Event(publicKey, address, type, date)
        }
    }
}