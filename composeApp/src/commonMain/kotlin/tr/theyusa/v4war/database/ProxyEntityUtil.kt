package tr.theyusa.v4war.database

import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_CHAIN
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_CONFIG
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_HYSTERIA
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_PROXY_SET
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_SOCKS
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_SS
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_SSH
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_TROJAN
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_TUIC
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_VLESS
import tr.theyusa.v4war.database.ProxyEntity.Companion.TYPE_VMESS
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.*
import kotlinx.coroutines.runBlocking

fun ProxyEntity.displayType(): String = when (type) {
    TYPE_SOCKS -> socksBean!!.protocolName()
    TYPE_SS -> "Shadowsocks"
    TYPE_VMESS -> "VMess"
    TYPE_VLESS -> "VLESS"
    TYPE_TROJAN -> "Trojan"
    TYPE_HYSTERIA -> "Hysteria" + hysteriaBean!!.protocolVersion
    TYPE_SSH -> "SSH"
    TYPE_TUIC -> "TUIC"
    TYPE_PROXY_SET -> proxySetBean!!.displayType()
    TYPE_CHAIN -> runBlocking {
        resolveRepository().getString(Res.string.proxy_chain)
    }
    TYPE_CONFIG -> configBean!!.displayType()
    else -> "Undefined type $type"
}
