package org.rivchain.cuplink

import java.nio.charset.Charset

internal class Crypto {
    // for development / testing only
    //public static boolean disable_crypto = true;
    // decrypt database using a password
    companion object {
        fun decryptDatabase(encrypted_message: ByteArray, password: ByteArray?): ByteArray {
            return encrypted_message
        }

        // encrypt database using a password
        fun encryptDatabase(data: ByteArray?, password: ByteArray?): ByteArray? {
            return if (data == null || password == null) {
                null
            } else data
        }

        fun encryptMessage(
            message: String,
            otherPublicKey: ByteArray?,
            ownPublicKey: ByteArray?,
            ownSecretKey: ByteArray?
        ): ByteArray {
            return message.toByteArray()
        }

        fun decryptMessage(
            message: ByteArray?,
            otherPublicKeySignOut: ByteArray?,
            ownPublicKey: ByteArray?,
            ownSecretKey: ByteArray?
        ): String? {
            return if (otherPublicKeySignOut == null) {
                null
            } else String(message!!, Charset.forName("UTF-8"))
        }
    }
}