package d.d.meshenger

import android.util.Log
import d.d.meshenger.Utils
import d.d.meshenger.Contact
import kotlin.Throws
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.util.*

class Settings {
    var settingsMode = "basic"
    var playVideo = false
    var playAudio = false

    var audioProcessing = false
    var videoCodec = "VP8"
    var audioCodec = "OPUS"

    /*
     * speakerphone:
     *
     *  "auto"  => enable/disable by proximity sensor (default)
     *  "true"  => enable hands-free talking
     *  "false" => disable hands-free talking
     */
    var speakerphone = "true"
    var videoResolution = "Default"

    var username = ""
    var secretKey = byteArrayOf()
    var publicKey = byteArrayOf()
    var nightMode = false
    var blockUnknown = false
    var addresses = mutableListOf<String>()

    // ICE (Interactive Connectivity Establishment) servers implement STUN and TURN
    var iceServers = mutableListOf<String>()

    fun getOwnContact(): Contact {
        return Contact(username, publicKey, addresses)
    }

    companion object {
        fun importJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"))!!
            s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))!!
            s.nightMode = obj.getBoolean("night_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")

            s.settingsMode = obj.getString("settings_mode")
            s.playVideo = obj.getBoolean("play_video")
            s.playAudio = obj.getBoolean("play_audio")
            s.audioProcessing = obj.getBoolean("audio_processing")
            s.videoCodec = obj.getString("video_codec")
            s.audioCodec = obj.getString("audio_codec")
            s.speakerphone = obj.getString("speakerphone")

            val array = obj.getJSONArray("addresses")
            val addresses = mutableListOf<String>()
            for (i in 0 until array.length()) {
                var address = array[i].toString()
                if (Utils.isIPAddress(address) || Utils.isDomain(address)) {
                    address = address.lowercase(Locale.ROOT)
                } else if (Utils.isMACAddress(address)) {
                    address = address.uppercase(Locale.ROOT)
                } else {
                    Log.d("Settings", "invalid address ${address}")
                    continue
                }
                if (address !in addresses) {
                    addresses.add(address)
                }
            }
            s.addresses = addresses.toMutableList()

            val iceServers = obj.getJSONArray("ice_servers")
            for (i in 0 until iceServers.length()) {
                s.iceServers.add(iceServers.getString(i))
            }

            return s
        }

        @Throws(JSONException::class)
        fun exportJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey))
            obj.put("public_key", Utils.byteArrayToHexString(s.publicKey))
            obj.put("night_mode", s.nightMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("settings_mode", s.settingsMode)
            obj.put("play_video", s.playVideo)
            obj.put("play_audio", s.playAudio)
            obj.put("audio_processing", s.audioProcessing)
            obj.put("video_codec", s.videoCodec)
            obj.put("audio_codec", s.audioCodec)
            obj.put("speakerphone", s.speakerphone)

            val addresses = JSONArray()
            for (i in s.addresses.indices) {
                addresses.put(s.addresses[i])
            }
            obj.put("addresses", addresses)

            val iceServers = JSONArray()
            for (i in s.iceServers.indices) {
                iceServers.put(s.iceServers[i])
            }
            obj.put("ice_servers", iceServers)

            return obj
        }
    }
}