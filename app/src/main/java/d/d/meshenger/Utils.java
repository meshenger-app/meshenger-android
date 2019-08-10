package d.d.meshenger;

import android.util.Log;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;


class Utils {

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j += 1) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String str) {
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] macAddressToEUI64(String address) {
        String[] hexes = address.split(":");
        byte[] bytes = new byte[]{
                (byte) (Integer.decode("0x" + hexes[0]).byteValue() | 2),
                Integer.decode("0x" + hexes[1]).byteValue(),
                Integer.decode("0x" + hexes[2]).byteValue(),
                (byte) 0xFF,
                (byte) 0xFE,
                Integer.decode("0x" + hexes[3]).byteValue(),
                Integer.decode("0x" + hexes[4]).byteValue(),
                Integer.decode("0x" + hexes[5]).byteValue(),
        };
        return bytes;
    }

    public static byte[] getMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                return nif.getHardwareAddress();

            }
        } catch (Exception ex) {
            // ignore
        }

        return new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    public static String formatMacAddress(byte[] macBytes){
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
                    if (a.getAddress().isLinkLocalAddress()){
                        return a.getAddress().getHostAddress().replaceFirst("%.*", "%zone");
                    } else {
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
