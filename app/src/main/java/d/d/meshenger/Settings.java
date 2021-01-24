package d.d.meshenger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class Settings {
    private String username;
    private byte[] secretKey;
    private byte[] publicKey;
    private boolean nightMode;
    /*
     * blockUnknown
     *
     * true  => do not show/accept calls from contacts that are not in the contact list
     * false => accept any calls (default)
     */
    private boolean blockUnknown;
    /*
     * settingsMode:
     *
     * "basic"    => show basic settings (default)
     * "advanced" => show more settings
     * "expert"   => show all settings
     */
    private String settingsMode;
    private boolean recordVideo;
    private boolean playVideo;
    private boolean recordAudio;
    private boolean playAudio;
    /*
     * autoAcceptCall:
     *
     *  true  => accept incoming and no ringing
     *  false => wait an incoming call to be accepted (default)
     */
    private boolean autoAcceptCall;
    /*
     * autoConnectCall:
     *
     *  true  => start outgoing call without prompt (default)
     *  false => allow to change call settings before the call is started
     */
    private boolean autoConnectCall;
    private boolean audioProcessing;
    private String videoCodec;
    private String audioCodec;
    /*
     * speakerphone:
     *
     *  "auto"  => enable/disable by proximity sensor (default)
     *  "true"  => enable hands-free talking
     *  "false" => disable hands-free talking
     */
    private String speakerphone;
    private String videoResolution;
    /*
     * addresses:
     *
     * List of MAC addresses, IP addresses
     * and hostnames to give out by QR-Code.
     * By default contains a single MAC address.
     */
    private List<String> addresses;
    /*
     * iceServers:
     *
     * Optional list of ICE (Interactive Connectivity Establishment)
     * server that implement STUN and TURN.
     */
    private List<String> iceServers;

    public Settings() {
        // set defaults
        this.username = "";
        this.secretKey = null;
        this.publicKey = null;
        this.nightMode = false;
        this.blockUnknown = false;
        this.settingsMode = "basic";
        this.recordVideo = true;
        this.playVideo = true;
        this.recordAudio = true;
        this.playAudio = true;
        this.autoAcceptCall = false;
        this.autoConnectCall = true;
        this.audioProcessing = true;
        this.videoCodec = "VP8";
        this.audioCodec = "OPUS";
        this.speakerphone = "auto";
        this.videoResolution = "default";
        this.addresses = new ArrayList<>();
        this.iceServers = new ArrayList<>();
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean getNightMode() {
        return nightMode;
    }

    public void setNightMode(boolean nightModeEnabled) {
        this.nightMode = nightModeEnabled;
    }

    public boolean getBlockUnknown() {
        return blockUnknown;
    }

    public void setBlockUnknown(boolean blockUnknownEnabled) {
        this.blockUnknown = blockUnknownEnabled;
    }

    public String getSettingsMode() {
        return settingsMode;
    }

    public void setSettingsMode(String settingsMode) {
        this.settingsMode = settingsMode;
    }

    public boolean getRecordVideo() {
        return recordVideo;
    }

    public void setRecordVideo(boolean recordVideo) {
        this.recordVideo = recordVideo;
    }

    public boolean getPlayVideo() {
        return playVideo;
    }

    public void setPlayVideo(boolean playVideo) {
        this.playVideo = playVideo;
    }

    public boolean getRecordAudio() {
        return recordAudio;
    }

    public void setRecordAudio(boolean recordAudio) {
        this.recordAudio = recordAudio;
    }

    public boolean getPlayAudio() {
        return playAudio;
    }

    public void setPlayAudio(boolean playAudio) {
        this.playAudio = playAudio;
    }

    public boolean getAutoAcceptCall() {
        return autoAcceptCall;
    }

    public void setAutoAcceptCall(boolean autoAcceptCall) {
        this.autoAcceptCall = autoAcceptCall;
    }

    public boolean getAutoConnectCall() {
        return autoConnectCall;
    }

    public void setAutoConnectCall(boolean autoConnectCall) {
        this.autoConnectCall = autoConnectCall;
    }

    public boolean getAudioProcessing() {
        return audioProcessing;
    }

    public void setAudioProcessing(boolean audioProcessing) {
        this.audioProcessing = audioProcessing;
    }

    public String getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(String videoCodec) {
        this.videoCodec = videoCodec;
    }

    public String getAudioCodec() {
        return audioCodec;
    }

    public void setAudioCodec(String audioCodec) {
        this.audioCodec = audioCodec;
    }

    public String getSpeakerphone() {
        return speakerphone;
    }

    public void setSpeakerphone(String speakerphone) {
        this.speakerphone = speakerphone;
    }

    public String getVideoResolution() {
        return videoResolution;
    }

    public void setVideoResolution(String videoResolution) {
        this.videoResolution = videoResolution;
    }

    public List<String> getAddresses() {
        return this.addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public void addAddress(String address) {
        for (String addr : this.getAddresses()) {
            if (addr.equalsIgnoreCase(address)) {
                Log.w("Settings", "Try to add duplicate address: " + addr);
                return;
            }
        }
        this.addresses.add(address);
    }

    public List<String> getIceServers() {
        return this.iceServers;
    }

    public void setIceServers(List<String> iceServers) {
        this.iceServers = iceServers;
    }

    public static Settings fromJSON(JSONObject obj) throws JSONException {
        Settings s = new Settings();
        s.username = obj.getString("username");
        s.secretKey = Utils.hexStringToByteArray(obj.getString("secret_key"));
        s.publicKey = Utils.hexStringToByteArray(obj.getString("public_key"));
        s.nightMode = obj.getBoolean("night_mode");
        s.blockUnknown = obj.getBoolean("block_unknown");
        s.settingsMode = obj.getString("settings_mode");
        s.recordVideo = obj.getBoolean("record_video");
        s.playVideo = obj.getBoolean("play_video");
        s.recordAudio = obj.getBoolean("record_audio");
        s.playAudio = obj.getBoolean("play_audio");
        s.autoAcceptCall = obj.getBoolean("auto_accept_call");
        s.autoConnectCall = obj.getBoolean("auto_connect_call");
        s.audioProcessing = obj.getBoolean("audio_processing");
        s.videoCodec = obj.getString("video_codec");
        s.audioCodec = obj.getString("audio_codec");
        s.speakerphone = obj.getString("speakerphone");
        s.videoResolution = obj.getString("video_resolution");

        JSONArray addresses = obj.getJSONArray("addresses");
        for (int i = 0; i < addresses.length(); i += 1) {
            s.addresses.add(addresses.getString(i));
        }

        JSONArray iceServers = obj.getJSONArray("ice_servers");
        for (int i = 0; i < iceServers.length(); i += 1) {
            s.iceServers.add(iceServers.getString(i));
        }

        return s;
    }

    public static JSONObject toJSON(Settings s) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("username", s.username);
        obj.put("secret_key", Utils.byteArrayToHexString(s.secretKey));
        obj.put("public_key", Utils.byteArrayToHexString(s.publicKey));
        obj.put("night_mode", s.nightMode);
        obj.put("block_unknown", s.blockUnknown);
        obj.put("settings_mode", s.settingsMode);
        obj.put("record_video", s.recordVideo);
        obj.put("play_video", s.playVideo);
        obj.put("record_audio", s.recordAudio);
        obj.put("play_audio", s.playAudio);
        obj.put("auto_accept_call", s.autoAcceptCall);
        obj.put("auto_connect_call", s.autoConnectCall);
        obj.put("audio_processing", s.audioProcessing);
        obj.put("video_codec", s.videoCodec);
        obj.put("audio_codec", s.audioCodec);
        obj.put("speakerphone", s.speakerphone);
        obj.put("video_resolution", s.videoResolution);

        JSONArray addresses = new JSONArray();
        for (int i = 0; i < s.addresses.size(); i += 1) {
            addresses.put(s.addresses.get(i));
        }
        obj.put("addresses", addresses);

        JSONArray iceServers = new JSONArray();
        for (int i = 0; i < s.iceServers.size(); i += 1) {
            iceServers.put(s.iceServers.get(i));
        }
        obj.put("ice_servers", iceServers);

        return obj;
    }

    public Contact getOwnContact() {
        return new Contact(this.username, this.publicKey, this.addresses, false);
    }
}
