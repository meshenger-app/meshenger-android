# Meshenger

Direct voice- and video phone calls. No need for accounts or access to the Internet. Just scan each others QR-Code that will contain the contacts IP address. This works at home or company networks but also in many off-the-grid networks such as community mesh networks.

Features:

- voice and video calls
- encrypted communication
- no accounts, no registration, no servers
- add custom addresses to reach contacts


Limitations:

- no calls through firewalls / NAT borders
- IP address change requires manual contact update

## Download

[<img src="docs/fdroid.png" alt="Get it on F-Droid" height="90">](https://f-droid.org/packages/d.d.meshenger/)
[<img src="docs/apk.png" alt="Get it on GitHub" height="90">](https://github.com/meshenger-app/meshenger-android/releases)
[<img src="docs/gplay.png" alt="Get it on Google Play" height="90">](https://play.google.com/store/apps/details?id=app.meshenger)

## Screenshots

<img src="graphical-assets/phone-screenshots/01_logo_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/02_contacts_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/03_ringing.png" width="170"> <img src="graphical-assets/phone-screenshots/04_call_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/05_qrcode_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/06_settings_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/07_address_management_4.4.5.png" width="170"> <img src="graphical-assets/phone-screenshots/08_history_4.4.5.png" width="170">

## Translations

Visit [weblate.org](https://hosted.weblate.org/engage/meshenger/) to contribute translations. An alternative is to directly translate the values in [strings.xml](https://github.com/meshenger-app/meshenger-android/blob/master/app/src/main/res/values/strings.xml) and then to create a pull request or send it via Email.

## Documentation

Meshenger connects to IP addresses in a true P2P fashion. Contacts are encoded in a text blob that can be exchanged via QR-Code, picture or copy&paste. They contain a name, a public key and a list of IP addresses or domain names. Also IPv6 link local addresses are supported, which would not even need a DHCP server. The exchanged public key is used to authenticate/encrypt signaling data to establish a [WebRTC](https://webrtc.org/) session that can transmit voice and video.

Details can be found in the [Documentation](docs/documentation.md) or in the [FAQ](docs/faq.md).

## License

GNU GENERAL PUBLIC LICENSE 3.0 or later, see [license text](LICENSE) or on [spdx.org](https://spdx.org/licenses/GPL-3.0-or-later.html).

## Similar Projects

This list only contains Open Source projects.

* [linphone](https://linphone.org/) - VoIP, SIP, Video telephone app.
* [Jami](https://jami.net/) - Multiplatform Chat and Video messenger, uses a DHT.
* [Briar](https://briarproject.org/) - Censorship-resistant P2P messaging. Connect via Bluetooth, Wi-Fi or Tor. Privacy orientated.
* [Qaul](https://qaul.net/) - Internet independent wireless mesh text messenger. Uses BLE, Wifi & Internet-overlay.
* [pion offline](https://github.com/pion/offline-browser-communication) - Offline WebRTC demo.
