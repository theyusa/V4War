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
import io.nekohasekai.sagernet.databinding.ActivityDnsLookupBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.toolbox.DnsLookupEngine
import io.nekohasekai.sagernet.utils.toolbox.DnsRecordType
import io.nekohasekai.sagernet.utils.toolbox.DnsResolverMode
import io.nekohasekai.sagernet.utils.toolbox.DnsServerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DnsLookupActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityDnsLookupBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private var selectedType = DnsRecordType.A
    private var queryJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences("toolbox_history", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDnsLookupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_dns_lookup),
        )
        styleUi()
        setupRecordChips()
        setupPresetChips()
        renderHistory()

        binding.chipGroupMode.setOnCheckedStateChangeListener { _, _ ->
            val showCustom = binding.chipModeCustom.isChecked && !binding.chipCompare.isChecked
            binding.containerCustomResolver.visibility = if (showCustom) View.VISIBLE else View.GONE
        }
        binding.chipCompare.setOnCheckedChangeListener { _, checked ->
            binding.containerCustomResolver.visibility =
                if (!checked && binding.chipModeCustom.isChecked) View.VISIBLE else View.GONE
        }

        binding.btnQuery.setOnClickListener { executeLookup() }
        binding.editDomain.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                executeLookup()
                true
            } else {
                false
            }
        }
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
        queryJob?.cancel()
    }

    private fun styleUi() {
        binding.scrollDnsLookup.applyListInsets()
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardQuery, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconDnsLookup, R.drawable.ic_action_dns)
        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnQuery)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.root)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        binding.progressIndicator.setIndicatorColor(accent)
        binding.progressIndicator.trackColor = ColorUtils.setAlphaComponent(accent, 45)
    }

    private fun setupRecordChips() {
        DnsRecordType.entries.forEach { type ->
            val chip = Chip(this).apply {
                text = type.label
                isCheckable = true
                isChecked = type == selectedType
                setOnClickListener { selectedType = type }
            }
            ExpressiveSettingsHelper.styleExpressiveChip(chip)
            binding.chipGroupRecordType.addView(chip)
        }
    }

    private fun setupPresetChips() {
        DnsLookupEngine.PRESETS.forEach { preset ->
            val chip = Chip(this).apply {
                text = preset.label
                isCheckable = false
                setOnClickListener { binding.editResolver.setText(preset.endpoint) }
            }
            ExpressiveSettingsHelper.styleExpressiveChip(chip)
            binding.chipGroupPresets.addView(chip)
        }
    }

    private fun currentMode(): DnsResolverMode = when {
        binding.chipModeCore.isChecked -> DnsResolverMode.FOLLOW_CORE
        binding.chipModeSystem.isChecked -> DnsResolverMode.SYSTEM_DNS
        else -> DnsResolverMode.CUSTOM
    }

    private fun executeLookup() {
        val domain = binding.editDomain.text?.toString().orEmpty().trim()
        if (domain.isEmpty()) {
            Toast.makeText(this, R.string.dns_lookup_empty_error, Toast.LENGTH_SHORT).show()
            return
        }
        saveHistory(domain)
        renderHistory()

        queryJob?.cancel()
        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnQuery.isEnabled = false
        val resolverEndpoint = binding.editResolver.text?.toString().orEmpty()
        val compare = binding.chipCompare.isChecked
        val viaProxy = binding.chipViaProxy.isChecked
        val mode = currentMode()
        val cm = getSystemService(ConnectivityManager::class.java)

        queryJob = lifecycleScope.launch {
            val report = DnsLookupEngine.lookup(
                rawDomain = domain,
                recordType = selectedType,
                mode = mode,
                customEndpoint = resolverEndpoint,
                compareMode = compare,
                viaProxy = viaProxy,
                connectivity = cm,
            )
            binding.progressIndicator.visibility = View.GONE
            binding.btnQuery.isEnabled = true
            renderReport(report.results)
        }
    }

    private fun renderReport(results: List<DnsServerResult>) {
        binding.containerResults.removeAllViews()
        results.forEachIndexed { index, result ->
            val card = buildResultCard(result)
            if (index > 0) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp2px(10)
            }
            binding.containerResults.addView(card)
        }
    }

    private fun buildResultCard(res: DnsServerResult): MaterialCardView {
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

        val titleCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        titleCol.addView(
            TextView(this).apply {
                text = res.label
                textSize = 15.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(getColorAttr(android.R.attr.textColorPrimary))
            },
        )
        titleCol.addView(
            TextView(this).apply {
                text = "${res.protocol} · ${res.endpoint}"
                textSize = 12f
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            },
        )

        val badgeColor = when (res.rcodeName) {
            "NOERROR" -> null
            "NXDOMAIN" -> getColor(R.color.material_amber_900)
            else -> getColor(R.color.material_red_500)
        }
        val statusBadge = TextView(this).apply {
            text = "${res.rcodeName} · ${res.rttMs} ms"
            textSize = 11f
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3))
        }
        ExpressiveSettingsHelper.styleExpressiveBadge(statusBadge, accentColor = badgeColor)

        header.addView(titleCol)
        header.addView(statusBadge)
        root.addView(header)

        if (res.error.isNotBlank()) {
            val errTv = TextView(this).apply {
                text = res.error
                textSize = 13f
                setPadding(0, dp2px(8), 0, 0)
                setTextColor(getColor(R.color.material_red_500))
            }
            root.addView(errTv)
        } else if (res.answers.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = getString(R.string.dns_lookup_no_records)
                textSize = 13f
                setPadding(0, dp2px(8), 0, 0)
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            }
            root.addView(emptyTv)
        } else {
            val box = LinearLayout(this).apply {
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
            ExpressiveSettingsHelper.styleExpressiveNestedBox(box, cornerRadiusDp = 14f, strokeAlpha = 160)

            res.answers.forEachIndexed { idx, ans ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    if (idx > 0) setPadding(0, dp2px(6), 0, 0)
                }
                row.addView(
                    TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = ans.value
                        textSize = 13.5f
                        fontFeatureSettings = "tnum"
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(getColorAttr(android.R.attr.textColorPrimary))
                    },
                )
                if (ans.ttl > 0) {
                    row.addView(
                        TextView(this).apply {
                            text = "TTL ${ans.ttl}s"
                            textSize = 11.5f
                            fontFeatureSettings = "tnum"
                            setPadding(dp2px(8), 0, 0, 0)
                            setTextColor(getColorAttr(android.R.attr.textColorSecondary))
                        },
                    )
                }
                row.setOnClickListener { copyText("DNS Answer", ans.value) }
                box.addView(row)
            }
            root.addView(box)
        }

        card.addView(root)
        return card
    }

    private fun loadHistory(): List<String> {
        val raw = prefs.getString("dns_lookup_history", "").orEmpty()
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
    }

    private fun saveHistory(entry: String) {
        val updated = (listOf(entry) + loadHistory()).distinct().take(10)
        prefs.edit().putString("dns_lookup_history", updated.joinToString("\n")).apply()
    }

    private fun renderHistory() {
        val history = loadHistory()
        binding.chipGroupHistory.removeAllViews()
        if (history.isEmpty()) {
            binding.scrollHistory.visibility = View.GONE
            return
        }
        binding.scrollHistory.visibility = View.VISIBLE
        history.forEach { item ->
            val chip = Chip(this).apply {
                text = item
                isCheckable = false
                setOnClickListener {
                    binding.editDomain.setText(item)
                    executeLookup()
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

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) = Unit

    override fun onServiceConnected(service: ISagerNetService) = Unit
}
