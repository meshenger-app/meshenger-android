package d.d.meshenger;


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.Serializable;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Contact implements Serializable {
    enum State{ONLINE, OFFLINE, PENDING};

    // Abstract data to contact a client
    public interface ConnectionData extends Serializable {
        // Create socket to client
        Socket createSocket() throws Exception;

        // For sharing contact information
        JSONObject toJsonObject() throws JSONException;
    }

    // Store a DNS or IP address
    static class Hostname implements ConnectionData {
        public String host_address;

        Hostname(String host_address) {
            this.host_address = host_address;
        }

        public Socket createSocket() throws Exception {
            URI uri = new URI("my://" + this.host_address);
            String host = uri.getHost();
            int port = uri.getPort();

            // set default port
            if (uri.getPort() == -1) {
                port = 10001;
            }

            if (uri.getHost() == null) {
                return null;
            }

            return new Socket(host, port);
        }

        public JSONObject toJsonObject() throws JSONException {
            JSONObject object =  new JSONObject();
            object.put("type", "Hostname");
            object.put("host_address", this.host_address);
            return object;
        }
    }

    // MAC address to construct IPv6 link local (eui-64) address
    static class LinkLocal implements ConnectionData {
        public String mac_address;
        public Integer port;

        LinkLocal(String mac_address, Integer port) {
            this.mac_address = mac_address;
            this.port = port;
        }

        public Socket createSocket() throws Exception {
            String[] bytes = this.mac_address.split(":");

            if (this.mac_address.length() != 17 || bytes.length != 6) {
                return null;
            }

            String address = "fe80"
                    + ":" + bytes[0] + bytes[1]
                    + ":" + bytes[2] + "ff"
                    + ":" + "fe" + bytes[3]
                    + ":" + bytes[4] + bytes[5]
                    + "%zone";

            return new Socket(address, 10001);
        }

        public JSONObject toJsonObject() throws JSONException {
            JSONObject object =  new JSONObject();
            object.put("type", "LinkLocal");
            object.put("mac_address", this.mac_address);
            object.put("port", this.port);
            return object;
        }
    }

    // Contact a client over the Internet using some kind of signaling server
    static class SignalingServer implements ConnectionData {
        // host name or IP address
        public String signaling_server;

        SignalingServer(String signaling_server) {
            this.signaling_server = signaling_server;
        }

        public Socket createSocket() throws Exception {
            return null; // TODO
        }

        public JSONObject toJsonObject() throws JSONException {

            JSONObject object = new JSONObject();
            object.put("type", "SignalingServer");
            object.put("signaling_server", signaling_server);

            return object;
        }
    }

    private long id;
    private String name;
    public String pubKey;
    private String info;
    private List<ConnectionData> connection_data;

    public boolean recent = false;

    private State state = State.PENDING;

    public Contact(int id, String name, String info, String pubKey, List<ConnectionData> connection_data) {
        this.id = id;
        this.name = name;
        this.pubKey = pubKey;
        this.info = info;
        this.connection_data = connection_data;
    }

    public Contact(String name, String info, String photo) {
        this.id = -1;
        this.name = name;
        this.info = info;
        this.pubKey = pubKey;
        this.connection_data = new ArrayList<>();
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getId() {
        return id;
    }

    public List<ConnectionData> getConnectionData() {
        return this.connection_data;
    }

    public void addConnectionData(ConnectionData data) {
        this.connection_data.add(data);
    }

    public String getPubKey() { return pubKey; }

    public void setPubKey(String pubKey) { this.pubKey = pubKey; }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public Socket createSocket() {
        int working_idx = -1;
        Socket socket = null;

        for (int i = 0; i < this.connection_data.size(); i += 1) {
            try {
                socket = this.connection_data.get(i).createSocket();
                if (socket != null) {
                    //make sure this is first
                    working_idx = i;
                    break;
                }
            } catch (Exception e) {
                Log.e("Contact", e.toString());
            }
        }

        if (working_idx > 0) {
            // move working data to front of list
            ConnectionData item = this.connection_data.remove(working_idx);
            this.connection_data.add(0, item);
        }

        return socket;
    }

    // check if the address belongs to this contact (hack: only handles MAC addresses)
    // TODO: needs to be done by authentication by key
    public boolean matchEndpoint(String mac_address) {
        for (ConnectionData data : this.connection_data) {
            if (data instanceof LinkLocal) {
                if (((LinkLocal) data).mac_address == mac_address) {
                    return true;
                }
            }
        }
        return false;
    }

    // share contact with others
    public static String exportJSON(Contact contact) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();

        object.put("name", contact.name);
        object.put("publickey", contact.pubKey);

        for (int i = 0; i < contact.connection_data.size(); i += 1) {
            array.put(i, contact.connection_data.get(i).toJsonObject());
        }

        object.put("connection_data", array);

        return object.toString();
    }

    public static Contact importJSON(String contact) throws JSONException {
        JSONObject object = new JSONObject(contact);
        String name = object.getString("name");
        String publickey = object.getString("publickey");
        List<ConnectionData> cdata = new ArrayList<ConnectionData>();
        JSONArray data_array = object.getJSONArray("connection_data");
        Contact c = new Contact(name, "",publickey);

        for (int i = 0; i < data_array.length(); i += 1) {
            JSONObject item = data_array.getJSONObject(i);
            String type = item.getString("type");
            if (type.equals("Hostname")) {
                c.addConnectionData(
                        new Hostname(item.getString("host_address"))
                );
            } else if (type.equals("LinkLocal")) {
                c.addConnectionData(
                        new LinkLocal(
                                item.getString("mac_address"),
                                10001
                        )
                );
            } else if (type.equals("SignalingServer")) {
                c.addConnectionData(
                        new SignalingServer(
                                item.getString("signaling_server")
                        ));
            } else {
                throw new JSONException("Unknown connection data type: " + type);
            }
        }

        return c;
    }
}

/*import java.io.Serializable;

public class Contact implements Serializable{
    enum State{ONLINE, OFFLINE, PENDING}

    private long id;
    private String address;
    private String name;
    public String pubKey;
    private String identifier;
    private String info;

    public boolean recent = false;

    private State state = State.PENDING;

    public Contact(int id, String address, String name, String info, String pubKey, String identifier) {
        this.id = id;
        this.address = address;
        this.name = name;
        this.pubKey = pubKey;
        this.identifier = identifier;
        this.info = info;
    }

    public Contact(String address, String name, String info, String pubKey, String identifier) {
        this.id = -1;
        this.address = address;
        this.name = name;
        this.pubKey = pubKey;
        this.identifier = identifier;
        this.info = info;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPubKey(String pubKey) { this.pubKey = pubKey; }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
*/