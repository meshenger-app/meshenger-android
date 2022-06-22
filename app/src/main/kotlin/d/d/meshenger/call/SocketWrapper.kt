package d.d.meshenger.call

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class SocketWrapper @Throws(IOException::class) constructor(private val socket: Socket) {

    companion object {
        private const val TAG = "SocketWrapper"

        private fun writeMessageHeader(packet: ByteArray, value: Int) {
            packet[0] = (value shr 24 and 0xff).toByte()
            packet[1] = (value shr 16 and 0xff).toByte()
            packet[2] = (value shr 8 and 0xff).toByte()
            packet[3] = (value shr 0 and 0xff).toByte()
        }

        private fun readMessageHeader(packet: ByteArray): Int {
            return (packet[0].toInt() and 0xFF shl 24
                    or (packet[1].toInt() and 0xFF shl 16)
                    or (packet[2].toInt() and 0xFF shl 8)
                    or (packet[3].toInt() and 0xFF shl 0))
        }

    }

    private var os: OutputStream = socket.getOutputStream()
    private var inpts: InputStream = socket.getInputStream()


    init {
        socket.soTimeout = 30 * 1000
    }

    fun getRemoteSocketAddress() = socket.remoteSocketAddress

    fun close() {
        try {
            socket.close()
        } catch (e: IOException) {
            Log.w(TAG, "close(): $e")
        }
    }

    @Throws(IOException::class)
    fun writeMessage(message: ByteArray) {
        val packet = ByteArray(4 + message.size)
        writeMessageHeader(packet, message.size)
        System.arraycopy(message, 0, packet, 4, message.size)
        this.os.write(packet)
        this.os.flush()
    }

    @Throws(IOException::class)
    fun readMessage(): ByteArray? {
        val buffer = ByteArray(16000)
        var pos = 0
        while (true) {
            val read: Int = inpts.read(buffer, pos, buffer.size - pos)
            if (read < 0) {
                break
            }
            pos += read
            if (pos < 4) {
                continue
            }
            val len = readMessageHeader(buffer)
            if (len < 0 || len > buffer.size) {
                break
            }

            // not enough data
            if (pos < 4 + len) {
                continue
            }
            val request = ByteArray(len)
            System.arraycopy(buffer, 4, request, 0, request.size)

            // move data of next message to the front of the buffer
            System.arraycopy(buffer, 4 + len, buffer, 0, pos - request.size)
            pos = 0
            return request
        }
        return null
    }

}