package d.d.meshenger.utils

import d.d.meshenger.model.AddressEntry
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.*
import java.util.*
import kotlin.experimental.xor

object AddressUtils {

    private const val TAG = "AddressUtils"

    fun getAllSocketAddresses(
        initial_addresses: List<String>,
        last_working_address: InetAddress?,
        port: Int
    ): Array<InetSocketAddress> {
        val address_set = HashSet<InetSocketAddress>()
        if (last_working_address != null) {
            address_set.add(InetSocketAddress(last_working_address, port))
        }
        for (address in initial_addresses) {
            try {
                if (Utils.isMAC(address)) {
                    // use own addresses as template
                    address_set.addAll(getAddressPermutations(address, port)!!)
                    // from neighbor table
                    address_set.addAll(getAddressesFromNeighborTable(address, port)!!)
                } else {
                    // parse address
                    address_set.add(InetSocketAddress.createUnresolved(address, port))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invalid address: $address")
                e.printStackTrace()
            }
        }

        // sort addresses
        val address_array = address_set.toTypedArray()
        Arrays.sort(
            address_array
        ) { lhs: InetSocketAddress, rhs: InetSocketAddress ->
            val rhs_addr = lhs.address
            val lhs_addr = rhs.address

            // prefer last working address
            if (last_working_address != null) {
                if (last_working_address.address.equals(lhs_addr)) {
                    return@sort -1
                }
                if (last_working_address.address .equals(rhs_addr)) {
                    return@sort 1
                }
            }
            if (lhs.isUnresolved && !rhs.isUnresolved) {
                return@sort -1
            }
            if (!lhs.isUnresolved && rhs.isUnresolved) {
                return@sort 1
            }
            val lhs_proto = getAddressProtocolValue(lhs_addr)
            val rhs_proto = getAddressProtocolValue(rhs_addr)
            if (lhs_proto < rhs_proto) {
                return@sort -1
            }
            if (lhs_proto > rhs_proto) {
                return@sort 1
            }
            val lhs_scope = getAddressScopeValue(lhs_addr)
            val rhs_scope = getAddressScopeValue(rhs_addr)
            if (lhs_scope < rhs_scope) {
                return@sort -1
            } else if (lhs_scope > rhs_scope) {
                return@sort 1
            } else {
                return@sort 0
            }
        }
        for (address in address_array) {
            Log.d(TAG, "got address: $address")
        }
        return address_array
    }

    fun getOwnAddresses(): ArrayList<AddressEntry> {
        val addressList = ArrayList<AddressEntry>()
        try {
            val all: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                val mac = nif.hardwareAddress
                if (nif.isLoopback) {
                    continue
                }
                if (Utils.isMAC(mac)) {
                    addressList.add(
                        AddressEntry(
                            Utils.bytesToMacAddress(mac),
                            nif.name,
                            Utils.isMulticastMAC(mac)
                        )
                    )
                }
                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address
                    if (addr.isLoopbackAddress) {
                        continue
                    }
                    addressList.add(
                        AddressEntry(
                            addr.hostAddress,
                            nif.name,
                            addr.isMulticastAddress
                        )
                    )
                }
            }
        } catch (ex: Exception) {
            // ignore
            Log.d(TAG, "error: $ex")
        }
        return addressList
    }

    private fun getAddressProtocolValue(addr: InetAddress): Int {
        return if (addr is Inet6Address) {
            1
        } else {
            2
        }
    }

    private fun getAddressScopeValue(addr: InetAddress): Int {
        if (addr.isLoopbackAddress) {
            return 0
        }
        if (addr.isLinkLocalAddress) {
            return 1
        }
        if (addr.isSiteLocalAddress) {
            return 2
        }
        if (addr.isMulticastAddress) {
            if (addr.isMCGlobal) {
                return 3
            }
            if (addr.isMCLinkLocal) {
                return 4
            }
            if (addr.isMCNodeLocal) {
                return 5
            }
            if (addr.isMCOrgLocal) {
                return 6
            }
            if (addr.isMCSiteLocal) {
                return 7
            }
        }
        return 8
    }


    // list all IP/MAC addresses of running network interfaces
    // for debugging only
    fun printOwnAddresses() {
        for (ae in getOwnAddresses()) {
            Log.d(
                TAG,
                "Address: " + ae.address + " (" + ae.device + (if (ae.multicast) ", multicast" else "") + ")"
            )
        }
    }

    // Check if the given MAC address is in the IPv6 address
    fun getEUI64MAC(addr6: Inet6Address): ByteArray? {
        val bytes = addr6.address

        // check for EUI-64 address
        if (bytes[11] != 0xFF.toByte() || bytes[12] != 0xFE.toByte()) {
            return null
        }
        val mac = ByteArray(6)
        mac[0] = (bytes[8] xor 2).toByte()
        mac[1] = bytes[9]
        mac[2] = bytes[10]
        mac[3] = bytes[13]
        mac[4] = bytes[14]
        mac[5] = bytes[15]
        return mac
    }

    private fun getAddressesFromNeighborTable(
        lookup_mac: String,
        port: Int
    ): ArrayList<InetSocketAddress>{
        val addrs = ArrayList<InetSocketAddress>()
        try {
            // get IPv4 and IPv6 entries
            val pc = Runtime.getRuntime().exec("ip n l")
            val rd = BufferedReader(
                InputStreamReader(pc.inputStream, "UTF-8")
            )
            var line = ""
            while (rd.readLine().also { line = it } != null) {
                val tokens = line.split("\\s+".toRegex()).toTypedArray()
                // IPv4
                if (tokens.size == 6) {
                    val addr = tokens[0]
                    val device = tokens[2]
                    val mac = tokens[4]
                    val state = tokens[5]
                    if (lookup_mac.equals(
                            mac,
                            ignoreCase = true
                        ) && Utils.isIP(addr) && !state.equals("failed", ignoreCase = true)
                    ) {
                        if (addr.startsWith("fe80:") || addr.startsWith("169.254.")) {
                            addrs.add(InetSocketAddress("$addr%$device", port))
                        } else {
                            addrs.add(InetSocketAddress(addr, port))
                        }
                    }
                }

                // IPv6
                if (tokens.size == 7) {
                    val addr = tokens[0]
                    val device = tokens[2]
                    val mac = tokens[4]
                    val state = tokens[6]
                    if (mac.equals(
                            lookup_mac,
                            ignoreCase = true
                        ) && Utils.isIP(addr) && !state.equals("failed", ignoreCase = true)
                    ) {
                        if (addr.startsWith("fe80:") || addr.startsWith("169.254.")) {
                            addrs.add(InetSocketAddress("$addr%$device", port))
                        } else {
                            addrs.add(InetSocketAddress(addr, port))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, e.toString())
        }
        return addrs
    }

    /*
    * Replace the MAC address of an EUi64 scheme IPv6 address with another MAC address.
    * E.g.: ("fe80::aaaa:aaff:faa:aaa", "bb:bb:bb:bb:bb:bb") => "fe80::9bbb:bbff:febb:bbbb"
    */
    private fun createEUI64Address(addr6: Inet6Address, mac: ByteArray): Inet6Address? {
        // addr6 is expected to be a EUI64 address
        return try {
            val bytes = addr6.address
            bytes[8] = (mac[0] xor 2).toByte()
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
    * Iterate all own addresses of the device. Check if they conform to the EUI64 scheme.
    * If yes, replace the MAC address in it with the supplied one and return that address.
    * Also set the given port for those generated addresses.
    */
    private fun getAddressPermutations(contact_mac: String, port: Int): ArrayList<InetSocketAddress> {
        val contact_mac_bytes = Utils.macAddressToBytes(contact_mac)
        val addrs = ArrayList<InetSocketAddress>()
        try {
            val all: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (nif.isLoopback || nif.name == "dummy0") {
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
                            // We found the interface MAC address in the IPv6 address assigned to that interface in the EUI-64 scheme.
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
}