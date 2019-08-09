package d.d.meshenger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.net.Socket;
import java.net.URI;


// Abstract data to contact a client
public interface ConnectionData extends Serializable {
    // Create socket to client
    Socket createSocket() throws Exception;

    // For sharing contact information
    JSONObject toJsonObject() throws JSONException;

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
                port = MainService.serverPort;
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
//This line is broken. Please look at the other place where the "fe80:.." is constructed.
 //use addressToEUI64() here
            return new Socket(address, MainService.serverPort);
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
}
