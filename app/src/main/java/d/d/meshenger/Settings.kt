package d.d.meshenger

import d.d.meshenger.Utils.hexStringToByteArray
import d.d.meshenger.Utils.byteArrayToHexString
import d.d.meshenger.Contact
import kotlin.Throws
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException

class Settings {
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
    var speakerphone: String? = null
    var videoResolution: String? = null


    var username = ""
    var secretKey: ByteArray? = null
    var publicKey: ByteArray? = null
    var nightMode = false
    var language = ""
    var blockUnknown = false
    var developmentMode = false
    var addresses: ArrayList<String>
    var customAddress: ArrayList<String> = ArrayList()

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
            s.customAddress.clear()
            if (obj.has("custom_address")){
                val address = obj.getJSONArray("custom_address")
                for(i in 0 until address.length()){
                    s.customAddress.add(address.getString(i))
                }
            }

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

            Utils.getDefaultWlan80Address(Utils.collectAddresses())?.let {
                s.addresses.add(it.address)
            }
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

            val addresses = JSONArray()
            run {
                var i = 0
                while (i < s.addresses.size) {
                    addresses.put(s.addresses[i])
                    i += 1
                }
            }
            val customIps = JSONArray()
            run {
                var i = 0
                while (i < s.customAddress.size) {
                    customIps.put(s.customAddress[i])
                    i += 1
                }
            }
            obj.put("addresses", addresses)
            obj.put("custom_address", customIps)
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
        speakerphone = "true" //Previous value = auto. auto is disabled
        videoResolution = "Default"
    }
}