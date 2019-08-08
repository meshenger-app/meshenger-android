package d.d.meshenger;

import android.util.Log;
import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class AppData implements Serializable {
    private long id;
    private long dbVer;
    private String secretKey;
    private String publicKey;
    private String username;
    private String language;
    private int mode;
    private int blockUC;
    private List<ConnectionData> connection_data;

    public AppData() {

    }

    public AppData(int id, long dbVer, String secretKey, String publicKey, String username,
            String language, int mode, int blockUC, List<ConnectionData> connection_data) {
        this.id = id;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
        this.connection_data = connection_data;
    }

    public AppData(long dbVer, String secretKey, String publicKey, String username,
            String language, int mode, int blockUC, List<ConnectionData> connection_data) {
        this.id = -1;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
        this.connection_data = new ArrayList<>();
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

    public List<ConnectionData> getConnectionData() {
        return this.connection_data;
    }

    public void addConnectionData(ConnectionData data) {
        this.connection_data.add(data);
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
                Log.e("AppData", e.toString());
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

    public Contact getOwnContact() {
        return new Contact(-1, this.username, "", this.publicKey, this.connection_data);
    }
}
