package org.rivchain.cuplink

import java.nio.charset.Charset

internal class Crypto {
    // for development / testing only
    //public static boolean disable_crypto = true;
    // decrypt database using a password
    companion object {

        fun encryptMessage(
            message: String
        ): ByteArray {
            return message.toByteArray()
        }

        fun decryptMessage(
            message: ByteArray?
        ): String {
            return String(message!!, Charset.forName("UTF-8"))
        }
    }
}