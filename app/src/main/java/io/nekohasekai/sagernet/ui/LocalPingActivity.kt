package io.nekohasekai.sagernet.ui

import android.graphics.Typeface
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.databinding.ActivityLocalPingBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.toolbox.LocalPingEngine
import io.nekohasekai.sagernet.utils.toolbox.PingProtocol
import io.nekohasekai.sagernet.utils.toolbox.PingSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocalPingActivity : ThemedActivity(), SagerConnection.Callback {

    private lateinit var binding: ActivityLocalPingBinding
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
    private var pingJob: Job? = null
    private val samples = mutableListOf<PingSample>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalPingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.tools_local_ping),
        )
        styleUi()

        binding.chipGroupProtocol.setOnCheckedStateChangeListener { _, _ ->
            val proto = selectedProtocol()
            binding.inputLayoutPort.visibility = if (proto == PingProtocol.ICMP) View.GONE else View.VISIBLE
            if (proto == PingProtocol.UDP && binding.editPort.text?.toString() == "443") {
                binding.editPort.setText("53")
            } else if (proto == PingProtocol.TCP && binding.editPort.text?.toString() == "53") {
                binding.editPort.setText("443")
            }
        }

        binding.btnStartStop.setOnClickListener {
            if (pingJob?.isActive == true) {
                stopPing()
            } else {
                startPing()
            }
        }

        binding.btnClear.setOnClickListener {
            if (pingJob?.isActive == true) stopPing()
            samples.clear()
            refreshStatsAndLog()
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
        pingJob?.cancel()
    }

    private fun styleUi() {
        binding.scrollLocalPing.applyListInsets()
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardControls, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardMetrics, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveCard(binding.cardLog, cornerRadiusDp = 20f)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxStats, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconPing, R.drawable.ic_test_latency_24)
        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnStartStop)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnClear)
        ExpressiveSettingsHelper.styleExpressiveChips(binding.root)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        binding.progressIndicator.setIndicatorColor(accent)
        binding.progressIndicator.trackColor = ColorUtils.setAlphaComponent(accent, 45)
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) = Unit

    override fun onServiceConnected(service: ISagerNetService) = Unit

    private fun selectedProtocol(): PingProtocol = when {
        binding.chipProtoUdp.isChecked -> PingProtocol.UDP
        binding.chipProtoIcmp.isChecked -> PingProtocol.ICMP
        else -> PingProtocol.TCP
    }

    private fun selectedCount(): Int = when {
        binding.chipCount25.isChecked -> 25
        binding.chipCountContinuous.isChecked -> 0
        else -> 10
    }

    private fun startPing() {
        val host = binding.editHost.text?.toString().orEmpty().trim()
        if (host.isEmpty()) {
            Toast.makeText(this, R.string.ping_empty_host_error, Toast.LENGTH_SHORT).show()
            return
        }
        val protocol = selectedProtocol()
        val port = binding.editPort.text?.toString()?.toIntOrNull()
            ?: if (protocol == PingProtocol.UDP) 53 else 443
        val count = selectedCount()
        val direct = binding.chipBypassVpn.isChecked
        val cm = getSystemService(ConnectivityManager::class.java)

        samples.clear()
        refreshStatsAndLog()

        binding.progressIndicator.visibility = View.VISIBLE
        binding.btnStartStop.setText(R.string.ping_stop)

        pingJob?.cancel()
        pingJob = lifecycleScope.launch {
            LocalPingEngine.startPing(
                host = host,
                port = port,
                protocol = protocol,
                count = count,
                intervalMs = 650L,
                timeoutMs = 2500,
                directPhysical = direct,
                connectivity = cm,
            ).collect { sample ->
                samples.add(sample)
                refreshStatsAndLog()
            }
            binding.progressIndicator.visibility = View.GONE
            binding.btnStartStop.setText(R.string.ping_start)
        }
    }

    private fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        binding.progressIndicator.visibility = View.GONE
        binding.btnStartStop.setText(R.string.ping_start)
    }

    private fun refreshStatsAndLog() {
        binding.chartRtt.setSamples(samples)
        val stats = LocalPingEngine.computeStats(samples)
        if (stats.sent == 0) {
            binding.textStatAvg.text = "-"
            binding.textStatMinmax.text = "-"
            binding.textStatJitter.text = "-"
            binding.textStatLoss.text = "-"
        } else if (stats.received == 0) {
            binding.textStatAvg.text = "-"
            binding.textStatMinmax.text = "-"
            binding.textStatJitter.text = "-"
            binding.textStatLoss.text = String.format("%.0f%% (%d/%d)", stats.lossPercent, stats.received, stats.sent)
        } else {
            binding.textStatAvg.text = String.format("%.1f ms", stats.avgMs)
            binding.textStatMinmax.text = String.format("%.0f/%.0f", stats.minMs, stats.maxMs)
            binding.textStatJitter.text = String.format("%.1f ms", stats.jitterMs)
            binding.textStatLoss.text = String.format("%.0f%%", stats.lossPercent)
        }

        binding.containerSamples.removeAllViews()
        val recent = samples.asReversed().take(20)
        recent.forEach { sample ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp2px(4), 0, dp2px(4))
            }
            val seqTv = TextView(this).apply {
                text = "#${sample.seq}"
                textSize = 12.5f
                fontFeatureSettings = "tnum"
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
                setPadding(0, 0, dp2px(10), 0)
            }
            val detailTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = sample.detail.ifBlank { "-" }
                textSize = 12.5f
                setTextColor(getColorAttr(android.R.attr.textColorSecondary))
            }
            val rttTv = TextView(this).apply {
                text = if (sample.success) String.format("%.1f ms", sample.rttMs) else "TIMEOUT"
                textSize = 13f
                fontFeatureSettings = "tnum"
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(
                    if (sample.success) getColorAttr(android.R.attr.textColorPrimary)
                    else getColor(R.color.material_red_500),
                )
            }
            row.addView(seqTv)
            row.addView(detailTv)
            row.addView(rttTv)
            binding.containerSamples.addView(row)
        }
    }
}
