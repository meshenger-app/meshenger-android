package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Settings {
    var username = ""
    var secretKey: ByteArray = ByteArray(0)
    var language = ""
    var nightMode = false
    var blockUnknown = false
    var developmentMode = false
    var addresses: MutableList<String> = ArrayList()
    var videoCodec = "H264"
    var startOnBootup = false

    // ICE (Interactive Connectivity Establishment) servers implement STUN and TURN
    var iceServers: MutableList<String> = ArrayList()

    fun addAddress(address: String) {
        for (addr in addresses) {
            if (addr.equals(address, ignoreCase = true)) {
                return
            }
        }
        addresses.add(address)
    }

    val ownContact: Contact
        get() = Contact(username, addresses)

    companion object {

        @Throws(JSONException::class)
        fun importJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"))
            s.language = obj.getString("language")
            s.nightMode = obj.getBoolean("night_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.developmentMode = obj.getBoolean("development_mode")
            s.startOnBootup = obj.getBoolean("start_on_bootup")
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
            s.videoCodec = obj.getString("videoCodec")
            return s
        }

        @Throws(JSONException::class)
        fun exportJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey))
            obj.put("language", s.language)
            obj.put("night_mode", s.nightMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("development_mode", s.developmentMode)
            obj.put("start_on_bootup", s.startOnBootup)
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
            obj.put("videoCodec", s.videoCodec)
            return obj
        }
    }
}