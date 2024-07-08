package d.d.meshenger

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import java.io.*
import java.net.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.and
import kotlin.experimental.xor

internal object AddressUtils
{
    fun isLinkLocalAddress(address: String): Boolean {
        if (isIPAddress(address)) {
            // IPv4
            if (address.startsWith("169.254.")) {
                return true
            }

            // IPv6 (check for fe80::/10)
            if (address.startsWith("fe") && address.length >= 6) {
                try {
                    val idx = address.indexOf(":")
                    if (idx != -1) {
                        val secondByte = address.substring(2, idx).toLong(radix = 16)
                        if ((secondByte.toByte().and(0xC0.toByte()) == 0x80.toByte())) {
                            return true
                        }
                    }
                } catch (nfe: NumberFormatException) {
                    // ignore
                }
            }
        }

        return false
    }

    fun getAllSocketAddresses(contact: Contact, useNeighborTable: Boolean = false): List<InetSocketAddress> {
        val port = MainService.serverPort
        val addresses = mutableListOf<InetSocketAddress>()
        val macs = mutableSetOf<String>()
        val extractFE80MAC = true

        Utils.checkIsNotOnMainThread()

        val lastWorkingAddress = contact.lastWorkingAddress
        if (lastWorkingAddress != null) {
            addresses.add(lastWorkingAddress)
        }

        val ownInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        for (address in contact.addresses) {
            val socketAddress = stringToInetSocketAddress(address, port) ?: continue

            if (isLinkLocalAddress(socketAddress.hostString)) {
                for (interfaceName in collectInterfaceNames(ownInterfaces)) {
                    addresses.add(InetSocketAddress("${socketAddress.hostString}%${interfaceName}", socketAddress.port))
                }
            } else if (socketAddress.isUnresolved) {
                // resolve domain/hostname
                try {
                    for (resolved in InetAddress.getAllByName(socketAddress.hostString)) {
                        addresses.add(
                            InetSocketAddress(resolved, socketAddress.port)
                        )
                    }
                } catch (e: UnknownHostException) {
                    // ignore
                    continue
                }
            } else {
                addresses.add(socketAddress)
            }

            // get MAC address from IPv6 EUI64 address
            if (extractFE80MAC) {
                val inetAddress = parseInetAddress(address)
                val macAddress = extractMAC(inetAddress)
                if (macAddress != null) {
                    addresses.addAll(mapMACtoPrefixes(ownInterfaces, macAddress, port))
                    if (useNeighborTable) {
                        macs.add(formatMAC(macAddress))
                    }
                }
            }
        }

        if (useNeighborTable) {
            addresses.addAll(
                getAddressesFromNeighborTable(macs.toList(), port)
            )
        }

        return addresses.distinct()
    }

    private fun ignoreDeviceByName(device: String): Boolean {
        return device.startsWith("dummy")
    }

    fun formatMAC(mac: ByteArray): String {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5])
    }

    private fun macAddressToBytes(macAddress: String): ByteArray? {
        if (isMACAddress(macAddress)) {
            val bytes = macAddress
                .split(":")
                .map { it.toInt(16).toByte() }
                .toByteArray()
            if (isValidMAC(bytes)) {
                return bytes
            }
        }
        return null
    }

    private fun parseInetAddress(address: String): InetAddress? {
        if (isIPAddress(address)) {
            val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                InetAddresses.parseNumericAddress(address)
            } else {
                InetAddress.getByName(address)
            }
            return inetAddress
        }

        return null
    }

    // remove interface from link local address
    fun stripInterface(address: String): String {
        val pc = address.indexOf('%')
        return if (pc == -1) {
            address
        } else {
            address.substring(0, pc)
        }
    }

    // coarse address type distinction
    enum class AddressType {
        GLOBAL_IP, GLOBAL_MAC, LOCAL_IP, LOCAL_MAC, MULTICAST_MAC, MULTICAST_IP, DOMAIN
    }

    fun getAddressType(address: String): AddressType {
        val macBytes = macAddressToBytes(address)
        if (macBytes != null) {
            if ((macBytes[0].toInt() and 1) != 0) {
                return AddressType.MULTICAST_MAC
            } else if ((macBytes[0].toInt() and 2) == 0) {
                // globally administered MAC address
                return AddressType.GLOBAL_MAC
            } else {
                return AddressType.LOCAL_MAC
            }
        }

        val ipAddress = parseInetAddress(address)
        if (ipAddress != null) {
            if (ipAddress.isMulticastAddress) {
                return AddressType.MULTICAST_IP
            } else if ((ipAddress.address[1].toInt() and 15) == 0x0E) {
                // global IP address
                return AddressType.GLOBAL_IP
            } else {
                return AddressType.LOCAL_IP
            }
        }

        return AddressType.DOMAIN
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

    private val DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-._]{1,255}$")
    private val MAC_PATTERN = Pattern.compile("^[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$")

    private val DEVICE_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,8}$")
    private val IPV6_STD_PATTERN = Pattern.compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private val IPV6_HEX_COMPRESSED_PATTERN = Pattern.compile("^((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)::((?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?)$")

    fun isIPAddress(address: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return InetAddresses.isNumericAddress(address)
        } else {
            // lots of work to support older SDKs
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
            && !address.contains("..")
    }

    fun stringToInetSocketAddress(address: String?, defaultPort: Int): InetSocketAddress? {
        if (address.isNullOrEmpty()) {
            return null
        } else if (address.startsWith("[")) {
            val end = address.lastIndexOf("]:")
            if (end > 0) {
                // [<address>]:<port>
                val addressPart = address.substring(1, end)
                val portPart = address.substring(end + 2)
                val port = portPart.toUShortOrNull()?.toInt()
                if (port != null && isIPAddress(addressPart)) {
                    return InetSocketAddress(addressPart, port)
                }
            }
        } else {
            if (address.count { it == ':' } == 1) {
                //<hostname>:<port>
                //<ipv4-address>:<port>
                val end = address.indexOf(":")
                val addressPart = address.substring(0, end)
                val portPart = address.substring(end + 1)
                val port = portPart.toUShortOrNull()?.toInt()
                if (port != null) {
                    return if (isIPAddress(addressPart)) {
                        InetSocketAddress(addressPart, port)
                    } else {
                        InetSocketAddress.createUnresolved(addressPart, port)
                    }
                }
            } else if (isIPAddress(address)) {
                //<ipv4-address>
                return InetSocketAddress(address, defaultPort)
            } else if (isDomain(address)) {
                //<hostname>
                return InetSocketAddress.createUnresolved(address, defaultPort)
            }
        }
        return null
    }

    fun inetSocketAddressToString(socketAddress: InetSocketAddress?): String? {
        if (socketAddress == null) {
            return null
        }
        val address = socketAddress.address
        val port = socketAddress.port
        if (port !in 0..65535) {
            return null
        } else if (address == null) {
            val host = socketAddress.hostString.trimStart('/')
            if (isIPAddress(host) || isDomain(host)) {
                return "$host:$port"
            } else {
                return null
            }
        } else if (address is Inet6Address) {
            val str = address.toString().trimStart('/')
            return "[$str]:$port"
        } else {
            val str = address.toString().trimStart('/')
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

                for (ia in nif.interfaceAddresses) {
                    if (ia.address.isLoopbackAddress) {
                        continue
                    }

                    val hostAddress = ia.address.hostAddress
                    if (hostAddress != null && addressList.find { it.address == hostAddress } == null) {
                        addressList.add(AddressEntry(stripInterface(hostAddress), nif.name))
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
            Log.d(this, "collectAddresses() error=$ex")
        }
        return addressList
    }

    // list all IP/MAC addresses of running network interfaces - for debugging only
    fun printOwnAddresses() {
        for (ae in collectAddresses()) {
            val type = getAddressType(ae.address)
            Log.d(this, "Address: ${ae.address} (${ae.device} $type)")
        }
    }

    // Check if the given MAC address is in the IPv6 address
    private fun getEUI64MAC(address: Inet6Address): ByteArray? {
        val bytes = address.address
        if (bytes.size == 16 && bytes[11] == 0xFF.toByte() && bytes[12] == 0xFE.toByte()) {
            return byteArrayOf(
                bytes[8] xor 2,
                bytes[9],
                bytes[10],
                bytes[13],
                bytes[14],
                bytes[15]
            )
        } else {
            return null
        }
    }

    /*
    * E.g. "11:22:33:44:55:66" => "fe80::1322:33ff:fe44:5566"
    */
    fun getLinkLocalFromMAC(macAddress: String): String? {
        val bytes = macAddressToBytes(macAddress)
        val inetAddress = parseInetAddress("fe80::")
        if (bytes != null && inetAddress is Inet6Address) {
            val inetAddressEUI64 = createEUI64Address(inetAddress, bytes)
            return inetAddressEUI64.toString().substring(1)
        }
        return null
    }

    /*
    * Replace the MAC address of an EUi64 scheme IPv6 address with another MAC address.
    * E.g.: ("fe80::aaaa:aaff:faa:aaa", "bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb"
    */
    private fun createEUI64Address(address: Inet6Address, mac: ByteArray): Inet6Address {
        // address is expected to be an EUI64 address
        val bytes = address.address
        bytes[8] = (mac[0] xor 2)
        bytes[9] = mac[1]
        bytes[10] = mac[2]
        // ff:fe may or mac not be already set
        bytes[11] = 0xFF.toByte()
        bytes[12] = 0xFE.toByte()
        bytes[13] = mac[3]
        bytes[14] = mac[4]
        bytes[15] = mac[5]
        return Inet6Address.getByAddress(null, bytes, address.scopeId)
    }

    private fun collectInterfaceNames(networkInterfaces: List<NetworkInterface>): List<String> {
        val ifNames = mutableSetOf<String>()
        for (nif in networkInterfaces) {
            if (nif.isLoopback) {
                continue
            }

            if (ignoreDeviceByName(nif.name)) {
                continue
            }

            ifNames.add(nif.name)
        }

        return ifNames.toList()
    }

    /*
    * Duplicate own addresses that contain a MAC address with the given MAC address.
    */
    private fun mapMACtoPrefixes(networkInterfaces: List<NetworkInterface>, macAddress: ByteArray?, port: Int): List<InetSocketAddress> {
        val addresses = ArrayList<InetSocketAddress>()

        if (macAddress == null || macAddress.size != 6) {
            return addresses
        }

        try {
            for (nif in networkInterfaces) {
                if (nif.isLoopback) {
                    continue
                }

                if (ignoreDeviceByName(nif.name)) {
                    continue
                }

                for (ia in nif.interfaceAddresses) {
                    val address = ia.address

                    if (address.isLoopbackAddress) {
                        continue
                    }

                    if (address is Inet6Address) {
                        if (getEUI64MAC(address) != null || address.isLinkLocalAddress) {
                            // If a MAC address is embedded in the address from our own system (IPv6 + EUI-64)
                            // => replace it by the target MAC address
                            // If the address is fe80:: with a "random" MAC address (no "ff:fe" filler)
                            // => replace it with the target MAC address anyway
                            val newAddress = createEUI64Address(address, macAddress)
                            addresses.add(InetSocketAddress(newAddress.hostAddress, port))
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
    private fun getAddressesFromNeighborTable(lookup_macs: List<String>, port: Int): List<InetSocketAddress> {
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
                    for (lookup_mac in lookup_macs) {
                        if (lookup_mac.equals(mac, ignoreCase = true)
                            && isIPAddress(address)
                            && !state.equals("failed", ignoreCase = true)
                        ) {
                            if (isLinkLocalAddress(address)) {
                                addresses.add(InetSocketAddress("$address%$device", port))
                            } else {
                                addresses.add(InetSocketAddress(address, port))
                            }
                        }
                    }
                }

                // IPv6
                if (tokens.size == 7) {
                    val address = tokens[0]
                    val device = tokens[2]
                    val mac = tokens[4]
                    val state = tokens[6]
                    for (lookup_mac in lookup_macs) {
                        if (lookup_mac.equals(mac, ignoreCase = true)
                            && isIPAddress(address)
                            && !state.equals("failed", ignoreCase = true)
                        ) {
                            if (isLinkLocalAddress(address)) {
                                addresses.add(InetSocketAddress("$address%$device", port))
                            } else {
                                addresses.add(InetSocketAddress(address, port))
                            }
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