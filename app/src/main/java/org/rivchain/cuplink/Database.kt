package org.rivchain.cuplink

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class Database {
    var settings: Settings = Settings()
    var contacts: ArrayList<Contact> = ArrayList()
    fun addContact(contact: Contact) {
        val idx = findContact(contact.getAddresses()[0].address?.hostAddress)
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
            if (contacts[i].getAddresses()[0].address?.hostAddress.equals(address)) {
                return i
            }
            i += 1
        }
        return -1
    }

    companion object {
        var version = "4.0.0" // current version

        @Throws(JSONException::class)
        fun load(context: Context): Database? {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            // read database
            val data = preferences.getString("db", null) ?: return null
            val obj = JSONObject(data)
            return fromJSON(obj)
        }

        @Throws(JSONException::class)
        fun store(db: Database, context: Context) {
            val obj = toJSON(db)
            // write database file
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit().putString("db", obj.toString()).apply()
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