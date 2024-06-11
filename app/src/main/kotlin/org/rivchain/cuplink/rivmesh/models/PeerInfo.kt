package org.rivchain.cuplink.rivmesh.models

import android.content.Context
import com.hbb20.CCPCountry
import com.hbb20.CountryCodePicker
import java.net.InetAddress

class PeerInfo {

    constructor()

    constructor(schema: String, address: InetAddress, port: Int, countryCode: String?, isMeshPeer: Boolean){
        this.schema = schema
        val a = address.toString();
        if(a.lastIndexOf('/')>0){
            this.hostName = a.split("/")[0]
        } else {
            this.hostName = a.substring(1)
        }
        this.port = port
        this.countryCode = countryCode
        this.isMeshPeer = isMeshPeer
    }

    lateinit var schema: String
    lateinit var hostName: String
    var port = 0
    var countryCode: String?=null
    var ping: Int = Int.MAX_VALUE
    var isMeshPeer = false

    override fun toString(): String {
        return if(this.hostName.contains(":")) {
            this.schema + "://[" + this.hostName + "]:" + port
        } else {
            this.schema + "://" + this.hostName + ":" + port
        }
    }

    override fun equals(other: Any?): Boolean {
        return toString() == other.toString()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    fun getCountry(context: Context): CCPCountry? {
        return if(countryCode == null){
            null
        } else {
            CCPCountry.getCountryForNameCodeFromLibraryMasterList(
                context,
                CountryCodePicker.Language.ENGLISH,
                countryCode
            )
        }
    }
}