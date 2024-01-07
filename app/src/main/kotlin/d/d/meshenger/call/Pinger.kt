package d.d.meshenger.call

import d.d.meshenger.*
import d.d.meshenger.AddressUtils
import org.json.JSONObject
import org.libsodium.jni.Sodium
import java.lang.Integer.max
import java.lang.Integer.min
import java.net.ConnectException
import java.net.Socket

/*
 * Checks if a contact is online.
*/
class Pinger(val binder: MainService.MainBinder, val contacts: List<Contact>) : Runnable {
    private fun pingContact(contact: Contact) : Contact.State {
        Log.d(this, "pingContact() contact: ${contact.name}")

        val otherPublicKey = ByteArray(Sodium.crypto_sign_publickeybytes())
        val settings = binder.getSettings()
        val useNeighborTable = settings.useNeighborTable
        val connectTimeout = settings.connectTimeout
        val ownPublicKey = settings.publicKey
        val ownSecretKey = settings.secretKey
        val connectRetries = settings.connectRetries
        var connectedSocket: Socket? = null
        var networkNotReachable = false
        var appNotRunning = false

        try {
            val allGeneratedAddresses = AddressUtils.getAllSocketAddresses(contact, useNeighborTable)
            Log.d(this, "pingContact() connectTimeout: ${connectTimeout}, contact.addresses: ${contact.addresses}, allGeneratedAddresses: $allGeneratedAddresses")

            // try to connect
            for (iteration in 0..max(0, min(connectRetries, 4))) {
                Log.d(this, "pingContact() loop number $iteration")
                for (address in allGeneratedAddresses) {
                    val socket = Socket()
                    try {
                        socket.connect(address, connectTimeout)
                        connectedSocket = socket
                        break
                    } catch (e: ConnectException) {
                        Log.d(this, "pingContact() $e, ${e.message}")
                        if (" ENETUNREACH " in e.toString()) {
                            networkNotReachable = true
                        } else {
                            appNotRunning = true
                        }
                    } catch (e: Exception) {
                        // ignore
                        Log.d(this, "pingContact() $e, ${e.message}")
                    }

                    closeSocket(socket)
                }

                // TCP/IP connection successfully
                if (connectedSocket != null) {
                    break
                }
            }

            if (connectedSocket == null) {
                return if (appNotRunning) {
                    Contact.State.APP_NOT_RUNNING
                } else if (networkNotReachable) {
                    Contact.State.NETWORK_UNREACHABLE
                } else {
                    Contact.State.CONTACT_OFFLINE
                }
            }

            connectedSocket.soTimeout = 3000

            val pw = PacketWriter(connectedSocket)
            val pr = PacketReader(connectedSocket)

            Log.d(this, "pingContact() send ping to ${contact.name}")
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
                Log.d(this, "pingContact() got pong")
                return Contact.State.CONTACT_ONLINE
            } else {
                return Contact.State.COMMUNICATION_FAILED
            }
        } catch (e: Exception) {
            return Contact.State.COMMUNICATION_FAILED
        } finally {
            // make sure to close the socket
            closeSocket(connectedSocket)
        }
    }

    private fun closeSocket(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    override fun run() {
        // set all states to unknown
        for (contact in contacts) {
            binder.getContacts()
                .getContactByPublicKey(contact.publicKey)
                ?.state = Contact.State.PENDING
        }

        MainService.refreshContacts(binder.getService())
        MainService.refreshEvents(binder.getService())

        // ping contacts
        for (contact in contacts) {
            val state = pingContact(contact)
            Log.d(this, "contact state is $state")

            // set contact state
            binder.getContacts()
                .getContactByPublicKey(contact.publicKey)
                ?.state = state
        }

        MainService.refreshContacts(binder.getService())
        MainService.refreshEvents(binder.getService())
    }
}
