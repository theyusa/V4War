package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutNetworkBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.ui.settings.ExpressiveShape
import io.nekohasekai.sagernet.utils.Theme

class NetworkFragment : Fragment(R.layout.layout_network) {

    private data class ToolCardSpec(
        val card: MaterialCardView,
        val iconView: ImageView,
        val chevronView: ImageView,
        val iconRes: Int,
        val destination: Class<*>,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val isNight = Theme.usingNightMode(ctx)
        val isAmoled = Theme.isBlackTheme(ctx)
        val screenBg = if (isAmoled) Color.BLACK else ctx.getColorAttr(android.R.attr.colorBackground)
        view.setBackgroundColor(screenBg)

        val binding = LayoutNetworkBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(bottom = bars.bottom + dp2px(64))
            insets
        }

        val (badgeContainer, badgeIconColor) = ExpressiveSettingsHelper.resolveBadgeColors(ctx, isNight, isAmoled)
        val chevronTint = ColorUtils.setAlphaComponent(
            ctx.getColorAttr(android.R.attr.textColorSecondary),
            185,
        )

        val specs = listOf(
            ToolCardSpec(
                binding.lanSharingCard,
                binding.lanSharingIcon,
                binding.lanSharingChevron,
                R.drawable.ic_hardware_router,
                LanSharingActivity::class.java,
            ),
            ToolCardSpec(
                binding.dnsLeakCard,
                binding.dnsLeakIcon,
                binding.dnsLeakChevron,
                R.drawable.ic_action_dns,
                DnsLeakActivity::class.java,
            ),
            ToolCardSpec(
                binding.natDiscoveryCard,
                binding.natDiscoveryIcon,
                binding.natDiscoveryChevron,
                R.drawable.ic_baseline_nat_24,
                StunActivity::class.java,
            ),
            ToolCardSpec(
                binding.cellularNetworkCard,
                binding.cellularNetworkIcon,
                binding.cellularNetworkChevron,
                R.drawable.ic_device_data_usage,
                CellularNetworkActivity::class.java,
            ),
            ToolCardSpec(
                binding.networkInterfacesCard,
                binding.networkInterfacesIcon,
                binding.networkInterfacesChevron,
                R.drawable.ic_baseline_layers_24,
                NetworkInterfacesActivity::class.java,
            ),
            ToolCardSpec(
                binding.ipLookupCard,
                binding.ipLookupIcon,
                binding.ipLookupChevron,
                R.drawable.ic_test_ip_24,
                IpLookupActivity::class.java,
            ),
            ToolCardSpec(
                binding.dnsLookupCard,
                binding.dnsLookupIcon,
                binding.dnsLookupChevron,
                R.drawable.ic_baseline_manage_search_24,
                DnsLookupActivity::class.java,
            ),
            ToolCardSpec(
                binding.localPingCard,
                binding.localPingIcon,
                binding.localPingChevron,
                R.drawable.ic_test_latency_24,
                LocalPingActivity::class.java,
            ),
            ToolCardSpec(
                binding.connectivityTestCard,
                binding.connectivityTestIcon,
                binding.connectivityTestChevron,
                R.drawable.baseline_public_24,
                ConnectivityTestActivity::class.java,
            ),
            ToolCardSpec(
                binding.trafficUsageCard,
                binding.trafficUsageIcon,
                binding.trafficUsageChevron,
                R.drawable.ic_baseline_multiline_chart_24,
                TrafficUsageActivity::class.java,
            ),
        )

        for (spec in specs) {
            ExpressiveSettingsHelper.styleExpressiveCard(spec.card, cornerRadiusDp = 20f)
            ResourcesCompat.getDrawable(ctx.resources, spec.iconRes, null)?.let { raw ->
                spec.iconView.setImageDrawable(
                    ExpressiveSettingsHelper.createMaskedIcon(
                        ctx,
                        raw,
                        badgeContainer,
                        badgeIconColor,
                        sizeDp = 42,
                        iconSizeDp = 22,
                        shape = ExpressiveShape.SQUIRCLE,
                    ),
                )
            }
            spec.chevronView.drawable?.mutate()?.let { d ->
                DrawableCompat.setTint(d, chevronTint)
                spec.chevronView.setImageDrawable(d)
            }
            spec.card.setOnClickListener {
                val clickCtx = context ?: return@setOnClickListener
                startActivity(Intent(clickCtx, spec.destination))
            }
        }
    }
}
