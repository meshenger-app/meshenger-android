package d.d.meshenger;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class Settings {
    private String username;
    private byte[] secretKey;
    private byte[] publicKey;
    private String language;
    private boolean nightMode;
    private boolean blockUnknown;
    private ArrayList<String> addresses;

    public Settings() {
        this.username = "";
        this.secretKey = null;
        this.publicKey = null;
        this.language = "";
        this.nightMode = false;
        this.blockUnknown = false;
        this.addresses = new ArrayList<>();
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean getNightMode() {
        return nightMode;
    }

    public void setNightMode(boolean nightMode) {
        this.nightMode = nightMode;
    }

    public boolean getBlockUnknown() {
        return blockUnknown;
    }

    public void setBlockUnknown(boolean blockUnknown) {
        this.blockUnknown = blockUnknown;
    }

    public ArrayList<String> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(ArrayList<String> addresses) {
        this.addresses = addresses;
    }

    public void addAddress(String address) {
        for (String addr : this.getAddresses()) {
            if (addr.equalsIgnoreCase(address)) {
                return;
            }
        }
        this.addresses.add(address);
    }

    public static Settings importJSON(JSONObject obj) throws JSONException {
        Settings s = new Settings();
        s.username = obj.getString("username");
        s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"));
        s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"));
        s.language = obj.getString("language");
        s.nightMode = obj.getBoolean("might_mode");
        s.blockUnknown = obj.getBoolean("block_unknown");
        JSONArray array = obj.getJSONArray("addresses");
        for (int i = 0; i < array.length(); i += 1) {
            s.addresses.add(array.getString(i));
        }
        return s;
    }

    public static JSONObject exportJSON(Settings s) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("username", s.username);
        obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey));
        obj.put("public_key", Utils.byteArrayToHexString(s.publicKey));
        obj.put("language", s.language);
        obj.put("might_mode", s.nightMode);
        obj.put("block_unknown", s.blockUnknown);
        JSONArray array = new JSONArray();
        for (int i = 0; i < s.addresses.size(); i += 1) {
            array.put(s.addresses.get(i));
        }
        obj.put("addresses", array);
        return obj;
    }

    public Contact getOwnContact() {
        return new Contact(this.username, this.publicKey, this.addresses);
    }
}
