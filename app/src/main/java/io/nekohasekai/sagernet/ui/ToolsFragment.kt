package io.nekohasekai.sagernet.ui

import android.graphics.Color
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
import io.nekohasekai.sagernet.utils.Theme

class ToolsFragment : ToolbarFragment(R.layout.layout_tools) {

    companion object {
        const val ARG_TAB = "initial_tab"
        const val TAB_NETWORK = 0
        const val TAB_BACKUP = 1

        fun newInstance(tab: Int = TAB_NETWORK): ToolsFragment {
            return ToolsFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TAB, tab)
                }
            }
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

        val pendingBackup = BackupEntry.takePending()
        val tools = listOf(
            NetworkFragment(),
            BackupFragment.newInstance(pendingBackup),
        )

        val binding = LayoutToolsBinding.bind(view)
        ExpressiveSettingsHelper.styleExpressiveTabs(
            binding.toolsTabContainer,
            binding.toolsTab,
            cornerRadiusDp = 14f,
        )
        binding.toolsPager.adapter = ToolsAdapter(tools)

        TabLayoutMediator(binding.toolsTab, binding.toolsPager) { tab, position ->
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
        }.attach()

        val initialTab = arguments?.getInt(ARG_TAB, -1) ?: -1
        if (pendingBackup != null || initialTab == TAB_BACKUP) {
            binding.toolsPager.setCurrentItem(TAB_BACKUP, false)
        } else if (initialTab in 0 until tools.size) {
            binding.toolsPager.setCurrentItem(initialTab, false)
        }
    }

    inner class ToolsAdapter(private val tools: List<Fragment>) : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = tools.size
        override fun createFragment(position: Int): Fragment = tools[position]
    }
}
