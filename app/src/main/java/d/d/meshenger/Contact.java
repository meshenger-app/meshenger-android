package d.d.meshenger;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;


public class Contact implements Serializable {
    private static final String TAG = "Contact";
    enum State { ONLINE, OFFLINE, UNKNOWN };

    private String name;
    private byte[] publicKey;
    private boolean blocked;
    private List<String> addresses;

    // contact state
    private State state = State.UNKNOWN;
    private long state_last_updated = System.currentTimeMillis();
    public static long STATE_TIMEOUT = 60 * 1000;

    // last working address (use this address next connection
    // and for unknown contact initialization)
    private InetAddress last_working_address = null;

    public Contact(String name, byte[] publicKey, List<String> addresses, boolean blocked) {
        this.name = name;
        this.publicKey = publicKey;
        this.addresses = addresses;
        this.blocked = blocked;
    }

    public State getState() {
        if ((state_last_updated + STATE_TIMEOUT) > System.currentTimeMillis()) {
            state = Contact.State.UNKNOWN;
        }
        return state;
    }

    public void setState(State state) {
        this.state_last_updated = System.currentTimeMillis();
        this.state = state;
    }

    public long getStateLastUpdated() {
        return this.state_last_updated;
    }

    public List<String> getAddresses() {
        return this.addresses;
    }

    public void addAddress(String address) {
        if (address.isEmpty()) {
            return;
        }

        for (String addr : this.addresses) {
            if (addr.equalsIgnoreCase(address)) {
                return;
            }
        }
        this.addresses.add(address);
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    // set good address to try first next time,
    // this is not stored in the database
    public void setLastWorkingAddress(InetAddress address) {
        this.last_working_address = address;
    }

    public InetAddress getLastWorkingAddress() {
        return this.last_working_address;
    }

    public static JSONObject toJSON(Contact contact) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();

        object.put("name", contact.name);
        object.put("public_key", Utils.byteArrayToHexString(contact.publicKey));
        object.put("blocked", contact.blocked);

        for (String address : contact.getAddresses()) {
            array.put(address);
        }
        object.put("addresses", array);

        return object;
    }

    public static Contact fromJSON(JSONObject obj) throws JSONException {
        String name = obj.getString("name");
        byte[] publicKey = Utils.hexStringToByteArray(obj.getString("public_key"));
        boolean blocked = obj.getBoolean("blocked");

        List<String> addresses = new ArrayList<>();
        JSONArray array = obj.getJSONArray("addresses");
        for (int i = 0; i < array.length(); i += 1) {
            addresses.add(array.getString(i).toUpperCase().trim());
        }

        return new Contact(name, publicKey, addresses, blocked);
    }
}
