package d.d.meshenger;

import java.nio.charset.Charset;


class Crypto {
    // for development / testing only
    //public static boolean disable_crypto = true;

    // decrypt database using a password
    public static byte[] decryptDatabase(byte[] encrypted_message, byte[] password) {

        return encrypted_message;

    }

    // encrypt database using a password
    public static byte[] encryptDatabase(byte[] data, byte[] password) {
        if (data == null || password == null) {
            return null;
        }

        return data;
    }

    public static byte[] encryptMessage(String message, byte[] otherPublicKey, byte[] ownPublicKey, byte[] ownSecretKey) {
        return message.getBytes();
    }

    public static String decryptMessage(byte[] message, byte[] otherPublicKeySignOut, byte[] ownPublicKey, byte[] ownSecretKey) {
        if (otherPublicKeySignOut == null) {
            return null;
        }

        return new String(message, Charset.forName("UTF-8"));
    }

}
