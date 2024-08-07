package d.d.meshenger

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.ArrayList
import java.util.Collections
import kotlin.experimental.xor
import kotlin.math.max
import kotlin.math.min

class Connector(
        private val connectTimeout: Int = 5000,
        private val connectRetries: Int = 3,
        private val guessEUI64Address: Boolean = false,
        private val useNeighborTable: Boolean = false) {
    var networkNotReachable = false
    var appNotRunning = false

    var unknownHostException = false
    var connectException = false
    var socketTimeoutException = false
    var exception = false

    interface AddressTry {
        fun onAddressTry(address: InetSocketAddress)
    }

    var addressTry: AddressTry? = null

    private fun isLinkLocalAddress(address: String): Boolean {
        return AddressUtils.parseInetAddress(address)?.isLinkLocalAddress ?: false
    }

    private fun getAllSocketAddresses(contact: Contact): List<InetSocketAddress> {
        val port = MainService.serverPort
        val addresses = mutableListOf<InetSocketAddress>()
        val macs = mutableSetOf<String>()

        val lastWorkingAddress = contact.lastWorkingAddress
        if (lastWorkingAddress != null) {
            addresses.add(lastWorkingAddress)
        }

        val ownInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        for (address in contact.addresses) {
            val socketAddress = AddressUtils.stringToInetSocketAddress(address, port) ?: continue

            if (isLinkLocalAddress(socketAddress.hostString)) {
                for (interfaceName in collectInterfaceNames(ownInterfaces)) {
                    addresses.add(InetSocketAddress("${socketAddress.hostString}%${interfaceName}", socketAddress.port))
                }
            } else {
                addresses.add(socketAddress)
            }

            // get MAC address from IPv6 EUI64 address and construct new ones with own IPv6 prefixes
            if (guessEUI64Address || useNeighborTable) {
                val inetAddress = AddressUtils.parseInetAddress(address)
                val macAddress = extractMAC(inetAddress)
                if (macAddress != null) {
                    if (guessEUI64Address) {
                        addresses.addAll(mapMACtoPrefixes(ownInterfaces, macAddress, port))
                    }
                    if (useNeighborTable) {
                        macs.add(AddressUtils.formatMAC(macAddress))
                    }
                }
            }
        }

        if (useNeighborTable) {
            addresses.addAll(
                getAddressesFromNeighborTable(macs.toList(), port)
            )
        }

        return addresses.distinct().sortedWith(InetSocketAddressComparator(lastWorkingAddress))
    }


    private fun createSocket(address: InetSocketAddress): Socket {
        val socket = Socket()
        try {
            socket.connect(address, connectTimeout)
            return socket
        } catch (e: Exception) {
            AddressUtils.closeSocket(socket)
            throw e
        }
    }

    public fun connect(contact: Contact): Socket? {
        Utils.checkIsNotOnMainThread()

        networkNotReachable = false
        appNotRunning = false
        unknownHostException = false
        connectException = false
        socketTimeoutException = false
        exception = false

        for (iteration in 0..max(0, min(connectRetries, 4))) {
            Log.d(this, "connect() loop number $iteration")

            for (address in getAllSocketAddresses(contact)) {
                Log.d(this, "connect() try address: $address")

            	addressTry?.onAddressTry(address)

                try {
                    if (address.isUnresolved) {
                        for (resolvedAddress in InetAddress.getAllByName(address.hostString)) {
                            return createSocket(InetSocketAddress(resolvedAddress, address.port))
                        }
                    } else {
                        return createSocket(address)
                    }
                } catch (e: SocketTimeoutException) {
                    // no connection
                    Log.d(this, "connect() socket has thrown SocketTimeoutException for address=$address")
                    socketTimeoutException = true
                } catch (e: ConnectException) {
                    // device is online, but does not listen on the given port
                    Log.d(this, "connect() socket has thrown ConnectException for address=$address")
                    connectException = true

                    if (" ENETUNREACH " in e.toString()) {
                        networkNotReachable = true
                    } else {
                        appNotRunning = true
                    }
                } catch (e: UnknownHostException) {
                    // hostname did not resolve
                    Log.d(this, "connect() socket has thrown UnknownHostException for address=$address")
                    unknownHostException = true
                } catch (e: Exception) {
                    Log.d(this, "connect() socket has thrown Exception for address=$address")
                    exception = true
                }
            }
        }

        return null
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

                if (AddressUtils.ignoreDeviceByName(nif.name)) {
                    continue
                }

                for (ia in nif.interfaceAddresses) {
                    val address = ia.address

                    if (address.isLoopbackAddress) {
                        continue
                    }

                    if (address is Inet6Address) {
                        if (extractMAC(address) != null || address.isLinkLocalAddress) {
                            // If a MAC address is embedded in the address from our own system (IPv6 + EUI-64)
                            // => replace it by the target MAC address
                            // If the address is fe80:: with a "random" MAC address (no "ff:fe" filler)
                            // => replace it with the target MAC address anyway
                            val newAddress = AddressUtils.createEUI64Address(address, macAddress)
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

    private fun collectInterfaceNames(networkInterfaces: List<NetworkInterface>): List<String> {
        val ifNames = mutableSetOf<String>()
        for (nif in networkInterfaces) {
            if (nif.isLoopback) {
                continue
            }

            if (AddressUtils.ignoreDeviceByName(nif.name)) {
                continue
            }

            ifNames.add(nif.name)
        }

        return ifNames.toList()
    }

    // experimental feature
    private fun getAddressesFromNeighborTable(lookupMACs: List<String>, port: Int): List<InetSocketAddress> {
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
                    for (lookupMAC in lookupMACs) {
                        if (lookupMAC.equals(mac, ignoreCase = true)
                            && AddressUtils.isIPAddress(address)
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
                    for (lookupMAC in lookupMACs) {
                        if (lookupMAC.equals(mac, ignoreCase = true)
                            && AddressUtils.isIPAddress(address)
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

    // Extract a MAC address from an IPv6 (EUI64) address
    private fun extractMAC(address: InetAddress?): ByteArray? {
        val bytes = address?.address
        if (bytes != null && bytes.size == 16 && bytes[11] == 0xFF.toByte() && bytes[12] == 0xFE.toByte()) {
            return byteArrayOf(
                bytes[8] xor 2,
                bytes[9],
                bytes[10],
                bytes[13],
                bytes[14],
                bytes[15]
            )
        }
        return null
    }
}
