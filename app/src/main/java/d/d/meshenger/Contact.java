package d.d.meshenger;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;


public class Contact implements Serializable {
    enum State { ONLINE, OFFLINE, PENDING };

    private String name;
    private String pubkey;
    private ArrayList<String> addresses;

    // contact state
    private State state = State.PENDING;

    // last successful address
    private InetSocketAddress last_address = null;

    public Contact(String name, String pubkey, ArrayList<String> addresses) {
        this.name = name;
        this.pubkey = pubkey;
        this.addresses = addresses;
    }

    private Contact() {
        this.name = "";
        this.pubkey = "";
        this.addresses = new ArrayList<>();
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public ArrayList<String> getAddresses() {
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

    public ArrayList<InetSocketAddress> getAllSocketAddresses() {
        ArrayList<InetSocketAddress> addrs = new ArrayList<>();
        for (String address : this.addresses) {
            try {
                if (Utils.isMAC(address)) {
                    addrs.addAll(Utils.getAddressPermutations(address, MainService.serverPort));
                } else {
                    addrs.add(Utils.parseInetSocketAddress(address, MainService.serverPort));
                }
            } catch (Exception e) {
                log("invalid address: " + address);
                e.printStackTrace();
            }
        }

        return addrs;
    }

    public String getPublicKey() {
        return pubkey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private static Socket establishConnection(InetSocketAddress address) {
        Socket socket = new Socket();
        try {
            // timeout to establish connection
            socket.connect(address, 300);
            return socket;
        } catch (SocketTimeoutException e) {
            // ignore
        } catch (ConnectException e) {
            // device is online, but does not listen on the given port
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                // ignore
            }
        }

        return null;
    }

    /*
    * Create a connection to the contact.
    * Try/Remember the last successful address.
    */
    public Socket createSocket() {
        Socket socket = null;

        ArrayList<InetSocketAddress> addresses = this.getAllSocketAddresses();

        // put last working address first in list
        if (this.last_address != null) {
            for (int i = 1; i < addresses.size(); i += 1) {
                if (addresses.get(i).equals(this.last_address)) {
                    // swap address
                    InetSocketAddress tmp = addresses.get(i);
                    addresses.set(i, addresses.get(0));
                    addresses.set(0, tmp);
                    break;
                }
            }
        }

        for (InetSocketAddress address : addresses) {
            socket = this.establishConnection(address);
            if (socket != null) {
                // TODO: address not verified yet!
                this.last_address = address;
                return socket;
            }
        }

        return null;
    }

    public static JSONObject exportJSON(Contact contact) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();

        object.put("name", contact.name);
        object.put("public_key", contact.pubkey);

        for (String address : contact.getAddresses()) {
            array.put(address);
        }
        object.put("addresses", array);

        return object;
    }

    public static Contact importJSON(JSONObject object) throws JSONException {
        Contact contact = new Contact();
        contact.name = object.getString("name");
        contact.pubkey = object.getString("public_key");

        JSONArray array = object.getJSONArray("addresses");
        for (int i = 0; i < array.length(); i += 1) {
            contact.addAddress(array.getString(i));
        }

        return contact;
    }

    private void log(String s) {
        Log.d(Contact.class.getSimpleName(), s);
    }
}
