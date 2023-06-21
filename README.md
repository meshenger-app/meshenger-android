# CupLink

Voice- and video calls without any server or Internet access. Simply scan each others QR-Code and call each other. This works in many local networks such as community mesh networks, company networks or at home.

Features:

- audio and video calls through IPv6 RiV-mesh network
- encrypted communication
- database backup

## Documentation

CupLink exchanges the contact name and IP address via QR-Code. An IP address is sufficient to connect to clients. This does not even need a DHCP server. 

Details can be found in the [Documentation](docs/Documentation.md) or in the [FAQ](docs/faq.md).

## Build

Starting from version 0.4.6.x WebRTC library should be pre-built and places into app/libs directory. Corresponding instruction on how to build it can be found [here](https://dev.to/ethand91/webrtc-for-beginners-part-55-building-the-webrtc-android-library-e8l). Also 0.4.6.x migrated to [Unified Plan](https://www.callstats.io/blog/what-is-unified-plan-and-how-will-it-affect-your-webrtc-development).
