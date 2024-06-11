package org.rivchain.cuplink.rivmesh.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.acra.ACRA
import org.rivchain.cuplink.rivmesh.models.DNSInfo
import org.rivchain.cuplink.rivmesh.models.Peer
import org.rivchain.cuplink.rivmesh.models.PeerInfo
import java.lang.reflect.Type
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URISyntaxException
import kotlin.random.Random

internal object Utils {

    fun generateRandomPort(): Int {
        // Define the range for allowed ports
        val minPort = 49152
        val maxPort = 65535

        // Generate a random port within the range
        return Random.nextInt(minPort, maxPort + 1)
    }

    fun deserializeStringList2PeerInfoSet(list: List<String>?): MutableSet<PeerInfo> {
        var gson = Gson()
        var out = mutableSetOf<PeerInfo>()
        if (list != null) {
            for(s in list) {
                out.add(gson.fromJson(s, PeerInfo::class.java))
            }
        }
        return out
    }

    fun deserializeStringList2DNSInfoSet(list: List<String>?): MutableSet<DNSInfo> {
        var gson = Gson()
        var out = mutableSetOf<DNSInfo>()
        if (list != null) {
            for(s in list) {
                out.add(gson.fromJson(s, DNSInfo::class.java))
            }
        }
        return out
    }

    fun deserializeStringSet2PeerInfoSet(list: Set<String>): MutableSet<PeerInfo> {
        var gson = Gson()
        var out = mutableSetOf<PeerInfo>()
        for(s in list) {
            out.add(gson.fromJson(s, PeerInfo::class.java))
        }
        return out
    }

    fun deserializeStringSet2DNSInfoSet(list: Set<String>): MutableSet<DNSInfo> {
        var gson = Gson()
        var out = mutableSetOf<DNSInfo>()
        for(s in list) {
            out.add(gson.fromJson(s, DNSInfo::class.java))
        }
        return out
    }

    fun serializePeerInfoSet2StringList(list: Set<PeerInfo>): ArrayList<String> {
        var gson = Gson()
        var out = ArrayList<String>()
        for(p in list) {
            out.add(gson.toJson(p))
        }
        return out
    }

    fun serializeDNSInfoSet2StringList(list: Set<DNSInfo>): ArrayList<String> {
        var gson = Gson()
        var out = ArrayList<String>()
        for(p in list) {
            out.add(gson.toJson(p))
        }
        return out
    }

    fun ping(hostname: String, port:Int): Int {
        val start = System.currentTimeMillis()
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(hostname, port), 5000)
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
            print(hostname)
            return Int.MAX_VALUE
        }
        return (System.currentTimeMillis() - start).toInt()
    }

    fun convertPeerInfoSet2PeerIdSet(list: Set<PeerInfo>): Set<String> {
        var out = mutableSetOf<String>()
        for(p in list) {
            out.add(p.toString())
        }
        return out
    }

    fun convertPeer2PeerStringList(list: List<Peer>): ArrayList<String> {
        var out = ArrayList<String>()
        var gson = Gson()
        for(p in list) {
            out.add(gson.toJson(p))
        }
        return out
    }

    fun deserializePeerString2PeerInfoSet(s: String): MutableSet<PeerInfo> {
        val gson = Gson()
        val listType: Type = object : TypeToken<ArrayList<Peer>>() {}.type
        ACRA.errorReporter.putCustomData("Peer list", s)
        val out = mutableSetOf<PeerInfo>()
        val peers: List<Peer> = gson.fromJson(s, listType)
        for (p in peers) {
            val fixedUrlString = if (p.remote.indexOf('%') > 0 && p.remote.indexOf(']') > 0) {
                val fixWlanPart =
                    p.remote.substring(p.remote.indexOf('%'), p.remote.indexOf(']'))
                p.remote.replace(fixWlanPart, "")
            } else {
                p.remote
            }
            try {
                val url = URI(fixedUrlString)
                out.add(
                    PeerInfo(
                        url.scheme,
                        InetAddress.getByName(url.host),
                        url.port,
                        p.country_short,
                        p.multicast
                    )
                )
            } catch (ex: URISyntaxException) {
                //skip peer when Remote URL invalid:
                //see https://github.com/yggdrasil-network/yggdrasil-go/issues/973
                ex.printStackTrace()
            }
        }
        return out
    }
}
