package d.d.meshenger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;


public class Utils {

    public static boolean hasPermission(Activity activity, String permission) {
        return (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED);
    }

    public static boolean hasCameraPermission(Activity activity) {
        return (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    public static void requestCameraPermission(Activity activity, int request_code) {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.CAMERA}, request_code);
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

    // check for a name that has no funny unicode characters to not let them look to much like other names
    public static boolean isValidContactName(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }

        if (!name.equals(name.trim())) {
            return false;
        }

        // somewhat arbitrary limit to prevent
        // messing up the contact list
        if (name.length() > 28) {
            return false;
        }

        return true;
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

/*
    // parse IPv6 address like [::]:12345
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
*/

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

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public static boolean isMAC(byte[] mac) {
        // we ignore the first byte (dummy mac addresses have the "local" bit set - resulting in 0x02)
        return ((mac != null)
            && (mac.length == 6)
            && ((mac[1] != 0x0) && (mac[2] != 0x0) && (mac[3] != 0x0) && (mac[4] != 0x0) && (mac[5] != 0x0))
        );
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


    public static long getExternalFileSize(Context ctx, Uri uri) {
        Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        long size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
        cursor.close();
        return size;
    }

    public static byte[] readExternalFile(Context ctx, Uri uri) throws IOException {
        int size = (int) getExternalFileSize(ctx, uri);
        InputStream is = ctx.getContentResolver().openInputStream(uri);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[size];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        is.close();
        return data;
    }

    public static void writeExternalFile(Context ctx, Uri uri, byte[] data) throws IOException {
        OutputStream fos = ctx.getContentResolver().openOutputStream(uri);
        fos.write(data);
        fos.close();
    }

    // write file to external storage
    public static void writeInternalFile(String filepath, byte[] data) throws IOException {
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
    public static byte[] readInternalFile(String filepath) throws IOException {
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

    public static String getUnknownCallerName(Context context, byte[] clientPublicKeyOut) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getResources().getString(R.string.unknown_caller));
        sb.append(" #");

        for (int i = 0; i < clientPublicKeyOut.length && i < 4; i++) {
            sb.append(String.format("%02X", clientPublicKeyOut[i]));
        }

        return sb.toString();
    }
}
