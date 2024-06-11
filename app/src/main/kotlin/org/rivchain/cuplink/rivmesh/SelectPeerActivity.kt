package org.rivchain.cuplink.rivmesh

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hbb20.BuildConfig
import com.hbb20.CCPCountry
import com.vincentbrison.openlibraries.android.dualcache.Builder
import com.vincentbrison.openlibraries.android.dualcache.JsonSerializer
import com.vincentbrison.openlibraries.android.dualcache.SizeOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rivchain.cuplink.BaseActivity
import org.rivchain.cuplink.MainService
import org.rivchain.cuplink.rivmesh.models.PeerInfo
import org.rivchain.cuplink.rivmesh.models.Status
import org.rivchain.cuplink.rivmesh.util.Utils.deserializeStringList2PeerInfoSet
import org.rivchain.cuplink.rivmesh.util.Utils.ping
import org.rivchain.cuplink.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.lang.reflect.Type
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Locale
open class SelectPeerActivity : BaseActivity(), ServiceConnection {

    private var service: MainService? = null
    var peerListUrl = PEER_LIST_URL
    private var peerListPing = true

    companion object {
        const val PEER_LIST = "PEER_LIST"
        const val PEER_LIST_URL = "https://map.rivchain.org/rest/peers.json"
        const val CACHE_NAME = "PEER_LIST_CACHE"
        const val ONLINE_PEERINFO_LIST = "online_peer_info_list"
        const val OFFLINE_PEERINFO_LIST = "offline_peer_info_list"
        const val TEST_APP_VERSION = BuildConfig.VERSION_CODE
        const val RAM_MAX_SIZE = 100000
        const val DISK_MAX_SIZE = 100000
    }

    private fun downloadJson(link: String): String {
        URL(link).openStream().use { input ->
            val outStream = ByteArrayOutputStream()
            outStream.use { output ->
                input.copyTo(output)
            }
            return String(outStream.toByteArray(), Charset.forName("UTF-8"))
        }
    }

    protected open fun setAlreadySelectedPeers(alreadySelectedPeers: MutableSet<PeerInfo>){

    }

    protected open fun peersMap(peersMap: Map<String, Map<String, Status>>){

    }

    protected open fun addPeer(peerInfo: PeerInfo){

    }

    protected open fun addAlreadySelectedPeers(alreadySelectedPeers: ArrayList<PeerInfo>){

    }

    protected open fun saveSelectedPeers(selectedPeers: Set<PeerInfo>){
        service!!.getMesh().setPeers(selectedPeers)
        service!!.saveDatabase()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindService(Intent(this, MainService::class.java), this, 0)

        val preferences =
            PreferenceManager.getDefaultSharedPreferences(this.baseContext)
        val peerListUrl: String =
            preferences.getString(PEER_LIST, "")!!
        if(peerListUrl.isNotBlank()){
            this@SelectPeerActivity.peerListUrl = peerListUrl
        }
        val extras = intent.extras

        val alreadySelectedPeers = deserializeStringList2PeerInfoSet(extras!!.getStringArrayList(PEER_LIST)!!)
        setAlreadySelectedPeers(alreadySelectedPeers)
        val peerInfoListCache = Builder<List<PeerInfo>>(CACHE_NAME, TEST_APP_VERSION)
            .enableLog()
            .useReferenceInRam(RAM_MAX_SIZE, SizeOfPeerList())
            .useSerializerInDisk(
                DISK_MAX_SIZE, true,
                JsonSerializer(ArrayList<PeerInfo>().javaClass), baseContext
            ).build();

        GlobalScope.launch {
            try {
                for (pi in alreadySelectedPeers) {
                    val ping = ping(pi.hostName, pi.port)
                    pi.ping = ping
                }
                try {
                    val peerInfoCache = peerInfoListCache.get(ONLINE_PEERINFO_LIST)
                    if (peerInfoCache != null && peerInfoCache.isNotEmpty()) {
                        for (peerInfo in peerInfoCache) {
                            val ping = ping(peerInfo.hostName, peerInfo.port)
                            peerInfo.ping = ping
                            if (alreadySelectedPeers.contains(peerInfo)) {
                                continue
                            }
                            withContext(Dispatchers.Main) {
                                addPeer(peerInfo)
                            }
                        }
                    }
                    val json = downloadJson(this@SelectPeerActivity.peerListUrl)
                    val countries = CCPCountry.getLibraryMasterCountriesEnglish()
                    val mapType: Type = object :
                        TypeToken<Map<String?, Map<String, Status>>>() {}.type
                    val peersMap: Map<String, Map<String, Status>> = Gson().fromJson(json, mapType)
                    peersMap(peersMap)
                    val cachePeerInfoList = mutableListOf<PeerInfo>()
                    for ((country, peers) in peersMap.entries) {
                        for ((peer, status) in peers) {
                            if (status.up) {
                                for (ccp in countries) {
                                    if (ccp.name.lowercase(Locale.getDefault())
                                            .contains(country.replace(".md", "").replace("-", " "))
                                    ) {
                                        if(!peerListPing){
                                            return@launch
                                        }
                                        val url = URI(peer)
                                        try {
                                            val address =
                                                withContext(Dispatchers.IO) {
                                                    InetAddress.getByName(url.host)
                                                }
                                            val peerInfo =
                                                PeerInfo(
                                                    url.scheme,
                                                    address,
                                                    url.port,
                                                    ccp.nameCode,
                                                    false
                                                )
                                            val ping = ping(url.host, url.port)
                                            peerInfo.ping = ping
                                            if (alreadySelectedPeers.contains(peerInfo)) {
                                                continue
                                            }
                                            if (peerInfo.ping < Int.MAX_VALUE) {
                                                cachePeerInfoList.add(peerInfo)
                                            }
                                            withContext(Dispatchers.Main) {
                                                addPeer(peerInfo)
                                                if (cachePeerInfoList.size > 0) {
                                                    peerInfoListCache.put(
                                                        ONLINE_PEERINFO_LIST,
                                                        cachePeerInfoList.toList()
                                                    )
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    when (e) {
                        is FileNotFoundException, is UnknownHostException -> {
                            val onlinePeerInfoList = peerInfoListCache.get(ONLINE_PEERINFO_LIST)
                            if (onlinePeerInfoList != null) {
                                for (peerInfo in onlinePeerInfoList) {
                                    val ping = ping(peerInfo.hostName, peerInfo.port)
                                    peerInfo.ping = ping
                                    if (alreadySelectedPeers.contains(peerInfo)) {
                                        continue
                                    }
                                    withContext(Dispatchers.Main) {
                                        addPeer(peerInfo)
                                    }
                                }
                            }
                            e.printStackTrace()
                        }
                        else -> e.printStackTrace()
                    }
                }
                val currentPeers = ArrayList(alreadySelectedPeers.sortedWith(compareBy { it.ping }))
                withContext(Dispatchers.Main) {
                    addAlreadySelectedPeers(currentPeers)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    protected open fun cancelPeerListPing() {
        peerListPing = false
    }

    override fun onStop() {
        super.onStop()
        cancelPeerListPing()
    }

    override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
        Log.d(this, "onServiceConnected()")
        service = (iBinder as MainService.MainBinder).getService()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        //nothing_todo
    }

    override fun onServiceRestart(){

    }

}

class SizeOfPeerList: SizeOf<List<PeerInfo>> {

    override fun sizeOf(obj: List<PeerInfo>): Int{
        var size = 0
        for (o in obj) {
            size += o.hostName.length * 2
            size += o.schema.length * 2
            if (o.countryCode != null) {
                size += o.countryCode!!.length * 2
            }
            size += 4
            size += 4
            size += 1
        }
        return size
    }
}