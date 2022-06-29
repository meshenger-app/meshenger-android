package d.d.meshenger

import d.d.meshenger.mock.MockContacts
import org.json.JSONArray

import org.json.JSONException

import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

//TODO: Mock Objects are active here
class Contacts {

    private val contacts: ArrayList<Contact?> = MockContacts.generateMockContactList()
//    ArrayList()

    companion object {
        @Throws(JSONException::class)
        fun toJSON(contacts: Contacts): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (contact in contacts.contacts) {
                array.put(Contact.toJSON(contact!!))
            }
            obj.put("entries", array)
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Contacts {
            val contacts = Contacts()
            val array = obj.getJSONArray("entries")
            var i = 0
            while (i < array.length()) {
                contacts.contacts.add(
                    Contact.fromJSON(array.getJSONObject(i))
                )
                i += 1
            }
            return contacts
        }
    }

    fun getContactList(): ArrayList<Contact?> = contacts

    fun getContactListCopy(): ArrayList<Contact?> = ArrayList(contacts)

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

    private fun findContact(publicKey: ByteArray?): Int {
        var i = 0
        while (i < contacts.size) {
            if (Arrays.equals(contacts[i]!!.publicKey, publicKey)) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun getContactByPublicKey(publicKey: ByteArray?): Contact? {
        for (contact in contacts) {
            if (Arrays.equals(contact!!.publicKey, publicKey)) {
                return contact
            }
        }
        return null
    }

    fun getContactByName(name: String): Contact? {
        for (contact in contacts) {
            if (contact!!.name == name) {
                return contact
            }
        }
        return null
    }



}