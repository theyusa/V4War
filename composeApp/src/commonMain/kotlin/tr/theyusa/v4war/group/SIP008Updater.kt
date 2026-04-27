/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package tr.theyusa.v4war.group

import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.GroupManager
import tr.theyusa.v4war.database.ProxyGroup
import tr.theyusa.v4war.database.SubscriptionBean
import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.shadowsocks.ShadowsocksBean
import tr.theyusa.v4war.fmt.shadowsocks.pluginToLocal
import tr.theyusa.v4war.ktx.Logs
import tr.theyusa.v4war.ktx.applyDefaultValues
import tr.theyusa.v4war.ktx.generateUserAgent
import tr.theyusa.v4war.ktx.kxs
import tr.theyusa.v4war.libcore.Libcore
import tr.theyusa.v4war.repository.resolveRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tr.theyusa.v4war.resources.*

/** https://shadowsocks.org/doc/sip008.html */
object SIP008Updater : GroupUpdater() {

    @Serializable
    private data class SIP008Response(
        @SerialName("bytes_used")
        val bytesUsed: Long? = null,
        @SerialName("bytes_remaining")
        val bytesRemaining: Long? = null,
        val servers: List<SIP008Server> = emptyList(),
    )

    @Serializable
    private data class SIP008Server(
        val server: String? = null,
        @SerialName("server_port")
        val serverPort: Int? = null,
        val method: String? = null,
        val password: String? = null,
        val remarks: String? = null,
        val plugin: String? = null,
        @SerialName("plugin_opts")
        val pluginOpts: String? = null,
    ) {
        fun toBean(): ShadowsocksBean = ShadowsocksBean().also {
            it.serverAddress = server.orEmpty()
            it.serverPort = serverPort ?: it.defaultPort
            it.password = password.orEmpty()
            it.method = method.orEmpty()
            it.name = remarks.orEmpty()

            val pluginName = plugin
            if (!pluginName.isNullOrBlank()) {
                it.plugin = pluginName + ";" + pluginOpts.orEmpty()
                it.pluginToLocal()
            }
        }
    }

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {
        val repository = resolveRepository()
        if (subscription.link.startsWith("http://")) Logs.w("Use SIP008 with HTTP!")

        val sip008Response: SIP008Response
        if (subscription.link.startsWith("content://")) {
            val contentText = readContentUri(subscription.link)

            sip008Response = contentText?.let { kxs.decodeFromString<SIP008Response>(it) }
                ?: error(repository.getString(Res.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                if (DataStore.serviceState.started) {
                    useSocks5(
                        DataStore.mixedPort,
                        DataStore.inboundUsername,
                        DataStore.inboundPassword,
                    )
                }
                // Strict !!!
                restrictedTLS()
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()

            sip008Response = kxs.decodeFromString(response.contentString)
        }

        subscription.bytesUsed = sip008Response.bytesUsed ?: -1
        subscription.bytesRemaining = sip008Response.bytesRemaining ?: -1
        subscription.applyDefaultValues()

        val servers = sip008Response.servers

        val proxies = mutableListOf<AbstractBean>()
        for (profile in servers) {
            val bean = profile.toBean()
            proxies.add(bean.applyDefaultValues())
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }
}
