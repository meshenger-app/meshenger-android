package org.rivchain.cuplink.util.models

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketException


class SSDPSocket(localInAddress: InetAddress) {
    private var mSSDPMulticastGroup: SocketAddress = InetSocketAddress(SSDP.ADDRESS, SSDP.PORT)

    var wildSocket: DatagramSocket?
    var mLocalSocket: MulticastSocket? = MulticastSocket(SSDP.PORT)

    var mNetIf: NetworkInterface = NetworkInterface.getByInetAddress(localInAddress)

    private var soTimeout: Int = 0

    init {
        mLocalSocket!!.joinGroup(mSSDPMulticastGroup, mNetIf)

        wildSocket = DatagramSocket(null)
        wildSocket!!.reuseAddress = true
        wildSocket!!.bind(InetSocketAddress(localInAddress, SSDP.SOURCE_PORT))
    }

    /** Used to send SSDP packet  */
    @Throws(IOException::class)
    fun send(data: String) {
        val dp = DatagramPacket(data.toByteArray(), data.length, mSSDPMulticastGroup)
        wildSocket!!.send(dp)
    }


    /** Used to receive SSDP Response packet  */
    @Throws(IOException::class)
    fun responseReceive(): DatagramPacket {
        val buf = ByteArray(1024)
        val dp = DatagramPacket(buf, buf.size)

        wildSocket!!.receive(dp)

        return dp
    }

    /** Used to receive SSDP Notify packet  */
    @Throws(IOException::class)
    fun notifyReceive(): DatagramPacket {
        val buf = ByteArray(1024)
        val dp = DatagramPacket(buf, buf.size)

        mLocalSocket!!.receive(dp)

        return dp
    }

    val isConnected: Boolean
        //    /** Starts the socket */
        get() = wildSocket != null && mLocalSocket != null && wildSocket!!.isConnected && mLocalSocket!!.isConnected

    /** Close the socket  */
    fun close() {
        if (mLocalSocket != null) {
            try {
                mLocalSocket!!.leaveGroup(mSSDPMulticastGroup, mNetIf)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mLocalSocket!!.close()
        }

        if (wildSocket != null) {
            wildSocket!!.disconnect()
            wildSocket!!.close()
        }
    }

    @Throws(SocketException::class)
    fun setTimeout(timeout: Int) {
        this.soTimeout = timeout
        wildSocket!!.soTimeout = this.soTimeout
    }
}