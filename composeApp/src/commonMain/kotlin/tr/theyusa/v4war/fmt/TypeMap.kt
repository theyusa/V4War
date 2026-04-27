package tr.theyusa.v4war.fmt

import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.ktx.reverse

object TypeMap : HashMap<String, Int>() {
    init {
        this["socks"] = ProxyEntity.TYPE_SOCKS
        this["ss"] = ProxyEntity.TYPE_SS
        this["vmess"] = ProxyEntity.TYPE_VMESS
        this["trojan"] = ProxyEntity.TYPE_TROJAN
        this["hysteria"] = ProxyEntity.TYPE_HYSTERIA
        this["ssh"] = ProxyEntity.TYPE_SSH
        this["tuic"] = ProxyEntity.TYPE_TUIC
        this["config"] = ProxyEntity.TYPE_CONFIG
    }

    val reversed = reverse()

}