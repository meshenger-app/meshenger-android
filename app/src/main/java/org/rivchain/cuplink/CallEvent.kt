package org.rivchain.cuplink

import java.net.InetAddress
import java.util.*

class CallEvent(var pubKey: ByteArray, // may be null in case the call attempt failed
                         var address: InetAddress, var type: Type) {
    var date: Date

    enum class Type {
        OUTGOING_UNKNOWN, OUTGOING_ACCEPTED, OUTGOING_DECLINED, OUTGOING_MISSED, OUTGOING_ERROR, INCOMING_UNKNOWN, INCOMING_ACCEPTED, INCOMING_DECLINED, INCOMING_MISSED, INCOMING_ERROR
    }

    init {
        date = Date()
    }
}