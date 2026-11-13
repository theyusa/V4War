package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ActivityDnsLeakBinding
import io.nekohasekai.sagernet.ktx.appUserAgent
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.newHttpClient
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress

class DnsLeakActivity : ThemedActivity() {

    private lateinit var binding: ActivityDnsLeakBinding
    private var testJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsLeakBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_dns_leak),
        )
        styleExpressiveCards()

        binding.btnCopyEgressIp.setOnClickListener {
            val ip = binding.textEgressIp.text.toString()
            if (ip.isNotBlank() && ip != "-") copyToClipboard("Egress IP", ip)
        }

        binding.btnCopyResolverIp.setOnClickListener {
            val ip = binding.textResolverIp.text.toString()
            if (ip.isNotBlank() && ip != "-") copyToClipboard("DNS Resolver IP", ip)
        }

        binding.btnRerunTest.setOnClickListener {
            runDnsLeakTest()
        }

        runDnsLeakTest()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        testJob?.cancel()
    }

    private fun styleExpressiveCards() {
        binding.scrollDnsLeak.applyListInsets()
        listOf(
            binding.cardVerdict,
            binding.cardFakeIp,
            binding.cardEgress,
            binding.cardResolver,
            binding.cardSystemDns,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        ExpressiveSettingsHelper.applySquircleIcon(binding.imageVerdictIcon, R.drawable.ic_baseline_security_24, sizeDp = 42, iconSizeDp = 22)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconFakeIpBadge, R.drawable.ic_baseline_layers_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconEgressBadge, R.drawable.baseline_public_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconResolverBadge, R.drawable.ic_action_dns)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconSystemDnsBadge, R.drawable.ic_action_settings)

        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.layoutRecommendation, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.layoutFakeIpSamples, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxEgressIp, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxResolverIp, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxSystemDns, cornerRadiusDp = 14f, strokeAlpha = 160)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        binding.progressIndicator.setIndicatorColor(accent)
        binding.progressIndicator.trackColor = ColorUtils.setAlphaComponent(accent, 45)
        binding.iconRecommendation.imageTintList = ColorStateList.valueOf(accent)
        binding.btnCopyEgressIp.imageTintList = ColorStateList.valueOf(accent)
        binding.btnCopyResolverIp.imageTintList = ColorStateList.valueOf(accent)

        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeFakeIp)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeWarp)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeResolverStatus)
        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnRerunTest)
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.network_interfaces_copied, Toast.LENGTH_SHORT).show()
    }

    private fun runDnsLeakTest() {
        testJob?.cancel()
        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnRerunTest.isEnabled = false
        binding.btnRerunTest.setText(R.string.connection_test_testing)

        // Reset UI to testing state without empty badges or hollow boxes
        ExpressiveSettingsHelper.applySquircleIcon(binding.imageVerdictIcon, R.drawable.ic_baseline_security_24, sizeDp = 42, iconSizeDp = 22)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict)
        binding.textVerdictTitle.setText(R.string.dns_leak_verdict_card_title)
        binding.badgeVerdict.visibility = View.VISIBLE
        binding.badgeVerdict.setText(R.string.connection_test_testing)
        binding.textVerdictDesc.setText(R.string.testing_dns)
        binding.layoutRecommendation.visibility = View.GONE

        binding.badgeFakeIp.visibility = View.GONE
        binding.textFakeIpSummary.setText(R.string.connection_test_testing)
        binding.layoutFakeIpSamples.removeAllViews()
        binding.layoutFakeIpSamples.visibility = View.GONE

        binding.badgeWarp.visibility = View.GONE
        binding.textEgressIp.text = "..."
        binding.btnCopyEgressIp.visibility = View.GONE
        binding.textEgressLocation.setText(R.string.connection_test_testing)

        binding.badgeResolverStatus.visibility = View.GONE
        binding.textResolverIp.text = "..."
        binding.btnCopyResolverIp.visibility = View.GONE
        binding.textResolverGeo.setText(R.string.connection_test_testing)
        binding.textResolverNote.visibility = View.GONE

        binding.textSystemDnsSummary.setText(R.string.connection_test_testing)
        binding.textPrivateDns.visibility = View.GONE

        testJob = lifecycleScope.launch(Dispatchers.IO) {
            val connectivity = getSystemService(ConnectivityManager::class.java)
            val activeNet = connectivity?.activeNetwork
            val caps = activeNet?.let { connectivity.getNetworkCapabilities(it) }
            val link = activeNet?.let { connectivity.getLinkProperties(it) }

            val isVpnConnected = DataStore.serviceState.connected &&
                (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
                 link?.interfaceName?.startsWith("tun") == true)

            val activeIface = link?.interfaceName ?: if (isVpnConnected) "tun0" else "wlan0"
            val activeDnsServers = link?.dnsServers?.map { it.hostAddress.orEmpty() }?.filter { it.isNotBlank() }.orEmpty()
            val isPrivateDns = if (Build.VERSION.SDK_INT >= 29) link?.isPrivateDnsActive == true else false
            val privateDnsServer = if (Build.VERSION.SDK_INT >= 29) link?.privateDnsServerName else null

            // Physical underlying network DNS (e.g. Wi-Fi / Cellular)
            val physicalDnsServers = mutableListOf<String>()
            connectivity?.allNetworks?.forEach { net ->
                val c = connectivity.getNetworkCapabilities(net)
                if (c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    connectivity.getLinkProperties(net)?.dnsServers?.forEach { dns ->
                        val host = dns.hostAddress.orEmpty()
                        if (host.isNotBlank()) physicalDnsServers.add(host)
                    }
                }
            }

            // 1. Probe Fake-IP
            val probeDomains = listOf("www.google.com", "cloudflare.com", "wikipedia.org")
            val fakeIpSamples = mutableListOf<Pair<String, String>>()
            var fakeIpDetected = false

            for (domain in probeDomains) {
                runCatching {
                    val addresses = InetAddress.getAllByName(domain)
                    val ip = addresses.firstOrNull()?.hostAddress
                    if (ip != null) {
                        fakeIpSamples.add(domain to ip)
                        if (ip.startsWith("198.18.") || ip.startsWith("198.19.")) {
                            fakeIpDetected = true
                        }
                    }
                }
            }

            // 2. Client Public Egress IP & Location
            var egressIp: String? = null
            var egressLoc: String? = null
            var egressWarp: String? = null

            runCatching {
                val req = Request.Builder()
                    .url("https://1.1.1.1/cdn-cgi/trace")
                    .header("User-Agent", appUserAgent())
                    .build()
                val client = newHttpClient(viaProxy = false, timeoutSeconds = 8)
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    for (line in body.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("ip=")) egressIp = trimmed.substringAfter("ip=").trim()
                        if (trimmed.startsWith("loc=")) egressLoc = trimmed.substringAfter("loc=").trim()
                        if (trimmed.startsWith("warp=")) egressWarp = trimmed.substringAfter("warp=").trim()
                    }
                }
            }

            // Fallback for Egress IP if 1.1.1.1/cdn-cgi/trace failed
            if (egressIp.isNullOrBlank()) {
                runCatching {
                    val req = Request.Builder()
                        .url("https://api.ipify.org?format=json")
                        .header("User-Agent", appUserAgent())
                        .build()
                    val client = newHttpClient(viaProxy = false, timeoutSeconds = 8)
                    client.newCall(req).execute().use { resp ->
                        val json = JSONObject(resp.body?.string().orEmpty())
                        egressIp = json.optString("ip").takeIf { it.isNotBlank() }
                    }
                }
            }

            // 3. DNS Resolver Egress IP via EDNS probe
            var resolverIp: String? = null
            var resolverGeo: String? = null

            runCatching {
                val req = Request.Builder()
                    .url("https://edns.ip-api.com/json")
                    .header("User-Agent", appUserAgent())
                    .build()
                val client = newHttpClient(viaProxy = false, timeoutSeconds = 12)
                    .newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val dnsObj = json.optJSONObject("dns")
                    if (dnsObj != null) {
                        resolverIp = dnsObj.optString("ip").takeIf { it.isNotBlank() }
                        resolverGeo = dnsObj.optString("geo").takeIf { it.isNotBlank() }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                renderResults(
                    isVpnConnected = isVpnConnected,
                    activeIface = activeIface,
                    activeDns = activeDnsServers,
                    isPrivateDns = isPrivateDns,
                    privateDnsServer = privateDnsServer,
                    physicalDns = physicalDnsServers,
                    fakeIpDetected = fakeIpDetected,
                    fakeIpSamples = fakeIpSamples,
                    egressIp = egressIp,
                    egressLoc = egressLoc,
                    egressWarp = egressWarp,
                    resolverIp = resolverIp,
                    resolverGeo = resolverGeo,
                )
            }
        }
    }

    private fun renderResults(
        isVpnConnected: Boolean,
        activeIface: String,
        activeDns: List<String>,
        isPrivateDns: Boolean,
        privateDnsServer: String?,
        physicalDns: List<String>,
        fakeIpDetected: Boolean,
        fakeIpSamples: List<Pair<String, String>>,
        egressIp: String?,
        egressLoc: String?,
        egressWarp: String?,
        resolverIp: String?,
        resolverGeo: String?,
    ) {
        binding.progressIndicator.visibility = View.GONE
        binding.btnRerunTest.isEnabled = true
        binding.btnRerunTest.setText(R.string.start_dns_leak_test)

        val colorError = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorError)
        val textColorPrimary = getColorAttr(android.R.attr.textColorPrimary)
        val safeAccent = ExpressiveSettingsHelper.resolveAccentColor(this)
        val isNight = io.nekohasekai.sagernet.utils.Theme.usingNightMode(this)

        // 1. Fake-IP Card
        binding.badgeFakeIp.visibility = View.VISIBLE
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeFakeIp)
        if (fakeIpDetected) {
            binding.badgeFakeIp.text = getString(R.string.dns_leak_fakeip_active, "198.18.0.0/15")
            binding.textFakeIpSummary.text = getString(R.string.dns_leak_fakeip_desc)
        } else {
            binding.badgeFakeIp.text = getString(R.string.dns_leak_fakeip_inactive)
            binding.textFakeIpSummary.text = if (isVpnConnected) {
                "Standard tunneled DNS is resolving domain addresses."
            } else {
                "Direct resolution via local physical network."
            }
        }

        binding.layoutFakeIpSamples.removeAllViews()
        binding.layoutFakeIpSamples.visibility = if (fakeIpSamples.isEmpty()) View.GONE else View.VISIBLE
        fakeIpSamples.forEach { (domain, ip) ->
            val sampleView = TextView(this).apply {
                text = "$domain  ->  $ip"
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                fontFeatureSettings = "tnum"
                setTextColor(textColorPrimary)
                setPadding(0, dp2px(3), 0, dp2px(3))
            }
            binding.layoutFakeIpSamples.addView(sampleView)
        }

        // 2. Client Public Egress
        binding.textEgressIp.text = egressIp ?: getString(R.string.network_interfaces_unavailable)
        binding.btnCopyEgressIp.visibility = if (!egressIp.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.textEgressLocation.text = if (egressLoc != null) "Location: $egressLoc" else "Location: Unknown"
        if (egressWarp != null && egressWarp != "off") {
            binding.badgeWarp.visibility = View.VISIBLE
            ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeWarp)
            binding.badgeWarp.text = "WARP: $egressWarp"
        } else {
            binding.badgeWarp.visibility = View.GONE
        }

        // 3. DNS Resolver Egress
        binding.textResolverIp.text = resolverIp ?: getString(R.string.network_interfaces_unavailable)
        binding.btnCopyResolverIp.visibility = if (!resolverIp.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.textResolverGeo.text = if (resolverGeo != null) "ISP / Geo: $resolverGeo" else "ISP / Geo: Unknown"
        binding.badgeResolverStatus.visibility = View.VISIBLE
        ExpressiveSettingsHelper.styleExpressiveBadge(
            binding.badgeResolverStatus,
            if (resolverIp != null) safeAccent else colorError,
        )
        binding.badgeResolverStatus.text = if (resolverIp != null) "EDNS Probe Success" else "Probe Inconclusive"
        binding.textResolverNote.visibility = View.GONE

        // 4. System & Private DNS
        val dnsDisplay = if (activeDns.isNotEmpty()) activeDns.joinToString(", ") else "None"
        binding.textSystemDnsSummary.text = "Active DNS: $dnsDisplay ($activeIface)"
        binding.textPrivateDns.visibility = View.VISIBLE
        binding.textPrivateDns.text = if (isPrivateDns) {
            getString(R.string.network_interfaces_private_dns_active, privateDnsServer ?: "Automatic")
        } else {
            getString(R.string.network_interfaces_private_dns_none)
        }

        // 5. Verdict Evaluation
        binding.layoutRecommendation.visibility = View.VISIBLE
        binding.imageVerdictIcon.imageTintList = null
        binding.textVerdictTitle.setText(R.string.dns_leak_verdict_card_title)
        binding.badgeVerdict.visibility = View.VISIBLE
        val errorTint = colorError

        when {
            !isVpnConnected -> {
                ExpressiveSettingsHelper.applySquircleIcon(binding.imageVerdictIcon, R.drawable.ic_baseline_info_24, sizeDp = 42, iconSizeDp = 22)
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict)
                binding.badgeVerdict.text = getString(R.string.dns_leak_direct_title)
                binding.textVerdictDesc.text = getString(R.string.dns_leak_direct_desc)
                binding.textRecommendation.text = "Connect to V4War VPN to protect your DNS queries and mask your public IP address."
            }
            fakeIpDetected -> {
                ExpressiveSettingsHelper.applySquircleIcon(binding.imageVerdictIcon, R.drawable.ic_baseline_check_circle_24, sizeDp = 42, iconSizeDp = 22)
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict, safeAccent)
                binding.badgeVerdict.text = getString(R.string.dns_leak_fakeip_title)
                binding.textVerdictDesc.text = getString(R.string.dns_leak_fakeip_desc)
                binding.textRecommendation.text = getString(R.string.dns_leak_rec_fakeip)
            }
            resolverIp != null && physicalDns.contains(resolverIp) -> {
                // Definite Leak: Resolver IP matches physical network DNS!
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imageVerdictIcon,
                    R.drawable.ic_baseline_warning_24,
                    sizeDp = 42,
                    iconSizeDp = 22,
                    customContainerColor = ColorUtils.setAlphaComponent(errorTint, if (isNight) 54 else 34),
                    customIconColor = errorTint,
                )
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict, errorTint)
                binding.badgeVerdict.text = getString(R.string.dns_leak_warning_title)
                binding.textVerdictDesc.text = getString(R.string.dns_leak_warning_desc, resolverGeo ?: resolverIp)
                binding.textRecommendation.text = getString(R.string.dns_leak_rec_fix)
            }
            isVpnConnected -> {
                ExpressiveSettingsHelper.applySquircleIcon(binding.imageVerdictIcon, R.drawable.ic_baseline_check_circle_24, sizeDp = 42, iconSizeDp = 22)
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict, safeAccent)
                binding.badgeVerdict.text = getString(R.string.dns_leak_safe_title)
                binding.textVerdictDesc.text = getString(R.string.dns_leak_safe_desc)
                binding.textRecommendation.text = getString(R.string.dns_leak_rec_safe)
            }
            else -> {
                ExpressiveSettingsHelper.applySquircleIcon(
                    binding.imageVerdictIcon,
                    R.drawable.ic_baseline_warning_24,
                    sizeDp = 42,
                    iconSizeDp = 22,
                    customContainerColor = ColorUtils.setAlphaComponent(errorTint, if (isNight) 54 else 34),
                    customIconColor = errorTint,
                )
                ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeVerdict, errorTint)
                binding.badgeVerdict.text = "Test Failed"
                binding.textVerdictDesc.text = "Could not reach diagnostic servers. Check your internet connection."
                binding.textRecommendation.text = "Check connection settings and try again."
            }
        }
    }
}
