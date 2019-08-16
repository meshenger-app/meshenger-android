package d.d.meshenger;

import android.util.Log;

import com.goterl.lazycode.lazysodium.LazySodiumAndroid;
import com.goterl.lazycode.lazysodium.SodiumAndroid;
import com.goterl.lazycode.lazysodium.interfaces.Box;
import com.goterl.lazycode.lazysodium.interfaces.SecretBox;
import com.goterl.lazycode.lazysodium.utils.KeyPair;
import com.goterl.lazycode.lazysodium.utils.Key;

import org.json.JSONObject;

import java.util.Arrays;


class Crypto {

    // decrypt data using a password
    public static byte[] decryptData(byte[] encrypted_message, byte[] password) {
        if (encrypted_message == null || password == null) {
            return null;
        }

        if (encrypted_message.length <= (SecretBox.NONCEBYTES + SecretBox.MACBYTES)) {
            return null;
        }

        SodiumAndroid sa = new SodiumAndroid();

        // hash password into key
        byte[] key = new byte[SecretBox.KEYBYTES];
        sa.crypto_generichash(key, key.length, password, password.length, null, 0);

        // separate nonce and encrypted data
        byte[] nonce = new byte[SecretBox.NONCEBYTES];
        byte[] encrypted_data = new byte[encrypted_message.length - SecretBox.NONCEBYTES];
        System.arraycopy(encrypted_message, 0, nonce, 0, nonce.length);
        System.arraycopy(encrypted_message, nonce.length, encrypted_data, 0, encrypted_data.length);

        // decrypt
        byte[] decrypted_data = new byte[encrypted_data.length - SecretBox.MACBYTES];
        int rc = sa.crypto_secretbox_open_easy(decrypted_data, encrypted_data, encrypted_data.length, nonce, key);

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

        SodiumAndroid sa = new SodiumAndroid();

        // hash password into key
        byte[] key = new byte[SecretBox.KEYBYTES];
        sa.crypto_generichash(key, key.length, password, password.length, null, 0);

        // create nonce
        byte[] nonce = new byte[SecretBox.NONCEBYTES];
        sa.randombytes_buf(nonce, nonce.length);

        // encrypt
        byte[] encrypted_data = new byte[SecretBox.MACBYTES + data.length];
        int rc = sa.crypto_secretbox_easy(encrypted_data, data, data.length, nonce, key);

        // prepend nonce
        byte[] encrypted_message = new byte[SecretBox.NONCEBYTES + SecretBox.MACBYTES + data.length];
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
    public static String encrypt(String message, String publicKey, String secretKey) {
        try {
            LazySodiumAndroid ls = new LazySodiumAndroid(new SodiumAndroid());
            byte[] nonce = ls.nonce(Box.NONCEBYTES);

            KeyPair encryptKeyPair = new KeyPair(
                Key.fromHexString(publicKey),
                Key.fromHexString(secretKey)
            );

            String encryptedMessage = ls.cryptoBoxEasy(message, nonce, encryptKeyPair);

            JSONObject obj = new JSONObject();
            obj.put("nonce", Utils.byteArrayToHexString(nonce));
            obj.put("data", encryptedMessage);

            // add newline for BufferedReader::readLine()
            return obj.toString() + "\n";
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    // decrypt data using a (receivers) public key and a (own) secret key
    public static String decrypt(String message, String publicKey, String secretKey) {
        try {
            JSONObject obj = new JSONObject(message);
            byte[] nonce = Utils.hexStringToByteArray(obj.optString("nonce", ""));
            String encryptedMessage = obj.optString("data", "");

            LazySodiumAndroid ls = new LazySodiumAndroid(new SodiumAndroid());

            KeyPair decryptKeyPair = new KeyPair(
                Key.fromHexString(publicKey),
                Key.fromHexString(secretKey)
            );

            String decrypted = ls.cryptoBoxOpenEasy(encryptedMessage, nonce, decryptKeyPair);
            if (decrypted != null) {
                return decrypted;
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    /*
    public static byte[] encrypt(byte[] data, String publicKeyStr, String secretKeyStr) {
        byte[] publicKey = hexStringToByteArray(publicKeyStr);
        byte[] secretKey = hexStringToByteArray(secretKeyStr);

        if (data == null || publicKey == null || secretKey == null) {
            return null;
        }

        SodiumAndroid sa = new SodiumAndroid();

        // create nonce
        byte[] nonce = new byte[SecretBox.NONCEBYTES];
        sa.randombytes_buf(nonce, nonce.length);

        // encrypt
        byte[] encrypted_data = new byte[SecretBox.MACBYTES + data.length];
        int rc = sa.crypto_box_easy(encrypted_data, data, data.length, nonce, publicKey, secretKey);

        // prepend nonce
        byte[] encrypted_message = new byte[SecretBox.NONCEBYTES + SecretBox.MACBYTES + data.length];
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
    public static byte[] decrypt(byte[] encrypted_message, String publicKeyStr, String secretKeyStr) {
        byte[] publicKey = hexStringToByteArray(publicKeyStr);
        byte[] secretKey = hexStringToByteArray(secretKeyStr);

        if (encrypted_message == null || publicKey == null || secretKey == null) {
            return null;
        }

        if (encrypted_message.length < (Box.NONCEBYTES + Box.MACBYTES)) {
            return null;
        }

        SodiumAndroid sa = new SodiumAndroid();

        // separate nonce and encrypted data
        byte[] nonce = new byte[SecretBox.NONCEBYTES];
        byte[] encrypted_data = new byte[encrypted_message.length - SecretBox.NONCEBYTES];
        System.arraycopy(encrypted_message, 0, nonce, 0, nonce.length);
        System.arraycopy(encrypted_message, nonce.length, encrypted_data, 0, encrypted_data.length);

        // decrypt
        byte[] decrypted_data = new byte[encrypted_data.length - SecretBox.MACBYTES];
        int rc = sa.crypto_box_open_easy(decrypted_data, encrypted_data, encrypted_data.length, nonce, publicKey, secretKey);

        // zero own memory
        Arrays.fill(nonce, (byte) 0);
        Arrays.fill(encrypted_data, (byte) 0);

        if (rc == 0) {
            return decrypted_data;
        } else {
            Arrays.fill(decrypted_data, (byte) 0);
            return null;
        }
    }*/

    private static void log(String s) {
        Log.d(Crypto.class.getSimpleName(), s);
    }
}
