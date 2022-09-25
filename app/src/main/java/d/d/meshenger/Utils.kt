package d.d.meshenger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.net.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.xor

internal object Utils {
    fun hasReadPermission(activity: Activity?): Boolean {
        return ContextCompat.checkSelfPermission(
            activity!!, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWritePermission(activity: Activity?): Boolean {
        return ContextCompat.checkSelfPermission(
            activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(activity: Activity?): Boolean {
        return ContextCompat.checkSelfPermission(
            activity!!, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission(activity: Activity?, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.CAMERA
            ), request_code
        )
    }

    fun requestReadPermission(activity: Activity?, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), request_code
        )
    }

    fun requestWritePermission(activity: Activity?, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), request_code
        )
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
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return ""
    }

    fun join(list: List<String?>?): String {
        return TextUtils.join(", ", list!!)
    }

    fun split(str: String): List<String> {
        val parts = str.split("\\s*,\\s*").toTypedArray()
        return Arrays.asList(*parts)
    }

    private val NAME_PATTERN = Pattern.compile("[\\w _-]+")

    // check for a name that has no funny unicode characters to not let them look to much like other names
    fun isValidName(name: String?): Boolean {
        if (name == null || name.length == 0) {
            return false
        }
        return if (name != name.trim { it <= ' ' }) {
            false
        } else NAME_PATTERN.matcher(name).matches()
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun byteArrayToHexString(bytes: ByteArray?): String {
        if (bytes == null) {
            return ""
        }
        val hexChars = CharArray(bytes.size * 2)
        var j = 0
        while (j < bytes.size) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            j += 1
        }
        return String(hexChars)
    }

    fun hexStringToByteArray(str: String?): ByteArray? {
        if (str == null) {
            return null
        }
        val len = str.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(str[i], 16) shl 4)
                    + Character.digit(str[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun parseInetSocketAddress(address: String?, defaultPort: Int): InetSocketAddress? {
        if (address == null || address.isEmpty()) {
            return null
        }
        var addr = address
        val firstColon = addr.indexOf(':')
        val lastColon = addr.lastIndexOf(':')
        var port = -1
        return try {
            // parse port suffix
            if (firstColon > 0 && lastColon > 0) {
                if (addr[lastColon - 1] == ']' || firstColon == lastColon) {
                    port = addr.substring(lastColon + 1).toInt()
                    addr = addr.substring(0, lastColon)
                }
            }
            if (port < 0) {
                port = defaultPort
            }
            InetSocketAddress(addr, port)
        } catch (e: Exception) {
            null
        }
    }

    fun bytesToMacAddress(mac: ByteArray): String {
        val sb = StringBuilder()
        for (b in mac) {
            sb.append(String.format("%02X:", b))
        }
        if (sb.length > 0) {
            sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
    }

    fun macAddressToBytes(mac: String): ByteArray {
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
    fun isMulticastMAC(mac: ByteArray): Boolean {
        return mac[0].toInt() and 1 != 0
    }

    // Check if MAC address is local/universal
    fun isUniversalMAC(mac: ByteArray): Boolean {
        return mac[0].toInt() and 2 == 0
    }

    fun isValidMAC(mac: ByteArray?): Boolean {
        // we ignore the first byte (dummy mac addresses have the "local" bit set - resulting in 0x02)
        return (mac != null
                && mac.size == 6
                && mac[1].toInt() != 0x0 && mac[2].toInt() != 0x0 && mac[3].toInt() != 0x0 && mac[4].toInt() != 0x0 && mac[5].toInt() != 0x0)
    }

    private fun isHexChar(c: Char): Boolean {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'
    }

    private val IPV4_PATTERN =
        Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
    private val IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private val IPV6_HEX_COMPRESSED_PATTERN =
        Pattern.compile("^((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)::((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)$")
    private val DOMAIN_PATTERN = Pattern.compile("^([\\w]{2,63}[.]){1,6}[\\w]{2,63}$")
    private val MAC_PATTERN = Pattern.compile("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    // check if a string is an IP address (heuristic)
    fun isIPAddress(address: String?): Boolean {
        return (IPV4_PATTERN.matcher(address).matches()
                || IPV6_STD_PATTERN.matcher(address).matches()
                || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches())
    }

    fun isMACAddress(address: String): Boolean {
        return MAC_PATTERN.matcher(address).matches()
    }

    fun isDomain(address: String): Boolean {
        return DOMAIN_PATTERN.matcher(address).matches()
    }

    fun isAddress(address: String): Boolean {
        return isMACAddress(address) || isIPAddress(address) || isDomain(address)
    }

    fun collectAddresses(): List<AddressEntry> {
        val addressList = ArrayList<AddressEntry>()
        try {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isLoopback) {
                    continue
                }

                if (nif.name.startsWith("dummy")) {
                    continue
                }

                val hardwareMAC = nif.hardwareAddress
                if (isValidMAC(hardwareMAC)) {
                    val macAddress = bytesToMacAddress(hardwareMAC)
                    if (addressList.find { it.address == macAddress } == null) {
                        addressList.add(AddressEntry(
                            macAddress,
                            nif.name,
                            isMulticastMAC(hardwareMAC)
                        ))
                    }
                }

                for (ia in nif.interfaceAddresses) {
                    if (ia.address.isLoopbackAddress) {
                        continue
                    }

                    val hostAddress = ia.address.hostAddress
                    if (hostAddress != null && addressList.find { it.address == hostAddress } == null) {
                        addressList.add(AddressEntry(
                            hostAddress,
                            nif.name,
                            ia.address.isMulticastAddress
                        ))
                    }

                    // extract MAC address from fe80:: address if possible
                    val softwareMAC = extractMacAddress(ia.address)
                    if (softwareMAC != null) {
                        val macAddress = bytesToMacAddress(softwareMAC)
                        if (addressList.find { it.address == macAddress } == null) {
                            addressList.add(AddressEntry(
                                macAddress,
                                nif.name,
                                isMulticastMAC(softwareMAC)
                            ))
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
            log("error: $ex")
        }
        return addressList
    }

    // list all IP/MAC addresses of running network interfaces - for debugging only
    fun printOwnAddresses() {
        for (ae in collectAddresses()) {
            log("Address: " + ae.address + " (" + ae.device + (if (ae.multicast) ", multicast" else "") + ")")
        }
    }

    // Check if the given MAC address is in the IPv6 address
    fun getEUI64MAC(addr6: Inet6Address): ByteArray? {
        val bytes = addr6.address
        if (bytes[11] != 0xFF.toByte() || bytes[12] != 0xFE.toByte()) {
            return null
        }
        val mac = ByteArray(6)
        mac[0] = (bytes[8] xor 2)
        mac[1] = bytes[9]
        mac[2] = bytes[10]
        mac[3] = bytes[13]
        mac[4] = bytes[14]
        mac[5] = bytes[15]
        return mac
    }

    /*
    * Replace the MAC address of an EUi64 scheme IPv6 address with another MAC address.
    * E.g.: ("fe80::aaaa:aaff:faa:aaa", "bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb"
    */
    private fun createEUI64Address(addr6: Inet6Address, mac: ByteArray): Inet6Address? {
        // addr6 is expected to be a EUI64 address
        return try {
            val bytes = addr6.address
            bytes[8] = (mac[0] xor 2)
            bytes[9] = mac[1]
            bytes[10] = mac[2]
            // already set, but doesn't harm
            bytes[11] = 0xFF.toByte()
            bytes[12] = 0xFE.toByte()
            bytes[13] = mac[3]
            bytes[14] = mac[4]
            bytes[15] = mac[5]
            Inet6Address.getByAddress(null, bytes, addr6.scopeId)
        } catch (e: UnknownHostException) {
            null
        }
    }

    /*
    * Iterate all device addresses, check if they conform to the EUI64 scheme.
    * If yes, replace the MAC address in it with the supplied one and return that address.
    * Also set the given port for those generated addresses.
    */
    fun getAddressPermutations(contact_mac: String, port: Int): List<InetSocketAddress> {
        val contact_mac_bytes = macAddressToBytes(contact_mac)
        val addrs = ArrayList<InetSocketAddress>()
        try {
            val all: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (nif.isLoopback) {
                    continue
                }
                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address
                    if (addr.isLoopbackAddress) {
                        continue
                    }
                    if (addr is Inet6Address) {
                        val extracted_mac = getEUI64MAC(addr)
                        if (extracted_mac != null && Arrays.equals(
                                extracted_mac,
                                nif.hardwareAddress
                            )
                        ) {
                            // We found the interface MAC address in the IPv6 address (EUI-64).
                            // Now assume that the contact has an address with the same scheme.
                            val new_addr: InetAddress? =
                                createEUI64Address(addr, contact_mac_bytes)
                            if (new_addr != null) {
                                addrs.add(InetSocketAddress(new_addr, port))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return addrs
    }

    // EUI-64 based address to MAC address
    fun getGeneralizedAddress(address: InetAddress): String? {
        val mac = extractMacAddress(address)
        if (mac != null) return bytesToMacAddress(mac)
        return address.hostAddress
    }

    private fun extractMacAddress(address: InetAddress): ByteArray? {
        if (address is Inet6Address) {
            return getEUI64MAC(address)
        }
        return null
    }

    // write file to external storage
    @Throws(IOException::class)
    fun writeExternalFile(filepath: String, data: ByteArray?) {
        val file = File(filepath)
        if (file.exists() && file.isFile) {
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
    fun readExternalFile(filepath: String): ByteArray {
        val file = File(filepath)
        if (!file.exists() || !file.isFile) {
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

    private fun log(s: String) {
        Log.d(Utils::class.java.simpleName, s)
    }
}