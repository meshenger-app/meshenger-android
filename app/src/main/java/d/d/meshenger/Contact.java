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
    enum State { ONLINE, OFFLINE, PENDING };

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
            if (data instanceof ConnectionData.LinkLocal) {
                if (((ConnectionData.LinkLocal) data).mac_address == mac_address) {
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

        object.put("username", contact.name);
        object.put("publicKey", contact.pubKey);

        for (int i = 0; i < contact.connection_data.size(); i += 1) {
            array.put(i, contact.connection_data.get(i).toJsonObject());
        }
        object.put("connection_data", array);

        return object.toString();
    }

    public static Contact importJSON(String contact) throws JSONException {
        JSONObject object = new JSONObject(contact);
        String name = object.getString("username");
        String pubKey = object.getString("publicKey");
        List<ConnectionData> cdata = new ArrayList<ConnectionData>();
        JSONArray data_array = object.getJSONArray("connection_data");
        Contact c = new Contact(name, "",pubKey);

        for (int i = 0; i < data_array.length(); i += 1) {
            JSONObject item = data_array.getJSONObject(i);
            String type = item.getString("type");
            if (type.equals("Hostname")) {
                c.addConnectionData(
                    new ConnectionData.Hostname(item.getString("host_address"))
                );
            } else if (type.equals("LinkLocal")) {
                c.addConnectionData(
                    new ConnectionData.LinkLocal(
                        item.getString("mac_address"),
                        MainService.serverPort
                    )
                );
            } else if (type.equals("SignalingServer")) {
                c.addConnectionData(
                    new ConnectionData.SignalingServer(
                        item.getString("signaling_server")
                    ));
            } else {
                throw new JSONException("Unknown connection data type: " + type);
            }
        }

        return c;
    }
}
