package org.rivchain.cuplink;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;


/* Read the message size from the header and return the message of the correct size */
class PacketReader {
    final InputStream is;
    final byte[] buffer;
    int pos;

    PacketReader(Socket socket) throws IOException {
        this.is = socket.getInputStream();
        this.buffer = new byte[16000];
        this.pos = 0;
    }

    private static int readMessageHeader(byte[] packet) {
        return ((packet[0] & 0xFF) << 24)
                | ((packet[1] & 0xFF) << 16)
                | ((packet[2] & 0xFF) << 8)
                | ((packet[3] & 0xFF) << 0);
    }

    byte[] readMessage() throws IOException {
        while (true) {
            int read = is.read(buffer, pos, buffer.length - pos);
            if (read < 0) {
                break;
            }

            pos += read;

            if (pos < 4) {
                continue;
            }

            int len = readMessageHeader(buffer);
            if (len < 0 || len > buffer.length) {
                break;
            }

            // not enough data
            if (pos < (4 + len)) {
                continue;
            }

            byte[] request = new byte[len];
            System.arraycopy(buffer, 4, request, 0, request.length);

            // move data of next messgae to the front of the buffer
            System.arraycopy(buffer, 4 + len, buffer, 0, pos - request.length);
            pos = 0;
            return request;
        }

        return null;
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
