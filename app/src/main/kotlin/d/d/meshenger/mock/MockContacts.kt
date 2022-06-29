package d.d.meshenger.mock

import d.d.meshenger.call.DirectRTCClient
import d.d.meshenger.model.Contact
import d.d.meshenger.model.Event
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.SimpleFormatter
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

        private val arrayOfCallDirections = arrayOf(
            DirectRTCClient.CallDirection.INCOMING,
            DirectRTCClient.CallDirection.OUTGOING
            )

        private val arrayOfCallTypes = arrayOf(
            Event.CallType.ACCEPTED,
            Event.CallType.DECLINED,
            Event.CallType.ERROR,
            Event.CallType.MISSED,
            Event.CallType.UNKNOWN

        )

        private fun generateRandomDate(): Date {
            val formatter = SimpleDateFormat("yyyy.MM.dd", Locale.US)
            return formatter.parse("2022-${(3..5).random()}-${(1..30).random()}")!!
        }


        fun generateMockContactList(): ArrayList<Contact>{
            val mockContactArrayList = ArrayList<Contact>()
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

        fun generateMockEventList(): ArrayList<Event> {
            val mockEventArrayList = ArrayList<Event>()
            val addressList = ArrayList(randomListOfAddresses)
            for(i in 0..9){
                val event = Event(
                    randomListOfPublicKeys[(0..9).random()],
                    randomListOfAddresses[(0..9).random()],
                    arrayOfCallDirections[(0..1).random()],
                    arrayOfCallTypes[(0..4).random()],
                    generateRandomDate()
                )
                mockEventArrayList.add(event)
            }
            return mockEventArrayList
        }
    }
}