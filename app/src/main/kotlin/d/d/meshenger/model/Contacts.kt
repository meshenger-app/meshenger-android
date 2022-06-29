package d.d.meshenger.model

import d.d.meshenger.mock.MockContacts
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class Contacts {

    companion object {

        @Throws(JSONException::class)
        fun toJSON(contacts: Contacts): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (contact in contacts.contactList) {
                array.put(Contact.toJSON(contact!!))
            }
            obj.put("entries", array)
            return obj
        }

        @Throws(JSONException::class)
        fun fromJSON(obj: JSONObject): Contacts {
            val contactList = Contacts()
            val array = obj.getJSONArray("entries")
            var i = 0
            while (i < array.length()) {
                contactList.contactList.add(
                    Contact.fromJSON(array.getJSONObject(i))
                )
                i += 1
            }
            return contactList
        }

    }

    val contactList= MockContacts.generateMockContactList()


    fun getContactListCopy(): ArrayList<Contact> {
        return ArrayList<Contact>(contactList)
    }

    fun addContact(contact: Contact) {
        val idx = findContact(contact.publicKey)
        if (idx >= 0) {
            // contact exists - replace
            contactList.set(idx, contact)
        } else {
            contactList.add(contact)
        }
    }

    fun deleteContact(publicKey: ByteArray) {
        val idx = findContact(publicKey)
        if (idx >= 0) {
            this.contactList.removeAt(idx)
        }
    }

    private fun findContact(publicKey: ByteArray): Int {
        var i = 0
        while (i < contactList.size) {
            if (Arrays.equals(contactList.get(i).publicKey, publicKey)) {
                return i
            }
            i += 1
        }
        return -1
    }

    fun getContactByPublicKey(publicKey: ByteArray): Contact? {
        for (contact in contactList) {
            if (Arrays.equals(contact.publicKey, publicKey)) {
                return contact
            }
        }
        return null
    }

    fun getContactByName(name: String): Contact? {
        for (contact in contactList) {
            if (contact.name == name) {
                return contact
            }
        }
        return null
    }

}