package tr.theyusa.v4war.group

import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.GroupManager
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.database.SubscriptionBean
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.hysteria.parseHysteria1Json
import tr.theyusa.v4war.fmt.parseOutbound
import tr.theyusa.v4war.fmt.shadowsocks.parseShadowsocks
import tr.theyusa.v4war.fmt.v2ray.StandardV2RayBean
import tr.theyusa.v4war.ktx.JSONMap
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.SubscriptionFoundException
import tr.theyusa.v4war.ktx.applyDefaultValues
import tr.theyusa.v4war.ktx.b64DecodeToString
import tr.theyusa.v4war.ktx.generateUserAgent
import tr.theyusa.v4war.ktx.isIpAddress
import tr.theyusa.v4war.ktx.kxs
import tr.theyusa.v4war.ktx.parseProxies
import tr.theyusa.v4war.ktx.toJsonMapKxs
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.no_proxies_found
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")
object RawUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {

        var proxies: List<AbstractBean>
        if (subscription.link.startsWith("content://")) {
            val contentText = readContentUri(subscription.link)

            proxies = contentText?.let { parseRaw(contentText) }
                ?: errNotFound()
        } else {

            val response = Libcore.newHttpClient().apply {
                if (DataStore.serviceState.started) {
                    useSocks5(
                        DataStore.mixedPort,
                        DataStore.inboundUsername,
                        DataStore.inboundPassword,
                    )
                }
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()
            proxies = parseRaw(response.contentString) ?: errNotFound()

            // https://github.com/crossutility/Quantumult/blob/master/extra-subscription-feature.md
            // Subscription-Userinfo: upload=2375927198; download=12983696043; total=1099511627776; expire=1862111613
            // Be careful that some value may be empty.
            val userInfo = response.getHeader("Subscription-Userinfo")
            if (userInfo.isNotBlank()) {
                var used = 0L
                var total = 0L
                var expired = 0L
                for (info in userInfo.split(";")) {
                    info.split("=", limit = 2).let {
                        if (it.size != 2) return@let
                        val key = it[0].trim()
                        val value = it[1].trim().toLongOrNull() ?: 0
                        when (key) {
                            "upload", "download" -> used += value
                            "total" -> total = value
                            "expire" -> expired = value
                        }
                    }
                }
                subscription.apply {
                    bytesUsed = used
                    bytesRemaining = total - used
                    expiryDate = expired
                }
            }
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        val trimmed = text.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val element = kxs.parseToJsonElement(text)
                if (element is JsonPrimitive) error("unexpected JSON primitive")
                parseJSON(element).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        if (!text.contains("://")) {
            try {
                parseProxies(text.b64DecodeToString()).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            parseProxies(text).takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (e: Exception) {
            Logs.w(e)
        }

        return null
    }

    fun parseJSON(element: JsonElement): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (element is JsonObject) {
            val json = element.toJsonMapKxs()
            when {
                "outbounds" in json || "endpoints" in json -> {
                    val outbounds = json["outbounds"] as? List<*>
                    val endpoints = json["endpoints"] as? List<*>
                    var length = outbounds?.size ?: 0
                    endpoints?.size?.let { length += it }
                    if (length == 0) {
                        errNotFound<Unit>()
                    }

                    fun add(outbound: Any?) {
                        val map = outbound as? JSONMap ?: return
                        parseOutbound(map)?.let {
                            proxies.add(it)
                        }
                    }
                    outbounds?.forEach { outbound ->
                        try {
                            add(outbound)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                    endpoints?.forEach { endpoint ->
                        try {
                            add(endpoint)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                }

                "server" in json && ("server_port" in json || "server_ports" in json) -> {
                    return parseOutbound(json)?.let {
                        listOf(it)
                    } ?: errNotFound()
                }

                "peers" in json -> return parseOutbound(json)?.let {
                    listOf(it)
                } ?: errNotFound()

                "server" in json && ("up" in json || "up_mbps" in json) -> {
                    return listOf(json.parseHysteria1Json())
                }

                "method" in json -> {
                    return listOf(json.parseShadowsocks())
                }

                "version" in json && "servers" in json -> {
                    val servers = json["servers"] as? List<*>
                    servers?.forEach {
                        val server = it as? JSONMap ?: return@forEach
                        proxies.add(server.parseShadowsocks())
                    }
                }

                else -> {
                    errNotFound()
                }
            }
        } else if (element is JsonArray) {
            for (item in element) {
                if (item is JsonObject) {
                    proxies.addAll(parseJSON(item))
                }
            }
        }

        proxies.forEach {
            it.initializeDefaultValues()
            if (it is StandardV2RayBean) {
                if (it.isTLS && it.sni.isBlank() && it.host.isNotBlank() && !it.host.isIpAddress()) {
                    it.sni = it.host
                }
            }
        }
        return proxies
    }

    private inline fun <reified T> errNotFound(): T = runBlocking {
        error(resolveRepository().getString(Res.string.no_proxies_found))
    }
}
