package d.d.meshenger;

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
    static String version = "3.0.3"; // current version

    Database() {
        this.contacts = new ArrayList<>();
        this.settings = new Settings();
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

    public void onDestroy() {
        // zero keys from memory
        if (this.settings.getSecretKey() != null) {
            Arrays.fill(this.settings.getSecretKey(), (byte) 0);
        }

        if (this.settings.getPublicKey() != null) {
            Arrays.fill(this.settings.getPublicKey(), (byte) 0);
        }

        for (Contact contact : this.contacts) {
            if (contact.getPublicKey() != null) {
                Arrays.fill(contact.getPublicKey(), (byte) 0);
            }
        }
    }

    public static Database load(String path, String password) throws IOException, JSONException {
        // read database file
        byte[] data = Utils.readExternalFile(path);

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.decryptDatabase(data, password.getBytes());

            if (data == null) {
                throw new IOException("wrong database password.");
            }
        }

        JSONObject obj = new JSONObject(
            new String(data, Charset.forName("UTF-8"))
        );

        boolean upgraded = upgradeDatabase(obj.getString("version"), Database.version, obj);
        Database db = Database.fromJSON(obj);

        if (upgraded) {
            log("store updated database");
            Database.store(path, db, password);
        }

        return db;
    }

    public static void store(String path, Database db, String password) throws IOException, JSONException {
        JSONObject obj = Database.toJSON(db);
        byte[] data = obj.toString().getBytes();

        // encrypt database
        if (password != null && password.length() > 0) {
            data = Crypto.encryptDatabase(data, password.getBytes());
        }

        // write database file
        Utils.writeExternalFile(path, data);
    }

    private static boolean upgradeDatabase(String from, String to, JSONObject obj) throws JSONException {
        if (from.equals(to)) {
            return false;
        }

        log("upgrade database from " + from + " to " + to);

        // 2.0.0 => 2.1.0
        if (from.equals("2.0.0")) {
            // add blocked field (added in 2.1.0)
            JSONArray contacts = obj.getJSONArray("contacts");
            for (int i = 0; i < contacts.length(); i += 1) {
                contacts.getJSONObject(i).put("blocked", false);
            }
            from = "2.1.0";
        }

        // 2.1.0 => 3.0.0
        if (from.equals("2.1.0")) {
            // add new fields
            obj.getJSONObject("settings").put("ice_servers", new JSONArray());
            obj.getJSONObject("settings").put("development_mode", false);
            from = "3.0.0";
        }

        // 3.0.0 => 3.0.1
        if (from.equals("3.0.0")) {
            // nothing to do
            from = "3.0.1";
        }

        // 3.0.1 => 3.0.2
        if (from.equals("3.0.1")) {
            // fix typo in setting name
            obj.getJSONObject("settings").put("night_mode", obj.getJSONObject("settings").getBoolean("might_mode"));
            from = "3.0.2";
        }

        // 3.0.2 => 3.0.3
        if (from.equals("3.0.2")) {
            // nothing to do
            from = "3.0.3";
        }

        obj.put("version", from);

        return true;
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

    private static void log(String s) {
        Log.d("Database", s);
    }
}
