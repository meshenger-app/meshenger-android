package d.d.meshenger

/*
 * Item for address management (AddressActivity)
 */
class AddressEntry internal constructor(
    var address: String,
    var device: String,
    var multicast: Boolean,
) : Comparable<AddressEntry> {
    override fun compareTo(other: AddressEntry): Int {
        return address.compareTo(other.address)
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
}