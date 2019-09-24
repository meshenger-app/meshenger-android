package d.d.meshenger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;


/* Write the message header before the message is send */
class PacketWriter {
    final OutputStream os;
    final byte[] header;

    public PacketWriter(Socket socket) throws IOException {
        this.os = socket.getOutputStream();
        this.header = new byte[4];
    }

    private static void writeMessageHeader(byte[] packet, int value) {
        packet[0] = (byte) ((value >> 24) & 0xff);
        packet[1] = (byte) ((value >> 16) & 0xff);
        packet[2] = (byte) ((value >> 8) & 0xff);
        packet[3] = (byte) ((value >> 0) & 0xff);
    }

    public void writeMessage(byte[] message) throws IOException {
    	writeMessageHeader(this.header, message.length);
    	// need to concatenate?
    	this.os.write(this.header);
        this.os.write(message);
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
