package d.d.meshenger.call

import java.net.Socket

/* Write the message header before the message is send */
internal class PacketWriter(socket: Socket) {
    private val os = socket.getOutputStream()
    private val header = ByteArray(4)

    fun writeMessage(message: ByteArray) {
        writeMessageHeader(header, message.size)
        // need to concatenate?
        os.write(header)
        os.write(message)
    }

    companion object {
        private fun writeMessageHeader(packet: ByteArray, value: Int) {
            packet[0] = (value shr 24 and 0xff).toByte()
            packet[1] = (value shr 16 and 0xff).toByte()
            packet[2] = (value shr 8 and 0xff).toByte()
            packet[3] = (value shr 0 and 0xff).toByte()
        }
    }
}