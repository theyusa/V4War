package tr.theyusa.v4war.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow

internal interface AppLanguageController {
    var value: String
    val flow: Flow<String>
}

internal enum class AppLanguage(
    val tag: String,
    val displayName: String?,
) {
    SYSTEM("", null),
    ENGLISH("en-US", "English"),
    TURKISH("tr", "Türkçe");

    companion object {
        private val tagMap = entries.associateBy { it.tag }
        fun fromTag(tag: String): AppLanguage? = tagMap[tag]
    }
}

@Composable
internal expect fun rememberApplyNightMode(): (Int) -> Unit

@Composable
internal expect fun rememberThemeExtraColors(): List<Color>

@Composable
internal expect fun rememberAppLanguageController(defaultTag: String): AppLanguageController

@Composable
internal expect fun AutoConnectPreference()

@Composable
internal expect fun PlatformGeneralOptions(needReload: () -> Unit)

@Composable
internal expect fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean)

@Composable
internal expect fun PlatformSecurityOptions()

@Composable
internal expect fun MeteredNetworkPreference(needReload: () -> Unit)

@Composable
internal expect fun HttpProxyBypassPreference(enabled: Boolean, needReload: () -> Unit)

@Composable
internal expect fun PlatformMiscOptions(needReload: () -> Unit)

@Composable
internal expect fun DisableProcessTextPreference()
