package io.nekohasekai.sagernet.utils.toolbox

import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.DrawableRes
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.ktx.newHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

enum class ConnectivityCategory {
    AI,
    STREAMING,
    SOCIAL,
    DEVELOPER,
}

enum class ConnectivityStatus {
    PENDING,
    TESTING,
    AVAILABLE,
    PARTIAL,
    BLOCKED,
    ERROR,
}

data class ServiceProbeSpec(
    val id: String,
    val name: String,
    val category: ConnectivityCategory,
    val probeType: ProbeKind,
    val url: String,
    val restrictedRegions: Set<String> = emptySet(),
    val secondaryUrl: String? = null,
    val tcpHost: String? = null,
    val tcpPort: Int = 443,
    @DrawableRes val iconRes: Int = 0,
    val adaptDarkTint: Boolean = false,
) {
    enum class ProbeKind {
        CF_TRACE,
        HTTP_GET,
        NETFLIX,
        TELEGRAM,
    }
}

data class ServiceProbeResult(
    val spec: ServiceProbeSpec,
    val status: ConnectivityStatus = ConnectivityStatus.PENDING,
    val region: String = "",
    val rttMs: Long = 0,
    val detail: String = "",
)

object ConnectivityChecker {

    private val aiRestricted = setOf("CN", "HK", "MO", "RU", "BY", "IR", "KP", "SY", "CU")
    private val grokRestricted = setOf("CN", "HK", "MO", "RU", "BY", "IR", "KP")

    val SERVICES = listOf(
        // AI
        ServiceProbeSpec("openai", "ChatGPT / OpenAI", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://chatgpt.com/cdn-cgi/trace", restrictedRegions = aiRestricted, iconRes = R.drawable.ic_platform_chatgpt),
        ServiceProbeSpec("claude", "Claude (Anthropic)", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://claude.ai/cdn-cgi/trace", restrictedRegions = aiRestricted, iconRes = R.drawable.ic_platform_claude),
        ServiceProbeSpec("gemini", "Google Gemini", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://gemini.google.com/", iconRes = R.drawable.ic_platform_gemini),
        ServiceProbeSpec("grok", "Grok (xAI)", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://api.x.ai/cdn-cgi/trace", restrictedRegions = grokRestricted, iconRes = R.drawable.ic_platform_grok, adaptDarkTint = true),
        ServiceProbeSpec("perplexity", "Perplexity AI", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://www.perplexity.ai/cdn-cgi/trace", restrictedRegions = setOf("CN", "KP", "IR"), iconRes = R.drawable.ic_platform_perplexity),
        ServiceProbeSpec("openrouter", "OpenRouter", ConnectivityCategory.AI, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://openrouter.ai/", iconRes = R.drawable.ic_platform_openrouter),

        // Streaming
        ServiceProbeSpec("netflix", "Netflix", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.NETFLIX, "https://www.netflix.com/title/81280792", secondaryUrl = "https://www.netflix.com/title/80018499", iconRes = R.drawable.ic_platform_netflix),
        ServiceProbeSpec("youtube", "YouTube", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.youtube.com/premium", iconRes = R.drawable.ic_platform_youtube),
        ServiceProbeSpec("disney", "Disney+", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.disneyplus.com/", iconRes = R.drawable.ic_platform_disney),
        ServiceProbeSpec("spotify", "Spotify", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://open.spotify.com/", iconRes = R.drawable.ic_platform_spotify),
        ServiceProbeSpec("twitch", "Twitch", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.twitch.tv/", iconRes = R.drawable.ic_platform_twitch),
        ServiceProbeSpec("tiktok", "TikTok", ConnectivityCategory.STREAMING, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.tiktok.com/", iconRes = R.drawable.ic_platform_tiktok),

        // Social
        ServiceProbeSpec("telegram", "Telegram", ConnectivityCategory.SOCIAL, ServiceProbeSpec.ProbeKind.TELEGRAM, "https://t.me/", tcpHost = "91.108.56.100", tcpPort = 443, iconRes = R.drawable.ic_platform_telegram),
        ServiceProbeSpec("discord", "Discord", ConnectivityCategory.SOCIAL, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://gateway.discord.gg/cdn-cgi/trace", restrictedRegions = setOf("CN", "IR", "KP"), iconRes = R.drawable.ic_platform_discord),
        ServiceProbeSpec("x_twitter", "X (Twitter)", ConnectivityCategory.SOCIAL, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://x.com/", iconRes = R.drawable.ic_platform_x, adaptDarkTint = true),
        ServiceProbeSpec("reddit", "Reddit", ConnectivityCategory.SOCIAL, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.reddit.com/", iconRes = R.drawable.ic_platform_reddit),

        // Developer
        ServiceProbeSpec("google", "Google", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.google.com/generate_204", iconRes = R.drawable.ic_platform_google),
        ServiceProbeSpec("github", "GitHub", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://github.com/", iconRes = R.drawable.ic_platform_github),
        ServiceProbeSpec("cloudflare", "Cloudflare", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.CF_TRACE, "https://www.cloudflare.com/cdn-cgi/trace", iconRes = R.drawable.ic_platform_cloudflare),
        ServiceProbeSpec("docker", "Docker Hub", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://hub.docker.com/", iconRes = R.drawable.ic_platform_docker),
        ServiceProbeSpec("npm", "npm Registry", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://registry.npmjs.org/", iconRes = R.drawable.ic_platform_npm),
        ServiceProbeSpec("wikipedia", "Wikipedia", ConnectivityCategory.DEVELOPER, ServiceProbeSpec.ProbeKind.HTTP_GET, "https://www.wikipedia.org/", iconRes = R.drawable.ic_platform_wikipedia, adaptDarkTint = true),
    )

    fun runChecks(
        specs: List<ServiceProbeSpec>,
        viaProxy: Boolean,
        connectivity: ConnectivityManager?,
    ): Flow<ServiceProbeResult> = channelFlow {
        val physical = if (!viaProxy) IpLookupEngine.findPhysicalNetwork(connectivity) else null
        val client = buildClient(viaProxy, physical)
        val sem = Semaphore(4)
        coroutineScope {
            specs.map { spec ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        send(ServiceProbeResult(spec, ConnectivityStatus.TESTING))
                        val result = checkSingle(spec, client, viaProxy, physical)
                        send(result)
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun checkSingle(
        spec: ServiceProbeSpec,
        client: OkHttpClient,
        viaProxy: Boolean,
        physical: Network?,
    ): ServiceProbeResult = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        return@withContext when (spec.probeType) {
            ServiceProbeSpec.ProbeKind.CF_TRACE -> probeCfTrace(spec, client, startNs)
            ServiceProbeSpec.ProbeKind.HTTP_GET -> probeHttpGet(spec, client, startNs)
            ServiceProbeSpec.ProbeKind.NETFLIX -> probeNetflix(spec, client, startNs)
            ServiceProbeSpec.ProbeKind.TELEGRAM -> probeTelegram(spec, client, viaProxy, physical, startNs)
        }
    }

    private fun probeCfTrace(spec: ServiceProbeSpec, client: OkHttpClient, startNs: Long): ServiceProbeResult {
        return runCatching {
            val req = Request.Builder().url(spec.url).header("User-Agent", appUserAgent()).build()
            client.newCall(req).execute().use { resp ->
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                val body = resp.body?.string().orEmpty()
                val loc = parseTraceLoc(body)
                if (!resp.isSuccessful) {
                    return ServiceProbeResult(spec, ConnectivityStatus.BLOCKED, loc, rtt, "HTTP ${resp.code}")
                }
                if (loc.isNotBlank() && spec.restrictedRegions.contains(loc.uppercase())) {
                    return ServiceProbeResult(spec, ConnectivityStatus.PARTIAL, loc, rtt, "Region restricted ($loc)")
                }
                ServiceProbeResult(spec, ConnectivityStatus.AVAILABLE, loc, rtt, "HTTP ${resp.code}")
            }
        }.getOrElse { e ->
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            ServiceProbeResult(spec, ConnectivityStatus.ERROR, "", rtt, e.message ?: "Connection failed")
        }
    }

    private fun probeHttpGet(spec: ServiceProbeSpec, client: OkHttpClient, startNs: Long): ServiceProbeResult {
        return runCatching {
            val req = Request.Builder()
                .url(spec.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                val body = runCatching {
                    resp.body?.source()?.let { source ->
                        source.request(32_000)
                        val count = minOf(source.buffer.size, 32_000L)
                        source.buffer.clone().readString(count, Charsets.UTF_8)
                    }.orEmpty()
                }.getOrDefault("")

                val status = when {
                    code == 403 || code == 451 -> ConnectivityStatus.BLOCKED
                    code in 200..399 -> ConnectivityStatus.AVAILABLE
                    else -> ConnectivityStatus.PARTIAL
                }

                var region = ""
                if (spec.id == "youtube") {
                    val m = Regex(""""GL"\s*:\s*"([A-Z]{2})"""").find(body)
                        ?: Regex(""""countryCode"\s*:\s*"([A-Z]{2})"""").find(body)
                    region = m?.groupValues?.getOrNull(1).orEmpty()
                } else if (finalUrl.contains("unsupportedcountry", ignoreCase = true) ||
                    body.contains("not available in your country", ignoreCase = true)
                ) {
                    return ServiceProbeResult(spec, ConnectivityStatus.PARTIAL, region, rtt, "Region restricted")
                }

                ServiceProbeResult(spec, status, region, rtt, "HTTP $code")
            }
        }.getOrElse { e ->
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            ServiceProbeResult(spec, ConnectivityStatus.ERROR, "", rtt, e.message ?: "Connection failed")
        }
    }

    private fun probeNetflix(spec: ServiceProbeSpec, client: OkHttpClient, startNs: Long): ServiceProbeResult {
        return runCatching {
            val req = Request.Builder()
                .url(spec.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                val finalPath = resp.request.url.encodedPath
                val region = Regex("""^/([a-z]{2})(?:-[a-z]{2})?/""", RegexOption.IGNORE_CASE)
                    .find(finalPath)?.groupValues?.getOrNull(1)?.uppercase().orEmpty()
                if (resp.code == 200) {
                    return ServiceProbeResult(spec, ConnectivityStatus.AVAILABLE, region, rtt, "Full Library")
                }
                val secUrl = spec.secondaryUrl ?: return ServiceProbeResult(spec, ConnectivityStatus.BLOCKED, region, rtt, "HTTP ${resp.code}")
                val secReq = Request.Builder()
                    .url(secUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36")
                    .build()
                client.newCall(secReq).execute().use { secResp ->
                    val rtt2 = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                    if (secResp.code == 200) {
                        ServiceProbeResult(spec, ConnectivityStatus.PARTIAL, region, rtt2, "Originals Only")
                    } else {
                        ServiceProbeResult(spec, ConnectivityStatus.BLOCKED, region, rtt2, "Blocked (HTTP ${secResp.code})")
                    }
                }
            }
        }.getOrElse { e ->
            val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
            ServiceProbeResult(spec, ConnectivityStatus.ERROR, "", rtt, e.message ?: "Connection failed")
        }
    }

    private fun probeTelegram(
        spec: ServiceProbeSpec,
        client: OkHttpClient,
        viaProxy: Boolean,
        physical: Network?,
        startNs: Long,
    ): ServiceProbeResult {
        val httpRes = probeHttpGet(spec, client, startNs)
        if (httpRes.status == ConnectivityStatus.AVAILABLE) return httpRes
        val tcpHost = spec.tcpHost ?: return httpRes
        if (!viaProxy || !DataStore.serviceState.connected) {
            runCatching {
                val resolved = physical?.getAllByName(tcpHost)?.firstOrNull()
                val socket = physical?.socketFactory?.createSocket() ?: Socket()
                socket.use { s ->
                    val target = if (resolved != null) InetSocketAddress(resolved, spec.tcpPort)
                    else InetSocketAddress(tcpHost, spec.tcpPort)
                    s.connect(target, 4000)
                }
                val rtt = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1)
                return ServiceProbeResult(spec, ConnectivityStatus.AVAILABLE, "", rtt, "DC TCP reachable")
            }
        }
        return httpRes
    }

    fun parseTraceLoc(body: String): String {
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("loc=")) {
                return trimmed.substringAfter("loc=").trim()
            }
        }
        return ""
    }

    private fun buildClient(viaProxy: Boolean, physical: Network?): OkHttpClient {
        val useProxy = viaProxy && DataStore.serviceState.connected && !DataStore.mixedInboundDisabled
        if (useProxy) {
            return newHttpClient(viaProxy = true, timeoutSeconds = 10)
        }
        val base = newHttpClient(viaProxy = false, timeoutSeconds = 10)
        return if (physical == null) base else base.newBuilder()
            .socketFactory(physical.socketFactory)
            .dns(
                Dns { host ->
                    val addrs = runCatching { physical.getAllByName(host).toList() }.getOrDefault(emptyList())
                    if (addrs.isNotEmpty()) addrs else throw UnknownHostException("Unable to resolve $host on physical network")
                },
            )
            .build()
    }
}
