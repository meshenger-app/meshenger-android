package d.d.meshenger

import java.io.*
import java.net.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.xor

internal object AddressUtils
{
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
            InetSocketAddress.createUnresolved(addr, port)
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToMacAddress(mac: ByteArray): String {
        val sb = StringBuilder()
        for (b in mac) {
            sb.append(String.format("%02X:", b))
        }
        if (sb.length > 0) {
            sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
    }

    private fun macAddressToBytes(mac: String): ByteArray {
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
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    private val IPV4_PATTERN =
        Pattern.compile("^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$")
    private val IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private val IPV6_HEX_COMPRESSED_PATTERN =
        Pattern.compile("^((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)::((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)$")
    private val DOMAIN_PATTERN = Pattern.compile("^([\\w]{2,63}[.]){1,6}[\\w]{2,63}$")
    private val MAC_PATTERN = Pattern.compile("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    // check if a string is an IP address (heuristic)
    fun isIPAddress(address: String): Boolean {
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
            Log.d(this, "error: $ex")
        }
        return addressList
    }

    // list all IP/MAC addresses of running network interfaces - for debugging only
    fun printOwnAddresses() {
        for (ae in collectAddresses()) {
            Log.d(this, "Address: ${ae.address} (${ae.device}" + (if (ae.multicast) ", multicast" else "") + ")")
        }
    }

    // Check if the given MAC address is in the IPv6 address
    private fun getEUI64MAC(addr6: Inet6Address): ByteArray? {
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
        // addr6 is expected to be an EUI64 address
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
    * Duplicate own addresses that contain a MAC address with the given MAC address.
    * This also creates fe80::/10 addresses.
    */
    fun getOwnAddressesWithMAC(contact_mac: String, port: Int): List<InetSocketAddress> {
        val contact_mac_bytes = macAddressToBytes(contact_mac)
        val addresses = ArrayList<InetSocketAddress>()

        try {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isLoopback) {
                    continue
                }

                if (nif.name.startsWith("dummy")) {
                    continue
                }

                for (ia in nif.interfaceAddresses) {
                    val address = ia.address
                    if (address.isLoopbackAddress) {
                        continue
                    }
                    if (address is Inet6Address) {
                        val extracted_mac = getEUI64MAC(address)
                        if (extracted_mac != null) {
                            // We found the interface MAC address in the IPv6 address (EUI-64).
                            // Now assume that the contact has an address with the same scheme.
                            val new_addr = createEUI64Address(address, contact_mac_bytes)
                            if (new_addr != null) {
                                addresses.add(InetSocketAddress(new_addr.hostAddress, port))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return addresses
    }

    // EUI-64 based address to MAC address.
    fun getGeneralizedAddress(address: InetAddress?): String? {
        if (address == null) return null
        val mac = extractMacAddress(address)
        if (mac != null) return bytesToMacAddress(mac)
        return address.hostAddress ?: address.hostName
    }

    private fun extractMacAddress(address: InetAddress): ByteArray? {
        if (address is Inet6Address) {
            return getEUI64MAC(address)
        }
        return null
    }

    // experimental feature
    private fun getAddressesFromNeighborTable(
        lookup_mac: String,
        port: Int
    ): List<InetSocketAddress> {
        val addresses = mutableListOf<InetSocketAddress>()
        try {
            // get IPv4 and IPv6 entries
            val pc = Runtime.getRuntime().exec("ip n l")
            val rd = BufferedReader(
                InputStreamReader(pc.inputStream, "UTF-8")
            )
            var line : String
            while (rd.readLine().also { line = it } != null) {
                val tokens = line.split("\\s+").toTypedArray()
                // IPv4
                if (tokens.size == 6) {
                    val address = tokens[0]
                    val device = tokens[2]
                    val mac = tokens[4]
                    val state = tokens[5]
                    if (lookup_mac.equals(
                            mac,
                            ignoreCase = true
                        ) && isIPAddress(address) && !state.equals("failed", ignoreCase = true)
                    ) {
                        if (address.startsWith("fe80:") || address.startsWith("169.254.")) {
                            addresses.add(InetSocketAddress("$address%$device", port))
                        } else {
                            addresses.add(InetSocketAddress(address, port))
                        }
                    }
                }

                // IPv6
                if (tokens.size == 7) {
                    val address = tokens[0]
                    val device = tokens[2]
                    val mac = tokens[4]
                    val state = tokens[6]
                    if (mac.equals(
                            lookup_mac,
                            ignoreCase = true
                        ) && isIPAddress(address) && !state.equals("failed", ignoreCase = true)
                    ) {
                        if (address.startsWith("fe80:") || address.startsWith("169.254.")) {
                            addresses.add(InetSocketAddress("$address%$device", port))
                        } else {
                            addresses.add(InetSocketAddress(address, port))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(this, e.toString())
        }

        return addresses
    }
}