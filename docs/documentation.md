Meshenger Documentation
=======================

## Motivation

Currently, the market of VoIP software is dominated by software owned by big companies and it is tied to central infrastructure in order to monetize the platform and control the data flow. Various communities (e.g. [Freifunk](https://freifunk.net/), [Yggdrasil](https://yggdrasil-network.github.io/), ..) try to establish decentralized networks, to connect people with local pathways even if there is no Internet. Common VoIP software usually does not work there. That is where Meshenger can serve as a "killer" App.

## Finding Contacts

Meshenger has no discovery mechanism by design. Contacts are shared via QR-Code or JSON text blob.

## How It Connects

Connections are established via the addresses that are part of a contact. by default this is only the generated MAC address of the WiFi adapter. The MAC address will be used to generate IPv6 link local and other addresses depending on the current IPv6 prefixes. The addresses in the contact can also be list of IPv4/IPv6 addresses or even domain names. This can be configured manually in the settings.

## Randomised MAC Addresses

By default, Meshenger puts the MAC address of the WiFi interface in the QR-code that others will use to call. But if the MAC address changes, other Meshenger instances won't be able to reach the local Meshenger instance. This behavior is defined by internal settings and settings available to the user:

<img src="mac-randomization-menu.png" height="200">

For more information see the Android documentation on [MAC Randomization Behavior](https://source.android.com/docs/core/connect/wifi-mac-randomization-behavior).

## WebRTC

Meshenger uses [WebRTC](https://webrtc.org/), a well-build, tested and mostly documented standard for video- and audio communication. It handles audio and video WebRTC also supports NAT traversal via ICE-Servers, but this feature has been turned off for Meshenger, as there is no use.

Resources:

- [Serverless WebRTC Android](https://github.com/wojta/serverless-webrtc-android)
- [Real time communication with Webrtc on Android](https://hackernoon.com/real-time-communication-with-webrtc-on-android-f96cdcfc4771)
- [Getting Started with WebRTC for Android](https://vivekc.xyz/getting-started-with-webrtc-for-android-daab1e268ff4)
- [WebRTC for Android](https://www.amryousef.me/android-webrtc)
- [Android WebRTC Tutorial](https://github.com/GleasonK/android-webrtc-tutorial)

## Crypto

[libsodium](https://github.com/jedisct1/libsodium) is used for the encryption and authentication of the initial TCP/IP connection (called signalling). After that, WebRTC is establishing a UDP connection for Audio/Video data. WebRTC uses it's own encryption.

### Database

The database contains the settings and contacts. It is stored in the internal/private file store of the app. If no password is used, the database is stored in plain text.

If a password is used, it will be salted and hashed using `libsodium::crypto_pwhash`. The hash is used as key for `libsodium::crypto_secretbox_open_easy` along with a nonce. The salt and nonce are stored along with the database and changed every time the database is stored.

The database file is prefixed with a four byte version header. Currently, it is set to zero.

### Calls

Contacts identities are based on public/secret keys (ed25519). These are used to sign the WebRTC SDP offers using `libsodium::crypto_sign` and are then encrypted using `libsodium.crypto_box_seal` (X25519, XSalsa20-Poly1305) with the recipients public key (curve25519) derived from the identity key.

Every packet is prefixed with a four byte header. Currently it only contains the packet length.

The WebRTC connection itself uses its own crypto scheme.

## Development

Meshenger is Free and Open Source Software. Everybody can participate or even fork the software.

### Building From Sources

On Linux based systems:

```
./gradlew assembleRelease
```

Android Studio works as well.

### First Phase

This project was sponsored by the [Google Summer of Code](https://summerofcode.withgoogle.com/) 2018 as part of the [Freifunk](https://freifunk.net) organization to make local community networks more attractive.

The development can be followed via the [Freifunk blog](https://blog.freifunk.net):

* [Initial Project Description](https://projects.freifunk.net/#/projects?project=local_phone_app&lang=en)
* [Meshenger Update 1](https://blog.freifunk.net/2018/06/10/meshenger-p2p-local-network-messenger-update-1/)
* [Meshenger Update 2](https://blog.freifunk.net/2018/07/07/meshenger-p2p-local-network-messenger-update-2/)
* [Meshenger final update](https://blog.freifunk.net/2018/08/14/meshenger-p2p-local-network-messenger-final-update/)

This phase was concluded with Meshenger 1.0.0.

### Second Phase

The second phase was to implement authentication/encryption of the WebRTC handshake and database encryption.
Along with other usability features (e.g. backup), other contact addresses should be able to be used (e.g. hostnames, multicast groups).

This phase was concluded with Meshenger 2.0.0.

### Third Phase

The third phase added better encryption, a call history and (re-) added unknown callers.

This phase was concluded with Meshenger 3.0.0.

### Fourth Phase

This phase focuses on bug fixing, stabilization, usability improvements and some features.

This phase is concluded with the Meshenger 4.0.0 and further releases. Now an app with the intented features exists, which we hope serves as a use case for mesh networks without Internet and will help those in need.
