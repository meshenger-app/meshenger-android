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

public class AppData implements Serializable {
    enum State{ONLINE, OFFLINE, PENDING};

    // Abstract data to contact a client
    public interface ConnectData extends Serializable {
        // Create socket to client
        Socket createSocket() throws Exception;

        // For sharing contact information
        JSONObject toJsonObject() throws JSONException;
    }

    // Store a DNS or IP address
    static class Hostname implements ConnectData {
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
    static class LinkLocal implements ConnectData {
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
                    + "%wlan0";

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

    // Contact a client over the Internet using some kind of chat server
    static class CustomChatServer implements ConnectData {
        // host name or IP address
        public String chat_server;

        CustomChatServer(String chat_server) {
            this.chat_server = chat_server;
        }

        public Socket createSocket() throws Exception {
            return null; // TODO
        }

        public JSONObject toJsonObject() throws JSONException {

            JSONObject object = new JSONObject();
            object.put("type", "CustomChatServer");
            object.put("chat_server", chat_server);

            return object;
        }
    }

        private long id;
        private long dbVer;
        private String secretKey;
        private String publicKey;
        private String username;
        private String identifier1;
        private String language;
        private int mode;
        private int blockUC;
        private List<ConnectData> connect_data;

    public boolean recent = false;

    private State state = State.PENDING;

    public AppData() {}

    public AppData(int id, long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC, List<ConnectData> connect_data) {
        this.id = id;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
        this.connect_data = connect_data;
    }

    public AppData(long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC, List<ConnectData> connect_data) {
        this.id = -1;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
        this.connect_data = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDbVer() {
        return dbVer;
    }

    public void setDbVer(long dbVer) {
        this.dbVer = dbVer;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdentifier1() {
        return identifier1;
    }

    public void setIdentifier1(String identifier1) {
        this.identifier1 = identifier1;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getBlockUC() {
        return blockUC;
    }

    public void setBlockUC(int blockUC) {
        this.blockUC = blockUC;
    }

    public List<ConnectData> getConnectData() {
        return this.connect_data;
    }

    public void addConnectData(ConnectData data) {
        this.connect_data.add(data);
    }

    public Socket createSocket() {
        int working_idx = -1;
        Socket socket = null;

        for (int i = 0; i < this.connect_data.size(); i += 1) {
            try {
                socket = this.connect_data.get(i).createSocket();
                if (socket != null) {
                    //make sure this is first
                    working_idx = i;
                    break;
                }
            } catch (Exception e) {
                Log.e("AppData", e.toString());
            }
        }

        if (working_idx > 0) {
            // move working data to front of list
            ConnectData item = this.connect_data.remove(working_idx);
            this.connect_data.add(0, item);
        }

        return socket;
    }

    // check if the address belongs to this contact (hack: only handles MAC addresses)
    // TODO: needs to be done by authentication by key
    public boolean matchEndpoint(String mac_address) {
        for (ConnectData data : this.connect_data) {
            if (data instanceof LinkLocal) {
                if (((LinkLocal) data).mac_address == mac_address) {
                    return true;
                }
            }
        }
        return false;
    }

    // share contact with others
    public static String exportJSON(AppData appData) throws JSONException {
        JSONObject object = new JSONObject();
        JSONArray array = new JSONArray();

        object.put("name", appData.username);
        object.put("publicKey", appData.publicKey);

        for (int i = 0; i < appData.connect_data.size(); i += 1) {
            array.put(i, appData.connect_data.get(i).toJsonObject());
        }

        object.put("connection_data", array);

        return object.toString();
    }

    public static AppData importJSON(String appData) throws JSONException {
        JSONObject object = new JSONObject(appData);
        String name = object.getString("name");
        String publickey = object.getString("publickey");

        List<ConnectData> adata = new ArrayList<ConnectData>();
        JSONArray data_array = object.getJSONArray("connection_data");
        AppData a = new AppData();

        for (int i = 0; i < data_array.length(); i += 1) {
            JSONObject item = data_array.getJSONObject(i);
            String type = item.getString("type");
            if (type.equals("Hostname")) {
                a.addConnectData(
                        new Hostname(item.getString("host_address"))
                );
            } else if (type.equals("LinkLocal")) {
                a.addConnectData(
                        new LinkLocal(
                                item.getString("mac_address"),
                                10001
                        )
                );
            } else if (type.equals("CustomChatServer")) {
                a.addConnectData(
                        new CustomChatServer(
                                item.getString("chat_server")
                        ));
            } else {
                throw new JSONException("Unknown connection data type: " + type);
            }
        }

        return a;
    }
}

/*package d.d.meshenger;

import java.io.Serializable;

public class AppData implements Serializable {

    private long id;
    private long dbVer;
    private String secretKey;
    private String publicKey;
    private String username;
    private String identifier1;
    private String language;
    private int mode;
    private int blockUC;

   public AppData(){}

   public AppData(int id, long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC) {
        this.id = id;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
    }

    public AppData(long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC) {
        this.id = -1;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDbVer() {
        return dbVer;
    }

    public void setDbVer(long dbVer) {
        this.dbVer = dbVer;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdentifier1() {
        return identifier1;
    }

    public void setIdentifier1(String identifier1) {
        this.identifier1 = identifier1;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getBlockUC() {
        return blockUC;
    }

    public void setBlockUC(int blockUC) {
        this.blockUC = blockUC;
    }
}
*/