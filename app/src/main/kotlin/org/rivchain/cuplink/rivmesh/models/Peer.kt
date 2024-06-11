package org.rivchain.cuplink.rivmesh.models

data class Peer (
    //Example [{"Key":"JQZIX3KIamcp/6S9rycKiAGyg9MK7U6h8UUY5ej36fY=","Root":"AAABERGfllXfKNJshDs/8uzKEIFkFEccE16dmZV/cAo=","Coords":[2,4],"Port":1,"Remote":"tcp://[fe80::5207:4518:4378:7f1%wlan0]:57541","IP":"202:d7cd:bd04:6bbc:acc6:b002:da12:86c7"},{"Key":"DCNBiKAV1xr72JAFUgNrOYfY6Qm/f0Nq6ESZTSLn1eo=","Root":"AAABERGfllXfKNJshDs/8uzKEIFkFEccE16dmZV/cAo=","Coords":[2,4,1],"Port":2,"Remote":"tcp://[fe80::1c39:839:90a5:6ef%wlan0]:1108","IP":"204:7b97:ceeb:fd45:1ca0:84ed:ff55:bf92"}]
    var address : String,
    var root : String,
    var key : String,
    var priority : Int,
    var coords : Array<Int>,
    var port : Int,
    var remote : String,
    var remote_ip : String,
    var bytes_recvd : Long,
    var bytes_sent : Long,
    var uptime : Float,
    var multicast: Boolean,
    var country_short: String,
    var country_long: String,
)