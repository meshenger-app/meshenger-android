package d.d.meshenger

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import java.io.*
import java.net.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.xor

internal object AddressUtils
{
    fun getAllSocketAddresses(initial_addresses: List<String>, last_working_address: InetSocketAddress?, port: Int): List<InetSocketAddress> {
        val addresses = mutableListOf<InetSocketAddress>()

        if (last_working_address != null) {
            addresses.add(last_working_address)
        }

        for (address in initial_addresses) {
            if (isMACAddress(address)) {
                val mac = macAddressToBytes(address)
                if (mac != null && mac.size == 6) {
                    val fe80 = formatFE80(mac)
                    addresses.add(InetSocketAddress.createUnresolved(fe80, port))
                    addresses.addAll(mapMACtoPrefixes(mac, port))
                }
            } else {
                val socketAddress = stringToInetSocketAddress(address, port)
                if (socketAddress != null) {
                    addresses.add(socketAddress)
                }
            }
        }

        return addresses
    }

    private fun ignoreDeviceByName(device: String): Boolean {
        return device.contains("rmnet") || device.startsWith("dummy")
    }

    private fun formatFE80(mac: ByteArray): String {
        return String.format("fe80::%02x:%02x:%02xff:fe%02x:%02x:%02x",
            (mac[0] xor 2), mac[1], mac[2], mac[3], mac[4], mac[5])
    }

    private fun macAddressToBytes(macAddress: String): ByteArray? {
        if (isMACAddress(macAddress)) {
            val elements = macAddress.split(":").toTypedArray()
            val array = ByteArray(elements.size)
            var i = 0
            while (i < elements.size) {
                array[i] = Integer.decode("0x" + elements[i]).toByte()
                i += 1
            }
            return array
        } else {
            return null
        }
    }

    // Check if MAC address is unicast/multicast
    fun isMulticastMAC(mac: ByteArray): Boolean {
        return mac[0].toInt() and 1 != 0
    }

    // Check if MAC address is local/universal
    fun isUniversalMAC(mac: ByteArray): Boolean {
        return mac[0].toInt() and 2 == 0
    }

    private fun isValidMAC(mac: ByteArray?): Boolean {
        // we ignore the first byte (dummy mac addresses have the "local" bit set - resulting in 0x02)
        return (mac != null
                && mac.size == 6
                && mac[1].toInt() != 0x0
                && mac[2].toInt() != 0x0
                && mac[3].toInt() != 0x0
                && mac[4].toInt() != 0x0
                && mac[5].toInt() != 0x0)
    }

    private val DOMAIN_PATTERN = Pattern.compile("^([a-z0-9\\-_]{1,63}[.]){1,40}[a-z]{2,}$")
    private val MAC_PATTERN = Pattern.compile("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    private val DEVICE_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,8}$")
    private val IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private val IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile("^((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)::((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)$")

    fun isIPAddress(address: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return InetAddresses.isNumericAddress(address)
        } else {
            // lots of work to support for older SDKs
            val pc = address.indexOf('%')

            val addressPart = if (pc != -1) {
                address.substring(0, pc)
            } else {
                address
            }

            val devicePart = if (pc != -1) {
                address.substring(pc + 1)
            } else {
                null
            }

            if (devicePart != null && !DEVICE_PATTERN.matcher(devicePart).matches()) {
                return false
            }

            @Suppress("DEPRECATION")
            return IPV6_STD_PATTERN.matcher(addressPart).matches()
                 || IPV6_HEX_COMPRESSED_PATTERN.matcher(addressPart).matches()
                 || Patterns.IP_ADDRESS.matcher(addressPart).matches()
        }
    }

    fun isMACAddress(address: String): Boolean {
        return MAC_PATTERN.matcher(address).matches()
    }

    fun isDomain(address: String): Boolean {
        return DOMAIN_PATTERN.matcher(address).matches()
    }

    fun isAddress(address: String): Boolean {
        return isIPAddress(address)
            || isMACAddress(address)
            || isDomain(address)
    }

    fun stringToInetSocketAddress(addr: String?, default_port: Int): InetSocketAddress? {
        if (addr == null || addr.isEmpty()) {
            return null
        } else if (addr.startsWith("[")) {
            val end = addr.lastIndexOf("]:")
            if (end > 0) {
                // [<address>]:<port>
                val addr_part = addr.substring(1, end)
                val port_part = addr.substring(end + 2)
                val port = port_part.toUShortOrNull()
                if (port != null && isAddress(addr_part)) {
                    return InetSocketAddress.createUnresolved(addr_part, port.toInt())
                }
            }
        } else {
            if (addr.count { it == ':' } == 1) {
                //<hostname>:<port>
                //<ipv4-address>:<port>
                val end = addr.indexOf(":")
                val addr_part = addr.substring(0, end)
                val port_part = addr.substring(end + 1)
                val port = port_part.toUShortOrNull()
                if (port != null && isAddress(addr_part)) {
                    return InetSocketAddress.createUnresolved(addr_part, port.toInt())
                }
            } else if (isAddress(addr)) {
                //<hostname>
                //<ipv4-address>
                return InetSocketAddress.createUnresolved(addr, default_port.toInt())
            }
        }
        return null
    }

    fun inetSocketAddressToString(saddr: InetSocketAddress?): String? {
        if (saddr == null) {
            return null
        }
        val addr = saddr.address
        val port = saddr.port
        if (port !in 0..65535) {
            return null
        } else if (addr == null) {
            val host = saddr.hostString.trimStart('/')
            if (AddressUtils.isAddress(host)) {
                return "$host:$port"
            } else {
                return null
            }
        } else if (addr is Inet6Address) {
            val str = addr.toString().trimStart('/')
            return "[$str]:$port"
        } else {
            val str = addr.toString().trimStart('/')
            return "$str:$port"
        }
    }

    fun collectAddresses(): List<AddressEntry> {
        val addressList = ArrayList<AddressEntry>()
        try {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isLoopback) {
                    continue
                }

                if (ignoreDeviceByName(nif.name)) {
                    continue
                }

                val hardwareMAC = nif.hardwareAddress
                if (isValidMAC(hardwareMAC)) {
                    val macAddress = formatFE80(hardwareMAC)
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
                    val softwareMAC = extractMAC(ia.address)
                    if (softwareMAC != null) {
                        val macAddress = formatFE80(softwareMAC)
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

    fun isEUI64Address(address: String): Boolean {
        return address.startsWith("fe80::")
            && address.length >= 25
            && address.substring(13, 18) == "ff:fe"
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
    * This ignores Link Local addresses like fe80::/10.
    */
    private fun mapMACtoPrefixes(macAddress: ByteArray, port: Int): List<InetSocketAddress> {
        val addresses = ArrayList<InetSocketAddress>()

        try {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isLoopback) {
                    continue
                }

                if (ignoreDeviceByName(nif.name)) {
                    continue
                }

                Log.d(this, "mapMACtoPrefixes: ${nif.name}")

                for (ia in nif.interfaceAddresses) {
                    val address = ia.address
                    if (address.isLoopbackAddress) {
                        continue
                    }
                    if (address is Inet6Address && !address.isLinkLocalAddress()) {
                        val extracted_mac = getEUI64MAC(address)
                        if (extracted_mac != null) {
                            // We found the interface MAC address in the IPv6 address (EUI-64).
                            // Now assume that the contact has an address with the same scheme.
                            val new_addr = createEUI64Address(address, macAddress)
                            if (new_addr != null) {
                                addresses.add(InetSocketAddress.createUnresolved(new_addr.hostAddress, port))
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

    fun extractMAC(address: InetAddress?): ByteArray? {
        if (address != null && address is Inet6Address) {
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