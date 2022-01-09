package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class Database {
    var settings: Settings
    var contacts: ArrayList<Contact>
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
        if (settings.secretKey != null) {
            Arrays.fill(settings.secretKey, 0.toByte())
        }
        if (settings.publicKey != null) {
            Arrays.fill(settings.publicKey, 0.toByte())
        }
        for (contact in contacts) {
            if (contact.publicKey != null) {
                Arrays.fill(contact.publicKey, 0.toByte())
            }
        }
    }

    companion object {
        var version = "3.0.3" // current version
        @Throws(IOException::class, JSONException::class)
        fun load(path: String?, password: String?): Database {
            // read database file
            var data = Utils.readExternalFile(path)

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

        @Throws(IOException::class, JSONException::class)
        fun store(path: String?, db: Database, password: String?) {
            val obj = toJSON(db)
            var data: ByteArray? = obj.toString().toByteArray()

            // encrypt database
            if (password != null && password.length > 0) {
                data = Crypto.encryptDatabase(data, password.toByteArray())
            }

            // write database file
            Utils.writeExternalFile(path, data)
        }

        @Throws(JSONException::class)
        private fun upgradeDatabase(from: String, to: String, obj: JSONObject): Boolean {
            var from = from
            if (from == to) {
                return false
            }
            log("upgrade database from $from to $to")

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
            var i = 0
            while (i < array.length()) {
                db.contacts.add(
                        Contact.importJSON(array.getJSONObject(i), true)
                )
                i += 1
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

    init {
        contacts = ArrayList()
        settings = Settings()
    }
}