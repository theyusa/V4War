package io.nekohasekai.sagernet.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.backup.BackupEntry
import io.nekohasekai.sagernet.databinding.LayoutToolsBinding
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.utils.HapticHelper
import io.nekohasekai.sagernet.utils.Theme

class ToolsFragment : ToolbarFragment(R.layout.layout_tools) {

    companion object {
        const val ARG_TAB = "initial_tab"
        const val TAB_NETWORK = 0
        const val TAB_BACKUP = 1
        private const val TAB_COUNT = 2

        fun newInstance(tab: Int = TAB_NETWORK): ToolsFragment {
            return ToolsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TAB, tab)
                }
            }
        }
    }

    private var toolsBinding: LayoutToolsBinding? = null
    private var tabMediator: TabLayoutMediator? = null
    private var pendingBackup: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            pendingBackup = BackupEntry.takePending()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        val isAmoled = Theme.isBlackTheme(ctx)
        val screenBg = if (isAmoled) Color.BLACK else ctx.getColorAttr(android.R.attr.colorBackground)
        view.setBackgroundColor(screenBg)

        val appbar = view.findViewById<AppBarLayout>(R.id.appbar)
        appbar?.setBackgroundColor(screenBg)
        toolbar?.visibility = View.GONE
        ExpressiveSettingsHelper.attachFloatingPillHeader(appbar, ctx)?.update(
            context = ctx,
            title = getString(R.string.menu_tools),
            iconRes = R.drawable.ic_navigation_menu,
            contentDescription = getString(R.string.navigation_open_drawer),
        ) {
            (activity as? MainActivity)?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
        }

        if (pendingBackup == null) {
            pendingBackup = BackupEntry.takePending()
        }
        val hasPendingBackup = pendingBackup != null

        val binding = LayoutToolsBinding.bind(view)
        toolsBinding = binding
        ExpressiveSettingsHelper.styleExpressiveTabs(
            binding.toolsTabContainer,
            binding.toolsTab,
            cornerRadiusDp = 14f,
        )
        binding.toolsPager.adapter = ToolsAdapter()

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.toolsTab, binding.toolsPager) { tab, position ->
            when (position) {
                TAB_NETWORK -> {
                    tab.text = getString(R.string.tools_tab_network)
                    tab.setIcon(R.drawable.baseline_public_24)
                }

                TAB_BACKUP -> {
                    tab.text = getString(R.string.tools_tab_backup)
                    tab.setIcon(R.drawable.ic_file_cloud_queue)
                }
            }
            tab.view.setOnLongClickListener { true }
        }.also { it.attach() }

        val initialTab = arguments?.getInt(ARG_TAB, -1) ?: -1
        if (hasPendingBackup || initialTab == TAB_BACKUP) {
            binding.toolsPager.setCurrentItem(TAB_BACKUP, false)
        } else if (initialTab in 0 until TAB_COUNT) {
            binding.toolsPager.setCurrentItem(initialTab, false)
        }
        binding.toolsTab.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                HapticHelper.perform(ctx, binding.toolsTab, HapticHelper.HapticKind.SELECTION)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                HapticHelper.perform(ctx, binding.toolsTab, HapticHelper.HapticKind.SELECTION)
            }
        })
    }

    override fun onDestroyView() {
        tabMediator?.detach()
        tabMediator = null
        toolsBinding?.toolsPager?.adapter = null
        toolsBinding = null
        super.onDestroyView()
    }

    private fun consumePendingBackup(): Uri? {
        val entry = pendingBackup
        pendingBackup = null
        return entry
    }

    inner class ToolsAdapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = TAB_COUNT
        override fun createFragment(position: Int): Fragment = when (position) {
            TAB_BACKUP -> BackupFragment.newInstance(consumePendingBackup())
            else -> NetworkFragment()
        }
    }
}
