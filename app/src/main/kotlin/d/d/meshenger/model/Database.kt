package d.d.meshenger.model

import d.d.meshenger.utils.Crypto.decryptDatabase
import d.d.meshenger.utils.Crypto.encryptDatabase
import d.d.meshenger.utils.Log.d
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class Database {

    companion object {
        private const val TAG = "Database"
        var version = "4.0.0" // current version

        @Throws(IOException::class, JSONException::class)
        fun fromData(data: ByteArray?, password: String?): Database? {
            // encrypt database
            var data = data
            if (password != null && password.isNotEmpty()) {
                data = decryptDatabase(data, password.toByteArray())
                if (data == null) {
                    throw IOException("Wrong database password.")
                }
            }
            val obj = JSONObject(
                String(data!!, Charset.forName("UTF-8"))
            )
            upgradeDatabase(obj.getString("version"), version, obj)
            val db = fromJSON(obj)
            d(TAG, "Loaded " + db.contacts.contactList.size.toString() + " contacts")
            d(TAG, "Loaded " + db.events.events.size.toString() + " events.")
            return db
        }

        @Throws(IOException::class, JSONException::class)
        fun toData(db: Database, password: String?): ByteArray? {
            val obj = toJSON(db)
            var data: ByteArray? = obj.toString().toByteArray()

            // encrypt database
            if (password != null && password.isNotEmpty()) {
                data = encryptDatabase(data, password.toByteArray())
            }
            d(TAG, "Stored " + db.contacts.contactList.size.toString() + " contacts")
            d(TAG, "Stored " + db.events.events.size.toString() + " events.")
            return data
        }

        @Throws(JSONException::class)
        private fun alignSettings(settings: JSONObject) {
            val defaults = Settings.toJSON(Settings())

            // default keys
            val defaults_iter = defaults!!.keys()
            val defaults_keys: MutableList<String> = ArrayList()
            while (defaults_iter.hasNext()) {
                defaults_keys.add(defaults_iter.next())
            }

            // current keys
            val settings_iter = settings.keys()
            val settings_keys: MutableList<String> = ArrayList()
            while (settings_iter.hasNext()) {
                settings_keys.add(settings_iter.next())
            }

            // add missing keys
            for (key in defaults_keys) {
                if (!settings.has(key)) {
                    settings.put(key, defaults[key])
                }
            }

            // remove extra keys
            for (key in settings_keys) {
                if (!defaults.has(key)) {
                    settings.remove(key)
                }
            }
        }

        @Throws(JSONException::class)
        private fun upgradeDatabase(from: String, to: String, db: JSONObject): Boolean {
            var from = from
            if (from == to) {
                return false
            }
            d(TAG, "Upgrade database from $from to $to")
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
                from = "2.1.0"
            }

            // 2.1.0 => 3.0.0
            if (from == "2.1.0") {
                // add new fields
                settings.put("ice_servers", JSONArray())
                settings.put("development_mode", false)
                from = "3.0.0"
            }

            // 3.0.0 => 3.0.1
            if (from == "3.0.0") {
                // nothing to do
                from = "3.0.1"
            }

            // 3.0.1 => 3.0.2
            if (from == "3.0.1") {
                // fix typo in setting name
                settings.put("night_mode", settings.getBoolean("might_mode"))
                from = "3.0.2"
            }

            // 3.0.2 => 3.0.3
            if (from == "3.0.2") {
                // nothing to do
                from = "3.0.3"
            }

            // 3.0.3 => 4.0.0
            if (from == "3.0.3") {
                from = "4.0.0"
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
            db.put("version", from)
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
            db.settings = Settings.fromJSON(settings)!!

            // import events
            val events = obj.getJSONObject("events")
            db.events = Events.fromJSON(events)
            return db
        }


    }

    var contacts = Contacts()
    var settings = Settings()
    var events = Events()

    fun onDestroy() {
        // zero keys from memory
        if (settings.secretKey != null) {
            Arrays.fill(settings.secretKey, 0.toByte())
        }
        if (settings.publicKey != null) {
            Arrays.fill(settings.publicKey, 0.toByte())
        }
        for (contact in contacts.contactList) {
            Arrays.fill(contact.publicKey, 0.toByte())
        }
    }

}