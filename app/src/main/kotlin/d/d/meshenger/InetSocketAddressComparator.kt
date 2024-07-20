package d.d.meshenger

import java.net.InetAddress
import java.net.InetSocketAddress

// sort addresses by scope (link-local address, hostname, ... , global IP address, DNS domain)
class InetSocketAddressComparator(val lastWorkingAddress: InetSocketAddress? = null) : Comparator<InetSocketAddress> {
    private fun isPrivateAddress(address: InetAddress): Boolean {
        val bytes = address.address
         if (bytes.size == 4) {
            return (bytes[0].toUByte() == 192.toUByte() && bytes[1].toUByte() == 168.toUByte()) // 192.168.0.0/16
                || (bytes[0].toUByte() == 10.toUByte()) // 10.0.0.0/8
                || (bytes[0].toUByte() == 172.toUByte() && (bytes[1].toUByte() >= 16u && bytes[1].toUByte() <= 31u)) // 172.16.0.0/12
        } else if (bytes.size == 16) {
            return (bytes[0].toUInt() and 0xfeu) == 0xfcu // fc00::/7 (ULA)
        } else {
            return false
        }
    }

    private fun getPriority(address: InetSocketAddress): Int {
        if (lastWorkingAddress != null && lastWorkingAddress == address) {
            // try last working address first
            return 1
        }

        if (address.isUnresolved) {
            if (address.hostName.endsWith(".lan") || address.hostName.endsWith(".local") || !address.hostName.contains(".")) {
                // local domain or hostname
                return 3
            } else {
                return 6
            }
        } else {
            if (address.address.isLinkLocalAddress) {
                //println("is link local: ${address}")
                return 2
            } else if (isPrivateAddress(address.address)) {
                return 4
            } else {
                return 5
            }
        }
    }

    override fun compare(address1: InetSocketAddress, address2: InetSocketAddress): Int {
        val a1 = getPriority(address1)
        val a2 = getPriority(address2)
        if (a1 > a2) {
            return 1
        } else if (a1 < a2) {
            return -1
        } else {
            return 0
        }
    }
}
