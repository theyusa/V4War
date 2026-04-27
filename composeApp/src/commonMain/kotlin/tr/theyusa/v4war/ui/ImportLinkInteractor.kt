package tr.theyusa.v4war.ui

import tr.theyusa.v4war.GroupType
import tr.theyusa.v4war.SubscriptionType
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.GroupManager
import tr.theyusa.v4war.database.ProfileManager
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.database.SubscriptionBean
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.KryoConverters
import tr.theyusa.v4war.group.GroupUpdater
import tr.theyusa.v4war.ktx.b64Decode
import tr.theyusa.v4war.ktx.blankAsNull
import tr.theyusa.v4war.ktx.defaultOr
import tr.theyusa.v4war.ktx.parseProxies
import tr.theyusa.v4war.ktx.zlibDecompress
import tr.theyusa.v4war.libcore.Libcore

sealed interface ImportLinkPreview {
    object Ignore : ImportLinkPreview
    class Subscription(val group: ProxyGroup) : ImportLinkPreview
    class Profiles(val proxies: List<AbstractBean>) : ImportLinkPreview
}

class ImportLinkInteractor {

    suspend fun parseUri(uri: String): ImportLinkPreview {
        return if (uri.startsWith("sing-box://") || uri.startsWith("v4war://subscription")) {
            val group = parseSubscription(uri)
            if (group == null) ImportLinkPreview.Ignore else ImportLinkPreview.Subscription(group)
        } else {
            ImportLinkPreview.Profiles(parseProfiles(uri))
        }
    }

    fun parseSubscription(uri: String): ProxyGroup? {
        val urlForQuery = Libcore.parseURL(uri)
        val group: ProxyGroup
        val url = defaultOr(
            "",
            { urlForQuery.queryParameter("url") },
            {
                when (urlForQuery.scheme) {
                    "http", "https" -> uri
                    else -> null
                }
            },
        )
        if (url.isNotBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            group.subscription = SubscriptionBean().apply {
                // cleartext format
                link = url
                type = when (urlForQuery.queryParameter("type")?.lowercase()) {
                    "oocv1" -> SubscriptionType.OOCv1
                    "sip008" -> SubscriptionType.SIP008
                    else -> SubscriptionType.RAW
                }
            }

            group.name = defaultOr(
                "",
                { urlForQuery.queryParameter("name") },
                { urlForQuery.fragment },
            )
        } else {
            val data =
                uri.substringAfter('?', "").substringBefore('#').blankAsNull() ?: return null
            group = KryoConverters.deserialize(
                ProxyGroup().apply { export = true },
                data.b64Decode().zlibDecompress(),
            ).apply {
                export = false
            }
        }

        if (group.name.isNullOrBlank() && group.subscription?.link.isNullOrBlank() && group.subscription?.token.isNullOrBlank()) {
            return null
        }
        group.name = group.name.blankAsNull() ?: ("Subscription #" + System.currentTimeMillis())
        return group
    }

    suspend fun parseProfiles(uri: String): List<AbstractBean> {
        return parseProxies(uri)
    }

    suspend fun importSubscription(group: ProxyGroup) {
        GroupManager.createGroup(group)
        GroupUpdater.startUpdate(group, true)
    }

    suspend fun importProfiles(proxies: List<AbstractBean>): Int {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        DataStore.selectedGroup = targetId
        return proxies.size
    }
}
