package d.d.meshenger

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList


class Database {

    var settings: Settings = Settings()
    var contacts: Contacts = Contacts()
    var events: Events = Events()

    companion object{
        private const val TAG = "Database"
        var version = "4.0.0"


        @Throws(IOException::class, JSONException::class)
        fun fromData(data: ByteArray?, password: String?): Database {
            // encrypt database
            var dat = data
            if (password != null && password.isNotEmpty()) {
                dat = Crypto.decryptDatabase(dat, password.toByteArray())
                if (dat == null) {
                    throw IOException("Wrong database password.")
                }
            }
            val obj = JSONObject(
                String(dat!!, Charset.forName("UTF-8"))
            )
            upgradeDatabase(obj.getString("version"), obj)
            val db = fromJSON(obj)
            Log.d(TAG, "Loaded " + db.contacts.getContactList().size.toString() + " contacts")
            Log.d(TAG, "Loaded " + db.events.getEventList().size.toString() + " events.")
            return db
        }

        @Throws(IOException::class, JSONException::class)
        fun toData(db: Database, password: String?): ByteArray {
            val obj = toJSON(db)
            var data = obj.toString().toByteArray()

            // encrypt database
            if (password != null && password.isNotEmpty()) {
                data = Crypto.encryptDatabase(data, password.toByteArray())!!
            }
            Log.d(TAG, "Stored " + db.contacts.getContactList().size.toString() + " contacts")
            Log.d(TAG, "Stored " + db.events.getEventList().size.toString() + " events.")
            return data
        }

        @Throws(JSONException::class)
        private fun alignSettings(settings: JSONObject) {
            val defaults: JSONObject = Settings.toJSON(Settings())

            // default keys
            val defaultsIter = defaults.keys()
            val defaultKeys: ArrayList<String> = ArrayList()
            while (defaultsIter.hasNext()) {
                defaultKeys.add(defaultsIter.next())
            }

            // current keys
            val settingsIter = settings.keys()
            val settingsKeys: ArrayList<String> = ArrayList()
            while (settingsIter.hasNext()) {
                settingsKeys.add(settingsIter.next())
            }

            // add missing keys
            for (key in defaultKeys) {
                if (!settings.has(key)) {
                    settings.put(key, defaults[key])
                }
            }

            // remove extra keys
            for (key in settingsKeys) {
                if (!defaults.has(key)) {
                    settings.remove(key)
                }
            }
        }

        @Throws(JSONException::class)
        private fun upgradeDatabase(from: String, db: JSONObject): Boolean {
            var fr = from
            if (fr == version) {
                return false
            }
            Log.d(TAG, "Upgrade database from $from to $version")
            val settings = db.getJSONObject("settings")

            // 2.0.0 => 2.1.0
            if (from == "2.0.0") {
                // add blocked field (added in 2.1.0)
                val contacts = db.getJSONArray("contacts")
                var i = 0
                while (i < contacts.length()) {
                    contacts.getJSONObject(i).put("blocked", false)
                    i += 1
                }
                fr = "2.1.0"
            }

            // 2.1.0 => 3.0.0
            if (from == "2.1.0") {
                // add new fields
                settings.put("ice_servers", JSONArray())
                settings.put("development_mode", false)
                fr = "3.0.0"
            }

            // 3.0.0 => 3.0.1
            if (from == "3.0.0") {
                // nothing to do
                fr = "3.0.1"
            }

            // 3.0.1 => 3.0.2
            if (from == "3.0.1") {
                // fix typo in setting name
                settings.put("night_mode", settings.getBoolean("might_mode"))
                fr = "3.0.2"
            }

            // 3.0.2 => 3.0.3
            if (from == "3.0.2") {
                // nothing to do
                fr = "3.0.3"
            }

            // 3.0.3 => 4.0.0
            if (from == "3.0.3") {
                fr = "4.0.0"
                val events = JSONObject()
                events.put("entries", JSONArray())
                settings.put("events", events)
                val contacts = JSONObject()
                val entries = db.getJSONArray("contacts")
                contacts.put("entries", entries)
                settings.put("contacts", contacts)
            }

            // add missing keys with defaults and remove unexpected keys
            alignSettings(settings)
            db.put("version", fr)
            return true
        }

        @Throws(JSONException::class)
        fun toJSON(db: Database): JSONObject {
            val obj = JSONObject()
            obj.put("version", version)
            obj.put("settings", Settings.toJSON(db.settings))
            obj.put("contacts", Contacts.toJSON(db.contacts))
            obj.put("events", Events.toJSON(db.events))
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Database {
            val db = Database()

            // import version
            version = obj.getString("version")

            // import contacts
            val contacts = obj.getJSONObject("contacts")
            db.contacts = Contacts.fromJSON(contacts)

            // import settings
            val settings = obj.getJSONObject("settings")
            db.settings = Settings.fromJSON(settings)

            // import events
            val events = obj.getJSONObject("events")
            db.events = Events.fromJSON(events)
            return db
        }
    }

    fun onDestroy() {
        // zero keys from memory
        settings.apply {
            if (this.secretKey != null) {
                Arrays.fill(this.secretKey!!, 0.toByte())
            }
            if (this.publicKey != null) {
                Arrays.fill(this.publicKey!!, 0.toByte())
            }

        }

        contacts.apply {
            for (i in this.getContactList()) {
                i?.let{
                    Arrays.fill(i.publicKey!!, 0.toByte())
                }
            }
        }

    }
}