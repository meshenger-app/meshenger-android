package d.d.meshenger.call

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class DefaultSdpObserver: SdpObserver {

    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
        // nothing to do
    }

    override fun onSetSuccess() {
        // nothing to do
    }

    override fun onCreateFailure(s: String?) {
        // nothing to do
    }

    override fun onSetFailure(s: String?) {
        // nothing to do
    }

}