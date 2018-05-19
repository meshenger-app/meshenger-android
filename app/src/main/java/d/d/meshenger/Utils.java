package d.d.meshenger;


import android.util.Log;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class Utils {


    public static String getMac() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:",b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
        }
        return "02:00:00:00:00:00";
    }

    public static String getAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                for(InterfaceAddress a : nif.getInterfaceAddresses()) {
                    if(a.getAddress().isLinkLocalAddress()){
                        return a.getAddress().toString().substring(1);
                    }
                }

                for(InterfaceAddress a : nif.getInterfaceAddresses()) {
                    if(!a.getAddress().isLinkLocalAddress()){
                        return a.getAddress().toString().substring(1);
                    }
                }
            }
        } catch (Exception ex) {
        }
        return null;
    }

}
