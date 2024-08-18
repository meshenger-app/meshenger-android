Meshenger Documentation
=======================

## Motivation

Currently, the market of VoIP software is dominated by software owned by big companies and tied to central infrastructure and the Internet in order to monetize the platform and control the data flow. On the other side there a various communities (e.g. [Freifunk](https://freifunk.net/), [Yggdrasil](https://yggdrasil-network.github.io/), ..) try to establish various kinds of decentralized networks to connect people with alternative pathways even without Internet. Common VoIP software usually does not work there. This is where Meshenger can serve as a use case or even "killer" App for these networks.

## Finding Contacts

Meshenger has no discovery mechanism by design. Contacts are shared via QR-Code or text blob in JSON.

## How It Connects

Connections are established via the addresses that are part of a contact. By default this is the link local address of the WiFi adapter. But the addresses in the contact can also contain other IPv4/IPv6 addresses or even domain names. This can be configured manually in the settings.

If any of the contacts IPv6 address and any of the own phones IPv6 address contains a MAC address, then Meshenger will also try to transplant the MAC address from one address to another to generate more possible callee addresses. All taraget addresses are tried on after another to contact the callee.

## Communication Setup

A call first starts on the caller side by creating a WebRTC offer. This a string that contains what video/audio codecs the phone supports and what IP addresses it has (ICE candidates). The offer is send via an initial TCP/IP connection to the callee by trying several IP addresses one ofter another.

This initial connection is encrypted with the public key of the callee and signed by the private key of the caller.

When the offer is received on the callee side, it is feed to WebRTC which then establishes a UDP connection to the caller using the IP address and encryption key in the WebRTC offer. The initial connection will then be closed and the call connection is established.

## Audio+Video / WebRTC

Meshenger [WebRTC](https://webrtc.org/), a well known library for video- and audio communication. It handles audio and video WebRTC also supports NAT traversal via ICE-Servers, but this feature has been turned off for Meshenger, because it is not needed. The special build of WebRTC adds a few patches from the Threema messenger and one own patch. See [here](/webrtc/README.md).

- [Serverless WebRTC Android](https://github.com/wojta/serverless-webrtc-android) demo
- [Real time communication with WebRTC on Android](https://hackernoon.com/real-time-communication-with-webrtc-on-android-f96cdcfc4771) documentation
- [Getting Started with WebRTC for Android](https://vivekc.xyz/getting-started-with-webrtc-for-android-daab1e268ff4) blog post
- [WebRTC for Android](https://www.amryousef.me/android-webrtc) blog post
- [Android WebRTC](https://github.com/GleasonK/android-webrtc-tutorial) tutorial

## Crypto / libsodium

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
