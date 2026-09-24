/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import d.d.meshenger.MainService.Companion.DEFAULT_PORT
import d.d.meshenger.MainService.Companion.MAX_PORT
import d.d.meshenger.MainService.Companion.MIN_PORT
import java.net.*
import java.util.*
import java.util.regex.Pattern
import kotlin.experimental.xor

internal object AddressUtils
{
    fun closeSocket(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun isValidPort(portString: String?): Boolean {
        return portString != null && parsePort(portString) != -1
    }

    fun parsePort(portString: String, defaultPort: Int = -1): Int {
        var port = -1
        try {
            port = portString.toInt()
        } catch (_: Exception) {
            // ignore parse error
        }

        return if (port in MIN_PORT..MAX_PORT) {
            port
        } else {
            defaultPort
        }
    }

    // List any interfaces we do not want to display
    // because we know that is cannot be used.
    fun ignoreDeviceByName(device: String): Boolean {
        return device.startsWith("dummy")
    }

    fun formatMAC(mac: ByteArray): String {
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            mac[0], mac[1], mac[2], mac[3], mac[4], mac[5])
    }

    /*
     * Strip device name from address:
     * "192.168.1.1%wlan1" => "192.168.1.1"
     * "fe80::1%wlan0" => "fe80::1"
     * "[fe80::1%wlan0]:1234" => "[fe80::]:1234"
     */
    fun stripInterface(address:String): String {
        val beg = address.indexOf('%')

        if (beg != -1) {
            val end = address.indexOfAny(charArrayOf(']', ':'), startIndex = beg)
            return if (end == -1) {
                address.substring(0, beg)
            } else {
                address.substring(0, beg) + address.substring(end)
            }
        }

        return address;
    }

    /*
     * Strip first entry, e.g.:
     * "/1.2.3.4" => "1.2.3.4"
     * "google.com/1.2.3.4" => "1.2.3.4"
     * "google.com" => "google.com"
     */
    fun stripHost(address: String): String {
        val pos = address.indexOf('/')
        return address.substring(pos + 1)
    }

    fun stripPort(address: String): String {
        val pos = address.indexOf("]:")
        if (pos != -1) {
            address.substring(1, pos + 1)
        }

        if (address.count { it == ':' } == 1) {
            val pos = address.lastIndexOf(":")
            return if (pos != -1) {
                address.substring(0, pos)
            } else {
                address
            }
        }

        return address
    }

    // remove interface, host and port from address
    fun stripAddress(address: String): String {
        return stripHost(stripPort(stripInterface(address)))
    }

    // coarse address type distinction
    enum class AddressType {
        GLOBAL_IP, LOCAL_IP, MULTICAST_IP, DOMAIN
    }

    fun parseInetAddress(address: String): InetAddress? {
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

    fun getAddressType(address: String): AddressType {
        val ipAddress = parseInetSocketAddress(address)
        if (ipAddress != null) {
            if (ipAddress.address.isMulticastAddress) {
                return AddressType.MULTICAST_IP
            } else if ((ipAddress.address.address[1].toInt() and 15) == 0x0E) {
                // global IP address
                return AddressType.GLOBAL_IP
            } else {
                return AddressType.LOCAL_IP
            }
        }

        return AddressType.DOMAIN
    }

    private fun isValidMACBytes(mac: ByteArray?): Boolean {
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
            @Suppress("DEPRECATION")
            return IPV6_STD_PATTERN.matcher(address).matches()
                 || IPV6_HEX_COMPRESSED_PATTERN.matcher(address).matches()
                 || Patterns.IP_ADDRESS.matcher(address).matches()
        }
    }

    fun isMACAddress(address: String): Boolean {
        return MAC_PATTERN.matcher(address).matches()
    }

    fun isDomain(address: String): Boolean {
        val addrWithoutPort = address.substringBefore(':')
        return DOMAIN_PATTERN.matcher(addrWithoutPort).matches()
            && !addrWithoutPort.contains("..")
    }

    // replacement of isIPAddress
    fun isInetSocketAddress(address: String?): Boolean {
        return (null != parseInetSocketAddress(address))
    }

    /* Parses an address string:
     *
     * "[<ip-address>]:<port>"
     * "[<ip-address>]"
     * "<ip-address>:<port>"
     * "<ip-address>"
     * "<domain>:<port>"
     * "<domain>"
    */
    fun parseInetSocketAddress(address: String?, defaultPort: Int = DEFAULT_PORT): InetSocketAddress? {
        if (address.isNullOrEmpty()) {
            return null
        } else if (address.startsWith("[")) {
            val end = address.lastIndexOf("]:")
            if (end > 0) {
                // "[<ip-address>]:<port>"
                val addressPart = address.substring(1, end)
                val portPart = address.substring(end + 2)
                val port = parsePort(portPart)
                if (port != null && isIPAddress(addressPart)) {
                    return InetSocketAddress(addressPart, port)
                }
            } else {
                val end = address.lastIndexOf("]")
                // [<ip-address>]
                if (end > 0) {
                    val addressPart = address.substring(1, end)
                    if (isIPAddress(addressPart)) {
                        return InetSocketAddress(addressPart, defaultPort)
                    }
                }
            }
        } else {
            if (address.count { it == ':' } == 1) {
                // "<domain>:<port>"
                // "<ip-address>:<port>"
                val end = address.indexOf(":")
                val addressPart = address.substring(0, end)
                val portPart = address.substring(end + 1)
                val port = parsePort(portPart)
                if (port != -1) {
                    return if (isIPAddress(addressPart)) {
                        InetSocketAddress(addressPart, port)
                    } else {
                        InetSocketAddress.createUnresolved(addressPart, port)
                    }
                }
            } else if (isIPAddress(address)) {
                // "<ip-address>"
                return InetSocketAddress(address, defaultPort)
            } else if (isDomain(address)) {
                // "<domain>"
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
        if (port !in MIN_PORT..MAX_PORT) {
            return null
        } else if (address == null) {
            val host = socketAddress.hostString.trimStart('/')
            if (isIPAddress(host) || isDomain(host)) {
                return "$host:$port"
            } else {
                return null
            }
        } else if (address is Inet6Address) {
            val str = stripHost(address.toString())
            return "[$str]:$port"
        } else {
            val str = stripHost(address.toString())
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
                    if (hostAddress != null) {
                        addressList.add(AddressEntry(stripInterface(hostAddress), nif.name))
                    }
                }
            }
        } catch (ex: Exception) {
            // ignore
            Log.d(this, "collectAddresses() error=$ex")
        }

        return addressList.distinct()
    }

    // list all IP/MAC addresses of running network interfaces - for debugging only
    fun printOwnAddresses() {
        for (ae in collectAddresses()) {
            val type = getAddressType(ae.address)
            Log.d(this, "Address: ${ae.address} (${ae.device} $type)")
        }
    }

    /*
    * Replace the MAC address of an EUi64 scheme IPv6 address with another MAC address.
    * E.g.: ("fe80::aaaa:aaff:faa:aaa", "bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb"
    */
    fun createEUI64Address(address: Inet6Address, mac: ByteArray): Inet6Address {
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
    * E.g. "11:22:33:44:55:66" => [0x11, 0x22, 0x33, 0x44, 0x55, 0x66]
    */
    fun macAddressToBytes(macAddress: String): ByteArray? {
        if (isMACAddress(macAddress)) {
            val bytes = macAddress
                .split(":")
                .map { it.toInt(16).toByte() }
                .toByteArray()
            if (isValidMACBytes(bytes)) {
                return bytes
            }
        }
        return null
    }
}
