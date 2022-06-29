package d.d.meshenger

class AddressEntry(address: String, device: String, multicast: Boolean): Comparable<AddressEntry> {

    var address: String
    var device: String
    var multicast = false

    companion object{
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

    init {
        this.address = address
        this.device = device
        this.multicast = multicast
    }

    override fun compareTo(other: AddressEntry): Int = address.compareTo(other.address)

    override fun toString(): String = address

    override fun equals(other: Any?): Boolean =
        if (other is AddressEntry) {
            address == other.address
        } else {
            super.equals(other)
        }

    override fun hashCode(): Int = address.hashCode()


}