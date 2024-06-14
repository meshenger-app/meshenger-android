package org.rivchain.cuplink.util.models

import java.net.DatagramPacket
import java.nio.charset.Charset


object SSDP {
    /* New line definition */
    const val NEWLINE: String = "\r\n"

    const val ADDRESS: String = "239.255.255.250"
    const val PORT: Int = 1900
    const val SOURCE_PORT: Int = 1901

    const val ST: String = "ST"
    const val LOCATION: String = "LOCATION"
    const val NT: String = "NT"
    const val NTS: String = "NTS"
    const val URN: String = "URN"
    const val USN: String = "USN"
    const val APPLICATION_URL: String = "Application-URL"

    /* Definitions of start line */
    const val SL_NOTIFY: String = "NOTIFY * HTTP/1.1"
    const val SL_MSEARCH: String = "M-SEARCH * HTTP/1.1"
    const val SL_OK: String = "HTTP/1.1 200 OK"

    /* Definitions of search targets */ //    public static final String ST_ALL = ST + ": ssdp:all";
    const val ST_SSAP: String = "$ST: urn:lge-com:service:webos-second-screen:1"
    const val ST_DIAL: String = "$ST: urn:dial-multiscreen-org:service:dial:1"
    const val DEVICE_MEDIA_SERVER_1: String = "urn:schemas-upnp-org:device:MediaServer:1"

    //    public static final String SERVICE_CONTENT_DIRECTORY_1 = "urn:schemas-upnp-org:service:ContentDirectory:1";
    //    public static final String SERVICE_CONNECTION_MANAGER_1 = "urn:schemas-upnp-org:service:ConnectionManager:1";
    //    public static final String SERVICE_AV_TRANSPORT_1 = "urn:schemas-upnp-org:service:AVTransport:1";
    //
    //    public static final String ST_ContentDirectory = ST + ":" + UPNP.SERVICE_CONTENT_DIRECTORY_1;
    /* Definitions of notification sub type */
    const val NTS_ALIVE: String = "ssdp:alive"
    const val NTS_BYEBYE: String = "ssdp:byebye"
    const val NTS_UPDATE: String = "ssdp:update"

    fun convertDatagram(dp: DatagramPacket): ParsedDatagram {
        return ParsedDatagram(dp)
    }

    class ParsedDatagram(var dp: DatagramPacket) {
        var data: MutableMap<String, String> = HashMap()
        lateinit var type: String

        init {
            val text = String(dp.data, ASCII_CHARSET)

            var pos = 0

            val CRLF = "\r\n"
            val LF = "\n"
            var eolPos: Int
            run {
                if ((text.indexOf(CRLF).also { eolPos = it }) != -1) {
                    pos = eolPos + CRLF.length
                } else if ((text.indexOf(LF).also { eolPos = it }) != -1) {
                    pos = eolPos + LF.length
                } else return@run
                // Get first line
                type = text.substring(0, eolPos)

                while (pos < text.length) {
                    var line: String
                    if ((text.indexOf(CRLF, pos).also { eolPos = it }) != -1) {
                        line = text.substring(pos, eolPos)
                        pos = eolPos + CRLF.length
                    } else if ((text.indexOf(LF, pos).also { eolPos = it }) != -1) {
                        line = text.substring(pos, eolPos)
                        pos = eolPos + LF.length
                    } else break

                    val index = line.indexOf(':')
                    if (index == -1) {
                        continue
                    }

                    val key = asciiUpper(line.substring(0, index))
                    val value = line.substring(index + 1).trim { it <= ' ' }

                    data[key] = value
                }
            }
        }

        companion object {
            var ASCII_CHARSET: Charset = Charset.forName("US-ASCII")

            // Fast toUpperCase for ASCII strings
            private fun asciiUpper(text: String): String {
                val chars = text.toCharArray()

                for (i in chars.indices) {
                    val c = chars[i]
                    chars[i] = if ((c.code in 97..122)) (c.code - 32).toChar() else c
                }

                return String(chars)
            }
        }
    }
}