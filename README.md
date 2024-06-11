# CupLink

Voice and video calls without any server or Internet access. Simply scan each other's QR code and call each other. This works in many local networks such as community mesh networks, company networks, or at home. By default, CupLink uses the RiV mesh network for seamless connectivity, but it can also operate independently.

## Features

- Voice and video calls
- No accounts or registration required
- Encrypted communication
- Database backup and encryption
- Ability to add custom addresses to reach contacts

## Documentation

CupLink exchanges the contact name and IP address via QR code. An IP address is sufficient to connect to clients, and there is no need for a DHCP server. CupLink utilizes RiV Mesh virtual static IPv6 addresses to establish connections.

For comprehensive details, please refer to the [Documentation](docs/Documentation.md) or the [FAQ](docs/faq.md).

## Build Instructions

Starting from version 0.4.6.x, the WebRTC library must be pre-built and placed into the `app/libs` directory. Instructions for building the library can be found [here](https://dev.to/ethand91/webrtc-for-beginners-part-55-building-the-webrtc-android-library-e8l). Additionally, version 0.4.6.x has migrated to the [Unified Plan](https://www.callstats.io/blog/what-is-unified-plan-and-how-will-it-affect-your-webrtc-development).