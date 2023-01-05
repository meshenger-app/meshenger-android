package d.d.meshenger

import org.json.JSONObject
import org.json.JSONArray
import java.util.*

class Settings {
    var username = ""
    var secretKey = byteArrayOf()
    var publicKey = byteArrayOf()
    var nightMode = "auto" // on, off, auto
    var speakerphoneMode = "auto" // on, off, auto
    var blockUnknown = false
    var useNeighborTable = false
    var promptOutgoingCalls = false
    var videoHardwareAcceleration = false
    var disableCallHistory = false
    var noAudioProcessing = false
    var connectTimeout = 500
    var addresses = mutableListOf<String>()

    fun getOwnContact(): Contact {
        return Contact(username, publicKey, addresses)
    }

    fun destroy() {
        publicKey.fill(0)
        secretKey.fill(0)
    }

    companion object {
        fun fromJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"))!!
            s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))!!
            s.nightMode = obj.getString("night_mode")
            s.speakerphoneMode = obj.getString("speakerphone_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.useNeighborTable = obj.getBoolean("use_neighbor_table")
            s.videoHardwareAcceleration = obj.getBoolean("video_hardware_acceleration")
            s.noAudioProcessing = obj.getBoolean("no_audio_processing")
            s.connectTimeout = obj.getInt("connect_timeout")
            s.disableCallHistory = obj.getBoolean("disable_call_history")
            s.promptOutgoingCalls = obj.getBoolean("prompt_outgoing_calls")

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (AddressUtils.isIPAddress(address) || AddressUtils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (AddressUtils.isMACAddress(address)) {
                    address = address.uppercase(Locale.ROOT)
                } else {
                    Log.d("Settings", "invalid address $address")
                    continue
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }
            s.addresses = addresses.toMutableList()

            return s
        }

        fun toJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey))
            obj.put("public_key", Utils.byteArrayToHexString(s.publicKey))
            obj.put("night_mode", s.nightMode)
            obj.put("speakerphone_mode", s.speakerphoneMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("use_neighbor_table", s.useNeighborTable)
            obj.put("connect_timeout", s.connectTimeout)
            obj.put("video_hardware_acceleration", s.videoHardwareAcceleration)
            obj.put("no_audio_processing", s.noAudioProcessing)
            obj.put("disable_call_history", s.disableCallHistory)
            obj.put("prompt_outgoing_calls", s.promptOutgoingCalls)

            val addresses = JSONArray()
            for (i in s.addresses.indices) {
                addresses.put(s.addresses[i])
            }
            obj.put("addresses", addresses)

            return obj
        }
    }
}