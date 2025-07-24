/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger.call

import java.net.Socket

/* Write the message header before the message is send */
internal class PacketWriter(socket: Socket) {
    private val os = socket.getOutputStream()

    fun writeMessage(message: ByteArray) {
        val buffer = ByteArray(4 + message.size)
        writeMessageHeader(buffer, message.size)
        message.copyInto(buffer, 4)
        os.write(buffer)
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