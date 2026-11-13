package io.nekohasekai.sagernet.ui

import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Bundle
import android.os.Process
import android.text.format.Formatter
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficDataBatch
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupRepo
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.ActivityTrafficUsageBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.ui.widget.TrafficBarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrafficUsageActivity : ThemedActivity(), SagerConnection.Callback {

    private data class BreakdownEntry(
        val title: String,
        val subtitle: String,
        val rx: Long,
        val tx: Long,
    ) {
        val total: Long get() = rx + tx
    }

    private lateinit var binding: ActivityTrafficUsageBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
    private var lastSpeed = SpeedDisplayData()
    private var currentState: BaseService.State = DataStore.serviceState
    private var currentProfileName: String? = null
    private var refreshJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrafficUsageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_traffic_usage),
        )
        styleUi()

        binding.chipGroupScope.setOnCheckedStateChangeListener { _, _ -> reloadBreakdown() }
        binding.chipHideZero.setOnCheckedChangeListener { _, _ -> reloadBreakdown() }
        binding.btnResetTraffic.setOnClickListener { confirmResetTraffic() }

        updateSessionUi()
        reloadBreakdown()
    }

    override fun onStart() {
        super.onStart()
        connection.connect(this, this)
        startPeriodicOsRefresh()
    }

    override fun onStop() {
        refreshJob?.cancel()
        refreshJob = null
        connection.disconnect(this)
        super.onStop()
    }

    private fun styleUi() {
        binding.scrollTraffic.applyListInsets()
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardLive, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardRecorded, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxLiveRates, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxSystemCounters, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconTraffic, R.drawable.ic_baseline_multiline_chart_24)
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeSessionState)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnResetTraffic)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.root)
    }

    private fun startPeriodicOsRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                updateSystemCounters()
                delay(2000L)
            }
        }
    }

    private fun updateSystemCounters() {
        val uid = Process.myUid()
        val uidRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        val uidTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

        binding.textAppTraffic.text = getString(
            R.string.traffic_app_uid_line,
            formatBytes(uidRx),
            formatBytes(uidTx),
        )
        binding.textDeviceTraffic.text = getString(
            R.string.traffic_device_total_line,
            formatBytes(totalRx),
            formatBytes(totalTx),
        )
    }

    private fun updateSessionUi() {
        val connected = currentState.connected
        binding.badgeSessionState.text = if (connected) {
            getString(R.string.traffic_state_active)
        } else {
            getString(R.string.network_interfaces_vpn_standby)
        }
        val stateAccent = if (connected) getColor(R.color.material_green_700) else null
        ExpressiveSettingsHelper.styleExpressiveBadge(binding.badgeSessionState, accentColor = stateAccent)

        binding.textActiveProfile.text = if (connected && !currentProfileName.isNullOrBlank()) {
            currentProfileName
        } else if (connected) {
            getString(R.string.traffic_state_active)
        } else {
            getString(R.string.network_interfaces_vpn_standby)
        }

        binding.textProxyRate.text = "↓ ${formatBytes(lastSpeed.rxRateProxy)}/s  ↑ ${formatBytes(lastSpeed.txRateProxy)}/s"
        binding.textDirectRate.text = "↓ ${formatBytes(lastSpeed.rxRateDirect)}/s  ↑ ${formatBytes(lastSpeed.txRateDirect)}/s"
        binding.barSession.setTraffic(lastSpeed.rxTotal, lastSpeed.txTotal)
        binding.textSessionTotals.text = getString(
            R.string.traffic_session_totals_line,
            formatBytes(lastSpeed.rxTotal),
            formatBytes(lastSpeed.txTotal),
            formatBytes(lastSpeed.rxTotal + lastSpeed.txTotal),
        )
        updateSystemCounters()
    }

    private fun reloadBreakdown() {
        val showGroups = binding.chipScopeGroups.isChecked
        val hideZero = binding.chipHideZero.isChecked
        lifecycleScope.launch {
            val (totalRx, totalTx, entries) = withContext(Dispatchers.IO) {
                val groups = GroupRepo.all()
                val groupNames = groups.associate { it.id to it.displayName() }
                var sumRx = 0L
                var sumTx = 0L
                val profileEntries = mutableListOf<BreakdownEntry>()
                val groupEntries = mutableListOf<BreakdownEntry>()

                for (group in groups) {
                    val members = SagerDatabase.proxyDao.getByGroup(group.id)
                    var gRx = 0L
                    var gTx = 0L
                    for (p in members) {
                        val rx = p.rx.coerceAtLeast(0L)
                        val tx = p.tx.coerceAtLeast(0L)
                        sumRx += rx
                        sumTx += tx
                        gRx += rx
                        gTx += tx
                        if (!hideZero || rx + tx > 0L) {
                            profileEntries.add(
                                BreakdownEntry(
                                    title = p.displayName(),
                                    subtitle = "${p.displayType()} · ${groupNames[group.id] ?: ""}",
                                    rx = rx,
                                    tx = tx,
                                ),
                            )
                        }
                    }
                    if (!hideZero || gRx + gTx > 0L) {
                        groupEntries.add(
                            BreakdownEntry(
                                title = group.displayName(),
                                subtitle = resources.getQuantityString(R.plurals.traffic_group_members, members.size, members.size),
                                rx = gRx,
                                tx = gTx,
                            ),
                        )
                    }
                }

                val selected = (if (showGroups) groupEntries else profileEntries)
                    .sortedByDescending { it.total }
                    .take(50)
                Triple(sumRx, sumTx, selected)
            }

            binding.textRecordedTotals.text = getString(
                R.string.traffic_recorded_totals_line,
                formatBytes(totalRx),
                formatBytes(totalTx),
                formatBytes(totalRx + totalTx),
            )
            renderBreakdown(entries)
        }
    }

    private fun renderBreakdown(entries: List<BreakdownEntry>) {
        binding.containerBreakdown.removeAllViews()
        if (entries.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            ExpressiveSettingsHelper.styleExpressiveCard(emptyCard, cornerRadiusDp = 20f)
            val tv = TextView(this).apply {
                val p = dp2px(18)
                setPadding(p, p, p, p)
                text = getString(R.string.traffic_empty)
                textSize = 13.5f
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            }
            emptyCard.addView(tv)
            binding.containerBreakdown.addView(emptyCard)
            return
        }

        val maxEntryTotal = entries.maxOfOrNull { it.total }.coerceAtLeastOne()
        val grandTotal = entries.sumOf { it.total }.coerceAtLeast(1L)

        entries.forEachIndexed { idx, item ->
            val card = buildBreakdownCard(idx + 1, item, maxEntryTotal, grandTotal)
            if (idx > 0) {
                (card.layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp2px(8)
            }
            binding.containerBreakdown.addView(card)
        }
    }

    private fun buildBreakdownCard(
        rank: Int,
        entry: BreakdownEntry,
        maxTotal: Long,
        grandTotal: Long,
    ): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        ExpressiveSettingsHelper.styleExpressiveCard(card, cornerRadiusDp = 20f)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val px = dp2px(16)
            val py = dp2px(12)
            setPadding(px, py, px, py)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val rankBadge = TextView(this).apply {
            text = "#$rank"
            textSize = 11f
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp2px(7), dp2px(3), dp2px(7), dp2px(3))
        }
        ExpressiveSettingsHelper.styleExpressiveBadge(rankBadge)

        val titleCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(10), 0, dp2px(8), 0)
        }
        titleCol.addView(
            TextView(this).apply {
                text = entry.title
                textSize = 14.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(getColorAttr(android.R.attr.textColorPrimary))
            },
        )
        titleCol.addView(
            TextView(this).apply {
                text = entry.subtitle
                textSize = 12f
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            },
        )

        val sharePct = (entry.total * 100.0) / grandTotal.toDouble()
        val totalTv = TextView(this).apply {
            text = "${formatBytes(entry.total)}\n${String.format("%.1f%%", sharePct)}"
            gravity = Gravity.END
            textSize = 12.5f
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColorAttr(android.R.attr.textColorPrimary))
        }

        topRow.addView(rankBadge)
        topRow.addView(titleCol)
        topRow.addView(totalTv)
        root.addView(topRow)

        val bar = TrafficBarView(this).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(8))
            lp.topMargin = dp2px(8)
            layoutParams = lp
            setTraffic(entry.rx, entry.tx, maxTotal)
        }
        root.addView(bar)

        val subTv = TextView(this).apply {
            text = "↓ ${formatBytes(entry.rx)}   ↑ ${formatBytes(entry.tx)}"
            textSize = 11.5f
            fontFeatureSettings = "tnum"
            setPadding(0, dp2px(5), 0, 0)
            setTextColor(getColorAttr(android.R.attr.textColorSecondary))
        }
        root.addView(subTv)

        card.addView(root)
        return card
    }

    private fun confirmResetTraffic() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.traffic_reset_confirm_title)
            .setMessage(R.string.traffic_reset_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.traffic_reset) { _, _ ->
                lifecycleScope.launch {
                    val allIds = withContext(Dispatchers.IO) {
                        GroupRepo.all().flatMap { g -> SagerDatabase.proxyDao.getByGroup(g.id).map { it.id } }
                    }.toLongArray()
                    if (allIds.isNotEmpty()) {
                        runCatching { connection.service?.resetTraffic(allIds) }
                        withContext(Dispatchers.IO) { ProfileManager.resetTraffic(allIds) }
                    }
                    Toast.makeText(this@TrafficUsageActivity, R.string.traffic_reset_done, Toast.LENGTH_SHORT).show()
                    reloadBreakdown()
                }
            }
            .show()
    }

    private fun formatBytes(bytes: Long): String = Formatter.formatFileSize(this, bytes.coerceAtLeast(0L))

    private fun Long?.coerceAtLeastOne(): Long = (this ?: 1L).coerceAtLeast(1L)

    // SagerConnection.Callback
    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        lastSpeed = stats
        updateSessionUi()
    }

    override suspend fun cbTrafficUpdate(data: TrafficDataBatch) {
        reloadBreakdown()
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        currentState = state
        currentProfileName = profileName
        updateSessionUi()
    }

    override fun onServiceConnected(service: ISagerNetService) {
        val stateOrdinal = runCatching { service.state }.getOrNull()
        if (stateOrdinal != null && stateOrdinal in BaseService.State.entries.indices) {
            currentState = BaseService.State.entries[stateOrdinal]
        }
        currentProfileName = runCatching { service.profileName }.getOrNull()
        updateSessionUi()
    }
}
