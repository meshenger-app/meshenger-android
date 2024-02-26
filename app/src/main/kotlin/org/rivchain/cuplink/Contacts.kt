package org.rivchain.cuplink

import org.json.JSONArray
import org.json.JSONObject

class Contacts {
    val contactList = mutableListOf<Contact>()

    fun destroy() {
        for (contact in contactList) {
            contact.publicKey.fill(0)
        }
    }

    fun addContact(contact: Contact) {
        val idx = findContact(contact.publicKey)
        if (idx >= 0) {
            // contact exists - replace
            contactList[idx] = contact
        } else {
            contactList.add(contact)
        }
    }

    fun deleteContact(publicKey: ByteArray) {
        val idx = findContact(publicKey)
        if (idx >= 0) {
            contactList.removeAt(idx)
        }
    }

    private fun findContact(publicKey: ByteArray): Int {
        for (i in 0 until contactList.size) {
            if (contactList[i].publicKey.contentEquals(publicKey)) {
                return i
            }
        }
        return -1
    }

    fun getContactByPublicKey(publicKey: ByteArray): Contact? {
        for (contact in contactList) {
            if (contact.publicKey.contentEquals(publicKey)) {
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

    companion object {
        fun toJSON(contacts: Contacts): JSONObject {
            val obj = JSONObject()
            val array = JSONArray()
            for (contact in contacts.contactList) {
                array.put(Contact.toJSON(contact, true))
            }
            obj.put("entries", array)
            return obj
        }

        fun fromJSON(obj: JSONObject): Contacts {
            val contacts = Contacts()
            val array = obj.getJSONArray("entries")
            for (i in 0 until array.length()) {
                contacts.contactList.add(
                    Contact.fromJSON(array.getJSONObject(i),true)
                )
            }
            return contacts
        }
    }
}