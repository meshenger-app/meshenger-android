package org.rivchain.cuplink

import java.net.InetAddress
import java.util.*

class CallEvent(
    var address: InetAddress, var type: Type
) {
    var date: Date = Date()

    enum class Type {
        OUTGOING_UNKNOWN, OUTGOING_ACCEPTED, OUTGOING_DECLINED, OUTGOING_MISSED, OUTGOING_ERROR, INCOMING_UNKNOWN, INCOMING_ACCEPTED, INCOMING_DECLINED, INCOMING_MISSED, INCOMING_ERROR
    }

}