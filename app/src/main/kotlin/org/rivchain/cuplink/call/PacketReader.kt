package org.rivchain.cuplink.call

import java.net.Socket

/* Read the message size from the header and return the message of the correct size */
internal class PacketReader(socket: Socket) {
    private val istream = socket.getInputStream()
    private val buffer  = ByteArray(16000)
    private var pos = 0

    fun readMessage(): ByteArray? {
        while (true) {
            val read : Int

            try {
                read = istream.read(buffer, pos, buffer.size - pos)
            } catch (_: Exception) {
                break
            }

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

    companion object {
        private fun readMessageHeader(packet: ByteArray): Int {
            return (packet[0].toInt() and 0xFF shl 24
                    or (packet[1].toInt() and 0xFF shl 16)
                    or (packet[2].toInt() and 0xFF shl 8)
                    or (packet[3].toInt() and 0xFF shl 0))
        }
    }
}