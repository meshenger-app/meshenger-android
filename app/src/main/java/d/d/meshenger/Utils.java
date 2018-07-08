package d.d.meshenger;


import android.util.Log;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

class Utils {


    public static byte[] getMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                return nif.getHardwareAddress();

            }
        } catch (Exception ex) {
        }
        return new byte[]{0x02, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static String formatAddress(byte[] macBytes){
        StringBuilder res1 = new StringBuilder();
        for (byte b : macBytes) {
            res1.append(String.format("%02X:",b));
        }

        if (res1.length() > 0) {
            res1.deleteCharAt(res1.length() - 1);
        }
        return res1.toString();
    }

    public static byte[] getEUI64(byte[] mac){
        return new byte[]{(byte)(mac[0] | 2), mac[1], mac[2], (byte)0xFF, (byte)0xFE, mac[3], mac[4], mac[5]};
    }

    public static byte[] getEUI64(){
        return getEUI64(getMacAddress());
    }

    public static String getLinkLocalAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                String fallBackAddress = null;
                for(InterfaceAddress a : nif.getInterfaceAddresses()) {
                    if(a.getAddress().isLinkLocalAddress()){
                        return a.getAddress().getHostAddress();
                    }else{
                        fallBackAddress = a.getAddress().getHostAddress();
                    }
                }
                return fallBackAddress;
            }
        } catch (Exception ex) {
        }
        return null;
    }

}
