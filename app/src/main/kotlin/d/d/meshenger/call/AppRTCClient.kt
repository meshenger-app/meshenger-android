package d.d.meshenger.call

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
interface AppRTCClient {

    /**
     * Asynchronously connect to an AppRTC room URL using supplied connection
     * parameters. Once connection is established onConnectedToRoom()
     * callback with room parameters is invoked.
     */
    fun connectToRoom( /*String address, int port*/)

    /**
     * Send offer SDP to the other participant.
     */
    fun sendOfferSdp(sdp: SessionDescription)

    /**
     * Send answer SDP to the other participant.
     */
    fun sendAnswerSdp(sdp: SessionDescription)

    /**
     * Send Ice candidate to the other participant.
     */
    fun sendLocalIceCandidate(candidate: IceCandidate)

    /**
     * Send removed ICE candidates to the other participant.
     */
    fun sendLocalIceCandidateRemovals(candidates: Array<IceCandidate>)

    /**
     * Disconnect from room.
     */
    fun disconnectFromRoom()

    /**
     * Struct holding the signaling parameters of an AppRTC room.
     */
    class SignalingParameters(
        val iceServers: List<PeerConnection.IceServer>, val initiator: Boolean,
        val clientId: String?, val wssUrl: String?, val wssPostUrl: String?, val offerSdp: SessionDescription?,
        val iceCandidates: List<IceCandidate>?
    )

    /**
     * Callback interface for messages delivered on signaling channel.
     *
     *
     * Methods are guaranteed to be invoked on the UI thread of |activity|.
     */
    interface SignalingEvents {
        /**
         * Callback fired once the room's signaling parameters
         * SignalingParameters are extracted.
         */
        fun onConnectedToRoom(params: SignalingParameters)

        /**
         * Callback fired once remote SDP is received.
         */
        fun onRemoteDescription(sdp: SessionDescription)

        /**
         * Callback fired once remote Ice candidate is received.
         */
        fun onRemoteIceCandidate(candidate: IceCandidate)

        /**
         * Callback fired once remote Ice candidate removals are received.
         */
        fun onRemoteIceCandidatesRemoved(candidates: Array<IceCandidate?>)

        /**
         * Callback fired once channel is closed.
         */
        fun onChannelClose()

        /**
         * Callback fired once channel error happened.
         */
        fun onChannelError(description: String)
    }
}