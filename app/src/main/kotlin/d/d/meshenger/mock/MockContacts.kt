package d.d.meshenger.mock

import d.d.meshenger.Contact
import d.d.meshenger.Contacts
import java.util.*
import kotlin.collections.ArrayList

class MockContacts {

    companion object{

        //Random list of names
        private val randomNames = listOf("Ada", "Bobby", "Cassie",
            "Dannie", "Emmet", "Favour", "Everest", "Favour",
            "Garry", "Horatio")

        private val randomListOfPublicKeys = listOf(
            byteArrayOf(149256.toByte()),
            byteArrayOf(903829.toByte()),
            byteArrayOf(892952.toByte()),
            byteArrayOf(239478.toByte()),
            byteArrayOf(127489.toByte()),
            byteArrayOf(235436.toByte()),
            byteArrayOf(535354.toByte()),
            byteArrayOf(234336.toByte()),
            byteArrayOf(766754.toByte()),
            byteArrayOf(234535.toByte()),
            )

        private val randomListOfAddresses = listOf(
            "ASK-OJS-OKN-LOK",
            "KOS-OKO-LSO-MCN",
            "PLO-MKC-MKI-LSI",
            "IOD-MKC-MSL-LOS",
            "MAN-MKS-OIE-LOS",
            "LSO-OKO-MSJ-RET",
            "OSP-PKI-OKO-OWI",
            "OPS-OWI-SPP-OPW",
            "SOS-OWL-PWL-LOK",
            "KLW-OLS-UER-KSO"

        )
        fun generateMockContactList(): ArrayList<Contact?>{
            val mockContactArrayList = ArrayList<Contact?>()
            val addressList = ArrayList(randomListOfAddresses)
            for(i in 0..9){
                val contact = Contact(
                    randomNames[(0..9).random()],
                    randomListOfPublicKeys[(0..9).random()],
                    addressList, false
                )
                mockContactArrayList.add(contact)
            }
            return mockContactArrayList
        }

    }
}