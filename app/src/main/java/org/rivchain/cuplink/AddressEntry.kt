package org.rivchain.cuplink

/*
 * Item for address management (AddressActivity)
 */
class AddressEntry internal constructor(
    var address: String,
    var device: String,
    var multicast: Boolean
) : Comparable<AddressEntry?> {
    override operator fun compareTo(other: AddressEntry?): Int {
        return address.compareTo(other!!.address)
    }

    override fun toString(): String {
        return address
    }

    override fun equals(other: Any?): Boolean {
        return if (other is AddressEntry) {
            address == other.address
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    companion object {
        fun findAddressEntry(list: List<AddressEntry>, address: String): AddressEntry? {
            var i = 0
            while (i < list.size) {
                if (list[i].address == address) {
                    return list[i]
                }
                i += 1
            }
            return null
        }

        fun listIndexOf(list: List<AddressEntry>, entry: AddressEntry): Int {
            val iter: Iterator<AddressEntry> = list.listIterator()
            var i = 0
            while (iter.hasNext()) {
                if (iter.next().address == entry.address) {
                    return i
                }
                i += 1
            }
            return -1
        }
    }
}