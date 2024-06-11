package org.rivchain.cuplink.util

import org.rivchain.cuplink.model.Contact
import org.tdf.rlp.RLPCodec

internal object RlpUtils {

    private val URL_REGEX = """(cpl://)localhost/#c/([a-zA-Z0-9]+$)""".toRegex()

    private val BASE_URL = """cpl://localhost/#c/"""

    @JvmStatic
    fun parseLink(url: String): Contact? {
        val match: MatchResult = URL_REGEX.find(url) ?: return null
        val path: String = match.groups[2]?.value ?: return null

        val contact = RLPCodec.decode(Utils.hexStringToByteArray(path), Contact::class.java)

        if (contact.name.isEmpty() ||
            contact.publicKey.isEmpty() ||
            contact.addresses.isEmpty()){
                return null
        }
        return contact
    }

    @JvmStatic
    fun generateLink(contact: Contact):String? {
        if (contact.name.isEmpty() ||
            contact.publicKey.isEmpty() ||
            contact.addresses.isEmpty()){
            return null
        }
        return BASE_URL + Utils.byteArrayToHexString(RLPCodec.encode(contact))
    }

}