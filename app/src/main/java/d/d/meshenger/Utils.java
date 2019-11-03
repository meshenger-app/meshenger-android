package d.d.meshenger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;


class Utils {

    public static boolean hasReadPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasWritePermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasCameraPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    public static void requestCameraPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.CAMERA}, request_code);
    }

    public static void requestReadPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE}, request_code);
    }

    public static void requestWritePermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, request_code);
    }

    public static boolean allGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String getApplicationVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String join(List<String> list) {
        return TextUtils.join(", ", list);
    }

    public static List<String> split(String str) {
        String[] parts = str.split("\\s*,\\s*");
        return Arrays.asList(parts);
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("[\\w _-]+");

    // check for a name that has no funny unicode characters to not let them look to much like other names
    public static boolean isValidName(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }

        if (!name.equals(name.trim())) {
            return false;
        }

        return NAME_PATTERN.matcher(name).matches();
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String byteArrayToHexString(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j += 1) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String str) {
        if (str == null) {
            return new byte[0];
        }
        int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    public static InetSocketAddress parseInetSocketAddress(String addr, int defaultPort) {
        if (addr == null || addr.length() == 0) {
            return null;
        }

        int firstColon = addr.indexOf(':');
        int lastColon = addr.lastIndexOf(':');
        int port = -1;

        try {
            // parse port suffix
            if (firstColon > 0 && lastColon > 0) {
                if (addr.charAt(lastColon - 1) == ']' || firstColon == lastColon) {
                    port = Integer.parseInt(addr.substring(lastColon + 1));
                    addr = addr.substring(0, lastColon);
                }
            }

            if (port < 0) {
                port = defaultPort;
            }

            return new InetSocketAddress(addr, port);
        } catch (Exception e) {
            return null;
        }
    }

    public static String bytesToMacAddress(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) {
            sb.append(String.format("%02X:", b));
        }

        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    public static byte[] macAddressToBytes(String mac) {
        String[] elements = mac.split(":");
        byte[] array = new byte[elements.length];

        for (int i = 0; i < elements.length; i += 1) {
            array[i] = Integer.decode("0x" + elements[i]).byteValue();
        }

        return array;
    }

    // Check if MAC address is unicast/multicast
    public static boolean isMulticastMAC(byte[] mac) {
        return (mac[0] & 1) != 0;
    }

    // Check if MAC address is local/universal
    public static boolean isUniversalMAC(byte[] mac) {
        return (mac[0] & 2) == 0;
    }

    public static boolean isValidMAC(byte[] mac) {
        // we ignore the first byte (dummy mac addresses have the "local" bit set - resulting in 0x02)
        return ((mac != null)
            && (mac.length == 6)
            && ((mac[1] != 0x0) && (mac[2] != 0x0) && (mac[3] != 0x0) && (mac[4] != 0x0) && (mac[5] != 0x0))
        );
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // check if a string is a MAC address (heuristic)
    public static boolean isMAC(String address) {
        if (address == null || address.length() != 17) {
            return false;
        }

        for (int i : new int[]{0, 1, 3, 4, 6, 7, 9, 10, 12, 13, 15, 16}) {
            if (!isHexChar(address.charAt(i))) {
                return false;
            }
        }

        for (int i : new int[]{2, 5, 8, 11, 14}) {
            if (address.charAt(i) != ':') {
                return false;
            }
        }

        return true;
    }

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-z0-9\\-.]+");

    // check if string is a domain (heuristic)
    public static boolean isDomain(String domain) {
        if (domain == null || domain.length() == 0) {
            return false;
        }

        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }

        if (domain.contains("..") || !domain.contains(".")) {
            return false;
        }

        if (domain.startsWith("-") || domain.endsWith("-")) {
            return false;
        }

        if (domain.contains(".-") || domain.contains("-.")) {
            return false;
        }

        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$");
    private static final Pattern IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$");

    // check if a string is an IP address (heuristic)
    public static boolean isIP(String address) {
        return IPV4_PATTERN.matcher(address).matches()
            || IPV6_STD_PATTERN.matcher(address).matches()
            || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches();
    }

    public static List<AddressEntry> collectAddresses() {
        ArrayList<AddressEntry> addressList = new ArrayList<>();
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                byte[] mac = nif.getHardwareAddress();

                if (!isValidMAC(mac)) {
                    log("Interface has invalid mac: " + nif.getName());
                    continue;
                }

                if (nif.isLoopback()) {
                    continue;
                }

                addressList.add(new AddressEntry(Utils.bytesToMacAddress(mac), nif.getName(), Utils.isMulticastMAC(mac)));

                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    addressList.add(new AddressEntry(addr.getHostAddress(), nif.getName(), addr.isMulticastAddress()));
                }
            }
        } catch (Exception ex) {
            // ignore
            log("error: " + ex.toString());
        }

        return addressList;
    }

    // list all IP/MAC addresses of running network interfaces - for debugging only
    public static void printOwnAddresses() {
        for (AddressEntry ae : collectAddresses()) {
            log("Address: " + ae.address + " (" + ae.device + (ae.multicast ? ", multicast" : "") + ")");
        }
    }

    // Check if the given MAC address is in the IPv6 address
    public static byte[] getEUI64MAC(Inet6Address addr6) {
        byte[] bytes = addr6.getAddress();
        if (bytes[11] != ((byte) 0xFF) || bytes[12] != ((byte) 0xFE)) {
            return null;
        }

        byte[] mac = new byte[6];
        mac[0] = (byte) (bytes[8] ^ 2);
        mac[1] = bytes[9];
        mac[2] = bytes[10];
        mac[3] = bytes[13];
        mac[4] = bytes[14];
        mac[5] = bytes[15];
        return mac;
    }

    /*
    * Replace the MAC address of an EUi64 scheme IPv6 address with another MAC address.
    * E.g.: ("fe80::aaaa:aaff:faa:aaa", "bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb"
    */
    private static Inet6Address createEUI64Address(Inet6Address addr6, byte[] mac) {
        // addr6 is expected to be a EUI64 address
        try {
            byte[] bytes = addr6.getAddress();

            bytes[8] = (byte) (mac[0] ^ 2);
            bytes[9] = mac[1];
            bytes[10] = mac[2];

            // already set, but doesn't harm
            bytes[11] = (byte) 0xFF;
            bytes[12] = (byte) 0xFE;

            bytes[13] = mac[3];
            bytes[14] = mac[4];
            bytes[15] = mac[5];

            return Inet6Address.getByAddress(null, bytes, addr6.getScopeId());
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /*
    * Iterate all device addresses, check if they conform to the EUI64 scheme.
    * If yes, replace the MAC address in it with the supplied one and return that address.
    * Also set the given port for those generated addresses.
    */
    public static List<InetSocketAddress> getAddressPermutations(String contact_mac, int port) {
        byte[] contact_mac_bytes = Utils.macAddressToBytes(contact_mac);
        ArrayList<InetSocketAddress> addrs = new ArrayList<InetSocketAddress>();
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (nif.isLoopback()) {
                    continue;
                }

                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }

                    if (addr instanceof Inet6Address) {
                        Inet6Address addr6 = (Inet6Address) addr;
                        byte[] extracted_mac = getEUI64MAC(addr6);
                        if (extracted_mac != null && Arrays.equals(extracted_mac, nif.getHardwareAddress())) {
                            // We found the interface MAC address in the IPv6 assigned to that interface in the EUI-64 scheme.
                            // Now assume that the contact has an address with the same scheme.
                            InetAddress new_addr = createEUI64Address(addr6, contact_mac_bytes);
                            if (new_addr != null) {
                                addrs.add(new InetSocketAddress(new_addr, port));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return addrs;
    }

    // EUI-64 based address to MAC address
    public static String getGeneralizedAddress(InetAddress address) {
        if (address instanceof Inet6Address) {
            // if the IPv6 address contains a MAC address, take that.
            byte[] mac = Utils.getEUI64MAC((Inet6Address) address);
            if (mac != null) {
                return Utils.bytesToMacAddress(mac);
            }
        }
        return address.getHostAddress();
    }

    // write file to external storage
    public static void writeExternalFile(String filepath, byte[] data) throws IOException {
        File file = new File(filepath);

        if (file.exists() && file.isFile()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete existing file: " + filepath);
            }
        }

        file.createNewFile();
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    // read file from external storage
    public static byte[] readExternalFile(String filepath) throws IOException {
        File file = new File(filepath);

        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist: " + filepath);
        }

        FileInputStream fis = new FileInputStream(file);

        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while ((nRead = fis.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        fis.close();
        return buffer.toByteArray();
    }

    private static void log(String s) {
        Log.d(Utils.class.getSimpleName(), s);
    }
}
