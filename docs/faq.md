# Frequently Asked Questions

* **How to find new contacts?**  
    You can find contacts in real life. Meshenger has no discovery mechanism by design. That would also require broadcast/multicast packets, which are usually blocked in mesh networks that Meshenger has a primary target area.
* **Is Meshenger stable**  
    Not really. There are crashes. It is beta software.
* **Are calls over Layer 3 networks possible?**  
    Yes, Meshenger does not only use link local address, but also other kinds of IP addresses that are either configured or can be created from a MAC address.
* **How much bandwidth does an Audio/Video call consume?**  
    Audio calls take 12KB/s, Video adds ~40KB/s per direction.
* **Why can't you call a thethered phone as client?**  
    IPv6 link local addresses seem to be blocked by Android in this kind of setup. Normal IPv4 addresses will work, but they might change on a whim at which point a connection cannot be established any more.
* **Why create a new phone App - there are so many!**  
    Most Apps do not work without Internet or when very limited broadcast/multicast communication is allowed. This project intends to motivate other projects to support this. It is good that this kind of App exists.
* **Can I call contacts over the Internet?**  
    In allmost all cases you can not. While Meshenger simply connects to IP addresses, most contacts can only be reached by local IP addresses that are not accessible over the Internet due to Firewalls and [NAT](https://en.wikipedia.org/wiki/Network_address_translation)
* **Meshenger does not mesh, nor does it send messages. Why the name?**  
    That's true. But Meshenger is meant to be a "Killer-App" for mesh networks. Voice/Video is kind of a message. Also, we are not very creative with names.
