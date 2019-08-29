package d.d.meshenger;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;


class Database {
    Settings settings;
    ArrayList<Contact> contacts;
    String version;

    Database() {
        this.contacts = new ArrayList<>();
        this.settings = new Settings();
        this.version = "2.1.0";
    }

    public boolean contactExists(String publicKey) {
        return (this.findContact(publicKey) >= 0);
    }

    public void addContact(Contact contact) {
        int idx = findContact(contact.getPublicKey());
        if (idx >= 0) {
            // contact exists - replace
            this.contacts.set(idx, contact);
        } else {
            this.contacts.add(contact);
        }
    }

    public void deleteContact(byte[] publicKey) {
        int idx = this.findContact(publicKey);
        if (idx >= 0) {
            this.contacts.remove(idx);
        }
    }

    public int findContact(byte[] publicKey) {
        for (int i = 0; i < this.contacts.size(); i += 1) {
            if (Arrays.equals(this.contacts.get(i).getPublicKey(), publicKey)) {
                return i;
            }
        }
        return -1;
    }

    public static Database load(String path, String password) throws IOException, JSONException {
        // read database file
        byte[] data = Utils.readExternalFile(path);

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.decryptData(data, password.getBytes());
        }

        JSONObject obj = new JSONObject(
            new String(data, Charset.forName("UTF-8"))
        );

        obj = upgradeDatabase(obj.getString("version"), "2.1.0", obj);

        return Database.fromJSON(obj);
    }

    public static void store(String path, Database db, String password) throws IOException, JSONException {
        JSONObject obj = Database.toJSON(db);
        byte[] data = obj.toString().getBytes();

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.encryptData(data, password.getBytes());
        }

        // write database file
        Utils.writeExternalFile(path, data);
    }

    private static JSONObject upgradeDatabase(String from, String to, JSONObject obj) throws JSONException {
        if (from.equals("2.0.0")) {
            // add blocked field (added in 2.1.0)
            JSONArray contacts = obj.getJSONArray("contacts");
            for (int i = 0; i < contacts.length(); i += 1) {
                contacts.getJSONObject(i).put("blocked", false);
            }
        }
        return obj;
    }

    public static JSONObject toJSON(Database db) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", db.version);
        obj.put("settings", Settings.exportJSON(db.settings));

        JSONArray contacts = new JSONArray();
        for (Contact contact : db.contacts) {
            contacts.put(Contact.exportJSON(contact, true));
        }
        obj.put("contacts", contacts);

        return obj;
    }

    public static Database fromJSON(JSONObject obj) throws JSONException {
        Database db = new Database();

        // import version
        db.version = obj.getString("version");

        // import contacts
        JSONArray array = obj.getJSONArray("contacts");
        for (int i = 0; i < array.length(); i += 1) {
            db.contacts.add(
                Contact.importJSON(array.getJSONObject(i), true)
            );
        }

        // import settings
        JSONObject settings = obj.getJSONObject("settings");
        db.settings = Settings.importJSON(settings);

        return db;
    }
}
