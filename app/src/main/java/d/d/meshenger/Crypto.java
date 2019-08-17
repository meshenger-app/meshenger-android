package d.d.meshenger;

import android.util.Log;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.SodiumConstants;

import java.nio.charset.Charset;
import java.util.Arrays;


class Crypto {

    // decrypt data using a password
    public static byte[] decryptData(byte[] encrypted_message, byte[] password) {
        if (encrypted_message == null || password == null) {
            return null;
        }

        if (encrypted_message.length <= (SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES)) {
            return null;
        }

        // hash password into key
        byte[] key = new byte[Sodium.crypto_generichash_bytes()];
        byte[] secret = new byte[0];
        Sodium.crypto_generichash(key, key.length, password, password.length, secret, secret.length);

        // separate nonce and encrypted data
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        byte[] encrypted_data = new byte[encrypted_message.length - SodiumConstants.NONCE_BYTES];
        System.arraycopy(encrypted_message, 0, nonce, 0, nonce.length);
        System.arraycopy(encrypted_message, nonce.length, encrypted_data, 0, encrypted_data.length);

        // decrypt
        byte[] decrypted_data = new byte[encrypted_data.length - SodiumConstants.MAC_BYTES];
        int rc = Sodium.crypto_secretbox_open_easy(decrypted_data, encrypted_data, encrypted_data.length, nonce, key);

        // zero own memory
        Arrays.fill(key, (byte) 0);
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc == 0) {
            return decrypted_data;
        } else {
            Arrays.fill(decrypted_data, (byte) 0);
            return null;
        }
    }

    // encrypt data using a password
    public static byte[] encryptData(byte[] data, byte[] password) {
        if (data == null || password == null) {
            return null;
        }

        // hash password into key
        byte[] key = new byte[Sodium.crypto_generichash_bytes()];
        byte[] secret = new byte[0];
        Sodium.crypto_generichash(key, key.length, password, password.length, secret, secret.length);

        // create nonce
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        Sodium.randombytes_buf(nonce, nonce.length);

        // encrypt
        byte[] encrypted_data = new byte[SodiumConstants.MAC_BYTES + data.length];
        int rc = Sodium.crypto_secretbox_easy(encrypted_data, data, data.length, nonce, key);

        // prepend nonce
        byte[] encrypted_message = new byte[SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES + data.length];
        System.arraycopy(nonce, 0, encrypted_message, 0, nonce.length);
        System.arraycopy(encrypted_data, 0, encrypted_message, nonce.length, encrypted_data.length);

        // zero own memory
        Arrays.fill(key, (byte) 0);
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc == 0) {
            return encrypted_message;
        } else {
            Arrays.fill(encrypted_message, (byte) 0);
            return null;
        }
    }

    // encrypt data using a (receivers) public key and a (own) secret key
    public static String encryptMessage(String messageStr, String publicKeyStr, String secretKeyStr) {
        try {
            byte[] publicKey = Utils.hexStringToByteArray(publicKeyStr);
            byte[] secretKey = Utils.hexStringToByteArray(secretKeyStr);
            byte[] encrypted_data = encrypt(messageStr.getBytes(), publicKey, secretKey);

            // add newline for BufferedReader::readLine()
            return Utils.byteArrayToHexString(encrypted_data) + "\n";
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    // decrypt data using a (receivers) public key and a (own) secret key
    public static String decryptMessage(String messageStr, String publicKeyStr, String secretKeyStr) {
        try {
            byte[] publicKey = Utils.hexStringToByteArray(publicKeyStr);
            byte[] secretKey = Utils.hexStringToByteArray(secretKeyStr);
            byte[] message = Utils.hexStringToByteArray(messageStr);

            byte[] decrypted = decrypt(message, publicKey, secretKey);
            if (decrypted != null) {
                return new String(decrypted, Charset.forName("UTF-8"));
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    private static byte[] encrypt(byte[] data, byte[] publicKey, byte[] secretKey) {
        if (data == null || publicKey == null || secretKey == null) {
            return null;
        }

        // create nonce
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        Sodium.randombytes_buf(nonce, nonce.length);

        // encrypt
        byte[] encrypted_data = new byte[SodiumConstants.MAC_BYTES + data.length];
        int rc = Sodium.crypto_box_easy(encrypted_data, data, data.length, nonce, publicKey, secretKey);

        // prepend nonce
        byte[] encrypted_message = new byte[SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES + data.length];
        System.arraycopy(nonce, 0, encrypted_message, 0, nonce.length);
        System.arraycopy(encrypted_data, 0, encrypted_message, nonce.length, encrypted_data.length);

        // zero own memory
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc == 0) {
            return encrypted_message;
        } else {
            Arrays.fill(encrypted_message, (byte) 0);
            return null;
        }
    }

    // decrypt data using a (receivers) public key and a (own) secret key
    private static byte[] decrypt(byte[] encrypted_message, byte[] publicKey, byte[] secretKey) {
        if (encrypted_message == null || publicKey == null || secretKey == null) {
            return null;
        }

        if (encrypted_message.length < (SodiumConstants.NONCE_BYTES + SodiumConstants.MAC_BYTES)) {
            return null;
        }

        // separate nonce and encrypted data
        byte[] nonce = new byte[SodiumConstants.NONCE_BYTES];
        byte[] encrypted_data = new byte[encrypted_message.length - SodiumConstants.NONCE_BYTES];
        System.arraycopy(encrypted_message, 0, nonce, 0, nonce.length);
        System.arraycopy(encrypted_message, nonce.length, encrypted_data, 0, encrypted_data.length);

        // decrypt
        byte[] decrypted_data = new byte[encrypted_data.length - SodiumConstants.MAC_BYTES];
        int rc = Sodium.crypto_box_open_easy(decrypted_data, encrypted_data, encrypted_data.length, nonce, publicKey, secretKey);

        // zero own memory
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc == 0) {
            return decrypted_data;
        } else {
            Arrays.fill(decrypted_data, (byte) 0);
            return null;
        }
    }

    private static void log(String s) {
        Log.d(Crypto.class.getSimpleName(), s);
    }
}
