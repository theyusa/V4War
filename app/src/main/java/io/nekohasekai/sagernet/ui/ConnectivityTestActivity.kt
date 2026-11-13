package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.databinding.ActivityConnectivityTestBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.toolbox.ConnectivityCategory
import io.nekohasekai.sagernet.utils.toolbox.ConnectivityChecker
import io.nekohasekai.sagernet.utils.toolbox.ConnectivityStatus
import io.nekohasekai.sagernet.utils.toolbox.ServiceProbeResult
import io.nekohasekai.sagernet.utils.toolbox.ServiceProbeSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ConnectivityTestActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityConnectivityTestBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private var testJob: Job? = null
    private val resultsMap = LinkedHashMap<String, ServiceProbeResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_connectivity_test),
        )
        styleUi()
        initPendingState()
        renderServices()

        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, _ -> renderServices() }
        binding.btnRun.setOnClickListener {
            if (testJob?.isActive == true) {
                cancelTest()
            } else {
                startTest()
            }
        }
        binding.btnCopyReport.setOnClickListener { copyReport() }
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
        testJob?.cancel()
    }

    private fun styleUi() {
        binding.scrollConnectivity.applyListInsets()
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardSummary, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconConnectivity, R.drawable.baseline_public_24)
        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnRun)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnCopyReport)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.root)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        binding.progressIndicator.setIndicatorColor(accent)
        binding.progressIndicator.trackColor = ColorUtils.setAlphaComponent(accent, 45)
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) = Unit

    override fun onServiceConnected(service: ISagerNetService) = Unit

    private fun selectedCategory(): ConnectivityCategory? = when {
        binding.chipCatAi.isChecked -> ConnectivityCategory.AI
        binding.chipCatStreaming.isChecked -> ConnectivityCategory.STREAMING
        binding.chipCatSocial.isChecked -> ConnectivityCategory.SOCIAL
        binding.chipCatDeveloper.isChecked -> ConnectivityCategory.DEVELOPER
        else -> null
    }

    private fun initPendingState() {
        resultsMap.clear()
        for (spec in ConnectivityChecker.SERVICES) {
            resultsMap[spec.id] = ServiceProbeResult(spec, ConnectivityStatus.PENDING)
        }
    }

    private fun startTest() {
        val cat = selectedCategory()
        val targets = ConnectivityChecker.SERVICES.filter { cat == null || it.category == cat }
        for (spec in targets) {
            resultsMap[spec.id] = ServiceProbeResult(spec, ConnectivityStatus.PENDING)
        }
        renderServices()

        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnRun.setText(R.string.ping_stop)

        val viaProxy = binding.chipViaProxy.isChecked
        val cm = getSystemService(ConnectivityManager::class.java)

        testJob?.cancel()
        testJob = lifecycleScope.launch {
            ConnectivityChecker.runChecks(targets, viaProxy, cm).collect { res ->
                resultsMap[res.spec.id] = res
                renderServices()
            }
            binding.progressIndicator.visibility = View.GONE
            binding.btnRun.setText(R.string.connectivity_run)
        }
    }

    private fun cancelTest() {
        testJob?.cancel()
        testJob = null
        binding.progressIndicator.visibility = View.GONE
        binding.btnRun.setText(R.string.connectivity_run)
    }

    private fun renderServices() {
        val cat = selectedCategory()
        val visible = ConnectivityChecker.SERVICES
            .mapNotNull { resultsMap[it.id] }
            .filter { cat == null || it.spec.category == cat }

        val okCount = visible.count { it.status == ConnectivityStatus.AVAILABLE }
        val warnCount = visible.count { it.status == ConnectivityStatus.PARTIAL }
        val failCount = visible.count { it.status == ConnectivityStatus.BLOCKED || it.status == ConnectivityStatus.ERROR }
        val doneCount = visible.count {
            it.status != ConnectivityStatus.PENDING && it.status != ConnectivityStatus.TESTING
        }
        if (doneCount > 0) {
            binding.textSummaryCounts.text = getString(
                R.string.connectivity_summary_counts,
                okCount,
                warnCount,
                failCount,
                visible.size,
            )
        } else {
            binding.textSummaryCounts.setText(R.string.tools_connectivity_test_summary)
        }

        binding.containerServices.removeAllViews()
        visible.forEachIndexed { idx, item ->
            val card = buildServiceCard(item)
            if (idx > 0) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp2px(8)
            }
            binding.containerServices.addView(card)
        }
    }

    private fun testSingleService(spec: ServiceProbeSpec) {
        if (testJob?.isActive == true) return
        val viaProxy = binding.chipViaProxy.isChecked
        val cm = getSystemService(ConnectivityManager::class.java)
        lifecycleScope.launch {
            ConnectivityChecker.runChecks(listOf(spec), viaProxy, cm).collect { res ->
                resultsMap[res.spec.id] = res
                renderServices()
            }
        }
    }

    private fun buildServiceCard(item: ServiceProbeResult): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { testSingleService(item.spec) }
        }
        ExpressiveSettingsHelper.styleExpressiveCard(card, cornerRadiusDp = 20f)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val px = dp2px(14)
            val py = dp2px(12)
            setPadding(px, py, px, py)
        }

        val iconSizeContainer = dp2px(40)
        val iconHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSizeContainer, iconSizeContainer).apply {
                marginEnd = dp2px(12)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(12).toFloat()
                setColor(Theme.resolveNestedBoxBackground(this@ConnectivityTestActivity))
                val stroke = Theme.resolveCardStrokeColor(this@ConnectivityTestActivity, selected = false)
                if (stroke != Color.TRANSPARENT) {
                    setStroke(dp2px(1), ColorUtils.setAlphaComponent(stroke, 110))
                }
            }
        }
        val iconRes = item.spec.iconRes.takeIf { it != 0 } ?: R.drawable.baseline_public_24
        val iconView = ImageView(this).apply {
            val innerSize = dp2px(24)
            layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
            if (item.spec.adaptDarkTint && Theme.usingNightMode(this@ConnectivityTestActivity)) {
                setColorFilter(Color.parseColor("#F3F4F6"))
            }
        }
        iconHolder.addView(iconView)

        val left = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp2px(8)
            }
            orientation = LinearLayout.VERTICAL
        }

        left.addView(
            TextView(this).apply {
                text = item.spec.name
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(getColorAttr(android.R.attr.textColorPrimary))
            },
        )

        val metaParts = buildList {
            add(categoryLabel(item.spec.category))
            if (item.region.isNotBlank()) add(item.region)
            if (item.rttMs > 0) add("${item.rttMs} ms")
            if (item.detail.isNotBlank()) add(item.detail)
        }
        left.addView(
            TextView(this).apply {
                text = metaParts.joinToString(" · ")
                textSize = 12.5f
                fontFeatureSettings = "tnum"
                setPadding(0, dp2px(2), 0, 0)
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            },
        )

        val (badgeText, badgeAccent) = when (item.status) {
            ConnectivityStatus.PENDING -> getString(R.string.connectivity_status_ready) to null
            ConnectivityStatus.TESTING -> getString(R.string.connectivity_status_testing) to null
            ConnectivityStatus.AVAILABLE -> getString(R.string.connectivity_status_ok) to getColor(R.color.material_green_700)
            ConnectivityStatus.PARTIAL -> getString(R.string.connectivity_status_partial) to getColor(R.color.material_amber_900)
            ConnectivityStatus.BLOCKED -> getString(R.string.connectivity_status_blocked) to getColor(R.color.material_red_500)
            ConnectivityStatus.ERROR -> getString(R.string.connectivity_status_error) to getColor(R.color.material_red_500)
        }

        val badge = TextView(this).apply {
            text = badgeText
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp2px(8), dp2px(4), dp2px(8), dp2px(4))
        }
        ExpressiveSettingsHelper.styleExpressiveBadge(badge, accentColor = badgeAccent)

        row.addView(iconHolder)
        row.addView(left)
        row.addView(badge)
        card.addView(row)
        return card
    }

    private fun categoryLabel(cat: ConnectivityCategory): String = when (cat) {
        ConnectivityCategory.AI -> getString(R.string.connectivity_cat_ai)
        ConnectivityCategory.STREAMING -> getString(R.string.connectivity_cat_streaming)
        ConnectivityCategory.SOCIAL -> getString(R.string.connectivity_cat_social)
        ConnectivityCategory.DEVELOPER -> getString(R.string.connectivity_cat_developer)
    }

    private fun copyReport() {
        val text = ConnectivityChecker.SERVICES.mapNotNull { resultsMap[it.id] }.joinToString("\n") { r ->
            val regionPart = if (r.region.isNotBlank()) " [${r.region}]" else ""
            val rttPart = if (r.rttMs > 0) " (${r.rttMs} ms)" else ""
            "${r.spec.name}: ${r.status.name}$regionPart$rttPart ${r.detail}".trim()
        }
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cb.setPrimaryClip(ClipData.newPlainText("Connectivity Report", text))
        Toast.makeText(this, R.string.network_interfaces_copied, Toast.LENGTH_SHORT).show()
    }
}
