package d.d.meshenger

import android.util.Log
import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Utils.hexStringToByteArray
import d.d.meshenger.call.DirectRTCClient.CallDirection
import org.json.JSONException
import org.json.JSONObject
import java.util.*


class Event (
    publicKey: ByteArray,
    address: String?,
    callDirection: CallDirection,
    callType: CallType,
    date: Date
) {

    companion object {
        const val TAG = "Event"


        private fun callDirectionFromString(value: String): CallDirection {
            return if (value == "INCOMING") {
                CallDirection.INCOMING
            } else {
                CallDirection.OUTGOING
            }
        }

        private fun callDirectionToString(value: CallDirection): String {
            return if (value === CallDirection.INCOMING) {
                "INCOMING"
            } else {
                "OUTGOING"
            }
        }

        private fun callTypeToString(value: CallType): String {
            return when (value) {
                CallType.ACCEPTED -> "ACCEPTED"
                CallType.DECLINED -> "DECLINED"
                CallType.MISSED -> "MISSED"
                CallType.ERROR -> "ERROR"
                else -> {
                    Log.w(TAG, "Invalid call type: $value")
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
                    Log.w(TAG, "Invalid call type: $value")
                    CallType.UNKNOWN
                }
            }
        }

        @Throws(JSONException::class)
        fun toJSON(event: Event): JSONObject {
            return JSONObject().apply{
                this.put("public_key", byteArrayToHexString(event.publicKey))
                this.put("address", event.address)
                this.put("call_direction", callDirectionToString(event.callDirection))
                this.put("call_type", callTypeToString(event.callType))
                this.put("date", event.date.time.toString())

            }
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Event{
            val publicKey = hexStringToByteArray(obj.getString("public_key"))
            val address = obj.getString("address")
            val callType = callTypeFromString(obj.getString("call_type"))
            val callDirection = callDirectionFromString(obj.getString("call_direction"))
            val date = Date(obj.getString("date").toLong(10))
            return Event(publicKey!!, address, callDirection, callType, date)
        }
    }

    enum class CallType {
        UNKNOWN, ACCEPTED, DECLINED, MISSED, ERROR
    }

    var publicKey: ByteArray?
    var address : String? = null // may be null in case the call attempt failed
    var callDirection: CallDirection
    var callType: CallType
    var date: Date

    init {
        this.publicKey = publicKey
        this.address = address
        this.callDirection = callDirection
        this.callType = callType
        this.date = date
    }


    fun isMissedCall(): Boolean =
        callDirection === CallDirection.INCOMING && callType !== CallType.ACCEPTED

}