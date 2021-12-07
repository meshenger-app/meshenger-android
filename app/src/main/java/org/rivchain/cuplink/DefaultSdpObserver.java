package org.rivchain.cuplink;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;


class DefaultSdpObserver implements SdpObserver {
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        // nothing to do
    }

    @Override
    public void onSetSuccess() {
        // nothing to do
    }

    @Override
    public void onCreateFailure(String s) {
        // nothing to do
    }

    @Override
    public void onSetFailure(String s) {
        // nothing to do
    }
}
