// Based on ccding/go-stun (Apache-2.0) as carried by NekoBoxPlus
package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.graphics.ColorUtils
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.ActivityStunBinding
import io.nekohasekai.sagernet.databinding.ItemStunServerResultBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets
import io.nekohasekai.sagernet.utils.stun.AndroidStunSocketFactory
import io.nekohasekai.sagernet.utils.stun.DefaultStunSocketFactory
import io.nekohasekai.sagernet.utils.stun.StunAssessment
import io.nekohasekai.sagernet.utils.stun.StunAssessmentKind
import io.nekohasekai.sagernet.utils.stun.StunPreferences
import io.nekohasekai.sagernet.utils.stun.StunPreset
import io.nekohasekai.sagernet.utils.stun.StunProtocolCodes
import io.nekohasekai.sagernet.utils.stun.StunServerListParser
import io.nekohasekai.sagernet.utils.stun.StunServerParseResult
import io.nekohasekai.sagernet.utils.stun.StunServerUiResult
import kotlinx.coroutines.launch
import io.nekohasekai.sagernet.utils.stun.StunServerStatus
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.core.content.ContextCompat

class StunActivity : ThemedActivity() {

    private lateinit var binding: ActivityStunBinding
    private val viewModel: StunTestViewModel by viewModels()

    private var selectedPreset = StunPreset.BALANCED
    private var detailsExpanded = false
    private val serverResultsAdapter = ServerResultsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStunBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.stun_test),
        )
        styleExpressiveCards()

        setupViews()
        observeViewModel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupViews() {
        selectedPreset = StunPreferences.preset
        binding.switchPhysicalNetwork.isChecked = StunPreferences.testPhysicalNetwork
        binding.editCustomServers.setText(StunPreferences.customServers)

        updatePresetUi(selectedPreset)

        binding.radioGroupPreset.setOnCheckedChangeListener { _, checkedId ->
            val preset = when (checkedId) {
                R.id.radioPresetBalanced -> StunPreset.BALANCED
                R.id.radioPresetFull -> StunPreset.FULL
                R.id.radioPresetFast -> StunPreset.FAST
                R.id.radioPresetCustom -> StunPreset.CUSTOM
                else -> StunPreset.BALANCED
            }
            selectedPreset = preset
            StunPreferences.preset = preset
            updatePresetUi(preset)
        }

        binding.editCustomServers.doAfterTextChanged {
            val text = it?.toString().orEmpty()
            StunPreferences.customServers = text
            binding.textCustomServersError.visibility = View.GONE
        }

        binding.switchPhysicalNetwork.setOnCheckedChangeListener { _, isChecked ->
            StunPreferences.testPhysicalNetwork = isChecked
        }

        // VPN active warning check
        val isVpnActive = DataStore.serviceState.connected
        binding.layoutVpnWarning.visibility = if (isVpnActive) View.VISIBLE else View.GONE

        // Server results recycler
        binding.recyclerServerResults.apply {
            layoutManager = LinearLayoutManager(this@StunActivity)
            adapter = serverResultsAdapter
        }

        binding.btnAction.setOnClickListener {
            if (viewModel.uiState.value.isRunning) {
                viewModel.cancel()
            } else {
                startTest()
            }
        }

        binding.btnToggleDetails.setOnClickListener {
            detailsExpanded = !detailsExpanded
            updateDetailsExpansion()
        }

        binding.btnCopyResult.setOnClickListener {
            copyResultToClipboard()
        }
    }

    private fun styleExpressiveCards() {
        binding.scrollStun.applyListInsets()
        listOf(
            binding.cardPreset,
            binding.cardNetworkOptions,
            binding.cardAction,
            binding.cardResult,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        ExpressiveSettingsHelper.applySquircleIcon(binding.iconPresetBadge, R.drawable.ic_baseline_nat_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconNetworkOptionBadge, R.drawable.ic_hardware_router)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconResultBadge, R.drawable.ic_baseline_check_circle_24)

        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxPresetRadios, cornerRadiusDp = 16f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.layoutVpnWarning, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxQualityMetrics, cornerRadiusDp = 14f, strokeAlpha = 160)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxTechnicalSummary, cornerRadiusDp = 14f, strokeAlpha = 160)

        val accent = ExpressiveSettingsHelper.resolveAccentColor(this)
        val textSecondary = getColorAttr(android.R.attr.textColorSecondary)
        val radioTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, textSecondary),
        )
        listOf(
            binding.radioPresetBalanced,
            binding.radioPresetFull,
            binding.radioPresetFast,
            binding.radioPresetCustom,
        ).forEach { CompoundButtonCompat.setButtonTintList(it, radioTint) }

        binding.progressBarStun.setIndicatorColor(accent)
        binding.progressBarStun.trackColor = ColorUtils.setAlphaComponent(accent, 45)

        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnAction)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnToggleDetails)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnCopyResult)
    }

    private fun updatePresetUi(preset: StunPreset) {
        when (preset) {
            StunPreset.BALANCED -> binding.radioPresetBalanced.isChecked = true
            StunPreset.FULL -> binding.radioPresetFull.isChecked = true
            StunPreset.FAST -> binding.radioPresetFast.isChecked = true
            StunPreset.CUSTOM -> binding.radioPresetCustom.isChecked = true
        }
        binding.textPresetDescription.setText(preset.descriptionRes)
        binding.layoutCustomServers.visibility = if (preset == StunPreset.CUSTOM) View.VISIBLE else View.GONE
    }

    private fun updateDetailsExpansion() {
        binding.layoutTechnicalDetails.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
        binding.btnToggleDetails.setText(
            if (detailsExpanded) R.string.stun_hide_details else R.string.stun_show_details
        )
        binding.btnToggleDetails.setIconResource(
            if (detailsExpanded) R.drawable.ic_baseline_expand_less_24 else R.drawable.ic_baseline_expand_more_24
        )
    }

    private fun startTest() {
        val servers = if (selectedPreset == StunPreset.CUSTOM) {
            when (val parsed = StunServerListParser.parse(binding.editCustomServers.text.toString())) {
                is StunServerParseResult.Valid -> parsed.servers
                is StunServerParseResult.Invalid -> {
                    val errorMsg = when (parsed.reason) {
                        StunServerParseResult.Reason.EMPTY -> getString(R.string.stun_custom_error_empty)
                        StunServerParseResult.Reason.FORMAT -> getString(R.string.stun_custom_error_format, parsed.line)
                        StunServerParseResult.Reason.PORT -> getString(R.string.stun_custom_error_port, parsed.line)
                        StunServerParseResult.Reason.TOO_MANY -> getString(R.string.stun_custom_error_too_many, StunServerListParser.MAX_SERVERS)
                    }
                    binding.textCustomServersError.text = errorMsg
                    binding.textCustomServersError.visibility = View.VISIBLE
                    return
                }
            }
        } else {
            selectedPreset.servers
        }

        val socketFactory = if (binding.switchPhysicalNetwork.isChecked) {
            val physicalNetwork = findPhysicalNetwork()
            if (physicalNetwork == null) {
                Toast.makeText(this, R.string.stun_no_physical_network, Toast.LENGTH_SHORT).show()
                DefaultStunSocketFactory()
            } else {
                AndroidStunSocketFactory(physicalNetwork)
            }
        } else {
            DefaultStunSocketFactory()
        }

        binding.cardResult.visibility = View.GONE
        viewModel.start(servers, if (DataStore.vpnIpv6) 1 else 0, socketFactory)
    }

    private fun findPhysicalNetwork(): Network? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun renderUiState(state: StunUiState) {
        if (state.isRunning) {
            binding.btnAction.setText(R.string.stun_action_cancel)
            binding.progressBarStun.visibility = View.VISIBLE
            binding.textProgress.visibility = View.VISIBLE
            val completedCount = state.results.count {
                it.status == StunServerStatus.COMPLETED ||
                    it.status == StunServerStatus.PARTIAL ||
                    it.status == StunServerStatus.FAILED
            }
            binding.textProgress.text = resources.getQuantityString(
                R.plurals.stun_testing_servers,
                state.totalServers,
                state.totalServers,
            )
            setInputsEnabled(false)

            if (state.results.isNotEmpty()) {
                binding.cardResult.visibility = View.VISIBLE
                if (state.assessment != null) {
                    binding.textAssessmentTitle.setText(assessmentTitleRes(state.assessment.kind))
                    binding.textAssessmentImpact.setText(assessmentImpactRes(state.assessment.kind))
                } else {
                    binding.textAssessmentTitle.setText(R.string.stun_attest_loading)
                    binding.textAssessmentImpact.text = getString(
                        R.string.stun_successful_servers_value,
                        completedCount,
                        state.totalServers,
                    )
                }
                updateQualityTelemetry(state.results, state.assessment)
                binding.textTechnicalSummary.text = buildTechnicalSummary(state.results, state.assessment)
                serverResultsAdapter.submitList(state.results)
            }
        } else {
            binding.btnAction.setText(R.string.stun_action_start)
            binding.progressBarStun.visibility = View.GONE
            binding.textProgress.visibility = View.GONE
            setInputsEnabled(true)

            if (state.assessment != null) {
                displayResult(state.assessment, state.results)
            }
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.radioPresetBalanced.isEnabled = enabled
        binding.radioPresetFull.isEnabled = enabled
        binding.radioPresetFast.isEnabled = enabled
        binding.radioPresetCustom.isEnabled = enabled
        binding.editCustomServers.isEnabled = enabled
        binding.switchPhysicalNetwork.isEnabled = enabled
    }

    private fun displayResult(assessment: StunAssessment, results: List<StunServerUiResult>) {
        binding.cardResult.visibility = View.VISIBLE
        binding.textAssessmentTitle.setText(assessmentTitleRes(assessment.kind))
        binding.textAssessmentImpact.setText(assessmentImpactRes(assessment.kind))
        updateQualityTelemetry(results, assessment)
        binding.textTechnicalSummary.text = buildTechnicalSummary(results, assessment)
        serverResultsAdapter.submitList(results)
    }

    private fun updateQualityTelemetry(results: List<StunServerUiResult>, assessment: StunAssessment?) {
        val rep = assessment?.representative ?: results.firstOrNull { it.bindingSuccess }
        if (rep == null) {
            binding.badgeNatType.visibility = View.GONE
            binding.boxQualityMetrics.visibility = View.GONE
            return
        }

        val badgeText = natCodeBadge(rep.natType, assessment?.kind)
        if (badgeText.isNotEmpty()) {
            binding.badgeNatType.visibility = View.VISIBLE
            binding.badgeNatType.text = badgeText
            ExpressiveSettingsHelper.styleExpressiveBadge(
                binding.badgeNatType,
                natBadgeColor(rep.natType, assessment?.kind),
            )
        } else {
            binding.badgeNatType.visibility = View.GONE
        }

        binding.boxQualityMetrics.visibility = View.VISIBLE
        val effectiveRtt = when {
            rep.medianRttMs >= 0.0 -> "${rep.medianRttMs.toInt().coerceAtLeast(1)} ms"
            rep.rttMs > 0 -> "${rep.rttMs} ms"
            else -> getString(R.string.stun_unknown_short)
        }
        binding.textMetricRtt.text = effectiveRtt
        binding.textMetricJitter.text = if (rep.jitterMs >= 0.0) {
            String.format(java.util.Locale.US, "%.1f ms", rep.jitterMs)
        } else {
            getString(R.string.stun_unknown_short)
        }
        binding.textMetricLoss.text = if (rep.packetLossPercent >= 0.0) {
            String.format(java.util.Locale.US, "%.0f%%", rep.packetLossPercent)
        } else {
            getString(R.string.stun_unknown_short)
        }
        binding.textMetricPortPreserved.text = when (rep.portPreserved) {
            true -> getString(R.string.stun_yes)
            false -> getString(R.string.stun_no)
            null -> getString(R.string.stun_unknown_short)
        }

        val extEndpoint = if (rep.externalAddress.isNotEmpty()) {
            if (rep.externalAddress.contains(':')) "[${rep.externalAddress}]:${rep.externalPort}"
            else "${rep.externalAddress}:${rep.externalPort}"
        } else ""
        val localEndpoint = if (rep.localAddress.isNotEmpty() && rep.localPort > 0) {
            if (rep.localAddress.contains(':')) "[${rep.localAddress}]:${rep.localPort}"
            else "${rep.localAddress}:${rep.localPort}"
        } else ""
        if (localEndpoint.isNotEmpty() && extEndpoint.isNotEmpty()) {
            binding.textEndpointSummary.visibility = View.VISIBLE
            binding.textEndpointSummary.text = "$localEndpoint  ->  $extEndpoint"
        } else if (extEndpoint.isNotEmpty()) {
            binding.textEndpointSummary.visibility = View.VISIBLE
            binding.textEndpointSummary.text = extEndpoint
        } else {
            binding.textEndpointSummary.visibility = View.GONE
        }
    }

    private fun natCodeBadge(natType: Int, kind: StunAssessmentKind?): String = when (natType) {
        StunProtocolCodes.NAT_NONE -> "OPEN"
        StunProtocolCodes.NAT_FULL -> "NAT1"
        StunProtocolCodes.NAT_RESTRICTED -> "NAT2"
        StunProtocolCodes.NAT_PORT_RESTRICTED -> "NAT3"
        StunProtocolCodes.NAT_SYMMETRIC -> "NAT4"
        StunProtocolCodes.NAT_CONE -> "NAT1-3"
        StunProtocolCodes.NAT_BLOCKED -> "BLOCKED"
        StunProtocolCodes.NAT_SYMMETRIC_UDP_FIREWALL -> "FW"
        else -> when (kind) {
            StunAssessmentKind.INCONSISTENT -> "MIXED"
            StunAssessmentKind.BASIC_ONLY -> "UDP"
            else -> ""
        }
    }

    private fun natBadgeColor(natType: Int, kind: StunAssessmentKind?): Int = when (natType) {
        StunProtocolCodes.NAT_NONE, StunProtocolCodes.NAT_FULL -> Color.parseColor("#10B981")
        StunProtocolCodes.NAT_RESTRICTED -> Color.parseColor("#22C55E")
        StunProtocolCodes.NAT_PORT_RESTRICTED -> Color.parseColor("#F59E0B")
        StunProtocolCodes.NAT_SYMMETRIC -> Color.parseColor("#EF4444")
        StunProtocolCodes.NAT_CONE -> ExpressiveSettingsHelper.resolveAccentColor(this)
        StunProtocolCodes.NAT_BLOCKED -> Color.parseColor("#EF4444")
        else -> if (kind == StunAssessmentKind.FAILED) Color.parseColor("#EF4444")
        else ExpressiveSettingsHelper.resolveAccentColor(this)
    }

    private fun buildTechnicalSummary(results: List<StunServerUiResult>, assessment: StunAssessment?): String {
        val lines = mutableListOf<String>()
        assessment?.representative?.let { rep ->
            lines += getString(R.string.stun_mapping_value, behaviorLabel(rep.mappingBehavior))
            lines += getString(R.string.stun_filtering_value, behaviorLabel(rep.filteringBehavior))
            lines += getString(R.string.stun_nat_type_value, natLabel(rep.natType))
            if (rep.localAddress.isNotEmpty() && rep.localPort > 0) {
                lines += getString(R.string.stun_local_endpoint_value, "${rep.localAddress}:${rep.localPort}")
            }
            if (rep.serverSoftware.isNotEmpty()) {
                lines += getString(R.string.stun_software_value, rep.serverSoftware)
            }
        }

        val endpoints = results.filter { it.bindingSuccess }
            .map { if (it.externalAddress.contains(':')) "[${it.externalAddress}]:${it.externalPort}" else "${it.externalAddress}:${it.externalPort}" }
            .distinct()
        if (endpoints.isNotEmpty()) {
            lines += getString(R.string.stun_external_endpoints_value, endpoints.joinToString(", "))
        }

        lines += getString(
            R.string.stun_successful_servers_value,
            results.count { it.bindingSuccess },
            results.size,
        )
        return lines.joinToString("\n")
    }

    private fun behaviorLabel(code: Int): String = when (code) {
        StunProtocolCodes.BEHAVIOR_ENDPOINT -> getString(R.string.stun_behavior_endpoint)
        StunProtocolCodes.BEHAVIOR_ADDRESS -> getString(R.string.stun_behavior_address)
        StunProtocolCodes.BEHAVIOR_ADDRESS_AND_PORT -> getString(R.string.stun_behavior_address_and_port)
        else -> getString(R.string.stun_value_unknown)
    }

    private fun natLabel(code: Int): String = when (code) {
        StunProtocolCodes.NAT_NONE -> getString(R.string.stun_nat_open)
        StunProtocolCodes.NAT_BLOCKED -> getString(R.string.stun_nat_blocked)
        StunProtocolCodes.NAT_FULL -> getString(R.string.stun_nat_full)
        StunProtocolCodes.NAT_SYMMETRIC -> getString(R.string.stun_nat_symmetric)
        StunProtocolCodes.NAT_RESTRICTED -> getString(R.string.stun_nat_restricted)
        StunProtocolCodes.NAT_PORT_RESTRICTED -> getString(R.string.stun_nat_port_restricted)
        StunProtocolCodes.NAT_SYMMETRIC_UDP_FIREWALL -> getString(R.string.stun_nat_symmetric_firewall)
        StunProtocolCodes.NAT_CONE -> getString(R.string.stun_nat_cone)
        else -> getString(R.string.stun_value_unknown)
    }

    private fun assessmentTitleRes(kind: StunAssessmentKind): Int = when (kind) {
        StunAssessmentKind.FAVORABLE -> R.string.stun_assessment_favorable
        StunAssessmentKind.MODERATE -> R.string.stun_assessment_moderate
        StunAssessmentKind.RESTRICTIVE -> R.string.stun_assessment_restrictive
        StunAssessmentKind.OPEN -> R.string.stun_assessment_open
        StunAssessmentKind.INCONSISTENT -> R.string.stun_assessment_inconsistent
        StunAssessmentKind.BASIC_ONLY -> R.string.stun_assessment_basic
        StunAssessmentKind.FAILED -> R.string.stun_assessment_failed
        StunAssessmentKind.CANCELLED -> R.string.stun_assessment_cancelled
    }

    private fun assessmentImpactRes(kind: StunAssessmentKind): Int = when (kind) {
        StunAssessmentKind.FAVORABLE -> R.string.stun_impact_favorable
        StunAssessmentKind.MODERATE -> R.string.stun_impact_moderate
        StunAssessmentKind.RESTRICTIVE -> R.string.stun_impact_restrictive
        StunAssessmentKind.OPEN -> R.string.stun_impact_open
        StunAssessmentKind.INCONSISTENT -> R.string.stun_impact_inconsistent
        StunAssessmentKind.BASIC_ONLY -> R.string.stun_impact_basic
        StunAssessmentKind.FAILED -> R.string.stun_impact_failed
        StunAssessmentKind.CANCELLED -> R.string.stun_impact_cancelled
    }

    private fun copyResultToClipboard() {
        val title = binding.textAssessmentTitle.text.toString()
        val impact = binding.textAssessmentImpact.text.toString()
        val summary = binding.textTechnicalSummary.text.toString()
        val fullText = "$title\n\n$impact\n\n$summary"

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("STUN NAT Result", fullText))
        Toast.makeText(this, R.string.network_interfaces_copied, Toast.LENGTH_SHORT).show()
    }

    private inner class ServerResultsAdapter : ListAdapter<StunServerUiResult, ServerResultViewHolder>(
        object : DiffUtil.ItemCallback<StunServerUiResult>() {
            override fun areItemsTheSame(oldItem: StunServerUiResult, newItem: StunServerUiResult): Boolean =
                oldItem.server == newItem.server

            override fun areContentsTheSame(oldItem: StunServerUiResult, newItem: StunServerUiResult): Boolean =
                oldItem == newItem
        },
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerResultViewHolder {
            val itemBinding = ItemStunServerResultBinding.inflate(layoutInflater, parent, false)
            return ServerResultViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ServerResultViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private inner class ServerResultViewHolder(
        private val itemBinding: ItemStunServerResultBinding,
    ) : RecyclerView.ViewHolder(itemBinding.root) {

        fun bind(item: StunServerUiResult) {
            ExpressiveSettingsHelper.styleExpressiveCard(itemBinding.cardServerItem, cornerRadiusDp = 16f)
            ExpressiveSettingsHelper.styleExpressiveBadge(itemBinding.badgeServerRtt)
            itemBinding.textServerName.text = item.server.ifBlank { getString(R.string.stun_unknown_server) }

            // RTT Ping indicator
            if (item.rttMs > 0) {
                itemBinding.badgeServerRtt.visibility = View.VISIBLE
                itemBinding.badgeServerRtt.text = getString(R.string.stun_rtt_value, item.rttMs)
            } else {
                itemBinding.badgeServerRtt.visibility = View.GONE
            }

            // Status & Progress spinner
            when (item.status) {
                StunServerStatus.PENDING -> {
                    itemBinding.progressServer.visibility = View.GONE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_pending)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.material_grey_500),
                    )
                    itemBinding.textServerDuration.visibility = View.GONE
                    itemBinding.textServerDetails.text = getString(R.string.stun_server_pending_details)
                    return
                }
                StunServerStatus.RUNNING -> {
                    itemBinding.progressServer.visibility = View.VISIBLE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_running)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.material_blue_500),
                    )
                    itemBinding.textServerDuration.visibility = View.GONE
                    if (item.bindingSuccess) {
                        val ep = if (item.externalAddress.contains(':')) "[${item.externalAddress}]:${item.externalPort}" else "${item.externalAddress}:${item.externalPort}"
                        itemBinding.textServerDetails.text = getString(R.string.stun_external_address_value, ep, if (item.ipFamily == 2) "IPv6" else "IPv4") + "\n" + getString(R.string.stun_server_testing_details)
                    } else {
                        itemBinding.textServerDetails.text = getString(R.string.stun_server_testing_details)
                    }
                    return
                }
                StunServerStatus.COMPLETED -> {
                    itemBinding.progressServer.visibility = View.GONE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_complete)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.material_green_500),
                    )
                }
                StunServerStatus.PARTIAL -> {
                    itemBinding.progressServer.visibility = View.GONE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_partial)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.material_amber_500),
                    )
                }
                StunServerStatus.CANCELLED -> {
                    itemBinding.progressServer.visibility = View.GONE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_cancelled)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.material_grey_500),
                    )
                }
                StunServerStatus.FAILED -> {
                    itemBinding.progressServer.visibility = View.GONE
                    itemBinding.badgeServerStatus.text = getString(R.string.stun_server_status_failed)
                    ExpressiveSettingsHelper.styleExpressiveBadge(
                        itemBinding.badgeServerStatus,
                        ContextCompat.getColor(this@StunActivity, R.color.color_route_block),
                    )
                }
            }

            if (item.durationMilliseconds > 0) {
                itemBinding.textServerDuration.visibility = View.VISIBLE
                itemBinding.textServerDuration.text = getString(R.string.stun_duration_value, item.durationMilliseconds)
            } else {
                itemBinding.textServerDuration.visibility = View.GONE
            }

            val details = mutableListOf<String>()
            if (item.bindingSuccess) {
                val ep = if (item.externalAddress.contains(':')) "[${item.externalAddress}]:${item.externalPort}" else "${item.externalAddress}:${item.externalPort}"
                details += getString(R.string.stun_external_address_value, ep, if (item.ipFamily == 2) "IPv6" else "IPv4")
                details += getString(R.string.stun_nat_type_value, natLabel(item.natType))
            }
            if (item.mappingBehavior != StunProtocolCodes.BEHAVIOR_UNKNOWN) {
                details += getString(R.string.stun_mapping_value, behaviorLabel(item.mappingBehavior))
            }
            if (item.filteringBehavior != StunProtocolCodes.BEHAVIOR_UNKNOWN) {
                details += getString(R.string.stun_filtering_value, behaviorLabel(item.filteringBehavior))
            }
            if (item.medianRttMs >= 0.0 && item.packetLossPercent >= 0.0) {
                details += String.format(
                    java.util.Locale.US,
                    "%s: %d ms • %s: %.1f ms • %s: %.0f%%",
                    getString(R.string.stun_metric_rtt),
                    item.medianRttMs.toInt().coerceAtLeast(1),
                    getString(R.string.stun_metric_jitter),
                    item.jitterMs.coerceAtLeast(0.0),
                    getString(R.string.stun_metric_loss),
                    item.packetLossPercent.coerceAtLeast(0.0),
                )
            }
            if (item.serverSoftware.isNotEmpty()) {
                details += getString(R.string.stun_software_value, item.serverSoftware)
            }
            if (item.errorCode.isNotBlank()) {
                val errLabel = when (item.errorCode) {
                    "behavior_unsupported" -> getString(R.string.stun_error_behavior_unsupported)
                    "dns" -> getString(R.string.stun_error_dns)
                    "timeout", "deadline" -> getString(R.string.stun_error_timeout)
                    "cancelled" -> getString(R.string.stun_server_status_cancelled)
                    "configuration" -> getString(R.string.stun_error_configuration)
                    else -> getString(R.string.stun_error_network)
                }
                details += getString(R.string.stun_issue_value, errLabel)
                if (item.errorMessage.isNotBlank()) {
                    details += getString(R.string.stun_diagnostic_value, item.errorMessage)
                }
            }
            if (item.warningCode == "response_address_mismatch") {
                details += getString(R.string.stun_warning_value, getString(R.string.stun_warning_response_address_mismatch))
            }

            itemBinding.textServerDetails.text = details.joinToString("\n")
        }
    }
}
