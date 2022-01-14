package org.rivchain.cuplink

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

class Utils {

    companion object {

        private val NAME_PATTERN = Pattern.compile("[\\w _-]+")
        private val hexArray = "0123456789abcdef".toCharArray()
        private val DOMAIN_PATTERN = Pattern.compile("[a-z0-9\\-.]+")
        private val IPV4_PATTERN =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
        private val IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
        private val IPV6_HEX_COMPRESSED_PATTERN =
            Pattern.compile("^((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)$")

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
            val parts = str.split("\\s*,\\s*".toRegex()).toTypedArray()
            return Arrays.asList(*parts)
        }

        // check for a name that has no funny unicode characters to not let them look to much like other names
        fun isValidName(name: String?): Boolean {
            if (name == null || name.length == 0) {
                return false
            }
            return if (name != name.trim { it <= ' ' }) {
                false
            } else NAME_PATTERN.matcher(name).matches()
        }

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

        fun hexStringToByteArray(str: String?): ByteArray {
            if (str == null) {
                return ByteArray(0)
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

        fun parseInetSocketAddress(addr: String?, defaultPort: Int): InetSocketAddress? {
            return if (addr == null || addr.length == 0) {
                null
            } else try {
                if (isIPv6(addr)) {
                    InetSocketAddress(InetAddress.getByName("[$addr]"), defaultPort)
                } else {
                    InetSocketAddress(InetAddress.getByName(addr), defaultPort)
                }
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
            val elements = mac.split(":".toRegex()).toTypedArray()
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
                    && mac[1].toInt() != 0x0
                    && mac[2].toInt() != 0x0
                    && mac[3].toInt() != 0x0
                    && mac[4].toInt() != 0x0
                    && mac[5].toInt() != 0x0)
        }

        private fun isHexChar(c: Char): Boolean {
            return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'
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

        // check if a string is an IP address (heuristic)
        fun isIP(address: String?): Boolean {
            return (IPV4_PATTERN.matcher(address).matches()
                    || IPV6_STD_PATTERN.matcher(address).matches()
                    || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches())
        }

        fun isIPv6(address: String?): Boolean {
            return (IPV6_STD_PATTERN.matcher(address).matches()
                    || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches())
        }

        fun collectAddresses(): List<AddressEntry> {
            val addressList = ArrayList<AddressEntry>()
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
                        var fullAddress = addr.hostAddress
                        if (fullAddress.indexOf('%') > 0) {
                            var subaddress = fullAddress.substring(0, fullAddress.indexOf('%'))
                            if (isIPv6(subaddress)) {
                                subaddress = "" + subaddress + ""
                            }
                            addressList.add(
                                AddressEntry(
                                    subaddress,
                                    nif.name,
                                    addr.isMulticastAddress
                                )
                            )
                        } else {
                            if (isIPv6(fullAddress)) {
                                fullAddress = "" + fullAddress + ""
                            }
                            addressList.add(
                                AddressEntry(
                                    fullAddress,
                                    nif.name,
                                    addr.isMulticastAddress
                                )
                            )
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
            mac[0] = (bytes[8].toInt() xor 2) as Byte
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
                bytes[8] = (mac[0].toInt() xor 2) as Byte
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
                            val addr6 = addr
                            val extracted_mac = getEUI64MAC(addr6)
                            if (extracted_mac != null && Arrays.equals(
                                    extracted_mac,
                                    nif.hardwareAddress
                                )
                            ) {
                                // We found the interface MAC address in the IPv6 assigned to that interface in the EUI-64 scheme.
                                // Now assume that the contact has an address with the same scheme.
                                val new_addr: InetAddress? =
                                    createEUI64Address(addr6, contact_mac_bytes)
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
        fun getGeneralizedAddress(address: InetAddress): String {
            if (address is Inet6Address) {
                // if the IPv6 address contains a MAC address, take that.
                val mac = getEUI64MAC(address)
                if (mac != null) {
                    return bytesToMacAddress(mac)
                }
            }
            return address.hostAddress
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
}