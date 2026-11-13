package io.nekohasekai.sagernet.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import androidx.core.view.GravityCompat
import androidx.core.view.doOnPreDraw
import com.google.android.material.appbar.AppBarLayout
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.update.UpdateActivity
import io.nekohasekai.sagernet.update.UpdateChecker
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.applyListInsets

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val binding = LayoutAboutBinding.bind(view)
        val screenBg = if (Theme.isBlackTheme(ctx)) Color.BLACK else ctx.getColorAttr(android.R.attr.colorBackground)
        view.setBackgroundColor(screenBg)

        val appbar = view.findViewById<AppBarLayout>(R.id.appbar)
        appbar?.setBackgroundColor(screenBg)
        toolbar?.visibility = View.GONE
        ExpressiveSettingsHelper.attachFloatingPillHeader(appbar, ctx)?.update(
            context = ctx,
            title = getString(R.string.menu_about),
            iconRes = R.drawable.ic_navigation_menu,
            contentDescription = getString(R.string.navigation_open_drawer),
        ) {
            (activity as? MainActivity)?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
        }

        binding.aboutScroll.applyListInsets()

        listOf(
            binding.titleCard,
            binding.cardAboutVersion,
            binding.cardAboutProject,
            binding.cardAboutLicense,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        ExpressiveSettingsHelper.styleExpressiveBadge(binding.aboutVersionBadge)
        binding.aboutVersionBadge.text = SagerNet.appVersionNameForDisplay
        binding.textAboutProjectHeader.setTextColor(ExpressiveSettingsHelper.resolveAccentColor(ctx))

        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutVersion, R.drawable.ic_baseline_update_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutUpdate, R.drawable.ic_baseline_download_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutCore, R.drawable.ic_baseline_layers_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutGithub, R.drawable.ic_baseline_code_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutWebsite, R.drawable.baseline_public_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutSupport, R.drawable.ic_baseline_favorite_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconAboutLicense, R.drawable.ic_baseline_menu_book_24)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxLicense, cornerRadiusDp = 14f)

        binding.textAboutVersionValue.text = SagerNet.appVersionNameForDisplay
        binding.aboutVersionRow.setOnClickListener {
            requireContext().launchCustomTab(UpdateChecker.RELEASES_PAGE)
        }

        if (BuildConfig.IN_APP_UPDATER) {
            binding.aboutUpdateRow.visibility = View.VISIBLE
            binding.textAboutUpdateChannel.setText(
                if (DataStore.allowBetaUpdate) R.string.update_channel_beta
                else R.string.update_channel_stable,
            )
            binding.aboutUpdateRow.setOnClickListener {
                UpdateActivity.start(requireContext())
            }
        } else {
            binding.aboutUpdateRow.visibility = View.GONE
        }

        binding.textAboutCoreTitle.text = getString(R.string.version_x, "V4War Core")
        binding.textAboutCoreValue.text = shortRef(BuildConfig.THRONE_CORE_REF)
        binding.aboutCoreRow.setOnClickListener { copyCoreRef() }
        binding.aboutCoreRow.setOnLongClickListener {
            copyCoreRef()
            true
        }

        binding.aboutGithubRow.setOnClickListener {
            requireContext().launchCustomTab("https://github.com/theyusa/V4War")
        }
        binding.aboutWebsiteRow.setOnClickListener {
            requireContext().launchCustomTab("https://github.com/theyusa/V4War#readme")
        }
        binding.aboutSupportRow.setOnClickListener {
            requireContext().launchCustomTab("https://github.com/theyusa/V4War")
        }

        binding.license.maxLines = LICENSE_COLLAPSED_MAX_LINES
        var isLicenseExpanded = false
        binding.licenseToggle.setOnClickListener {
            val scrollY = binding.aboutScroll.scrollY
            isLicenseExpanded = !isLicenseExpanded
            binding.license.maxLines = if (isLicenseExpanded) {
                Int.MAX_VALUE
            } else {
                LICENSE_COLLAPSED_MAX_LINES
            }
            binding.licenseIndicator.animate()
                .rotation(if (isLicenseExpanded) 180f else 0f)
                .setDuration(180L)
                .start()
            binding.aboutScroll.doOnPreDraw {
                binding.aboutScroll.scrollTo(0, scrollY)
            }
        }

        runOnDefaultDispatcher {
            val license = view.context.assets.open("LICENSE").bufferedReader().readText()
            onMainDispatcher {
                binding.license.text = license
                Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
            }
        }
    }

    /** A commit hash is shortened to 8 characters, a branch or tag name stays as it is. */
    private fun shortRef(ref: String): String =
        if (ref.length == 40 && ref.all { it in '0'..'9' || it in 'a'..'f' }) ref.take(8) else ref

    private fun copyCoreRef() {
        val copied = SagerNet.trySetPrimaryClip(BuildConfig.THRONE_CORE_REF)
        // Android 13+ confirms a clipboard copy itself
        if (copied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        snackbar(if (copied) R.string.grp_copied else R.string.action_export_err).show()
    }

    companion object {
        private const val LICENSE_COLLAPSED_MAX_LINES = 8
    }
}
