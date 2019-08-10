package d.d.meshenger;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.ArrayList;


class Database {
    final static String DATABASE_FILENAME = "database.bin";

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

    // check if the database is encrypted
    public static boolean isEncrypted(Context context) {
        try {
            String path = context.getFilesDir() + "/" + DATABASE_FILENAME;
            byte[] data = Utils.readExternalFile(path);
            for (int i = 0; data != null && i < data.length; i += 1) {
                if (data[i] < 0x20) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Database load(Context context, String password) {
        String path = context.getFilesDir() + "/" + DATABASE_FILENAME;
        String version = Utils.getApplicationVersion(context);
        try {
            // read data
            byte[] data = Utils.readExternalFile(path);

            if (password != null & password.length() > 0) {
                data = Crypto.decryptData(data, password.getBytes());
            }

            JSONObject obj = new JSONObject(
                new String(data,  Charset.forName("UTF-8"))
            );

            if (!version.equals(obj.optString("version", ""))) {
                // TODO: upgrade database
            }

            return Database.fromJSON(obj);
        } catch (Exception e) {
            Log.e("Database", "Database file not found: " + path);
        }

        return new Database();
    }

    public static void store(Context context, Database db, String password) {
        try {
            String path = context.getFilesDir() + "/" + DATABASE_FILENAME;
            JSONObject obj = Database.toJSON(db);
            byte[] data = obj.toString().getBytes();

            if (password != null & password.length() > 0) {
                data = Crypto.encryptData(data, password.getBytes());
            }

            Utils.writeExternalFile(path, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
