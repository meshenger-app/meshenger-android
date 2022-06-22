package d.d.meshenger.model

import d.d.meshenger.utils.Log.w
import d.d.meshenger.utils.Utils.byteArrayToHexString
import d.d.meshenger.utils.Utils.hexStringToByteArray
import d.d.meshenger.call.DirectRTCClient.CallDirection
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class Event (
    val publicKey: ByteArray,
    val address: String,
    val callDirection: CallDirection,
    val callType: CallType,
    val date: Date
) {


    enum class CallType {
        UNKNOWN, ACCEPTED, DECLINED, MISSED, ERROR
    }

    companion object {
        const val TAG = "Event"

        private fun callDirectionFromString(value: String): CallDirection {
            return if (value == "INCOMING") {
                CallDirection.INCOMING
            } else {
                CallDirection.OUTGOING
            }
        }


        private fun callDirectionToString(value: CallDirection): String? {
            return if (value === CallDirection.INCOMING) {
                "INCOMING"
            } else {
                "OUTGOING"
            }
        }

        private fun callTypeToString(value: CallType): String {
            return when (value) {
                CallType.UNKNOWN -> "UNKNOWN"
                CallType.ACCEPTED -> "ACCEPTED"
                CallType.DECLINED -> "DECLINED"
                CallType.MISSED -> "MISSED"
                CallType.ERROR -> "ERROR"
                else -> {
                    w(TAG, "Invalid call type: $value")
                    "UNKNOWN"
                }
            }
        }

        private fun callTypeFromString(value: String): CallType {
            return when (value) {
                "UNKNOWN" -> CallType.UNKNOWN
                "ACCEPTED" -> CallType.ACCEPTED
                "DECLINED" -> CallType.DECLINED
                "MISSED" -> CallType.MISSED
                "ERROR" -> CallType.ERROR
                else -> {
                    w(TAG, "Invalid call type: $value")
                    CallType.UNKNOWN
                }
            }
        }

        @Throws(JSONException::class)
        fun toJSON(event: Event): JSONObject? {
            val obj = JSONObject()
            obj.put("public_key", byteArrayToHexString(event.publicKey))
            obj.put("address", event.address)
            obj.put("call_direction", callDirectionToString(event.callDirection))
            obj.put("call_type", callTypeToString(event.callType))
            obj.put("date", event.date.getTime().toString())
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Event {
            val publicKey = hexStringToByteArray(obj.getString("public_key"))!!
            val address = obj.getString("address")
            val callType: CallType = callTypeFromString(obj.getString("call_type"))
            val callDirection: CallDirection =
                callDirectionFromString(obj.getString("call_direction"))
            val date = Date(obj.getString("date").toLong(10))
            return Event(publicKey, address, callDirection, callType, date)
        }

    }

    fun isMissedCall(): Boolean {
        return callDirection === CallDirection.INCOMING && callType != CallType.ACCEPTED
    }



}