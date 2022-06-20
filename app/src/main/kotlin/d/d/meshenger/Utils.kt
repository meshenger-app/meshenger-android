package d.d.meshenger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.and


object Utils {

    //TODO (IODevBlue): This method is useless with methods annotated with @requiresPermisson
    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun requestCameraPermission(activity: Activity, request_code: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), request_code)
    }

    fun allGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun getApplicationVersion(context: Context): String {
        try {
            val info: PackageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)
            return info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    fun join(list: ArrayList<String>?): String? = list?.let{TextUtils.join(", ", it)}

    fun split(str: String): List<String?>? {
        val parts = str.split("\\s*,\\s*").toTypedArray()
        return Arrays.asList(*parts)
    }

    // check for a name that has no funny unicode characters to not let them look to much like other names
    fun isValidContactName(name: String?): Boolean {

        if (name == null || name.length == 0) {
            return false
        }
        if (name != name.trim { it <= ' ' }) {
            return false
        }

        // somewhat arbitrary limit to prevent
        // messing up the contact list
        return if (name.length > 28) {
            false
        } else true
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun byteArrayToHexString(bytes: ByteArray?): String? {
        if (bytes == null) {
            return ""
        }
        val hexChars = CharArray(bytes.size * 2)
        var j = 0

        while (j < bytes.size) {
            val v: Int = (bytes[j] and 0xFF.toByte()).toInt()
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            j += 1
        }
        return String(hexChars)
    }

    fun hexStringToByteArray(str: String?): ByteArray? {
        if (str == null) {
            return ByteArray(0)
        }
        val len = str.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((str[i].digitToIntOrNull(16) ?: -1 shl 4)
            + str[i + 1].digitToIntOrNull(16)!!).toByte()
            i += 2
        }
        return data
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
    fun bytesToMacAddress(mac: ByteArray): String? {
        val sb = StringBuilder()
        for (b in mac) {
            sb.append(String.format("%02X:", b))
        }
        if (sb.length > 0) {
            sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
    }

    fun macAddressToBytes(mac: String): ByteArray? {
        val elements = mac.split(":").toTypedArray()
        val array = ByteArray(elements.size)
        var i = 0
        while (i < elements.size) {
            array[i] = Integer.decode("0x" + elements[i]).toByte()
            i += 1
        }
        return array
    }

    // Check if MAC address is unicast/multicast
    fun isMulticastMAC(mac: ByteArray): Boolean =
        (mac[0] and 1).toInt() == 0


    // Check if MAC address is local/universal
    fun isUniversalMAC(mac: ByteArray): Boolean =
        (mac[0] and 2).toInt() == 0

    private fun isHexChar(c: Char): Boolean {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'
    }

    fun isMAC(mac: ByteArray?): Boolean {
        // we ignore the first byte (dummy mac addresses have the "local" bit set - resulting in 0x02)
        return (mac != null
                && mac.size == 6
                && mac[1].toInt() != 0x0
                && mac[2].toInt() != 0x0
                && mac[3].toInt() != 0x0
                && mac[4].toInt() != 0x0
                && mac[5].toInt() != 0x0
                )
    }

    // check if a string is a MAC address (heuristic)
    fun isMAC(address: String?): Boolean {
        if (address == null || address.length != 17) {
            return false
        }
        for (i in intArrayOf(0, 1, 3, 4, 6, 7, 9, 10, 12, 13, 15, 16)) {
            if (!isHexChar(address[i])) {
                return false
            }
        }
        for (i in intArrayOf(2, 5, 8, 11, 14)) {
            if (address[i] != ':') {
                return false
            }
        }
        return true
    }

    private val DOMAIN_PATTERN: Pattern = Pattern.compile("[a-z0-9\\-.]+")

    // check if string is a domain (heuristic)
    fun isDomain(domain: String?): Boolean {

        if (domain == null || domain.length == 0) {
            return false
        }
        if (domain.startsWith(".") || domain.endsWith(".")) {
            return false
        }
        if (domain.contains("..") || !domain.contains(".")) {
            return false
        }
        if (domain.startsWith("-") || domain.endsWith("-")) {
            return false
        }
        return if (domain.contains(".-") || domain.contains("-.")) {
            false
        } else DOMAIN_PATTERN.matcher(domain).matches()
    }

    private val IPV4_PATTERN: Pattern =
        Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
    private val IPV6_STD_PATTERN: Pattern =
        Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private val IPV6_HEX_COMPRESSED_PATTERN: Pattern =
        Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$")

    // check if a string is an IP address (heuristic)
    fun isIP(address: String?): Boolean {
        val charS = address as CharSequence
        return (IPV4_PATTERN.matcher(charS).matches()
                || IPV6_STD_PATTERN.matcher(charS).matches()
                || IPV6_HEX_COMPRESSED_PATTERN.matcher(charS).matches())
    }


    fun getExternalFileSize(ctx: Context, uri: Uri): Long {
        val cursor: Cursor = ctx.getContentResolver().query(uri, null, null, null, null)!!
        var size: Long
        cursor.let{
            it.moveToFirst()
            size = it.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
            cursor.close()
        }
        return size
    }

    @Throws(IOException::class)
    fun readExternalFile(ctx: Context, uri: Uri): ByteArray? {
        val size = getExternalFileSize(ctx, uri).toInt()
        val inpstr: InputStream? = ctx.getContentResolver().openInputStream(uri)
        val buffer = ByteArrayOutputStream()
        var nRead: Int
        val data = ByteArray(size)
        while (inpstr?.read(data, 0, data.size).also { nRead = it!! } != -1) {
            buffer.write(data, 0, nRead)
        }
        inpstr?.close()
        return data
    }

    @Throws(IOException::class)
    fun writeExternalFile(ctx: Context, uri: Uri, data: ByteArray?) {
        val fos: OutputStream = ctx.getContentResolver().openOutputStream(uri)!!
        fos.write(data)
        fos.close()
    }

    // write file to external storage
    @Throws(IOException::class)
    fun writeInternalFile(filepath: String, data: ByteArray?) {
        val file = File(filepath)
        if (file.exists() && file.isFile()) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filepath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(data)
        fos.close()
    }

    // read file from external storage
    @Throws(IOException::class)
    fun readInternalFile(filepath: String): ByteArray? {
        val file = File(filepath)
        if (!file.exists() || !file.isFile()) {
            throw IOException("File does not exist: $filepath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val data = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }

    fun getUnknownCallerName(context: Context, clientPublicKeyOut: ByteArray): String? {
        val sb = StringBuilder()
        sb.append(context.getResources().getString(R.string.unknown_caller))
        sb.append(" #")
        var i = 0
        while (i < clientPublicKeyOut.size && i < 4) {
            sb.append(String.format("%02X", clientPublicKeyOut[i]))
            i++
        }
        return sb.toString()
    }

}