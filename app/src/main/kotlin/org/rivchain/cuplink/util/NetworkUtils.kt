package org.rivchain.cuplink.util

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import org.rivchain.cuplink.MainService
import org.rivchain.cuplink.model.AddressEntry
import org.rivchain.cuplink.model.Contact
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.regex.Pattern
import kotlin.experimental.and
import kotlin.experimental.xor
import net.mm2d.upnp.Device


internal object NetworkUtils {

    fun openPortWithUPnP(device: Device, internalIP: String, internalPort: Int, externalPort: Int) {
        if (device.deviceType == "urn:schemas-upnp-org:device:InternetGatewayDevice:1") {
            val wanDevice =
                device.findDeviceByType("urn:schemas-upnp-org:device:WANDevice:1")
            if (wanDevice != null) {
                val wanConnectionDevice = wanDevice.findDeviceByType("urn:schemas-upnp-org:device:WANConnectionDevice:1")
                if (wanConnectionDevice != null) {
                    val service = wanConnectionDevice.findServiceByType("urn:schemas-upnp-org:service:WANIPConnection:1")
                    if (service != null){
                        val action = service.findAction("AddPortMapping")
                        if (action != null) {
                            val argumentValues = mutableMapOf<String, String>()
                            argumentValues["NewRemoteHost"] = ""
                            argumentValues["NewExternalPort"] = externalPort.toString()
                            argumentValues["NewProtocol"] = "TCP"
                            argumentValues["NewInternalPort"] = internalPort.toString()
                            argumentValues["NewInternalClient"] = internalIP
                            argumentValues["NewEnabled"] = "1"
                            argumentValues["NewPortMappingDescription"] = "CupLink UPnP Port Mapping"
                            argumentValues["NewLeaseDuration"] = "0"
                            try {
                                action.invoke(argumentValues)
                                Log.d(this, "Port mapping added successfully.")
                            } catch (e: Exception) {
                                Log.d(this, "Failed to add port mapping: ${e.message}")
                                e.printStackTrace()
                            }
                        } else
                            Log.d(this, "AddPortMapping action not found.")
                    } else
                        Log.d(this, "WANIPConnection service not found.")
                } else
                    Log.d(this, "WANConnectionDevice not found.")
            } else
                Log.d(this, "WANDevice not found.")
        } else
            Log.d(this, "InternetGatewayDevice not found.")
    }

    fun getLocalInterfaceIPs(): List<String> {
        val ipList = mutableListOf<String>()
        try {
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val inetAddresses = Collections.list(networkInterface.inetAddresses)
                    for (inetAddress in inetAddresses) {
                        val ip = inetAddress.hostAddress
                        if (inetAddress is InetAddress && isPrivateIP(ip!!)) {
                            ipList.add(ip)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return ipList
    }

    fun isPrivateIP(ip: String): Boolean {
        return ip.matches(Regex("^(10)(\\.([2]([0-5][0-5]|[01234][6-9])|[1][0-9][0-9]|[1-9][0-9]|[0-9])){3}\$")) ||
                ip.matches(Regex("^(172)\\.(1[6-9]|2[0-9]|3[0-1])(\\.(2[0-4][0-9]|25[0-5]|[1][0-9][0-9]|[1-9][0-9]|[0-9])){2}$")) ||
                ip.matches(Regex("^(192)\\.(168)(\\.(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])){2}$"))
    }

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
            if (isMACAddress(address)) {
                val mac = macAddressToBytes(address)
                addresses.addAll(mapMACtoPrefixes(ownInterfaces, mac, port))
                if (useNeighborTable && mac != null) {
                    macs.add(formatMAC(mac))
                }
            } else {
                val socketAddress = stringToInetSocketAddress(address, port) ?: continue

                if (isLinkLocalAddress(socketAddress.hostString)) {
                    for (interfaceName in collectInterfaceNames(ownInterfaces)) {
                        addresses.add(InetSocketAddress("${socketAddress.hostString}%${interfaceName}", socketAddress.port))
                    }
                } else {
                    addresses.add(socketAddress)
                }

                // get MAC address from IPv6 EUI64 address
                if (extractFE80MAC && isIPAddress(address)) {
                    val inetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        InetAddresses.parseNumericAddress(address)
                    } else {
                        InetAddress.getByName(address)
                    }
                    val mac = extractMAC(inetAddress)
                    addresses.addAll(mapMACtoPrefixes(ownInterfaces, mac, port))
                    if (useNeighborTable && mac != null) {
                        macs.add(formatMAC(mac))
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
        return device.contains("rmnet") || device.startsWith("dummy")
    }

    fun formatMAC(mac: ByteArray): String {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5])
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
            if (isValidMAC(array)) {
                return array
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

    private val DOMAIN_PATTERN = Pattern.compile("^([a-z0-9\\-_]{1,63}[.]){1,40}[a-z]{2,}$")
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
    }

    fun isAddress(address: String): Boolean {
        return isIPAddress(address)
            || isMACAddress(address)
            || isDomain(address)
    }

    fun stringToInetSocketAddress(address: String?, defaultPort: Int): InetSocketAddress? {
        if (address == null || address.isEmpty()) {
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
                    if (isIPAddress(addressPart)) {
                        return InetSocketAddress(addressPart, port)
                    } else {
                        return InetSocketAddress.createUnresolved(addressPart, port)
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
            if (isAddress(host)) {
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

    fun  collectAddresses(): List<AddressEntry> {
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
                    val macAddress = formatMAC(hardwareMAC)
                    if (addressList.find { it.address == macAddress } == null) {
                        addressList.add(AddressEntry(macAddress, nif.name))
                    }
                }

                for (ia in nif.interfaceAddresses) {
                    if (ia.address.isLoopbackAddress) {
                        continue
                    }

                    val hostAddress = ia.address.hostAddress
                    if (hostAddress != null && addressList.find { it.address == hostAddress } == null) {
                        addressList.add(AddressEntry(stripInterface(hostAddress), nif.name))
                    }

                    // extract MAC address from fe80:: address
                    val softwareMAC = extractMAC(ia.address)
                    if (softwareMAC != null) {
                        val macAddress = formatMAC(softwareMAC)
                        if (addressList.find { it.address == macAddress } == null) {
                            addressList.add(AddressEntry(macAddress, nif.name))
                        }
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