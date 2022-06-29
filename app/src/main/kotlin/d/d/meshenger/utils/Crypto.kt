package d.d.meshenger.utils

import d.d.meshenger.utils.Log.d
import org.libsodium.jni.Sodium
import org.libsodium.jni.SodiumConstants
import java.nio.charset.Charset
import java.util.*

object Crypto {

        // for development / testing only
        private val disable_crypto = false

        // decrypt database using a password
        fun decryptDatabase(encrypted_message: ByteArray?, password: ByteArray?): ByteArray? {
            if (encrypted_message == null || password == null) {
                return null
            }
            if (encrypted_message.size <= 4 + Sodium.crypto_pwhash_saltbytes() + SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES) {
                return null
            }
            if (disable_crypto) {
                return encrypted_message
            }

            // separate salt, nonce and encrypted data
            val header = ByteArray(4)
            val salt = ByteArray(Sodium.crypto_pwhash_saltbytes())
            val nonce = ByteArray(SodiumConstants.NONCE_BYTES)
            val encrypted_data =
                ByteArray(encrypted_message.size - header.size - salt.size - nonce.size)
            System.arraycopy(encrypted_message, 0, header, 0, header.size)
            System.arraycopy(encrypted_message, header.size, salt, 0, salt.size)
            System.arraycopy(encrypted_message, header.size + salt.size, nonce, 0, nonce.size)
            System.arraycopy(
                encrypted_message,
                header.size + salt.size + nonce.size,
                encrypted_data,
                0,
                encrypted_data.size
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
            val decrypted_data = ByteArray(encrypted_data.size - SodiumConstants.MAC_BYTES)
            val rc2 = Sodium.crypto_secretbox_open_easy(
                decrypted_data,
                encrypted_data,
                encrypted_data.size,
                nonce,
                key
            )

            // zero own memory
            Arrays.fill(header, 0.toByte())
            Arrays.fill(salt, 0.toByte())
            Arrays.fill(key, 0.toByte())
            Arrays.fill(nonce, 0.toByte())
            Arrays.fill(encrypted_data, 0.toByte())
            return if (rc1 == 0 && rc2 == 0) {
                decrypted_data
            } else {
                Arrays.fill(decrypted_data, 0.toByte())
                null
            }
        }

        // encrypt database using a password
        fun encryptDatabase(data: ByteArray?, password: ByteArray?): ByteArray? {
            if (data == null || password == null) {
                return null
            }
            if (disable_crypto) {
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
            val encrypted_data = ByteArray(SodiumConstants.MAC_BYTES + data.size)
            val rc2 = Sodium.crypto_secretbox_easy(encrypted_data, data, data.size, nonce, key)

            // prepend header, salt and nonce
            val encrypted_message =
                ByteArray(header.size + salt.size + nonce.size + encrypted_data.size)
            System.arraycopy(header, 0, encrypted_message, 0, header.size)
            System.arraycopy(salt, 0, encrypted_message, header.size, salt.size)
            System.arraycopy(nonce, 0, encrypted_message, header.size + salt.size, nonce.size)
            System.arraycopy(
                encrypted_data,
                0,
                encrypted_message,
                header.size + salt.size + nonce.size,
                encrypted_data.size
            )

            // zero own memory
            Arrays.fill(header, 0.toByte())
            Arrays.fill(salt, 0.toByte())
            Arrays.fill(key, 0.toByte())
            Arrays.fill(nonce, 0.toByte())
            Arrays.fill(encrypted_data, 0.toByte())
            return if (rc1 == 0 && rc2 == 0) {
                encrypted_message
            } else {
                Arrays.fill(encrypted_message, 0.toByte())
                null
            }
        }

        fun encryptMessage(
            message: String,
            otherPublicKey: ByteArray?,
            ownPublicKey: ByteArray,
            ownSecretKey: ByteArray?
        ): ByteArray? {
            if (disable_crypto) {
                return message.toByteArray()
            }
            val message_bytes = message.toByteArray()
            val signed = sign(message_bytes, ownSecretKey) ?: return null
            val data = ByteArray(ownPublicKey.size + signed.size)
            System.arraycopy(ownPublicKey, 0, data, 0, ownPublicKey.size)
            System.arraycopy(signed, 0, data, ownPublicKey.size, signed.size)
            return encrypt(data, otherPublicKey)
        }

        fun decryptMessage(
            message: ByteArray?,
            otherPublicKeySignOut: ByteArray?,
            ownPublicKey: ByteArray?,
            ownSecretKey: ByteArray?
        ): String? {
            if (otherPublicKeySignOut == null || otherPublicKeySignOut.size != Sodium.crypto_sign_publickeybytes()) {
                return null
            }
            if (disable_crypto) {
                return String(message!!, Charset.forName("UTF-8"))
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
            val signed_message = ByteArray(Sodium.crypto_sign_bytes() + data.size)
            val signed_message_len = IntArray(1)
            val rc =
                Sodium.crypto_sign(signed_message, signed_message_len, data, data.size, secretKey)
            return if (rc == 0 && signed_message.size == signed_message_len[0]) {
                signed_message
            } else {
                null
            }
        }

        // verify signed message
        private fun unsign(signed_message: ByteArray?, publicKey: ByteArray?): ByteArray? {
            if (signed_message == null || signed_message.size < Sodium.crypto_sign_bytes()) {
                return null
            }
            if (publicKey == null || publicKey.size != Sodium.crypto_sign_publickeybytes()) {
                return null
            }
            val unsigned_message = ByteArray(signed_message.size - Sodium.crypto_sign_bytes())
            val messageSize = IntArray(1)
            val rc = Sodium.crypto_sign_open(
                unsigned_message,
                messageSize,
                signed_message,
                signed_message.size,
                publicKey
            )
            return if (rc == 0 && unsigned_message.size == messageSize[0]) {
                unsigned_message
            } else {
                null
            }
        }

        // decrypt an anonymous message using the receivers public key
        private fun encrypt(data: ByteArray?, pk_sign: ByteArray?): ByteArray? {
            if (data == null) {
                return null
            }
            if (pk_sign == null || pk_sign.size != Sodium.crypto_sign_publickeybytes()) {
                return null
            }
            val pk_box: ByteArray? = ByteArray(Sodium.crypto_box_publickeybytes())
            val rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pk_box, pk_sign)
            if (rc1 != 0 || pk_box == null || pk_box.size != Sodium.crypto_box_publickeybytes()) {
                return null
            }
            val ciphertext = ByteArray(SodiumConstants.SEAL_BYTES + data.size)
            val rc = Sodium.crypto_box_seal(ciphertext, data, data.size, pk_box)
            return if (rc == 0) {
                ciphertext
            } else {
                null
            }
        }

        // decrypt an anonymous message using the receivers public and secret key
        private fun decrypt(
            ciphertext: ByteArray?,
            pk_sign: ByteArray?,
            sk_sign: ByteArray?
        ): ByteArray? {
            if (ciphertext == null || ciphertext.size < SodiumConstants.SEAL_BYTES) {
                return null
            }
            if (pk_sign == null || pk_sign.size != Sodium.crypto_sign_publickeybytes()) {
                return null
            }
            if (sk_sign == null || sk_sign.size != Sodium.crypto_sign_secretkeybytes()) {
                return null
            }

            // convert signature keys to box keys
            val pk_box = ByteArray(Sodium.crypto_box_publickeybytes())
            val sk_box = ByteArray(Sodium.crypto_box_secretkeybytes())
            val rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pk_box, pk_sign)
            val rc2 = Sodium.crypto_sign_ed25519_sk_to_curve25519(sk_box, sk_sign)
            if (rc1 != 0 || rc2 != 0) {
                return null
            }
            val decrypted = ByteArray(ciphertext.size - SodiumConstants.SEAL_BYTES)
            val rc =
                Sodium.crypto_box_seal_open(decrypted, ciphertext, ciphertext.size, pk_box, sk_box)
            return if (rc == 0) {
                decrypted
            } else {
                null
            }
        }

        private fun log(s: String) {
            d(Crypto::class.java.simpleName, s)
        }


    }