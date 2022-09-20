package d.d.meshenger

import d.d.meshenger.Utils
import d.d.meshenger.Crypto
import d.d.meshenger.Contact
import kotlin.Throws
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class Database {
    var settings = Settings()
    var contacts = mutableListOf<Contact>()

    fun addContact(contact: Contact) {
        val idx = findContact(contact.publicKey)
        if (idx >= 0) {
            // contact exists - replace
            contacts[idx] = contact
        } else {
            contacts.add(contact)
        }
    }

    fun deleteContact(publicKey: ByteArray?) {
        val idx = findContact(publicKey)
        if (idx >= 0) {
            contacts.removeAt(idx)
        }
    }

    fun findContact(publicKey: ByteArray?): Int {
        var i = 0
        while (i < contacts.size) {
            if (Arrays.equals(contacts[i].publicKey, publicKey)) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun onDestroy() {
        // zero keys from memory
        settings.publicKey.fill(0)
        settings.publicKey.fill(0)

        for (contact in contacts) {
            contact.publicKey.fill(0)
        }
    }

    companion object {
        var version = "3.0.3" // current version
        @JvmStatic
        @Throws(IOException::class, JSONException::class)
        fun load(path: String?, password: String?): Database {
            // read database file
            var data: ByteArray? = Utils.readExternalFile(path!!)

            // encrypt database
            if (password != null && password.length > 0) {
                data = Crypto.decryptDatabase(data, password.toByteArray())
                if (data == null) {
                    throw IOException("wrong database password.")
                }
            }
            val obj = JSONObject(
                String(data!!, Charset.forName("UTF-8"))
            )
            val upgraded = upgradeDatabase(obj.getString("version"), version, obj)
            val db = fromJSON(obj)
            if (upgraded) {
                log("store updated database")
                store(path, db, password)
            }
            return db
        }

        @JvmStatic
        fun store(path: String, db: Database, password: String?) {
            val obj = toJSON(db)
            var data: ByteArray? = obj.toString().toByteArray()

            // encrypt database
            if (password != null && password.isNotEmpty()) {
                data = Crypto.encryptDatabase(data, password.toByteArray())
            }

            // write database file
            Utils.writeExternalFile(path, data)
        }

        private fun upgradeDatabase(from: String, to: String, obj: JSONObject): Boolean {
            var from = from
            if (from == to) {
                return false
            }
            log("upgrade database from $from to $to")

            // 2.0.0 => 2.1.0
            if (from == "2.0.0") {
                // add blocked field (added in 2.1.0)
                val contacts = obj.getJSONArray("contacts")
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
                obj.getJSONObject("settings").put("ice_servers", JSONArray())
                obj.getJSONObject("settings").put("development_mode", false)
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
                obj.getJSONObject("settings")
                    .put("night_mode", obj.getJSONObject("settings").getBoolean("might_mode"))
                from = "3.0.2"
            }

            // 3.0.2 => 3.0.3
            if (from == "3.0.2") {
                // nothing to do
                from = "3.0.3"
            }
            obj.put("version", from)
            return true
        }

        @Throws(JSONException::class)
        fun toJSON(db: Database): JSONObject {
            val obj = JSONObject()
            obj.put("version", version)
            obj.put("settings", Settings.exportJSON(db.settings))

            val contacts = JSONArray()
            for (contact in db.contacts) {
                contacts.put(Contact.exportJSON(contact, true))
            }
            obj.put("contacts", contacts)
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Database {
            val db = Database()

            // import version
            version = obj.getString("version")

            // import contacts
            val array = obj.getJSONArray("contacts")
            for (i in 0 until array.length()) {
                db.contacts.add(
                    Contact.importJSON(array.getJSONObject(i), true)
                )
            }

            // import settings
            val settings = obj.getJSONObject("settings")
            db.settings = Settings.importJSON(settings)
            return db
        }

        private fun log(s: String) {
            Log.d("Database", s)
        }
    }
}