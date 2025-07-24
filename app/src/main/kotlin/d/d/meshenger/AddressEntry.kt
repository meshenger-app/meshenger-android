/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger

/*
 * Item for address management (AddressActivity)
 */
class AddressEntry internal constructor(
    val address: String,
    val device: String
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
