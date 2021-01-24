package d.d.meshenger.call;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.io.IOException;

import d.d.meshenger.Log;


class SocketWrapper {
    private static final String TAG = "SocketWrapper";
    private final Socket socket;
    private final OutputStream os;
    private final InputStream is;

    public SocketWrapper(final Socket socket) throws IOException {
        this.socket = socket;
        this.os = socket.getOutputStream();
        this.is = socket.getInputStream();
        socket.setSoTimeout(30 * 1000);
    }

    public void close() {
        try {
            this.socket.close();
        } catch (IOException e) {
            Log.w(TAG, "close(): " + e);
        }
    }

    public SocketAddress getRemoteSocketAddress() {
        return this.socket.getRemoteSocketAddress();
    }

    private static void writeMessageHeader(byte[] packet, int value) {
        packet[0] = (byte) ((value >> 24) & 0xff);
        packet[1] = (byte) ((value >> 16) & 0xff);
        packet[2] = (byte) ((value >> 8) & 0xff);
        packet[3] = (byte) ((value >> 0) & 0xff);
    }

    private static int readMessageHeader(byte[] packet) {
        return ((packet[0] & 0xFF) << 24)
            | ((packet[1] & 0xFF) << 16)
            | ((packet[2] & 0xFF) << 8)
            | ((packet[3] & 0xFF) << 0);
    }

    public void writeMessage(byte[] message) throws IOException {
        byte[] packet = new byte[4 + message.length];
        writeMessageHeader(packet, message.length);
        System.arraycopy(message, 0, packet, 4, message.length);
        this.os.write(packet);
        this.os.flush();
    }

    byte[] readMessage() throws IOException {
        byte[] buffer = new byte[16000];
        int pos = 0;

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

            // move data of next message to the front of the buffer
            System.arraycopy(buffer, 4 + len, buffer, 0, pos - request.length);
            pos = 0;
            return request;
        }

        return null;
    }
}
