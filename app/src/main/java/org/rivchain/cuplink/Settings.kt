package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

class Settings {
    private var username = ""
    var secretKey: ByteArray? = null
    var publicKey: ByteArray? = null
    private var language = ""
    private var nightMode = false
    var blockUnknown = false
    private var developmentMode = false
    private var addresses: MutableList<String>

    // ICE (Interactive Connectivity Establishment) servers implement STUN and TURN
    private var iceServers: MutableList<String>

    fun getUsername(): String {
        return username
    }

    fun setUsername(username: String) {
        this.username = username
    }

    fun getLanguage(): String {
        return language
    }

    fun setLanguage(language: String) {
        this.language = language
    }

    fun getNightMode(): Boolean {
        return nightMode
    }

    fun setNightMode(nightMode: Boolean) {
        this.nightMode = nightMode
    }

    @JvmName("setBlockUnknown1")
    fun setBlockUnknown(blockUnknown: Boolean) {
        this.blockUnknown = blockUnknown
    }

    fun getDevelopmentMode(): Boolean {
        return developmentMode
    }

    fun setDevelopmentMode(developmentMode: Boolean) {
        this.developmentMode = developmentMode
    }

    fun getAddresses(): List<String> {
        return addresses
    }

    fun setAddresses(addresses: MutableList<String>) {
        this.addresses = addresses
    }

    fun addAddress(address: String) {
        for (addr in getAddresses()) {
            if (addr.equals(address, ignoreCase = true)) {
                return
            }
        }
        addresses.add(address)
    }

    fun getIceServers(): List<String> {
        return iceServers
    }

    fun setIceServers(iceServers: MutableList<String>) {
        this.iceServers = iceServers
    }

    val ownContact: Contact
        get() = Contact(username, publicKey, addresses)

    companion object {
        @Throws(JSONException::class)
        fun importJSON(obj: JSONObject): Settings {
            val s = Settings()
            s.username = obj.getString("username")
            s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"))
            s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"))
            s.language = obj.getString("language")
            s.nightMode = obj.getBoolean("night_mode")
            s.blockUnknown = obj.getBoolean("block_unknown")
            s.developmentMode = obj.getBoolean("development_mode")
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
        fun exportJSON(s: Settings): JSONObject {
            val obj = JSONObject()
            obj.put("username", s.username)
            obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey))
            obj.put("public_key", Utils.byteArrayToHexString(s.publicKey))
            obj.put("language", s.language)
            obj.put("night_mode", s.nightMode)
            obj.put("block_unknown", s.blockUnknown)
            obj.put("development_mode", s.developmentMode)
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
        addresses = ArrayList()
        iceServers = ArrayList()
    }
}