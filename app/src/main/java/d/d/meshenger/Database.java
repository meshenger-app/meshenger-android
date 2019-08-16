package d.d.meshenger;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;


class Database {
    Settings settings;
    ArrayList<Contact> contacts;
    String version;

    Database() {
        this.contacts = new ArrayList<>();
        this.settings = new Settings();
        this.version = "";
    }

    public boolean contactExists(String publicKey) {
        return (this.findContact(publicKey) >= 0);
    }

    public void addContact(Contact contact) throws ContactAlreadyAddedException {
        if (this.contactExists(contact.getPublicKey())) {
            throw new ContactAlreadyAddedException();
        } else {
            this.contacts.add(contact);
        }
    }

    public int findContact(String publicKey) {
        for (int i = 0; i < this.contacts.size(); i += 1) {
            if (this.contacts.get(i).getPublicKey().equalsIgnoreCase(publicKey)) {
                return i;
            }
        }
        return -1;
    }

    public void deleteContact(String publicKey) {
        int idx = this.findContact(publicKey);
        if (idx >= 0) {
            this.contacts.remove(idx);
        }
    }

    class ContactAlreadyAddedException extends Exception {
        @Override
        public String getMessage() {
            return "Contact already added";
        }
    }

    public static Database load(String path, String password) throws IOException, JSONException {
        String version = "2.0.0";

        // read data
        byte[] data = Utils.readExternalFile(path);

        if (password != null & password.length() > 0) {
            Log.d("Database", "use password for database: " + password);
            data = Crypto.decryptData(data, password.getBytes());
        }

        JSONObject obj = new JSONObject(
            new String(data, Charset.forName("UTF-8"))
        );

        if (!version.equals(obj.optString("version", ""))) {
            obj = upgradeDatabase(obj.optString("version", ""), version, obj);
        }

        return Database.fromJSON(obj);
    }

    public static void store(String path, Database db, String password) throws IOException, JSONException {
        JSONObject obj = Database.toJSON(db);
        byte[] data = obj.toString().getBytes();

        if (password != null & password.length() > 0) {
            data = Crypto.encryptData(data, password.getBytes());
        }

        Utils.writeExternalFile(path, data);
    }

    private static JSONObject upgradeDatabase(String from, String to, JSONObject obj) {
        // not much to do yet
        try {
            if (from.isEmpty()) {
                obj.put("version", to);
            }
        } catch (Exception e) {
            // ignore ...
        }
        return obj;
    }

    public static JSONObject toJSON(Database db) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("version", db.version);
        obj.put("settings", Settings.exportJSON(db.settings));

        JSONArray contacts = new JSONArray();
        for (Contact contact : db.contacts) {
            contacts.put(Contact.exportJSON(contact));
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
                Contact.importJSON(array.getJSONObject(i))
            );
        }

        // import settings
        JSONObject settings = obj.getJSONObject("settings");
        db.settings = Settings.importJSON(settings);

        return db;
    }
}
