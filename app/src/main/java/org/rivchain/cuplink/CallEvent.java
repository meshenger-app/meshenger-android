package org.rivchain.cuplink;

import java.net.InetAddress;
import java.util.Date;


class CallEvent {
    byte[] pubKey;

    ;
    InetAddress address; // may be null in case the call attempt failed
    Type type;
    Date date;

    CallEvent(byte[] pubKey, InetAddress address, Type type) {
        this.pubKey = pubKey;
        this.address = address;
        this.type = type;
        this.date = new Date();
    }

    enum Type {
        OUTGOING_UNKNOWN,
        OUTGOING_ACCEPTED,
        OUTGOING_DECLINED,
        OUTGOING_MISSED,
        OUTGOING_ERROR,
        INCOMING_UNKNOWN,
        INCOMING_ACCEPTED,
        INCOMING_DECLINED,
        INCOMING_MISSED,
        INCOMING_ERROR
    }
}
