Meshenger Documentation
=======================

## Motivation

Currently, the market of VoIP software is dominated by software owned by big companies and tied to central infrastructures in order to monetize the platform and control the data flow. Individuals and community networks often try to establish decentralized networks, which provide a community infrastructure to provide Internet to areas not yet covered and to connect people with local pathways.

In short, Meshenger wants to put local and direct communication first.

## Finding Contacts

Meshenger uses primarily IPv6 addresses (fc00::/7) to establish direct connections to contacts. As part of the QR-code exchange, the MAC address of the WiFi adapter is exchanged. Other addresses such as hostnames can be configured manually as well.

From the contacts MAC address, IPv6 addresses are derived by looking at the own IPv6 addresses. If the own phone has addresses that contain the own MAC address, then these addresses are taken and the MAC substituded with the contacts MAC address. These generated addresses will be used to try to reach the contact.

All IPv6 capable devices have these addresses in the form of IPv6 link local addresses: e.g. `fe80:1122:33ff:fe44:5566` when the MAC is `11:22:33:44:55:66`. (Also a bit needs to be flipped, but we ignore that here for the sake of simplicity.)
Other types of IPv6 addresses of the same scheme ([EUI-64](https://de.wikipedia.org/wiki/EUI-64)) might also be present.

Instead of MAC addresses, IPv4/IPv6 addresses or DNS names can also be used as well. The only limitation is that both phones need to have a reachable IP address.

## WebRTC

Meshenger uses [WebRTC](https://webrtc.org/), a well-build, tested and mostly documented standard for video- and audio communication. It handles audio and video WebRTC also supports NAT traversal via ICE-Servers, but this feature has been turned off for Meshenger.

Resources:

- [Serverless WebRTC Android](https://github.com/wojta/serverless-webrtc-android)
- [Real time communication with Webrtc on Android](https://hackernoon.com/real-time-communication-with-webrtc-on-android-f96cdcfc4771)
- [Getting Started with WebRTC for Android](https://vivekc.xyz/getting-started-with-webrtc-for-android-daab1e268ff4)
- [WebRTC for Android](https://www.amryousef.me/android-webrtc)
- [Android WebRTC Tutorial](https://github.com/GleasonK/android-webrtc-tutorial)

## Crypto

For encryption and authentication, libsodium is used. (TODO)

## Development

Meshenger is Free and Open Source Software. Everybody can participate or even fork the software.

## Building From Sources

On Linux based systems:

```
./gradlew assembleRelease
```

Android Studio works as well.

### First Phase development

This project was sponsored by the [Google Summer of Code](https://summerofcode.withgoogle.com/) 2018 as part of the [Freifunk](https://freifunk.net) organization to make local community networks more attractive.

The development can be followed via the [Freifunk blog](https://blog.freifunk.net):

* [Initial Project Description](https://projects.freifunk.net/#/projects?project=local_phone_app&lang=en)
* [Meshenger Update 1](https://blog.freifunk.net/2018/06/10/meshenger-p2p-local-network-messenger-update-1/)
* [Meshenger Update 2](https://blog.freifunk.net/2018/07/07/meshenger-p2p-local-network-messenger-update-2/)
* [Meshenger final update](https://blog.freifunk.net/2018/08/14/meshenger-p2p-local-network-messenger-final-update/)

This phase was concluded with Meshenger 1.0.0.

### Second phase

The second phase was to implement authentication/encryption of the WebRTC handshake and database encryption.
Along with other usability features (e.g. backup), other contact addresses should be able to be used (e.g. hostnames, multicast groups).

This phase was started with Meshenger 2.0.0.
