package io.nekohasekai.sagernet.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.ActivityCellularNetworkBinding
import io.nekohasekai.sagernet.ui.settings.ExpressiveSettingsHelper
import io.nekohasekai.sagernet.widget.applyListInsets

class CellularNetworkActivity : ThemedActivity() {

    private lateinit var binding: ActivityCellularNetworkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCellularNetworkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExpressiveSettingsHelper.attachActivityFloatingHeader(
            this,
            binding.root,
            getString(R.string.cellular_network_title),
        )
        styleExpressiveCards()

        binding.btnRadioInfoModern.setOnClickListener {
            openFirstAvailable(
                componentIntent(
                    "com.android.phone",
                    "com.android.phone.settings.RadioInfo",
                ),
            )
        }

        binding.btnRadioInfoLegacy.setOnClickListener {
            openFirstAvailable(
                componentIntent(
                    "com.android.settings",
                    "com.android.settings.RadioInfo",
                ),
                componentIntent(
                    "com.android.settings",
                    "com.android.settings.Settings\$RadioInfoActivity",
                ),
            )
        }

        binding.btnTestingMenu.setOnClickListener {
            openFirstAvailable(
                componentIntent(
                    "com.android.settings",
                    "com.android.settings.TestingSettings",
                ),
                componentIntent(
                    "com.android.settings",
                    "com.android.settings.Settings\$TestingSettingsActivity",
                ),
            )
        }

        binding.btnStandardSettings.setOnClickListener {
            openFirstAvailable(
                Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        }
    }

    private fun styleExpressiveCards() {
        binding.scrollCellular.applyListInsets()
        listOf(
            binding.cardDescription,
            binding.cardPinNetworkType,
            binding.cardMethods,
        ).forEach { ExpressiveSettingsHelper.styleExpressiveCard(it, cornerRadiusDp = 20f) }

        ExpressiveSettingsHelper.applySquircleIcon(binding.iconCellularBadge, R.drawable.ic_device_data_usage)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconPinNetworkBadge, R.drawable.ic_baseline_push_pin_24)
        ExpressiveSettingsHelper.applySquircleIcon(binding.iconMethodsBadge, R.drawable.ic_action_settings)
        ExpressiveSettingsHelper.styleExpressiveNestedBox(binding.boxCellularDesc, cornerRadiusDp = 14f, strokeAlpha = 160)

        ExpressiveSettingsHelper.stylePrimaryButton(binding.btnRadioInfoModern)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnRadioInfoLegacy)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnTestingMenu)
        ExpressiveSettingsHelper.styleOutlinedButton(binding.btnStandardSettings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun componentIntent(packageName: String, className: String) =
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, className)
        }

    private fun openFirstAvailable(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(
            this,
            R.string.cellular_network_method_unavailable,
            Toast.LENGTH_LONG,
        ).show()
    }
}
