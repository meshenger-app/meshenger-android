package d.d.meshenger

import d.d.meshenger.Log.d
import kotlin.Throws
import d.d.meshenger.PacketReader
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import kotlin.experimental.and

/* Read the message size from the header and return the message of the correct size */
internal class PacketReader(socket: Socket) {
    val `is`: InputStream
    val buffer: ByteArray
    var pos: Int
    @Throws(IOException::class)
    fun readMessage(): ByteArray? {
        while (true) {
            val read = `is`.read(buffer, pos, buffer.size - pos)
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

            // move data of next messgae to the front of the buffer
            System.arraycopy(buffer, 4 + len, buffer, 0, pos - request.size)
            pos = 0
            return request
        }
        return null
    }

    private fun log(s: String) {
        d(this, s)
    }

    companion object {
        private fun readMessageHeader(packet: ByteArray): Int {
            return (packet[0].toInt() and 0xFF shl 24
                    or (packet[1].toInt() and 0xFF shl 16)
                    or (packet[2].toInt() and 0xFF shl 8)
                    or (packet[3].toInt() and 0xFF shl 0))
        }
    }

    init {
        `is` = socket.getInputStream()
        buffer = ByteArray(16000)
        pos = 0
    }
}