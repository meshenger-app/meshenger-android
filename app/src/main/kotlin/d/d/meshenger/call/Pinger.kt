package d.d.meshenger.call

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import d.d.meshenger.*
import d.d.meshenger.AddressUtils
import org.json.JSONObject
import org.libsodium.jni.Sodium
import java.net.ConnectException
import java.net.Socket

class Pinger(val binder: MainService.MainBinder, val contacts: List<Contact>) : Runnable {
    private fun pingContact(contact: Contact) : Contact.State {
        val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = binder.getSettings()
        val useNeighborTable = settings.useNeighborTable
        val connectTimeout = settings.connectTimeout
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey
        var connected = false
        val socket = Socket()

        try {
            // try to connect
            for (address in AddressUtils.getAllSocketAddresses(contact, useNeighborTable)) {
                try {
                    socket.connect(address, connectTimeout)
                    connected = true
                    break
                } catch (e: ConnectException) {
                    // target online, but Meshenger not running
                    return Contact.State.APP_NOT_RUNNING
                } catch (e: Exception) {
                    // ignore
                }
            }

            if (!connected) {
                return Contact.State.CONTACT_OFFLINE
            }

            socket.soTimeout = 3000

            val pw = PacketWriter(socket)
            val pr = PacketReader(socket)

            Log.d(this, "send ping to ${contact.name}")
            val encrypted = Crypto.encryptMessage(
                "{\"action\":\"ping\"}",
                contact.publicKey,
                ownPublicKey,
                ownSecretKey
            ) ?: return Contact.State.COMMUNICATION_FAILED

            pw.writeMessage(encrypted)
            val request = pr.readMessage() ?: return Contact.State.COMMUNICATION_FAILED
            val decrypted = Crypto.decryptMessage(
                request,
                otherPublicKey,
                ownPublicKey,
                ownSecretKey
            ) ?: return Contact.State.AUTHENTICATION_FAILED

            if (!otherPublicKey.contentEquals(contact.publicKey)) {
                return Contact.State.AUTHENTICATION_FAILED
            }

            val obj = JSONObject(decrypted)
            val action = obj.optString("action", "")
            if (action == "pong") {
                Log.d(this, "got pong")
                return Contact.State.CONTACT_ONLINE
            } else {
                return Contact.State.COMMUNICATION_FAILED
            }
        } catch (e: Exception) {
            return Contact.State.COMMUNICATION_FAILED
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    override fun run() {
        for (contact in contacts) {
            val state = pingContact(contact)
            Log.d(this, "contact state is $state")
            // set contact state
            binder.getContacts()
                .getContactByPublicKey(contact.publicKey)
                ?.state = state
        }

        LocalBroadcastManager.getInstance(binder.getService()).sendBroadcast(Intent("refresh_contact_list"))
        LocalBroadcastManager.getInstance(binder.getService()).sendBroadcast(Intent("refresh_event_list"))
    }
}
