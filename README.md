# Meshenger

 Voice- and video calls without any server or Internet access. Simply scan each others QR-Code and call each other. This works in many local networks such as community mesh networks, company networks or at home.

Features:

- audio and video calls
- encrypted communication
- database backup and encryption
- add custom addresses to reach contacts

## Download

[<img src="docs/fdroid.png" alt="Get it on F-Droid" height="90">](https://f-droid.org/packages/d.d.meshenger/)
[<img src="docs/apk.png" alt="Get it on GitHub" height="90">](https://github.com/meshenger-app/meshenger-android/releases)

## Screenshots

<img src="docs/logo_2.0.0.png" width="170"> <img src="docs/hello_2.0.0.png" width="170"> <img src="docs/connected_2.0.0.png" width="170"> <img src="docs/qrcode_2.0.0.png" width="170"> <img src="docs/settings_3.0.0.png" width="170">

## Documentation

Meshenger exchanges the contact name, public key and MAC/IP/DNS address via QR-Code. By default, only a MAC address is transferred as address and then used to create an IPv6 link local address to connect to the contact. This does not even need a DHCP server. The exchanged public key is used to authenticate/encrypt signaling data to establish a [WebRTC](https://webrtc.org/) session that can transmit audio and video.

Details can be found in the [Documentation](docs/Documentation.md) or in the [FAQ](docs/faq.md).
