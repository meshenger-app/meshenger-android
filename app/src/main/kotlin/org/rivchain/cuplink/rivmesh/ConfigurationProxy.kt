package org.rivchain.cuplink.rivmesh

import mobile.Mobile
import org.json.JSONArray
import org.json.JSONObject
import org.rivchain.cuplink.rivmesh.models.PeerInfo

class ConfigurationProxy() {

    lateinit var json: JSONObject
    private var byteArray = byteArrayOf()

    fun invoke(): ConfigurationProxy {
        byteArray = Mobile.generateConfigJSON()
        json = JSONObject(byteArray.toString(charset("UTF-8")))
        fix()
        return this
    }

    fun resetKeys() {
        val newJson = JSONObject(String(Mobile.generateConfigJSON()))
        updateJSON { json ->
            json.put("PrivateKey", newJson.getString("PrivateKey"))
        }
    }

    fun setKeys(privateKey: String) {
        updateJSON { json ->
            json.put("PrivateKey", privateKey)
        }
    }

    fun updateJSON(fn: (JSONObject) -> Unit) {
        json = JSONObject(byteArray.toString(charset("UTF-8")))
        fn(json)
        val str = json.toString()
        byteArray = str.toByteArray(charset("UTF-8"))
    }

    fun fromJSON(json: JSONObject): ConfigurationProxy{
        this.byteArray = json.toString().toByteArray(charset("UTF-8"))
        this.json = json
        return this
    }

    private fun fix() {
        updateJSON { json ->
            json.put("AdminListen", "none")
            json.put("IfName", "none")
            json.put("IfMTU", 65535)

            if (json.getJSONArray("MulticastInterfaces").get(0) is String) {
                var ar = JSONArray()
                ar.put(0, JSONObject("""
                    {
                        "Regex": ".*",
                        "Beacon": false,
                        "Listen": false,
                        "Password": ""
                    }
                """.trimIndent()))
                json.put("MulticastInterfaces", ar)
            }
        }
    }

    fun getJSON(): JSONObject {
        fix()
        return json
    }

    fun getJSONByteArray(): ByteArray {
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun getPeers(): JSONArray {
        return json.getJSONArray("Peers")
    }

    fun setPeers(peers: Set<PeerInfo>){
        updateJSON { json ->
            val a = json.getJSONArray("Peers")
            val l = a.length()
            var i = 0
            while (i < l) {
                a.remove(0)
                i++
            }
            for (peer in peers){
                a.put(peer.toString())
            }
        }
        this.byteArray = json.toString().toByteArray(charset("UTF-8"))
    }

    fun getListen(): JSONArray {
        return json.getJSONArray("Listen")
    }

    fun setListen(peers: Set<PeerInfo>){
        updateJSON { json ->
            val a = json.getJSONArray("Listen")
            val l = a.length()
            var i = 0
            while (i < l) {
                a.remove(0)
                i++
            }
            for (peer in peers){
                a.put(peer.toString())
            }
        }
        this.byteArray = json.toString().toByteArray(charset("UTF-8"))
    }

    var multicastRegex: String
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).optString("Regex")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Regex", value)
            }
        }

    var multicastListen: Boolean
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).getBoolean("Listen")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Listen", value)
            }
        }

    var multicastBeacon: Boolean
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).getBoolean("Beacon")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Beacon", value)
            }
        }

    var multicastPassword: String
        get() = (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).optString("Password")
        set(value) {
            updateJSON { json ->
                (json.getJSONArray("MulticastInterfaces").get(0) as JSONObject).put("Password", value)
            }
        }
}