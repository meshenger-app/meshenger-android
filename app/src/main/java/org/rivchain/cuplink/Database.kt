package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class Database {
    var settings: Settings = Settings()
    var contacts: ArrayList<Contact> = ArrayList()
    fun addContact(contact: Contact) {
        val idx = findContact(contact.getAddresses()?.get(0)?.address?.hostAddress)
        if (idx >= 0) {
            // contact exists - replace
            contacts[idx] = contact
        } else {
            contacts.add(contact)
        }
    }

    fun deleteContact(address: String?) {
        val idx = findContact(address)
        if (idx >= 0) {
            contacts.removeAt(idx)
        }
    }

    private fun findContact(address: String?): Int {
        var i = 0
        while (i < contacts.size) {
            if (contacts[i].getAddresses()?.get(0)?.address?.hostAddress.equals(address)) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun onDestroy() {

    }

    companion object {
        var version = "3.0.3" // current version

        @Throws(IOException::class, JSONException::class)
        fun load(path: String?): Database {
            // read database file
            var data = Utils.readExternalFile(path!!)

            val obj = JSONObject(
                String(data, Charset.forName("UTF-8"))
            )
            val db = fromJSON(obj)
            return db
        }

        @Throws(IOException::class, JSONException::class)
        fun store(path: String?, db: Database) {
            val obj = toJSON(db)
            var data: ByteArray? = obj.toString().toByteArray()

            // write database file
            Utils.writeExternalFile(path!!, data)
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

}