package org.rivchain.cuplink.rivmesh.models

import android.content.Context
import com.hbb20.CCPCountry
import com.hbb20.CountryCodePicker
import java.net.InetAddress


class DNSInfo {

    constructor(address: InetAddress, countryCode: String, description: String){
        this.address = address
        this.countryCode = countryCode
        this.description = description
    }

    var address: InetAddress
    var countryCode: String
    var description: String
    var ping: Int = Int.MAX_VALUE

    override fun toString(): String {
        return "[" + address.toString().substring(1) + "]"
    }

    override fun equals(other: Any?): Boolean {
        return toString() == other.toString()
    }

    fun getCountry(context: Context): CCPCountry? {
        return CCPCountry.getCountryForNameCodeFromLibraryMasterList(context, CountryCodePicker.Language.ENGLISH, countryCode)
    }

}