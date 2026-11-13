package io.nekohasekai.sagernet.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.getColorAttr

object Theme {

    @Volatile
    private var cacheListenerRegistered = false
    @Volatile
    private var cachedAppTheme: Int = -1
    @Volatile
    private var cachedAmoledTheme: Boolean? = null
    @Volatile
    private var cachedUseSystemTheme: Boolean? = null
    @Volatile
    private var cachedCustomThemeColor: Int? = null

    private fun ensureCacheListener() {
        if (cacheListenerRegistered) return
        synchronized(this) {
            if (cacheListenerRegistered) return
            cacheListenerRegistered = true
            runCatching {
                DataStore.configurationStore.registerChangeListener(
                    object : OnPreferenceDataStoreChangeListener {
                        override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
                            when (key) {
                                Key.APP_THEME -> cachedAppTheme = -1
                                Key.NIGHT_THEME -> currentNightMode = -1
                                Key.AMOLED_THEME -> cachedAmoledTheme = null
                                Key.USE_SYSTEM_THEME -> cachedUseSystemTheme = null
                                "custom_theme_color" -> cachedCustomThemeColor = null
                            }
                        }
                    }
                )
            }
        }
    }

    fun invalidateCache() {
        cachedAppTheme = -1
        currentNightMode = -1
        cachedAmoledTheme = null
        cachedUseSystemTheme = null
        cachedCustomThemeColor = null
    }

    private fun appTheme(): Int {
        ensureCacheListener()
        val cached = cachedAppTheme
        if (cached != -1) return cached
        return DataStore.appTheme.also { cachedAppTheme = it }
    }

    private fun nightTheme(): Int {
        ensureCacheListener()
        val cached = currentNightMode
        if (cached != -1) return cached
        return DataStore.nightTheme.also { currentNightMode = it }
    }

    private fun amoledTheme(): Boolean {
        ensureCacheListener()
        cachedAmoledTheme?.let { return it }
        return DataStore.amoledTheme.also { cachedAmoledTheme = it }
    }

    private fun useSystemTheme(): Boolean {
        ensureCacheListener()
        cachedUseSystemTheme?.let { return it }
        return DataStore.useSystemTheme.also { cachedUseSystemTheme = it }
    }

    private fun customThemeColor(): Int {
        ensureCacheListener()
        cachedCustomThemeColor?.let { return it }
        return DataStore.customThemeColor.also { cachedCustomThemeColor = it }
    }

    const val MONET = 0
    const val RED = 1
    const val PINK_SSR = 2
    const val PINK = 3
    const val PURPLE = 4
    const val DEEP_PURPLE = 5
    const val INDIGO = 6
    const val BLUE = 7
    const val LIGHT_BLUE = 8
    const val CYAN = 9
    const val TEAL = 10
    const val GREEN = 11
    const val LIGHT_GREEN = 12
    const val LIME = 13
    const val YELLOW = 14
    const val AMBER = 15
    const val ORANGE = 16
    const val DEEP_ORANGE = 17
    const val BROWN = 18
    const val GREY = 19
    const val BLUE_GREY = 20
    const val BLACK = 21
    const val VERDANT_MINT = 22
    const val WHITE = 23
    const val LIGHT_GRAY = 24
    const val CUSTOM = 99

    private fun defaultTheme() = VERDANT_MINT

    const val NIGHT_SYSTEM = 0
    const val NIGHT_DARK = 1
    const val NIGHT_LIGHT = 2
    const val NIGHT_AMOLED = 3
    const val NIGHT_PURE_AMOLED = 4

    fun activeThemeId(): Int {
        return if (useSystemTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MONET
        } else {
            appTheme()
        }
    }

    fun isColorfulSeed(themeId: Int): Boolean {
        return themeId !in setOf(BLACK, WHITE, LIGHT_GRAY) && isValidTheme(themeId)
    }

    fun rememberSeedTheme(themeId: Int) {
        if (isColorfulSeed(themeId)) {
            DataStore.lastSeedTheme = themeId
        }
    }

    fun resolveSeedRestoreTheme(): Int {
        val saved = DataStore.lastSeedTheme
        return if (isColorfulSeed(saved)) saved else defaultTheme()
    }

    fun isValidTheme(themeId: Int): Boolean {
        return themeId in MONET..LIGHT_GRAY || themeId == CUSTOM
    }

    fun getClosestThemeForColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val sat = hsv[1]

        if (sat < 0.12f) return GREY
        return when {
            hue in 345f..360f || hue < 15f -> RED
            hue in 15f..35f -> DEEP_ORANGE
            hue in 35f..50f -> ORANGE
            hue in 50f..65f -> AMBER
            hue in 65f..90f -> LIME
            hue in 90f..140f -> GREEN
            hue in 140f..165f -> VERDANT_MINT
            hue in 165f..185f -> TEAL
            hue in 185f..205f -> CYAN
            hue in 205f..230f -> BLUE
            hue in 230f..250f -> INDIGO
            hue in 250f..275f -> DEEP_PURPLE
            hue in 275f..315f -> PURPLE
            hue in 315f..345f -> PINK
            else -> VERDANT_MINT
        }
    }

    fun getSystemWallpaperColor(context: Context): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val colors = wallpaperManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    val primary = colors?.primaryColor?.toArgb()
                    if (primary != null && primary != 0) {
                        return primary
                    }
                }
                val sysAccent = context.getColor(android.R.color.system_accent1_600)
                if (sysAccent != 0) return sysAccent
            } catch (_: Throwable) {
            }
        }
        return null
    }

    fun apply(context: Context) {
        invalidateCache()
        context.setTheme(getTheme(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (appTheme() == MONET || useSystemTheme()) &&
            !isWhiteTheme(context) && !isLightGrayTheme(context) && !isBlackTheme(context) &&
            context is android.app.Activity
        ) {
            com.google.android.material.color.DynamicColors.applyIfAvailable(context)
        }
    }

    fun applyDialog(context: Context) {
        context.setTheme(getDialogTheme(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (appTheme() == MONET || useSystemTheme()) &&
            !isWhiteTheme(context) && !isLightGrayTheme(context) && !isBlackTheme(context) &&
            context is android.app.Activity
        ) {
            com.google.android.material.color.DynamicColors.applyIfAvailable(context)
        }
    }

    fun usingMonetTheme(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (useSystemTheme() || appTheme() == MONET)
    }

    private data class AmoledStyleQuad(
        val tinted: Int,
        val tintedDialog: Int,
        val pure: Int,
        val pureDialog: Int,
    )

    private val amoledStyles: Map<Int, AmoledStyleQuad> = mapOf(
        RED to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Red, R.style.Theme_SagerNet_Dialog_Amoled_Red,
            R.style.Theme_SagerNet_PureAmoled_Red, R.style.Theme_SagerNet_Dialog_PureAmoled_Red,
        ),
        PINK_SSR to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_PinkSSR, R.style.Theme_SagerNet_Dialog_Amoled_PinkSSR,
            R.style.Theme_SagerNet_PureAmoled_PinkSSR, R.style.Theme_SagerNet_Dialog_PureAmoled_PinkSSR,
        ),
        PINK to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Pink, R.style.Theme_SagerNet_Dialog_Amoled_Pink,
            R.style.Theme_SagerNet_PureAmoled_Pink, R.style.Theme_SagerNet_Dialog_PureAmoled_Pink,
        ),
        PURPLE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Purple, R.style.Theme_SagerNet_Dialog_Amoled_Purple,
            R.style.Theme_SagerNet_PureAmoled_Purple, R.style.Theme_SagerNet_Dialog_PureAmoled_Purple,
        ),
        DEEP_PURPLE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_DeepPurple, R.style.Theme_SagerNet_Dialog_Amoled_DeepPurple,
            R.style.Theme_SagerNet_PureAmoled_DeepPurple, R.style.Theme_SagerNet_Dialog_PureAmoled_DeepPurple,
        ),
        INDIGO to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Indigo, R.style.Theme_SagerNet_Dialog_Amoled_Indigo,
            R.style.Theme_SagerNet_PureAmoled_Indigo, R.style.Theme_SagerNet_Dialog_PureAmoled_Indigo,
        ),
        BLUE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Blue, R.style.Theme_SagerNet_Dialog_Amoled_Blue,
            R.style.Theme_SagerNet_PureAmoled_Blue, R.style.Theme_SagerNet_Dialog_PureAmoled_Blue,
        ),
        LIGHT_BLUE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_LightBlue, R.style.Theme_SagerNet_Dialog_Amoled_LightBlue,
            R.style.Theme_SagerNet_PureAmoled_LightBlue, R.style.Theme_SagerNet_Dialog_PureAmoled_LightBlue,
        ),
        CYAN to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Cyan, R.style.Theme_SagerNet_Dialog_Amoled_Cyan,
            R.style.Theme_SagerNet_PureAmoled_Cyan, R.style.Theme_SagerNet_Dialog_PureAmoled_Cyan,
        ),
        TEAL to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Teal, R.style.Theme_SagerNet_Dialog_Amoled_Teal,
            R.style.Theme_SagerNet_PureAmoled_Teal, R.style.Theme_SagerNet_Dialog_PureAmoled_Teal,
        ),
        GREEN to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Green, R.style.Theme_SagerNet_Dialog_Amoled_Green,
            R.style.Theme_SagerNet_PureAmoled_Green, R.style.Theme_SagerNet_Dialog_PureAmoled_Green,
        ),
        LIGHT_GREEN to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_LightGreen, R.style.Theme_SagerNet_Dialog_Amoled_LightGreen,
            R.style.Theme_SagerNet_PureAmoled_LightGreen, R.style.Theme_SagerNet_Dialog_PureAmoled_LightGreen,
        ),
        LIME to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Lime, R.style.Theme_SagerNet_Dialog_Amoled_Lime,
            R.style.Theme_SagerNet_PureAmoled_Lime, R.style.Theme_SagerNet_Dialog_PureAmoled_Lime,
        ),
        YELLOW to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Yellow, R.style.Theme_SagerNet_Dialog_Amoled_Yellow,
            R.style.Theme_SagerNet_PureAmoled_Yellow, R.style.Theme_SagerNet_Dialog_PureAmoled_Yellow,
        ),
        AMBER to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Amber, R.style.Theme_SagerNet_Dialog_Amoled_Amber,
            R.style.Theme_SagerNet_PureAmoled_Amber, R.style.Theme_SagerNet_Dialog_PureAmoled_Amber,
        ),
        ORANGE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Orange, R.style.Theme_SagerNet_Dialog_Amoled_Orange,
            R.style.Theme_SagerNet_PureAmoled_Orange, R.style.Theme_SagerNet_Dialog_PureAmoled_Orange,
        ),
        DEEP_ORANGE to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_DeepOrange, R.style.Theme_SagerNet_Dialog_Amoled_DeepOrange,
            R.style.Theme_SagerNet_PureAmoled_DeepOrange, R.style.Theme_SagerNet_Dialog_PureAmoled_DeepOrange,
        ),
        BROWN to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Brown, R.style.Theme_SagerNet_Dialog_Amoled_Brown,
            R.style.Theme_SagerNet_PureAmoled_Brown, R.style.Theme_SagerNet_Dialog_PureAmoled_Brown,
        ),
        GREY to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_Grey, R.style.Theme_SagerNet_Dialog_Amoled_Grey,
            R.style.Theme_SagerNet_PureAmoled_Grey, R.style.Theme_SagerNet_Dialog_PureAmoled_Grey,
        ),
        BLUE_GREY to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_BlueGrey, R.style.Theme_SagerNet_Dialog_Amoled_BlueGrey,
            R.style.Theme_SagerNet_PureAmoled_BlueGrey, R.style.Theme_SagerNet_Dialog_PureAmoled_BlueGrey,
        ),
        VERDANT_MINT to AmoledStyleQuad(
            R.style.Theme_SagerNet_Amoled_VerdantMint, R.style.Theme_SagerNet_Dialog_Amoled_VerdantMint,
            R.style.Theme_SagerNet_PureAmoled_VerdantMint, R.style.Theme_SagerNet_Dialog_PureAmoled_VerdantMint,
        ),
    )

    private fun resolveAmoledStyle(themeId: Int, isDialog: Boolean, isPure: Boolean): Int {
        val resolvedId = when (themeId) {
            CUSTOM -> getClosestThemeForColor(0xFF000000.toInt() or customThemeColor())
            MONET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    return when {
                        isDialog && isPure -> R.style.Theme_SagerNet_Dialog_PureAmoled_Monet
                        isDialog -> R.style.Theme_SagerNet_Dialog_Amoled_Monet
                        isPure -> R.style.Theme_SagerNet_PureAmoled_Monet
                        else -> R.style.Theme_SagerNet_Amoled_Monet
                    }
                }
                val sysColor = getSystemWallpaperColor(app)
                if (sysColor != null) getClosestThemeForColor(sysColor) else BLUE
            }
            else -> themeId
        }
        if (resolvedId in setOf(BLACK, WHITE, LIGHT_GRAY)) {
            return if (isDialog) R.style.Theme_SagerNet_Dialog_Black else R.style.Theme_SagerNet_Black
        }
        val quad = amoledStyles[resolvedId]
            ?: return if (isDialog) R.style.Theme_SagerNet_Dialog_Black else R.style.Theme_SagerNet_Black
        return when {
            isDialog && isPure -> quad.pureDialog
            isDialog -> quad.tintedDialog
            isPure -> quad.pure
            else -> quad.tinted
        }
    }

    fun getTheme(context: Context? = null): Int {
        val ctx = context ?: app
        val themeId = activeThemeId()
        if (themeId == BLACK) {
            return R.style.Theme_SagerNet_Black
        }
        if (usingNightMode(ctx) && themeId in setOf(WHITE, LIGHT_GRAY)) {
            return R.style.Theme_SagerNet_Black
        }
        if (usingNightMode(ctx)) {
            val mode = nightTheme()
            if (mode == NIGHT_PURE_AMOLED ||
                (mode != NIGHT_AMOLED && amoledTheme())
            ) {
                return resolveAmoledStyle(themeId, isDialog = false, isPure = true)
            }
            if (mode == NIGHT_AMOLED) {
                return resolveAmoledStyle(themeId, isDialog = false, isPure = false)
            }
        }
        return getTheme(themeId, ctx)
    }

    fun getDialogTheme(context: Context? = null): Int {
        val ctx = context ?: app
        val themeId = activeThemeId()
        if (themeId == BLACK) {
            return R.style.Theme_SagerNet_Dialog_Black
        }
        if (usingNightMode(ctx) && themeId in setOf(WHITE, LIGHT_GRAY)) {
            return R.style.Theme_SagerNet_Dialog_Black
        }
        if (usingNightMode(ctx)) {
            val mode = nightTheme()
            if (mode == NIGHT_PURE_AMOLED ||
                (mode != NIGHT_AMOLED && amoledTheme())
            ) {
                return resolveAmoledStyle(themeId, isDialog = true, isPure = true)
            }
            if (mode == NIGHT_AMOLED) {
                return resolveAmoledStyle(themeId, isDialog = true, isPure = false)
            }
        }
        return getDialogTheme(themeId, ctx)
    }

    fun getAmoledTheme(themeId: Int): Int {
        return resolveAmoledStyle(themeId, isDialog = false, isPure = nightTheme() == NIGHT_PURE_AMOLED)
    }

    fun getAmoledDialogTheme(themeId: Int): Int {
        return resolveAmoledStyle(themeId, isDialog = true, isPure = nightTheme() == NIGHT_PURE_AMOLED)
    }

    fun getTheme(theme: Int, context: Context? = null): Int {
        return when (theme) {
            CUSTOM -> getTheme(getClosestThemeForColor(0xFF000000.toInt() or customThemeColor()), context)
            MONET -> R.style.Theme_SagerNet_Monet
            RED -> R.style.Theme_SagerNet_Red
            PINK_SSR -> R.style.Theme_SagerNet_Pink_SSR
            PINK -> R.style.Theme_SagerNet
            PURPLE -> R.style.Theme_SagerNet_Purple
            DEEP_PURPLE -> R.style.Theme_SagerNet_DeepPurple
            INDIGO -> R.style.Theme_SagerNet_Indigo
            BLUE -> R.style.Theme_SagerNet_Blue
            LIGHT_BLUE -> R.style.Theme_SagerNet_LightBlue
            CYAN -> R.style.Theme_SagerNet_Cyan
            TEAL -> R.style.Theme_SagerNet_Teal
            GREEN -> R.style.Theme_SagerNet_Green
            LIGHT_GREEN -> R.style.Theme_SagerNet_LightGreen
            LIME -> R.style.Theme_SagerNet_Lime
            YELLOW -> R.style.Theme_SagerNet_Yellow
            AMBER -> R.style.Theme_SagerNet_Amber
            ORANGE -> R.style.Theme_SagerNet_Orange
            DEEP_ORANGE -> R.style.Theme_SagerNet_DeepOrange
            BROWN -> R.style.Theme_SagerNet_Brown
            GREY -> R.style.Theme_SagerNet_Grey
            BLUE_GREY -> R.style.Theme_SagerNet_BlueGrey
            BLACK -> R.style.Theme_SagerNet_Black
            VERDANT_MINT -> R.style.Theme_SagerNet_VerdantMint
            WHITE -> R.style.Theme_SagerNet_White
            LIGHT_GRAY -> R.style.Theme_SagerNet_LightGray
            else -> R.style.Theme_SagerNet_LightGray
        }
    }

    fun getDialogTheme(theme: Int, context: Context? = null): Int {
        return when (theme) {
            CUSTOM -> getDialogTheme(getClosestThemeForColor(0xFF000000.toInt() or customThemeColor()), context)
            MONET -> R.style.Theme_SagerNet_Dialog_Monet
            RED -> R.style.Theme_SagerNet_Dialog_Red
            PINK_SSR -> R.style.Theme_SagerNet_Dialog_Pink_SSR
            PINK -> R.style.Theme_SagerNet_Dialog
            PURPLE -> R.style.Theme_SagerNet_Dialog_Purple
            DEEP_PURPLE -> R.style.Theme_SagerNet_Dialog_DeepPurple
            INDIGO -> R.style.Theme_SagerNet_Dialog_Indigo
            BLUE -> R.style.Theme_SagerNet_Dialog_Blue
            LIGHT_BLUE -> R.style.Theme_SagerNet_Dialog_LightBlue
            CYAN -> R.style.Theme_SagerNet_Dialog_Cyan
            TEAL -> R.style.Theme_SagerNet_Dialog_Teal
            GREEN -> R.style.Theme_SagerNet_Dialog_Green
            LIGHT_GREEN -> R.style.Theme_SagerNet_Dialog_LightGreen
            LIME -> R.style.Theme_SagerNet_Dialog_Lime
            YELLOW -> R.style.Theme_SagerNet_Dialog_Yellow
            AMBER -> R.style.Theme_SagerNet_Dialog_Amber
            ORANGE -> R.style.Theme_SagerNet_Dialog_Orange
            DEEP_ORANGE -> R.style.Theme_SagerNet_Dialog_DeepOrange
            BROWN -> R.style.Theme_SagerNet_Dialog_Brown
            GREY -> R.style.Theme_SagerNet_Dialog_Grey
            BLUE_GREY -> R.style.Theme_SagerNet_Dialog_BlueGrey
            BLACK -> R.style.Theme_SagerNet_Dialog_Black
            VERDANT_MINT -> R.style.Theme_SagerNet_Dialog_VerdantMint
            WHITE -> R.style.Theme_SagerNet_Dialog_White
            LIGHT_GRAY -> R.style.Theme_SagerNet_Dialog_LightGray
            else -> R.style.Theme_SagerNet_Dialog_LightGray
        }
    }

    fun isSystemNight(context: Context = app): Boolean {
        val ctxUiMode = (context as? android.app.Activity)?.resources?.configuration?.uiMode
            ?: context.resources?.configuration?.uiMode
        if (ctxUiMode != null && (ctxUiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            return (ctxUiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val sysUiMode = android.content.res.Resources.getSystem().configuration.uiMode
        return (sysUiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun isWhiteTheme(context: Context? = null): Boolean {
        val ctx = context ?: app
        return !usingNightMode(ctx) && appTheme() == WHITE
    }

    fun isLightGrayTheme(context: Context? = null): Boolean {
        val ctx = context ?: app
        return !usingNightMode(ctx) && appTheme() == LIGHT_GRAY
    }

    fun isTintedAmoled(context: Context? = null): Boolean {
        val ctx = context ?: app
        val mode = nightTheme()
        return usingNightMode(ctx) && mode == NIGHT_AMOLED && !isMonochromeBlack(ctx)
    }

    fun isPureAmoled(context: Context? = null): Boolean {
        val ctx = context ?: app
        val mode = nightTheme()
        val pureFlag = mode == NIGHT_PURE_AMOLED || (mode != NIGHT_AMOLED && amoledTheme())
        return usingNightMode(ctx) && pureFlag && !isMonochromeBlack(ctx)
    }

    fun isAnyAmoled(context: Context? = null): Boolean {
        val ctx = context ?: app
        val mode = nightTheme()
        return usingNightMode(ctx) && (mode in setOf(NIGHT_AMOLED, NIGHT_PURE_AMOLED) || amoledTheme())
    }

    fun isBlackTheme(context: Context? = null): Boolean {
        val ctx = context ?: app
        val mode = nightTheme()
        val t = appTheme()
        return t == BLACK ||
            (usingNightMode(ctx) && (amoledTheme() || mode in setOf(NIGHT_AMOLED, NIGHT_PURE_AMOLED) || t in setOf(WHITE, LIGHT_GRAY)))
    }

    fun getPrimaryColor(context: Context): Int {
        val t = appTheme()
        if (t == BLACK || (isBlackTheme(context) && t in setOf(BLACK, WHITE, LIGHT_GRAY))) {
            return Color.WHITE
        }
        if (isWhiteTheme(context)) {
            return Color.parseColor("#212121")
        }
        if (isLightGrayTheme(context)) {
            return Color.parseColor("#1F2937")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (t == MONET || useSystemTheme())) {
            val wallpaperColor = getSystemWallpaperColor(context)
            if (wallpaperColor != null) {
                return wallpaperColor
            }
        }
        return context.getColorAttr(R.attr.colorPrimary)
    }

    /**
     * Resolves the card/box fill color across Light, Dark, Normal AMOLED (2-3 seed tones on #000000),
     * Pure AMOLED (#000000 boxes), and Monochrome Black.
     */
    fun resolveCardBackground(context: Context): Int {
        if (isMonochromeBlack(context) || isPureAmoled(context)) {
            return Color.BLACK
        }
        if (isTintedAmoled(context)) {
            if (activeThemeId() in setOf(MONET, CUSTOM)) {
                val prim = getSeedPrimaryColor(context)
                return ColorUtils.blendARGB(Color.BLACK, prim, 0.11f)
            }
            val elevated = context.getColorAttr(R.attr.cardElevatedSurfaceColor)
            if (elevated != 0 && elevated != Color.BLACK) {
                return elevated
            }
            return ColorUtils.blendARGB(Color.BLACK, getSeedPrimaryColor(context), 0.11f)
        }
        val elevated = context.getColorAttr(R.attr.cardElevatedSurfaceColor)
        if (elevated != 0) return elevated
        return if (usingNightMode(context)) 0xFF1E1F1E.toInt() else Color.WHITE
    }

    /**
     * Resolves Tone 3 (secondary nested container / selected surface) harmonious with [resolveCardBackground].
     */
    fun resolveNestedBoxBackground(context: Context): Int {
        if (isMonochromeBlack(context)) return 0xFF141414.toInt()
        val seed = getSeedPrimaryColor(context)
        if (isPureAmoled(context)) {
            return ColorUtils.blendARGB(Color.BLACK, seed, 0.08f)
        }
        val base = resolveCardBackground(context)
        if (isTintedAmoled(context)) {
            return ColorUtils.blendARGB(base, seed, 0.12f)
        }
        return ColorUtils.blendARGB(base, seed, if (usingNightMode(context)) 0.10f else 0.07f)
    }

    /**
     * Resolves card/box border color. Pure AMOLED uses a softened hairline alpha so thin borders look crisp.
     */
    fun resolveCardStrokeColor(context: Context, selected: Boolean = false): Int {
        if (isMonochromeBlack(context)) {
            return if (selected) Color.WHITE else ContextCompat.getColor(context, R.color.card_stroke)
        }
        val seed = getSeedPrimaryColor(context)
        val accentStroke = if (ColorUtils.calculateLuminance(seed) < 0.08) Color.WHITE else seed
        return when {
            isPureAmoled(context) -> {
                if (selected) ColorUtils.setAlphaComponent(accentStroke, 240)
                else ColorUtils.setAlphaComponent(accentStroke, 130)
            }
            isTintedAmoled(context) -> {
                if (selected) accentStroke else ContextCompat.getColor(context, R.color.card_stroke)
            }
            selected -> accentStroke
            else -> {
                context.getColorAttr(R.attr.profileCardStrokeColor).takeIf { it != 0 }
                    ?: ContextCompat.getColor(context, R.color.card_stroke)
            }
        }
    }

    /**
     * Resolves border thickness in pixels: ultra-thin 0.5dp hairline for AMOLED / Pure AMOLED.
     */
    fun resolveCardStrokeWidthPx(context: Context, selected: Boolean = false): Float {
        val density = context.resources.displayMetrics.density
        return if (isPureAmoled(context) || isTintedAmoled(context) || isMonochromeBlack(context)) {
            if (selected) (1.15f * density).coerceAtLeast(1f) else (0.5f * density).coerceAtLeast(1f)
        } else {
            if (selected) (1.5f * density).coerceAtLeast(1f) else context.resources.getDimension(R.dimen.card_stroke_width)
        }
    }

    fun getSeedPrimaryColor(context: Context): Int {
        if (appTheme() == CUSTOM) {
            return 0xFF000000.toInt() or customThemeColor()
        }
        val attr = context.getColorAttr(R.attr.colorPrimary)
        return if (attr != 0) attr else getPrimaryColor(context)
    }

    var currentNightMode = -1
    fun getNightMode(): Int {
        return getNightMode(nightTheme())
    }

    fun getNightMode(mode: Int): Int {
        return when (mode) {
            NIGHT_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            NIGHT_DARK, NIGHT_AMOLED, NIGHT_PURE_AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
            NIGHT_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun usingNightMode(context: Context? = null): Boolean {
        val ctx = context ?: app
        if (appTheme() == BLACK) return true
        return when (nightTheme()) {
            NIGHT_DARK, NIGHT_AMOLED, NIGHT_PURE_AMOLED -> true
            NIGHT_LIGHT -> false
            else -> isSystemNight(ctx)
        }
    }

    fun isMonochromeBlack(context: Context? = null): Boolean {
        val ctx = context ?: app
        val t = appTheme()
        return t == BLACK || (usingNightMode(ctx) && t in setOf(BLACK, WHITE, LIGHT_GRAY))
    }

    fun applyNightTheme() {
        invalidateCache()
        if (DataStore.nightTheme == NIGHT_DARK && DataStore.amoledTheme) {
            DataStore.nightTheme = NIGHT_PURE_AMOLED
        }
        if (DataStore.appTheme == BLACK && DataStore.nightTheme !in setOf(NIGHT_AMOLED, NIGHT_PURE_AMOLED)) {
            DataStore.nightTheme = NIGHT_PURE_AMOLED
            DataStore.amoledTheme = true
        }
        currentNightMode = DataStore.nightTheme
        when (DataStore.nightTheme) {
            NIGHT_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            NIGHT_DARK -> {
                DataStore.amoledTheme = false
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            NIGHT_LIGHT -> {
                DataStore.amoledTheme = false
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            NIGHT_AMOLED -> {
                DataStore.amoledTheme = false
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            NIGHT_PURE_AMOLED -> {
                DataStore.amoledTheme = true
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

}
