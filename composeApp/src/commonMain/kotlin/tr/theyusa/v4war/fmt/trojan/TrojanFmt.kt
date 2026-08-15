package tr.theyusa.v4war.fmt.trojan

import tr.theyusa.v4war.fmt.v2ray.parseDuckSoft
import tr.theyusa.v4war.ktx.parseBoolean
import tr.theyusa.v4war.ktx.queryParameterNotBlank
import tr.theyusa.v4war.libcore.Libcore

fun parseTrojan(link: String): TrojanBean {
    val url = Libcore.parseURL(link)
    return TrojanBean().apply {
        parseDuckSoft(url)
        allowInsecure = url.parseBoolean("allowInsecure")
        url.queryParameterNotBlank("peer")?.let {
            sni = it
        }
    }

}
