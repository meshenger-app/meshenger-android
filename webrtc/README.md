# WebRTC for Meshenger

For Meshenger to work for tethered network interfaces (Android Hotspot or USB LAN dongle) as well, we need a patched WebRTC.
Let's use the Threema messenger build setup for WebRTC:

* https://github.com/threema-ch/webrtc-build-docker (Threema build environment)
* https://github.com/threema-ch/webrtc-android (Threema own builds)

And then add [detect-tethered-networks-on-android.patch](detect-tethered-networks-on-android.patch) on top ([source](https://groups.google.com/g/discuss-webrtc/c/pTWe0QLKNC4/m/_ulh_2NJBwAJ)).

Build instructions for WebRTC v123.0.0:
```
git clone  https://github.com/threema-ch/webrtc-build-docker.git
cd webrtc-build-docker
git reset --hard 967efaeeb387ebdbf424a56a0a7bb1ab9ecccea6
cp ~/meshenger-android/webrtc/detect-tethered-networks-on-android.patch patches/
./cli.sh build-tools
./cli.sh fetch 41b1493ddb5d98e9125d5cb002fd57ce76ebd8a7
./cli.sh patch
docker run -it -v ${PWD}/webrtc:/webrtc threema/webrtc-build-tools:latest bash -c "
                set -euo pipefail
                cd src && ./tools_webrtc/android/build_aar.py && cd -
            "
cp webrtc/src/libwebrtc.aar ~/meshenger-android/webrtc/libwebrtc-123.0.0.aar
```
