package d.d.meshenger

import d.d.meshenger.Log.d
import kotlin.Throws
import d.d.meshenger.PacketWriter
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

/* Write the message header before the message is send */
internal class PacketWriter(socket: Socket) {
    val os: OutputStream
    val header: ByteArray
    @Throws(IOException::class)
    fun writeMessage(message: ByteArray) {
        writeMessageHeader(header, message.size)
        // need to concatenate?
        os.write(header)
        os.write(message)
    }

    private fun log(s: String) {
        d(this, s)
    }

    companion object {
        private fun writeMessageHeader(packet: ByteArray, value: Int) {
            packet[0] = (value shr 24 and 0xff).toByte()
            packet[1] = (value shr 16 and 0xff).toByte()
            packet[2] = (value shr 8 and 0xff).toByte()
            packet[3] = (value shr 0 and 0xff).toByte()
        }
    }

    init {
        os = socket.getOutputStream()
        header = ByteArray(4)
    }
}