package d.d.meshenger.call

import d.d.meshenger.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection.*
import org.webrtc.RtpReceiver

internal open class DefaultObserver : Observer {
    override fun onSignalingChange(signalingState: SignalingState) {
        Log.d(this, "onSignalingChange: $signalingState")
    }
    override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
        Log.d(this, "onIceConnectionChange: $iceConnectionState")
    }
    override fun onIceConnectionReceivingChange(b: Boolean) {
        Log.d(this, "onIceConnectionReceivingChange: $b")
    }
    override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
        Log.d(this, "onIceGatheringChange: $iceGatheringState")
    }
    override fun onIceCandidate(iceCandidate: IceCandidate) {
        Log.d(this, "onIceCandidate: $iceCandidate")
    }
    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
        Log.d(this, "onIceCandidatesRemoved: ${iceCandidates.joinToString()}")
    }
    override fun onAddStream(mediaStream: MediaStream) {}
    override fun onRemoveStream(mediaStream: MediaStream) {}
    override fun onDataChannel(dataChannel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<MediaStream>) {}
}