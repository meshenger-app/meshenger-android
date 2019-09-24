package d.d.meshenger;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.SodiumConstants;

import java.nio.charset.Charset;
import java.util.Arrays;


class Crypto {

    // decrypt database using a password
    public static byte[] decryptDatabase(byte[] encrypted_message, byte[] password) {
        if (encrypted_message == null || password == null) {
            return null;
        }

        if (encrypted_message.length <= (4 + Sodium.crypto_pwhash_saltbytes() + SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES)) {
            return null;
        }

        // separate salt, nonce and encrypted data
        byte[] header = new byte[4];
        byte[] salt = new byte[Sodium.crypto_pwhash_saltbytes()];
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        byte[] encrypted_data = new byte[encrypted_message.length - header.length - salt.length - nonce.length];
        System.arraycopy(encrypted_message, 0, header, 0, header.length);
        System.arraycopy(encrypted_message, header.length, salt, 0, salt.length);
        System.arraycopy(encrypted_message, header.length + salt.length, nonce, 0, nonce.length);
        System.arraycopy(encrypted_message, header.length + salt.length + nonce.length, encrypted_data, 0, encrypted_data.length);

        // expect header to be 0
        if (!(header[0] == 0 && header[1] == 0 && header[2] == 0 && header[3] == 0)) {
            return null;
        }

        // hash password into key
        byte[] key = new byte[Sodium.crypto_box_seedbytes()];
        int rc1 = Sodium.crypto_pwhash(key, key.length, password, password.length, salt,
            Sodium.crypto_pwhash_opslimit_interactive(),
            Sodium.crypto_pwhash_memlimit_interactive(),
            Sodium.crypto_pwhash_alg_default());

        // decrypt
        byte[] decrypted_data = new byte[encrypted_data.length - SodiumConstants.MAC_BYTES];
        int rc2 = Sodium.crypto_secretbox_open_easy(decrypted_data, encrypted_data, encrypted_data.length, nonce, key);

        // zero own memory
        Arrays.fill(header, (byte) 0);
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(key, (byte) 0);
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc1 == 0 && rc2 == 0) {
            return decrypted_data;
        } else {
            Arrays.fill(decrypted_data, (byte) 0);
            return null;
        }
    }

    // encrypt database using a password
    public static byte[] encryptDatabase(byte[] data, byte[] password) {
        if (data == null || password == null) {
            return null;
        }

        // hash password into key
        byte[] salt = new byte[Sodium.crypto_pwhash_saltbytes()];
        Sodium.randombytes_buf(salt, salt.length);

        // hash password into key
        byte[] key = new byte[Sodium.crypto_box_seedbytes()];
        int rc1 = Sodium.crypto_pwhash(key, key.length, password, password.length, salt,
                Sodium.crypto_pwhash_opslimit_interactive(),
                Sodium.crypto_pwhash_memlimit_interactive(),
                Sodium.crypto_pwhash_alg_default());

        byte[] header = new byte[4];
        header[0] = 0;
        header[1] = 0;
        header[2] = 0;
        header[3] = 0;

        // create nonce
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        Sodium.randombytes_buf(nonce, nonce.length);

        // encrypt
        byte[] encrypted_data = new byte[SodiumConstants.MAC_BYTES + data.length];
        int rc2 = Sodium.crypto_secretbox_easy(encrypted_data, data, data.length, nonce, key);

        // prepend header, salt and nonce
        byte[] encrypted_message = new byte[header.length + salt.length + nonce.length + encrypted_data.length];
        System.arraycopy(header, 0, encrypted_message, 0, header.length);
        System.arraycopy(salt, 0, encrypted_message, header.length, salt.length);
        System.arraycopy(nonce, 0, encrypted_message, header.length + salt.length, nonce.length);
        System.arraycopy(encrypted_data, 0, encrypted_message, header.length + salt.length + nonce.length, encrypted_data.length);

        // zero own memory
        Arrays.fill(header, (byte) 0);
        Arrays.fill(salt, (byte) 0);
        Arrays.fill(key, (byte) 0);
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc1 == 0 && rc2 == 0) {
            return encrypted_message;
        } else {
            Arrays.fill(encrypted_message, (byte) 0);
            return null;
        }
    }

    public static byte[] encryptMessage(String message, byte[] otherPublicKey, byte[] ownPublicKey, byte[] ownSecretKey) {
        byte[] message_bytes = message.getBytes();
        byte[] signed = sign(message_bytes, ownSecretKey);
        if (signed == null) {
            return null;
        }

        byte[] data = new byte[ownPublicKey.length + signed.length];
        System.arraycopy(ownPublicKey, 0, data, 0, ownPublicKey.length);
        System.arraycopy(signed, 0, data, ownPublicKey.length, signed.length);

        return encrypt(data, otherPublicKey);
    }

    public static String decryptMessage(byte[] message, byte[] otherPublicKeySignOut, byte[] ownPublicKey, byte[] ownSecretKey) {
        if (otherPublicKeySignOut == null || otherPublicKeySignOut.length != Sodium.crypto_sign_publickeybytes()) {
            return null;
        }

        // make sure this is zeroed
        Arrays.fill(otherPublicKeySignOut, (byte) 0);

        byte[] messageData = decrypt(message, ownPublicKey, ownSecretKey);

        if (messageData == null || messageData.length <= otherPublicKeySignOut.length) {
            return null;
        }

        // split message data in sender public key and content
        byte[] messageSignedData = new byte[messageData.length - otherPublicKeySignOut.length];
        System.arraycopy(messageData, 0, otherPublicKeySignOut, 0, otherPublicKeySignOut.length);
        System.arraycopy(messageData, otherPublicKeySignOut.length, messageSignedData, 0, messageSignedData.length);

        byte[] unsignedData = unsign(messageSignedData, otherPublicKeySignOut);

        if (unsignedData == null) {
            // signature does not match transmitted public key
            return null;
        }

        return new String(unsignedData, Charset.forName("UTF-8"));
    }

    private static byte[] sign(byte[] data, byte[] secretKey) {
        if (data == null) {
            return null;
        }

        if (secretKey == null || secretKey.length != Sodium.crypto_sign_secretkeybytes()) {
            return null;
        }

        byte[] signed_message = new byte[Sodium.crypto_sign_bytes() + data.length];
        final int[] signed_message_len = new int[1];
        int rc = Sodium.crypto_sign(signed_message, signed_message_len, data, data.length, secretKey);
        if ((rc == 0) && (signed_message.length == signed_message_len[0])) {
            return signed_message;
        } else {
            return null;
        }
    }

    // verify signed message
    private static byte[] unsign(byte[] signed_message, byte[] publicKey) {
        if (signed_message == null || signed_message.length < Sodium.crypto_sign_bytes()) {
            return null;
        }

        if (publicKey == null || publicKey.length != Sodium.crypto_sign_publickeybytes()) {
            return null;
        }

        byte[] unsigned_message = new byte[signed_message.length - Sodium.crypto_sign_bytes()];

        final int[] messageSize = new int[1];
        int rc = Sodium.crypto_sign_open(unsigned_message, messageSize, signed_message, signed_message.length, publicKey);
        if (rc == 0 && unsigned_message.length == messageSize[0]) {
            return unsigned_message;
        } else {
            return null;
        }
    }

    // decrypt an anonymous message using the receivers public key
    private static byte[] encrypt(byte[] data, byte[] pk_sign) {
        if (data == null) {
            return null;
        }

        if (pk_sign == null || pk_sign.length != Sodium.crypto_sign_publickeybytes()) {
            return null;
        }

        byte[] pk_box = new byte[Sodium.crypto_box_publickeybytes()];
        int rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pk_box, pk_sign);

        if (rc1 != 0 || pk_box == null || pk_box.length != Sodium.crypto_box_publickeybytes()) {
            return null;
        }

        byte[] ciphertext = new byte[SodiumConstants.SEAL_BYTES + data.length];
        int rc = Sodium.crypto_box_seal(ciphertext, data, data.length, pk_box);

        if (rc == 0) {
            return ciphertext;
        } else {
            return null;
        }
    }

    // decrypt an anonymous message using the receivers public and secret key
    private static byte[] decrypt(byte[] ciphertext, byte[] pk_sign, byte[] sk_sign) {
        if (ciphertext == null || ciphertext.length < SodiumConstants.SEAL_BYTES) {
            return null;
        }

        if (pk_sign == null || pk_sign.length != Sodium.crypto_sign_publickeybytes()) {
            return null;
        }

        if (sk_sign == null || sk_sign.length != Sodium.crypto_sign_secretkeybytes()) {
            return null;
        }

        // convert signature keys to box keys
        byte[] pk_box = new byte[Sodium.crypto_box_publickeybytes()];
        byte[] sk_box = new byte[Sodium.crypto_box_secretkeybytes()];
        int rc1 = Sodium.crypto_sign_ed25519_pk_to_curve25519(pk_box, pk_sign);
        int rc2 = Sodium.crypto_sign_ed25519_sk_to_curve25519(sk_box, sk_sign);

        if (rc1 != 0 || rc2 != 0) {
            return null;
        }

        byte[] decrypted = new byte[ciphertext.length - SodiumConstants.SEAL_BYTES];
        int rc = Sodium.crypto_box_seal_open(decrypted, ciphertext, ciphertext.length, pk_box, sk_box);

        if (rc == 0) {
            return decrypted;
        } else {
            return null;
        }
    }

    private static void log(String s) {
        Log.d(Crypto.class.getSimpleName(), s);
    }
}
