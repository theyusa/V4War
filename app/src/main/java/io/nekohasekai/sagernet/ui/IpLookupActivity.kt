package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ActivityIpLookupBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.utils.toolbox.IpCategory
import io.nekohasekai.sagernet.utils.toolbox.IpGeoInfo
import io.nekohasekai.sagernet.utils.toolbox.IpLookupEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IpLookupActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityIpLookupBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private var lastExitCheckConnected: Boolean? = null
    private var compareJob: Job? = null
    private var lookupJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences("toolbox_history", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIpLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_ip_lookup),
        )
        styleUi()
        renderHistory()

        binding.btnRefreshExits.setOnClickListener { loadExitComparison() }
        binding.btnLookup.setOnClickListener { runLookup() }
        binding.editTarget.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                runLookup()
                true
            } else {
                false
            }
        }
        binding.boxProxyExit.setOnClickListener {
            val ip = binding.textProxyExitIp.text.toString()
            if (ip.isNotBlank() && ip != "-") copyText("Proxy Exit IP", ip)
        }
        binding.boxDirectExit.setOnClickListener {
            val ip = binding.textDirectExitIp.text.toString()
            if (ip.isNotBlank() && ip != "-") copyText("Direct Exit IP", ip)
        }

        loadExitComparison()
    }

    override fun onStart() {
        super.onStart()
        connection.connect(this, this)
    }

    override fun onStop() {
        connection.disconnect(this)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        compareJob?.cancel()
        lookupJob?.cancel()
    }

    private fun styleUi() {
        binding.scrollIpLookup.applyListInsets()
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardExitOverview, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardSearch, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxProxyExit, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxDirectExit, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconExitBadge, R.drawable.ic_test_ip_24)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeProxyExit)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeDirectExit)
        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnLookup)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnRefreshExits)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.root)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        binding.progressIndicator.setIndicatorColor(accent)
        binding.progressIndicator.trackColor = ColorUtils.setAlphaComponent(accent, 45)
        binding.textResultsHeader.setTextColor(accent)
    }

    private fun loadExitComparison() {
        val connected = DataStore.serviceState.connected
        lastExitCheckConnected = connected
        compareJob?.cancel()
        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnRefreshExits.isEnabled = false
        val cm = getSystemService(ConnectivityManager::class.java)

        if (!connected) {
            binding.badgeProxyExit.visibility = View.VISIBLE
            binding.badgeProxyExit.text = getString(R.string.network_interfaces_vpn_standby)
            binding.textProxyExitIp.text = "-"
            binding.textProxyExitMeta.text = getString(R.string.ip_lookup_vpn_disconnected_hint)
        } else {
            binding.badgeProxyExit.visibility = View.GONE
            binding.textProxyExitIp.text = "..."
            binding.textProxyExitMeta.setText(R.string.connection_test_testing)
        }

        binding.badgeDirectExit.visibility = View.GONE
        binding.textDirectExitIp.text = "..."
        binding.textDirectExitMeta.setText(R.string.connection_test_testing)

        compareJob = lifecycleScope.launch {
            var remaining = 2
            fun finishOne() {
                remaining--
                if (remaining <= 0) {
                    binding.progressIndicator.visibility = View.GONE
                    binding.btnRefreshExits.isEnabled = true
                }
            }

            launch {
                val proxy = IpLookupEngine.queryProxyExit()
                if (!DataStore.serviceState.connected || proxy == null) {
                    binding.badgeProxyExit.visibility = View.VISIBLE
                    binding.badgeProxyExit.text = getString(R.string.network_interfaces_vpn_standby)
                    binding.textProxyExitIp.text = "-"
                    binding.textProxyExitMeta.text = getString(R.string.ip_lookup_vpn_disconnected_hint)
                } else {
                    bindExitBox(proxy, binding.badgeProxyExit, binding.textProxyExitIp, binding.textProxyExitMeta)
                }
                finishOne()
            }

            launch {
                val direct = IpLookupEngine.queryDirectExit(cm)
                bindExitBox(direct, binding.badgeDirectExit, binding.textDirectExitIp, binding.textDirectExitMeta)
                finishOne()
            }
        }
    }

    private fun bindExitBox(
        info: IpGeoInfo,
        badge: TextView,
        ipView: TextView,
        metaView: TextView,
    ) {
        if (info.ip.isBlank()) {
            badge.visibility = View.VISIBLE
            badge.text = getString(R.string.stun_unknown_short)
            ipView.text = "-"
            metaView.text = info.error.ifBlank { getString(R.string.toolbox_unavailable) }
            return
        }
        ipView.text = info.ip
        val badgeText = buildBadgeText(info)
        if (badgeText.isNotBlank()) {
            badge.visibility = View.VISIBLE
            badge.text = badgeText
        } else {
            badge.visibility = View.GONE
        }
        metaView.text = formatMeta(info)
    }

    private fun runLookup() {
        val raw = binding.editTarget.text?.toString().orEmpty().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.ip_lookup_empty_error, Toast.LENGTH_SHORT).show()
            return
        }
        saveHistory(raw)
        renderHistory()

        lookupJob?.cancel()
        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnLookup.isEnabled = false
        val viaProxy = binding.chipViaProxy.isChecked
        val cm = getSystemService(ConnectivityManager::class.java)

        lookupJob = lifecycleScope.launch {
            val res = IpLookupEngine.lookupTarget(raw, viaProxy, cm)
            binding.progressIndicator.visibility = View.GONE
            binding.btnLookup.isEnabled = true
            renderResults(res.normalizedTarget, res.resolvedVia, res.results, res.error)
        }
    }

    private fun renderResults(
        target: String,
        via: String,
        items: List<IpGeoInfo>,
        error: String,
    ) {
        binding.containerResults.removeAllViews()
        binding.textResultsHeader.visibility = View.VISIBLE
        binding.textResultsHeader.text = if (via.isNotBlank()) "$target · $via" else target

        if (items.isEmpty()) {
            val emptyCard = createResultCard(
                IpGeoInfo(
                    ip = target,
                    version = 4,
                    category = IpCategory.RESERVED,
                    error = error.ifBlank { getString(R.string.toolbox_unavailable) },
                ),
                isError = true,
            )
            binding.containerResults.addView(emptyCard)
            return
        }

        items.forEachIndexed { index, info ->
            val card = createResultCard(info, isError = info.error.isNotBlank() && info.country.isBlank())
            if (index > 0) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp2px(10)
            }
            binding.containerResults.addView(card)
        }
    }

    private fun createResultCard(info: IpGeoInfo, isError: Boolean): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        ExpressiveSettingsHelper.styleExpressiveCard(card, cornerRadiusDp = 20f)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp2px(16)
            setPadding(p, p, p, p)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val ipTv = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = info.ip
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            fontFeatureSettings = "tnum"
            setTextColor(getColorAttr(android.R.attr.textColorPrimary))
        }

        val badgeTv = TextView(this).apply {
            text = if (isError) "ERR" else buildBadgeText(info)
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3))
        }
        val badgeAccent = if (isError) getColor(R.color.material_red_500) else null
        ExpressiveSettingsHelper.styleExpressiveBadge(badgeTv, accentColor = badgeAccent)

        header.addView(ipTv)
        header.addView(badgeTv)
        root.addView(header)

        val metaText = if (info.error.isNotBlank()) info.error else formatMeta(info)
        if (metaText.isNotBlank()) {
            val metaTv = TextView(this).apply {
                text = metaText
                textSize = 13f
                setPadding(0, dp2px(6), 0, 0)
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            }
            root.addView(metaTv)
        }

        val detailLines = buildList {
            if (info.timezone.isNotBlank()) add("${getString(R.string.ip_lookup_timezone)}: ${info.timezone}")
            if (info.source.isNotBlank()) add("${getString(R.string.ip_lookup_source)}: ${info.source}")
        }
        if (detailLines.isNotEmpty()) {
            val extraBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = dp2px(10)
                setPadding(p, p, p, p)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.topMargin = dp2px(10)
                layoutParams = lp
            }
            ExpressiveSettingsHelper.styleExpressiveNestedBox(extraBox, cornerRadiusDp = 14f, strokeAlpha = 160)
            for (line in detailLines) {
                extraBox.addView(
                    TextView(this).apply {
                        text = line
                        textSize = 12.5f
                        setTextColor(getColorAttr(android.R.attr.textColorSecondary))
                    },
                )
            }
            root.addView(extraBox)
        }

        card.addView(root)
        card.setOnClickListener { copyText("IP Lookup", info.ip) }
        return card
    }

    private fun buildBadgeText(info: IpGeoInfo): String {
        val ver = "IPv${info.version}"
        val cc = info.countryCode.takeIf { it.isNotBlank() }?.uppercase()
        val catLabel = when (info.category) {
            IpCategory.PUBLIC -> cc ?: getString(R.string.ip_lookup_cat_public)
            IpCategory.PRIVATE -> getString(R.string.ip_lookup_cat_private)
            IpCategory.LOOPBACK -> "Loopback"
            IpCategory.CGNAT -> "CGNAT"
            IpCategory.FAKE_IP -> "Fake-IP"
            IpCategory.LINK_LOCAL -> "Link-Local"
            IpCategory.MULTICAST -> "Multicast"
            IpCategory.RESERVED -> getString(R.string.ip_lookup_cat_reserved)
        }
        return "$ver · $catLabel"
    }

    private fun formatMeta(info: IpGeoInfo): String {
        val loc = listOf(info.city, info.region, info.country).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val net = listOf(info.asn, info.org.ifBlank { info.isp }).filter { it.isNotBlank() }.joinToString(" · ")
        return listOf(loc, net).filter { it.isNotBlank() }.joinToString("\n").ifBlank {
            getString(R.string.ip_lookup_category_format, info.category.name)
        }
    }

    private fun loadHistory(): List<String> {
        val raw = prefs.getString("ip_lookup_history", "").orEmpty()
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
    }

    private fun saveHistory(entry: String) {
        val updated = (listOf(entry) + loadHistory()).distinct().take(10)
        prefs.edit().putString("ip_lookup_history", updated.joinToString("\n")).apply()
    }

    private fun renderHistory() {
        val history = loadHistory()
        binding.chipGroupHistory.removeAllViews()
        if (history.isEmpty()) {
            binding.scrollHistory.visibility = View.GONE
            return
        }
        binding.scrollHistory.visibility = View.VISIBLE
        for (item in history) {
            val chip = Chip(this).apply {
                text = item
                isCheckable = false
                setOnClickListener {
                    binding.editTarget.setText(item)
                    runLookup()
                }
            }
            ExpressiveSettingsHelper.styleExpressiveChip(chip)
            binding.chipGroupHistory.addView(chip)
        }
    }

    private fun copyText(label: String, value: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cb.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, R.string.network_interfaces_copied, Toast.LENGTH_SHORT).show()
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        if (lastExitCheckConnected != state.connected) {
            loadExitComparison()
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        val connected = runCatching {
            val idx = service.state
            idx in BaseService.State.entries.indices && BaseService.State.entries[idx].connected
        }.getOrDefault(DataStore.serviceState.connected)
        if (lastExitCheckConnected != connected) {
            loadExitComparison()
        }
    }
}
