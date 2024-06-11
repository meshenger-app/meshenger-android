package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONObject
import org.rivchain.cuplink.Crypto.decryptDatabase
import org.rivchain.cuplink.Crypto.encryptDatabase
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.model.Contacts
import org.rivchain.cuplink.model.Events
import org.rivchain.cuplink.model.Settings
import org.rivchain.cuplink.rivmesh.ConfigurationProxy
import org.rivchain.cuplink.util.Log
import java.nio.charset.Charset

class Database {

    var version = BuildConfig.VERSION_NAME
    var settings = Settings()
    var contacts = Contacts()
    var events = Events()
    var mesh = ConfigurationProxy()

    // clear keys before the app exits
    fun destroy() {
        settings.destroy()
        contacts.destroy()
        events.destroy()
        mesh.resetKeys()
    }

    class WrongPasswordException() : Exception()

    companion object {
        fun fromData(dbData: ByteArray, password: String?): Database {
            // encrypt database
            val stringData = if (!password.isNullOrEmpty()) {
                Log.d(this, "Decrypt database")
                val encrypted = decryptDatabase(dbData, password.toByteArray()) ?: throw WrongPasswordException()
                encrypted
            } else {
                if (dbData.isNotEmpty() && dbData[0] != '{'.code.toByte()) {
                    throw WrongPasswordException()
                }
                dbData
            }

            val obj = JSONObject(
                String(stringData, Charset.forName("UTF-8"))
            )

            upgradeDatabase(obj)

            val db = fromJSON(obj)
            Log.d(this, "Loaded ${db.contacts.contactList.size} contacts")
            Log.d(this, "Loaded ${db.events.eventList.size} events")
            Log.d(this, "Loaded ${db.mesh.getPeers().length()} peers")
            return db
        }

        fun toData(db: Database, password: String?): ByteArray? {
            val obj = toJSON(db)
            var dbdata : ByteArray? = obj.toString().toByteArray()

            // encrypt database
            if (!password.isNullOrEmpty()) {
                Log.d(this, "Encrypt database")
                dbdata = encryptDatabase(dbdata, password.toByteArray())
            }
            Log.d(this, "Stored ${db.contacts.contactList.size} contacts")
            Log.d(this, "Stored ${db.events.eventList.size} events")
            Log.d(this, "Stored ${db.mesh.getPeers().length()} peers")
            return dbdata
        }

        // add missing keys with defaults and remove unexpected keys
        private fun alignSettings(settings: JSONObject) {
            val defaults: JSONObject = Settings.toJSON(Settings())

            // default keys
            val defaultsIter = defaults.keys()
            val defaultsKeys = mutableListOf<String>()
            while (defaultsIter.hasNext()) {
                defaultsKeys.add(defaultsIter.next())
            }

            // current keys
            val settingsIter = settings.keys()
            val settingsKeys = mutableListOf<String>()
            while (settingsIter.hasNext()) {
                settingsKeys.add(settingsIter.next())
            }

            // add missing keys
            for (key in defaultsKeys) {
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

        private fun upgradeDatabase(db: JSONObject): Boolean {
            val from = db.getString("version")
            val to = BuildConfig.VERSION_NAME

            if (from == to) {
                return false
            }

            Log.d(this, "Upgrade database from $from to $to")
            val settings = db.getJSONObject("settings")
            var newFrom = from

            // 2.0.0 => 2.1.0
            if (newFrom == "2.0.0") {
                // add blocked field (added in 2.1.0)
                val contacts = db.getJSONArray("contacts")
                var i = 0
                while (i < contacts.length()) {
                    contacts.getJSONObject(i).put("blocked", false)
                    i += 1
                }
                newFrom = "2.1.0"
            }

            // 2.1.0 => 3.0.0
            if (newFrom == "2.1.0") {
                // add new fields
                settings.put("ice_servers", JSONArray())
                settings.put("development_mode", false)
                newFrom = "3.0.0"
            }

            // 3.0.0 => 3.0.1
            if (newFrom == "3.0.0") {
                // nothing to do
                newFrom = "3.0.1"
            }

            // 3.0.1 => 3.0.2
            if (newFrom == "3.0.1") {
                // fix typo in setting name
                settings.put("night_mode", settings.getBoolean("might_mode"))
                newFrom = "3.0.2"
            }

            // 3.0.2 => 3.0.3
            if (newFrom == "3.0.2") {
                // nothing to do
                newFrom = "3.0.3"
            }

            // 3.0.3 => 4.0.0
            if (newFrom == "3.0.3") {
                newFrom = "4.0.0"
                val contacts = Contacts()
                val contactArray = db.getJSONArray("contacts")
                for (i in 0 until contactArray.length()) {
                    val contactObj = contactArray.getJSONObject(i)
                    contacts.addContact(Contact.fromJSON(contactObj, true))
                }

                db.put("contacts", Contacts.toJSON(contacts))

                val events = Events()
                db.put("events", Events.toJSON(events))
            }

            // 4.0.0+ => 4.0.4
            if (newFrom in listOf("4.0.0", "4.0.1", "4.0.2", "4.0.3")) {
                // nothing to do
                newFrom = "4.0.4"
            }

            if (newFrom == "4.0.4") {
                db.put("connect_timeout", 500)
                db.put("use_system_table", false)
                db.put("prompt_outgoing_calls", false)
                db.remove("settings_mode")
                db.remove("ice_servers")

                val eventsObject = db.getJSONObject("events")
                val eventsArray = eventsObject.getJSONArray("entries")
                for (i in 0 until eventsArray.length()) {
                    val eventObject = eventsArray.getJSONObject(i)
                    val newType = when (val oldType = eventObject.getString("type")) {
                        "OUTGOING_UNKNOWN" -> "UNKNOWN"
                        "INCOMING_UNKNOWN" -> "UNKNOWN"
                        "OUTGOING_DECLINED" -> "OUTGOING_ACCEPTED"
                        "INCOMING_DECLINED" -> "INCOMING_ACCEPTED"
                        else -> oldType
                    }

                    eventObject.put("type", newType)
                }

                if (settings.getBoolean("night_mode")) {
                    settings.put("night_mode", "on")
                } else {
                    settings.put("night_mode", "off")
                }

                newFrom = "4.0.5"
            }

            if (newFrom == "4.0.5") {
                settings.put("video_hardware_acceleration", false)
                settings.put("no_audio_processing", false)
                newFrom = "4.0.6"
            }

            if (newFrom == "4.0.6") {
                settings.put("speakerphone_mode", "auto")
                settings.put("disable_call_history", false)
                newFrom = "4.0.7"
            }

            if (newFrom == "4.0.7") {
                settings.put("disable_proximity_sensor", false)
                newFrom = "4.0.8"
            }

            if (newFrom == "4.0.8") {
                // nothing to do
                newFrom = "4.0.9"
            }

            if (newFrom == "4.0.9") {
                settings.put("start_on_bootup", true)
                settings.put("disable_audio_processing", settings.getBoolean("no_audio_processing"))
                settings.remove("no_audio_processing")
                newFrom = "4.1.0"
            }

            if (newFrom == "4.1.0") {
                settings.put("show_username_as_logo", false)
                settings.put("connect_retries", 1)
                newFrom = "4.1.1"
            }

            if (newFrom == "4.1.1") {
                // nothing to do
                newFrom = "4.1.2"
            }

            if (newFrom == "4.1.2") {
                // nothing to do
                newFrom = "4.1.3"
            }

            if (newFrom == "4.1.3") {
                settings.put("push_to_talk", false)
                newFrom = "4.1.4"
            }

            if (newFrom == "4.1.4") {
                settings.put("enable_microphone_by_default", true)
                settings.put("enable_camera_by_default", false)
                settings.put("select_front_camera_by_default", true)
                newFrom = "4.1.5"
            }

            if (newFrom == "4.1.5") {
                settings.put("menu_password", "")
                settings.put("auto_accept_calls", false)
                newFrom = "4.1.6"
            }

            if (newFrom == "4.1.6") {
                settings.put("video_degradation_mode", "balanced")
                settings.put("camera_resolution", "auto")
                settings.put("camera_framerate", "auto")
                settings.put("disable_cpu_overuse_detection", false)
                newFrom = "4.1.7"
            }

            if (newFrom == "4.1.7") {
                settings.put("automatic_status_updates", true)
                newFrom = "4.1.8"
            }

            if (newFrom == "4.1.8") {
                val eventsObject = db.getJSONObject("events")
                // replace variable
                eventsObject.remove("events_viewed")
                eventsObject.put("events_missed", 0)
                newFrom = "4.1.9"
            }

            if (newFrom == "4.1.9") {
                // nothing to do
                newFrom = "4.2.0"
            }

            if (newFrom == "4.2.0") {
                // nothing to do
                newFrom = "4.2.1"
            }

            if (newFrom == "4.2.1") {
                // nothing to do
                newFrom = "4.2.2"
            }

            if (newFrom == "4.2.2") {
                // nothing to do
                newFrom = "4.2.3"
            }

            if (newFrom == "4.2.3") {
                // disable detection to enable custom resolution/framerate
                settings.put("disable_cpu_overuse_detection", true)
                newFrom = "4.2.4"
            }

            if (newFrom == "4.2.4") {
                // nothing to do
                newFrom = "4.2.5"
            }

            if (newFrom == "4.2.5") {
                // nothing to do
                newFrom = "4.2.6"
            }

            if (newFrom == "4.2.6") {
                // nothing to do
                newFrom = "4.2.7"
            }

            if (newFrom.startsWith("0.6.5")) {
                // clean up events from db if there are no
                // corresponding contacts
                val events = Events.fromJSON(db.getJSONObject("events")).eventList
                val contacts: Contacts = Contacts.fromJSON(db.getJSONObject("contacts"))
                events.removeAll {
                    contacts.findContact(it.publicKey) < 0
                }
                val e = Events()
                e.eventList = events
                db.put("events", Events.toJSON(e))

                newFrom = "0.6.6"
            }

            alignSettings(settings)

            db.put("version", newFrom)
            return true
        }

        private fun toJSON(db: Database): JSONObject {
            val obj = JSONObject()
            obj.put("version", db.version)
            obj.put("settings", Settings.toJSON(db.settings))
            obj.put("contacts", Contacts.toJSON(db.contacts))
            obj.put("events", Events.toJSON(db.events))
            obj.put("mesh", db.mesh.getJSON())
            return obj
        }

        private fun fromJSON(obj: JSONObject): Database {
            val db = Database()

            db.version = obj.getString("version")

            // import contacts
            val contacts = obj.getJSONObject("contacts")
            db.contacts = Contacts.fromJSON(contacts)

            // import settings
            val settings = obj.getJSONObject("settings")
            db.settings = Settings.fromJSON(settings)

            // import events
            val events = obj.getJSONObject("events")
            db.events = Events.fromJSON(events)

            // import mesh
            db.mesh = ConfigurationProxy().fromJSON(obj.getJSONObject("mesh"))

            return db
        }
    }
}