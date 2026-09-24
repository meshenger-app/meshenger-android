/*
* Copyright (C) 2025 Meshenger Contributors
* SPDX-License-Identifier: GPL-3.0-or-later
*/

package d.d.meshenger.call

import d.d.meshenger.AddressUtils
import d.d.meshenger.MainService
import d.d.meshenger.Settings
import java.net.*

/*
 * WebRTC initially exchanges an offer and answer via a signaling service.
 * The offer message contains a list of all IP addresses of the device in
 * the form of ICE candidates.
 * Since the signaling service is a direct IP connection for Meshenger
 * we can and want to avoid this leak of potentially private sensitive IP addresses.
*/

internal object RTCUtils
{
    const val disableFilter = true

    /*
    * Remove ICE candidate and connection line entries.
    *
    * The ICE candidates are a list of all IP addresses of the system.
    * This is default WebRTC behavior! We want to remove it.
    *
    * We instead let the receiver insert the senders/remote IP address,
    * see filterOfferAfterReception().
    */
    fun filterOfferBeforeSend(offer: String, remoteAddress: InetSocketAddress, settings: Settings): String {
        if (disableFilter) {
            return offer
        }

        if (remoteAddress.address.isLinkLocalAddress) {
            // link-local IP addresses are not supported by WebRTC
            return offer
        }

        val filtered = mutableListOf<String>()
        for (line in offer.lines()) {
            // remove ICE candidate entries
            if (line.startsWith("a=candidate:") ) {
                continue
            }

            // remove connection line entries
            if (line.startsWith("c=")) {
                continue
            }
            filtered.add(line)
        }

        return filtered.joinToString("\n")
    }

    /*
     * Add the senders/remote IP address as an ICE candidate.
     * This will then be used by WebRTC to establish a connection.
     *
     * See also filterOfferBeforeSend().
    */
    fun completeOfferAfterReception(offer: String, remoteAddress: InetSocketAddress, settings: Settings): String {
        if (disableFilter) {
            return offer
        }

        if (remoteAddress.address.isLinkLocalAddress) {
            // link-local IP addresses are not supported by WebRTC
            return offer
        }

        // Get IP address, without interface, host or port.
        // E.g: 192.168.1.45, 2a02:8109::7cc5, fe80::1234%wlan0 (WebRTC does not accept link local addresses..)
        val remoteAddressString = AddressUtils.stripAddress(remoteAddress.address.toString())

        // scheme: "a=candidate:<foundation> <component> <protocol> <priority> <public-ip-here> <port> typ host"
        val iceUdp = "a=candidate:3333333333 1 udp 2222222222 $remoteAddressString ${remoteAddress.port + 1} typ host"
        val iceTcp = "a=candidate:4444444444 2 tcp 1111111111 $remoteAddressString ${remoteAddress.port + 1} typ host"

        //Log.d(this, "iceUdp: $iceUdp")
        //Log.d(this, "iceTcp: $iceTcp")

        // use non-route-able "connection line" entry => force use of ICE candidate entries
        val c = if (remoteAddress.address is Inet6Address) {
            "c=IN IP6 0.0.0.0"
        } else {
            "c=IN IP4 0.0.0.0"
        }

        val nl =  if (offer.endsWith("\n")) {
            ""
        } else {
            "\n"
        }

        return "${offer}${nl}${iceUdp}\n${iceTcp}\n${c}\n"
    }

    fun filterAnswerBeforeSend(answer: String, remoteAddress: InetSocketAddress, settings: Settings): String {
        if (disableFilter) {
            return answer
        }

        // TODO
        return answer
    }

    fun completeAnswerAfterReception(answer: String, remoteAddress: InetSocketAddress, settings: Settings): String {
        if (disableFilter) {
            return answer
        }

        // TODO
        return answer
    }
}
