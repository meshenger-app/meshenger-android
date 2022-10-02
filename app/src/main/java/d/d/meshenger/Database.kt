package d.d.meshenger

import d.d.meshenger.Crypto.decryptDatabase
import d.d.meshenger.Event
import d.d.meshenger.Contacts
import d.d.meshenger.Crypto.encryptDatabase
import d.d.meshenger.Contact
import kotlin.Throws
import org.json.JSONException
import d.d.meshenger.Crypto
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class Database {
    var settings = Settings()
    var contacts = Contacts()
    var events = Events()

    // clear keys before the app exits
    fun destroy() {
        settings.destroy()
        contacts.destroy()
        events.destroy()
    }

    companion object {
        private const val TAG = "Database"
        var version = "4.0.0" // current version

        fun fromData(data: ByteArray, password: String?): Database {
            // encrypt database
            val stringData = if (password != null && password.isNotEmpty()) {
                Log.d(this, "Decrypt database")
                val encrypted = decryptDatabase(data, password.toByteArray())
                if (encrypted == null) {
                    throw IOException("Wrong database password.") //resources.getString(R.string.cancel)
                }
                encrypted
            } else {
                data
            }

            val obj = JSONObject(
                String(stringData, Charset.forName("UTF-8"))
            )
            upgradeDatabase(obj.getString("version"), version, obj)
            val db = fromJSON(obj)
            Log.d(this, "Loaded ${db.contacts.contactList.size} contacts")
            Log.d(this, "Loaded ${db.events.eventList.size} events.")
            return db
        }

        fun toData(db: Database, password: String?): ByteArray? {
            val obj = toJSON(db)
            var data: ByteArray? = obj.toString().toByteArray()

            // encrypt database
            if (password != null && password.isNotEmpty()) {
                Log.d(this, "Encrypt database")
                data = encryptDatabase(data, password.toByteArray())
            }
            Log.d(TAG, "Stored ${db.contacts.contactList.size} contacts")
            Log.d(TAG, "Stored ${db.events.eventList.size} events.")
            return data
        }

        @Throws(JSONException::class)
        private fun alignSettings(settings: JSONObject) {
            val defaults: JSONObject = Settings.toJSON(Settings())

            // default keys
            val defaults_iter = defaults.keys()
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
            Log.d(this, "Upgrade database from $from to $to")
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
            db.settings = Settings.fromJSON(settings)

            // import events
            val events = obj.getJSONObject("events")
            db.events = Events.fromJSON(events)

            return db
        }
    }
}