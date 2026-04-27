package tr.theyusa.v4war.fmt

import tr.theyusa.v4war.database.ProxyEntity
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.ktx.b64Decode
import tr.theyusa.v4war.ktx.b64EncodeUrlSafe
import tr.theyusa.v4war.ktx.zlibCompress
import tr.theyusa.v4war.ktx.zlibDecompress

fun parseUniversal(link: String): AbstractBean {
    return if (link.contains("?")) {
        val type = link.substringAfter("v4war://").substringBefore("?")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter("?").b64Decode().zlibDecompress())
        }.requireBean()
    } else {
        val type = link.substringAfter("v4war://").substringBefore(":")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter(":").substringAfter(":").b64Decode())
        }.requireBean()
    }
}

fun AbstractBean.toUniversalLink(): String {
    var link = "v4war://"
    link += TypeMap.reversed[ProxyEntity().putBean(this).type]
    link += "?"
    link += KryoConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    return link
}


fun ProxyGroup.toUniversalLink(): String {
    var link = "v4war://subscription?"
    export = true
    link += KryoConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    export = false
    return link
}
