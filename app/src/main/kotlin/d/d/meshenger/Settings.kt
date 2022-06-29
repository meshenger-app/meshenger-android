package d.d.meshenger

import android.util.Log
import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Utils.hexStringToByteArray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


class Settings {

    var username: String = ""
    var secretKey: ByteArray? = null
    var publicKey: ByteArray? = null
    var nightMode = false

    /*
     * blockUnknown
     *
     * true  => do not show/accept calls from contacts that are not in the contact list
     * false => accept any calls (default)
     */
    var blockUnknown = false

    /*
     * settingsMode:
     *
     * "basic"    => show basic settings (default)
     * "advanced" => show more settings
     * "expert"   => show all settings
     */
    var settingsMode: String
    var recordVideo = false
    var playVideo = false
    var recordAudio = false
    var playAudio = false

    /*
     * autoAcceptCall:
     *
     *  true  => accept incoming and no ringing
     *  false => wait an incoming call to be accepted (default)
     */
    var autoAcceptCall = false

    /*
     * autoConnectCall:
     *
     *  true  => start outgoing call without prompt (default)
     *  false => allow to change call settings before the call is started
     */
    var autoConnectCall = false
    var audioProcessing = false
    var videoCodec: String
    var audioCodec: String

    /*
     * speakerphone:
     *
     *  "auto"  => enable/disable by proximity sensor (default)
     *  "true"  => enable hands-free talking
     *  "false" => disable hands-free talking
     */
    var speakerphone: String
    var videoResolution: String

    /*
     * addresses:
     *
     * List of MAC addresses, IP addresses
     * and hostnames to give out by QR-Code.
     * By default contains a single MAC address.
     */
    var addresses: ArrayList<String>

    /*
     * iceServers:
     *
     * Optional list of ICE (Interactive Connectivity Establishment)
     * server that implement STUN and TURN.
     */
    var iceServers: ArrayList<String>

    companion object{

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = hexStringToByteArray(obj.getString("secret_key"))!!
            s.publicKey = hexStringToByteArray(obj.getString("public_key"))!!
            s.nightMode = obj.getBoolean("night_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.settingsMode = obj.getString("settings_mode")
            s.recordVideo = obj.getBoolean("record_video")
            s.playVideo = obj.getBoolean("play_video")
            s.recordAudio = obj.getBoolean("record_audio")
            s.playAudio = obj.getBoolean("play_audio")
            s.autoAcceptCall = obj.getBoolean("auto_accept_call")
            s.autoConnectCall = obj.getBoolean("auto_connect_call")
            s.audioProcessing = obj.getBoolean("audio_processing")
            s.videoCodec = obj.getString("video_codec")
            s.audioCodec = obj.getString("audio_codec")
            s.speakerphone = obj.getString("speakerphone")
            s.videoResolution = obj.getString("video_resolution")
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
        fun toJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", byteArrayToHexString(s.secretKey))
            obj.put("public_key", byteArrayToHexString(s.publicKey))
            obj.put("night_mode", s.nightMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("settings_mode", s.settingsMode)
            obj.put("record_video", s.recordVideo)
            obj.put("play_video", s.playVideo)
            obj.put("record_audio", s.recordAudio)
            obj.put("play_audio", s.playAudio)
            obj.put("auto_accept_call", s.autoAcceptCall)
            obj.put("auto_connect_call", s.autoConnectCall)
            obj.put("audio_processing", s.audioProcessing)
            obj.put("video_codec", s.videoCodec)
            obj.put("audio_codec", s.audioCodec)
            obj.put("speakerphone", s.speakerphone)
            obj.put("video_resolution", s.videoResolution)
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
        // set defaults
        nightMode = false
        blockUnknown = false
        settingsMode = "basic"
        recordVideo = true
        playVideo = true
        recordAudio = true
        playAudio = true
        autoAcceptCall = false
        autoConnectCall = true
        audioProcessing = true
        videoCodec = "VP8"
        audioCodec = "OPUS"
        speakerphone = "auto"
        videoResolution = "default"
        addresses = ArrayList()
        iceServers = ArrayList()

    }


    fun addAddress(address: String) {
        for (addr in this.addresses) {
            if (addr.equals(address, ignoreCase = true)) {
                Log.w("Settings", "Try to add duplicate address: $addr")
                return
            }
        }
        addresses.add(address)
    }

    fun getOwnContact(): Contact {
        return Contact(username, publicKey, addresses, false)
    }
}