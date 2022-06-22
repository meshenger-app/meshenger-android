package d.d.meshenger.model

data class AddressEntry(val address: String, val device: String, val multicast: Boolean){

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


    operator fun compareTo(e: AddressEntry): Int {
        return address.compareTo(e.address)
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