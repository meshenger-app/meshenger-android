Meshenger Documentation
=======================

## Motivation

Currently, the market of VoIP software is dominated by software owned by big companies and tied to central infrastructures in order to monetize the platform and control the data flow. Individuals and community networks often try to establish decentralized networks, which provide a community infrastructure to provide Internet to areas not yet covered and to connect people with local pathways.

In short, Meshenger wants to put local and direct communication first.

## Finding Contacts

Meshenger uses primarily IPv6 addresses (fc00::/7) to establish direct connections to contacts. As part of the QR-code exchange, the MAC address of the WiFi adapter is exchanged. Other addresses such as hostnames can be configured manually as well.

From the contacts MAC address, IPv6 addresses are derived by looking at the own IPv6 addresses. If the own phone has addresses that contain the own MAC address, then these addresses are taken and the MAC substituded with the contacts MAC address. These generated addresses will be used to try to reach the contact.

All IPv6 capable devices have these addresses in the form of IPv6 link local addresses: e.g. `fe80:1122:33ff:fe44:5566` when the MAC is `11:22:33:44:55:66`. (Also, a bit needs to be flipped, but we ignore that here for the sake of simplicity.)
Other types of IPv6 addresses of the same scheme ([EUI-64](https://de.wikipedia.org/wiki/EUI-64)) might also be present.

Instead of MAC addresses, IPv4/IPv6 addresses or DNS names (e.g. `hostname.local`) can also be used as well. The only limitation is that both phones need to have a reachable IP address.

## Randomised MAC Addresses

Most Android version these days try to prevent identification of phones by changing the MAC address. The MAC address might be randomly initialised different for each WiFi network, change on every connect or after some timeout (IPv6 Privacy Extensions). This is a major drawback for Meshenger, since connections cannot be established anymore after the address changes. If this is an issue for you, then you need to try to disable MAC address randomisation for a paritcular network. The Adnroid Developer Settings let you disable it, but this might be not what you want.

## WebRTC

Meshenger uses [WebRTC](https://webrtc.org/), a well-build, tested and mostly documented standard for video- and audio communication. It handles audio and video WebRTC also supports NAT traversal via ICE-Servers, but this feature has been turned off for Meshenger.

Resources:

- [Serverless WebRTC Android](https://github.com/wojta/serverless-webrtc-android)
- [Real time communication with Webrtc on Android](https://hackernoon.com/real-time-communication-with-webrtc-on-android-f96cdcfc4771)
- [Getting Started with WebRTC for Android](https://vivekc.xyz/getting-started-with-webrtc-for-android-daab1e268ff4)
- [WebRTC for Android](https://www.amryousef.me/android-webrtc)
- [Android WebRTC Tutorial](https://github.com/GleasonK/android-webrtc-tutorial)

## Crypto

[libsodium](https://github.com/jedisct1/libsodium) is used for the encryption and authentication of the initial connection. After that, WebRTC is used. WebRTC uses it's own encryption.

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

This phase focuses on bug fixing, stabilization and adding support for multicast addresses to allow calls into different networks.

## Source Code

StartActivity.java (starts first)
 - show splash screen
 - start MainService
   - runs in background
 - check if database is loaded, username set, key pair generated
 - start MainActivity
   - shows contact list and event list

MainActivity.java
 - displays ContactListFragment and EventListFragment

MainService:
 - listen for incoming calles in a thread
 - ping contacts on request
 - holds database instance
 - provides MainBinder class to access database

 MainService:
 - listens for incoming connections (calls and pings) in thread
 - ping contacts on request
 - allows access to the database (settings and contact list) via class MainBinder

Incoming call:
1. MainService.handleClient() starts CallActivity Intent with Contact object as argument ("ACTION_INCOMING_CALL")

Outgoing call:
1. ContactListFragment or EventListFragment starts CallActivity Intent with Contact object as argument ("ACTION_OUTGOING_CALL")
2. In CallActivity, RTCCall.startCall() is called
