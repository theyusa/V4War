package fr.husi.database

import fr.husi.database.ProxyEntity.Companion.TYPE_CHAIN
import fr.husi.database.ProxyEntity.Companion.TYPE_CONFIG
import fr.husi.database.ProxyEntity.Companion.TYPE_HYSTERIA
import fr.husi.database.ProxyEntity.Companion.TYPE_PROXY_SET
import fr.husi.database.ProxyEntity.Companion.TYPE_SOCKS
import fr.husi.database.ProxyEntity.Companion.TYPE_SS
import fr.husi.database.ProxyEntity.Companion.TYPE_SSH
import fr.husi.database.ProxyEntity.Companion.TYPE_TROJAN
import fr.husi.database.ProxyEntity.Companion.TYPE_TUIC
import fr.husi.database.ProxyEntity.Companion.TYPE_VLESS
import fr.husi.database.ProxyEntity.Companion.TYPE_VMESS
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
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
