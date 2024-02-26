# Frequently Asked Questions

* **How to find new contacts?**  
    You can find contacts in real life. Meshenger has no discovery mechanism by design. That would also require broadcast/multicast packets, which are usually blocked in mesh networks that Meshenger has a primary target area.
* **Why can I not connect to a contact?**  
    The contact is either on a different LAN or the address (e.g. MAC address) of the device has changed. In that case update the contact. The devices MAC address may change due to Androids MAC address randomization. It changes the MAC address on each reconnect or for each WLAN network. You can check in Meshengers address management settings if the used address is still present on the system. MAC address randomization can be configured, but it depends on the Android version how to do it.
* **Is Meshenger stable?**  
    Not really. There are crashes. It is beta software.
* **Why is the requested resolution/framerate not used**  
    One reason is that the hardware is not capabale of the requested resolution/framerate. Another reason is CPU usage. You can ignore the CPU overuse detection in the settings. This should help.
* **What about SIP?**  
    SIP can also call IP addresses. But most SIP Apps are not geared towards that feature. Addresses need to be entered manually, there is no QR-Code support, no key support. Meshenger could have been build on top of a SIP stack, but this just did not happen.
* **Are calls over Layer 3 networks possible?**  
    Yes, Meshenger does not only use link local address, but also other kinds of IP addresses that are either configured or can be created from a MAC address.
* **How much bandwidth does an Audio/Video call consume?**  
    Audio calls take 12KB/s, Video adds ~40KB/s per direction.
* **Why can't you call a thethered/hotspot phone as client?**  
    Simply put, this does not work reliably. It is probably an Android internal issue. The initial TCP/IP connection works, but then UDP packets for audio/video cannot cross.
* **Why create a new phone App - there are so many!**  
    Most Apps do not work without Internet or when very limited broadcast/multicast communication is allowed. This project intends to motivate other projects to support this. It is good that this kind of App exists.
* **Can I call contacts over the Internet?**  
    In allmost all cases you can not. While Meshenger simply connects to IP addresses, most contacts can only be reached by local IP addresses that are not accessible over the Internet due to Firewalls and [NAT](https://en.wikipedia.org/wiki/Network_address_translation)
* **Meshenger does not mesh, nor does it send messages. Why the name?**  
    That's true. But Meshenger is meant to be a "Killer-App" for mesh networks. Voice/Video is kind of a message. Also, we are not very creative with names.
