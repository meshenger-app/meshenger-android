package d.d.meshenger;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.Date;

import d.d.meshenger.call.DirectRTCClient.CallDirection;


public class Event {
    public static final String TAG = "Event";

    public enum CallType {
        UNKNOWN,
        ACCEPTED,
        DECLINED,
        MISSED,
        ERROR
    };

    byte[] publicKey;
    String address; // may be null in case the call attempt failed
	CallDirection callDirection;
    CallType callType;
    Date date;

    public Event(byte[] publicKey, String address, CallDirection callDirection, CallType callType, Date date) {
        this.publicKey = publicKey;
        this.address = address;
        this.callDirection = callDirection;
        this.callType = callType;
        this.date = date;
    }

    public boolean isMissedCall() {
        return callDirection == CallDirection.INCOMING && (callType != CallType.ACCEPTED);
    }

    private static CallDirection callDirectionFromString(String value) {
        if (value.equals("INCOMING")) {
            return CallDirection.INCOMING;
        } else {
            return CallDirection.OUTGOING;
        }
    }

    private static String callDirectionToString(CallDirection value) {
        if (value == CallDirection.INCOMING) {
            return "INCOMING";
        } else {
            return "OUTGOING";
        }
    }

    private static String callTypeToString(CallType value) {
        switch (value) {
            case UNKNOWN: return "UNKNOWN";
            case ACCEPTED: return "ACCEPTED";
            case DECLINED: return "DECLINED";
            case MISSED: return "MISSED";
            case ERROR: return "ERROR";
            default:
                Log.w(TAG, "Invalid call type: " + value);
                return "UNKNOWN";
        }
    }

    private static CallType callTypeFromString(String value) {
        switch (value) {
            case "UNKNOWN": return CallType.UNKNOWN;
            case "ACCEPTED": return CallType.ACCEPTED;
            case "DECLINED": return CallType.DECLINED;
            case "MISSED": return CallType.MISSED;
            case "ERROR": return CallType.ERROR;
            default:
                Log.w(TAG, "Invalid call type: " + value);
                return CallType.UNKNOWN;
        }
    }

    public static JSONObject toJSON(Event event) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("public_key", Utils.byteArrayToHexString(event.publicKey));
        obj.put("address", event.address);
        obj.put("call_direction", callDirectionToString(event.callDirection));
        obj.put("call_type", callTypeToString(event.callType));
        obj.put("date", String.valueOf(event.date.getTime()));
        return obj;
    }

    public static Event fromJSON(JSONObject obj) throws JSONException {
        byte[] publicKey = Utils.hexStringToByteArray(obj.getString("public_key"));
        String address = obj.getString("address");
        CallType callType = callTypeFromString(obj.getString("call_type"));
        CallDirection callDirection = callDirectionFromString(obj.getString("call_direction"));
        Date date = new Date(Long.parseLong(obj.getString("date"), 10));
        return new Event(publicKey, address, callDirection, callType, date);
    }
}
