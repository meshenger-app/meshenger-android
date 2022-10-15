package d.d.meshenger

import org.libsodium.jni.Sodium
import org.libsodium.jni.SodiumConstants
import java.nio.charset.Charset
import java.util.*

internal object Crypto {
    // for development / testing only
    private val disableCrypto = false

    // decrypt database using a password
    @JvmStatic
    fun decryptDatabase(encrypted_message: ByteArray?, password: ByteArray?): ByteArray? {
        if (encrypted_message == null || password == null) {
            return null
        }

        if (encrypted_message.size <= 4 + Sodium.crypto_pwhash_saltbytes() + SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES) {
            return null
        }

        if (disableCrypto) {
            return encrypted_message
        }

        // separate salt, nonce and encrypted data
        val header = ByteArray(4)
        val salt = ByteArray(Sodium.crypto_pwhash_saltbytes())
        val nonce = ByteArray(SodiumConstants.NONCE_BYTES)
        val encryptedData =
            ByteArray(encrypted_message.size - header.size - salt.size - nonce.size)
        System.arraycopy(encrypted_message, 0, header, 0, header.size)
        System.arraycopy(encrypted_message, header.size, salt, 0, salt.size)
        System.arraycopy(encrypted_message, header.size + salt.size, nonce, 0, nonce.size)
        System.arraycopy(
            encrypted_message,
            header.size + salt.size + nonce.size,
            encryptedData,
            0,
            encryptedData.size
        )

        // expect header to be 0
        if (!(header[0].toInt() == 0 && header[1].toInt() == 0 && header[2].toInt() == 0 && header[3].toInt() == 0)) {
            return null
        }

        // hash password into key
        val key = ByteArray(Sodium.crypto_box_seedbytes())
        val rc1 = Sodium.crypto_pwhash(
            key, key.size, password, password.size, salt,
            Sodium.crypto_pwhash_opslimit_interactive(),
            Sodium.crypto_pwhash_memlimit_interactive(),
            Sodium.crypto_pwhash_alg_default()
        )

        // decrypt
        val decryptedData = ByteArray(encryptedData.size - SodiumConstants.MAC_BYTES)
        val rc2 = Sodium.crypto_secretbox_open_easy(
            decryptedData,
            encryptedData,
            encryptedData.size,
            nonce,
            key
        )

        // zero own memory
        Arrays.fill(header, 0.toByte())
        Arrays.fill(salt, 0.toByte())
        Arrays.fill(key, 0.toByte())
        Arrays.fill(nonce, 0.toByte())
        Arrays.fill(encryptedData, 0.toByte())
        return if (rc1 == 0 && rc2 == 0) {
            decryptedData
        } else {
            Arrays.fill(decryptedData, 0.toByte())
            null
        }
    }

    // encrypt database using a password
    @JvmStatic
    fun encryptDatabase(data: ByteArray?, password: ByteArray?): ByteArray? {
        if (data == null || password == null) {
            return null
        }

        if (disableCrypto) {
            return data
        }

        // hash password into key
        val salt = ByteArray(Sodium.crypto_pwhash_saltbytes())
        Sodium.randombytes_buf(salt, salt.size)

        // hash password into key
        val key = ByteArray(Sodium.crypto_box_seedbytes())
        val rc1 = Sodium.crypto_pwhash(
            key, key.size, password, password.size, salt,
            Sodium.crypto_pwhash_opslimit_interactive(),
            Sodium.crypto_pwhash_memlimit_interactive(),
            Sodium.crypto_pwhash_alg_default()
        )
        val header = ByteArray(4)
        header[0] = 0
        header[1] = 0
        header[2] = 0
        header[3] = 0

        // create nonce
        val nonce = ByteArray(SodiumConstants.NONCE_BYTES)
        Sodium.randombytes_buf(nonce, nonce.size)

        // encrypt
        val encryptedData = ByteArray(SodiumConstants.MAC_BYTES + data.size)
        val rc2 = Sodium.crypto_secretbox_easy(encryptedData, data, data.size, nonce, key)

        // prepend header, salt and nonce
        val encryptedMessage =
            ByteArray(header.size + salt.size + nonce.size + encryptedData.size)
        System.arraycopy(header, 0, encryptedMessage, 0, header.size)
        System.arraycopy(salt, 0, encryptedMessage, header.size, salt.size)
        System.arraycopy(nonce, 0, encryptedMessage, header.size + salt.size, nonce.size)
        System.arraycopy(
            encryptedData,
            0,
            encryptedMessage,
            header.size + salt.size + nonce.size,
            encryptedData.size
        )

        // zero own memory
        Arrays.fill(header, 0.toByte())
        Arrays.fill(salt, 0.toByte())
        Arrays.fill(key, 0.toByte())
        Arrays.fill(nonce, 0.toByte())
        Arrays.fill(encryptedData, 0.toByte())
        return if (rc1 == 0 && rc2 == 0) {
            encryptedMessage
        } else {
            Arrays.fill(encryptedMessage, 0.toByte())
            null
        }
    }

    @JvmStatic
    fun encryptMessage(
        message: String,
        otherPublicKey: ByteArray?,
        ownPublicKey: ByteArray,
        ownSecretKey: ByteArray?
    ): ByteArray? {
        if (disableCrypto) {
            return message.toByteArray()
        }

        val messageBytes = message.toByteArray()
        val signed = sign(messageBytes, ownSecretKey) ?: return null
        val data = ByteArray(ownPublicKey.size + signed.size)
        System.arraycopy(ownPublicKey, 0, data, 0, ownPublicKey.size)
        System.arraycopy(signed, 0, data, ownPublicKey.size, signed.size)
        return encrypt(data, otherPublicKey)
    }

    @JvmStatic
    fun decryptMessage(
        message: ByteArray?,
        otherPublicKeySignOut: ByteArray?,
        ownPublicKey: ByteArray?,
        ownSecretKey: ByteArray?
    ): String? {
        if (otherPublicKeySignOut == null || otherPublicKeySignOut.size != Sodium.crypto_sign_publickeybytes()) {
            return null
        }

        if (disableCrypto) {
            return String((message)!!, Charset.forName("UTF-8"))
        }

        // make sure this is zeroed
        Arrays.fill(otherPublicKeySignOut, 0.toByte())
        val messageData = decrypt(message, ownPublicKey, ownSecretKey)
        if (messageData == null || messageData.size <= otherPublicKeySignOut.size) {
            return null
        }

        // split message data in sender public key and content
        val messageSignedData = ByteArray(messageData.size - otherPublicKeySignOut.size)
        System.arraycopy(messageData, 0, otherPublicKeySignOut, 0, otherPublicKeySignOut.size)
        System.arraycopy(
            messageData,
            otherPublicKeySignOut.size,
            messageSignedData,
            0,
            messageSignedData.size
        )
        val unsignedData = unsign(messageSignedData, otherPublicKeySignOut)
            ?: // signature does not match transmitted public key
            return null
        return String(unsignedData, Charset.forName("UTF-8"))
    }

    private fun sign(data: ByteArray?, secretKey: ByteArray?): ByteArray? {
        if (data == null) {
            return null
        }
        if (secretKey == null || secretKey.size != Sodium.crypto_sign_secretkeybytes()) {
            return null
        }
        val signedMessage = ByteArray(Sodium.crypto_sign_bytes() + data.size)
        val signedMessageLen = IntArray(1)
        val rc = Sodium.crypto_sign(signedMessage, signedMessageLen, data, data.size, secretKey)
        return if (rc == 0 && signedMessage.size == signedMessageLen[0]) {
            signedMessage
        } else {
            null
        }
    }

    // verify signed message
    private fun unsign(signedMessage: ByteArray?, publicKey: ByteArray?): ByteArray? {
        if (signedMessage == null || signedMessage.size < Sodium.crypto_sign_bytes()) {
            return null
        }
        if (publicKey == null || publicKey.size != Sodium.crypto_sign_publickeybytes()) {
            return null
        }
        val unsignedMessage = ByteArray(signedMessage.size - Sodium.crypto_sign_bytes())
        val messageSize = IntArray(1)
        val rc = Sodium.crypto_sign_open(
            unsignedMessage,
            messageSize,
            signedMessage,
            signedMessage.size,
            publicKey
        )
        return if (rc == 0 && unsignedMessage.size == messageSize[0]) {
            unsignedMessage
        } else {
            null
        }
    }

    // decrypt an anonymous message using the receivers public key
    private fun encrypt(data: ByteArray?, pkSign: ByteArray?): ByteArray? {
        if (data == null) {
            return null
        }
        if (pkSign == null || pkSign.size != Sodium.crypto_sign_publickeybytes()) {
            return null
        }
        val pkBox = ByteArray(Sodium.crypto_box_publickeybytes())
        val rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pkBox, pkSign)
        if (rc1 != 0 || pkBox.size != Sodium.crypto_box_publickeybytes()) {
            return null
        }
        val ciphertext = ByteArray(SodiumConstants.SEAL_BYTES + data.size)
        val rc = Sodium.crypto_box_seal(ciphertext, data, data.size, pkBox)
        return if (rc == 0) {
            ciphertext
        } else {
            null
        }
    }

    // decrypt an anonymous message using the receivers public and secret key
    private fun decrypt(
        ciphertext: ByteArray?,
        pkSign: ByteArray?,
        skSign: ByteArray?
    ): ByteArray? {
        if (ciphertext == null || ciphertext.size < SodiumConstants.SEAL_BYTES) {
            return null
        }
        if (pkSign == null || pkSign.size != Sodium.crypto_sign_publickeybytes()) {
            return null
        }
        if (skSign == null || skSign.size != Sodium.crypto_sign_secretkeybytes()) {
            return null
        }

        // convert signature keys to box keys
        val pkBox = ByteArray(Sodium.crypto_box_publickeybytes())
        val skBox = ByteArray(Sodium.crypto_box_secretkeybytes())
        val rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pkBox, pkSign)
        val rc2 = Sodium.crypto_sign_ed25519_sk_to_curve25519(skBox, skSign)
        if (rc1 != 0 || rc2 != 0) {
            return null
        }
        val decrypted = ByteArray(ciphertext.size - SodiumConstants.SEAL_BYTES)
        val rc = Sodium.crypto_box_seal_open(decrypted, ciphertext, ciphertext.size, pkBox, skBox)
        return if (rc == 0) {
            decrypted
        } else {
            null
        }
    }
}