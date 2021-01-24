package d.d.meshenger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Contacts {
    private List<Contact> contacts;

    public Contacts() {
        contacts = new ArrayList<>();
    }

    public List<Contact> getContactList() {
        return contacts;
    }

    public List<Contact> getContactListCopy() {
        return new ArrayList<>(contacts);
    }

    public void addContact(Contact contact) {
        int idx = findContact(contact.getPublicKey());
        if (idx >= 0) {
            // contact exists - replace
            contacts.set(idx, contact);
        } else {
            contacts.add(contact);
        }
    }

    public void deleteContact(byte[] publicKey) {
        int idx = findContact(publicKey);
        if (idx >= 0) {
            this.contacts.remove(idx);
        }
    }

    private int findContact(byte[] publicKey) {
        for (int i = 0; i < contacts.size(); i += 1) {
            if (Arrays.equals(contacts.get(i).getPublicKey(), publicKey)) {
                return i;
            }
        }
        return -1;
    }

    public Contact getContactByPublicKey(byte[] publicKey) {
        for (Contact contact : contacts) {
            if (Arrays.equals(contact.getPublicKey(), publicKey)) {
                return contact;
            }
        }
        return null;
    }

    Contact getContactByName(String name) {
        for (Contact contact : contacts) {
            if (contact.getName().equals(name)) {
                return contact;
            }
        }
        return null;
    }

    public static JSONObject toJSON(Contacts contacts) throws JSONException {
        JSONObject obj = new JSONObject();

        JSONArray array = new JSONArray();
        for (Contact contact : contacts.contacts) {
            array.put(Contact.toJSON(contact));
        }
        obj.put("entries", array);

        return obj;
    }

    public static Contacts fromJSON(JSONObject obj) throws JSONException {
        Contacts contacts = new Contacts();

        JSONArray array = obj.getJSONArray("entries");
        for (int i = 0; i < array.length(); i += 1) {
            contacts.contacts.add(
                Contact.fromJSON(array.getJSONObject(i))
            );
        }
        return contacts;
    }
}
