package tr.theyusa.v4war.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow

internal expect fun LazyListScope.autoConnect()

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

internal expect fun LazyListScope.platformGeneralOptions(needReload: () -> Unit)

internal expect fun LazyListScope.platformSecurityOptions()

internal expect fun LazyListScope.meteredNetworkSetting(needReload: () -> Unit)

internal expect fun LazyListScope.platformRouteOptions(
    needReload: () -> Unit,
    isVpnMode: Boolean,
)

internal expect fun LazyListScope.platformMiscOptions(needReload: () -> Unit)

@Composable
internal expect fun rememberThemeExtraColors(): List<Color>

@Composable
internal expect fun rememberAppLanguageController(defaultTag: String): AppLanguageController

internal expect fun LazyListScope.disableProcessText()

internal expect fun LazyListScope.httpProxyBypass(enabled: Boolean, needReload: () -> Unit)
