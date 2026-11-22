@file:SuppressLint("RestrictedApi")

package io.nekohasekai.sagernet.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import com.google.android.material.color.ColorResourcesOverride
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.TonalPalette
import io.nekohasekai.sagernet.R

/**
 * Material 3 Expressive dynamic tonal scheme engine backed by Material Color Utilities (HCT),
 * matching aShellYou's 9 palette styles and 20 curated seed colors for XML/Views themes.
 */
enum class MaterialPaletteStyle(
    val id: Int,
    val titleEn: String,
    val titleTr: String,
) {
    TONAL_SPOT(0, "Tonal Spot", "Tonal Nokta"),
    EXPRESSIVE(1, "Expressive", "Etkileyici"),
    VIBRANT(2, "Vibrant", "Canlı"),
    RAINBOW(3, "Rainbow", "Gökkuşağı"),
    FRUIT_SALAD(4, "Fruit Salad", "Meyve Salatası"),
    FIDELITY(5, "Fidelity", "Sadakat"),
    CONTENT(6, "Content", "İçerik"),
    NEUTRAL(7, "Neutral", "Nötr"),
    MONOCHROME(8, "Monochrome", "Monokrom");

    fun label(isTr: Boolean): String = if (isTr) titleTr else titleEn

    companion object {
        fun fromId(id: Int): MaterialPaletteStyle = entries.firstOrNull { it.id == id } ?: TONAL_SPOT
    }
}

data class MaterialSeedPreset(
    val seedArgb: Int,
    val nameEn: String,
    val nameTr: String,
) {
    fun label(isTr: Boolean): String = if (isTr) nameTr else nameEn
}

data class PaletteKeyColors(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
)

data class MaterialTonalColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceDim: Int,
    val surfaceBright: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val outline: Int,
    val outlineVariant: Int,
) {
    fun toResourceOverrideMap(): Map<Int, Int> = mapOf(
        R.color.m3_dyn_primary to primary,
        R.color.m3_dyn_on_primary to onPrimary,
        R.color.m3_dyn_primary_container to primaryContainer,
        R.color.m3_dyn_on_primary_container to onPrimaryContainer,
        R.color.m3_dyn_inverse_primary to inversePrimary,
        R.color.m3_dyn_secondary to secondary,
        R.color.m3_dyn_on_secondary to onSecondary,
        R.color.m3_dyn_secondary_container to secondaryContainer,
        R.color.m3_dyn_on_secondary_container to onSecondaryContainer,
        R.color.m3_dyn_tertiary to tertiary,
        R.color.m3_dyn_on_tertiary to onTertiary,
        R.color.m3_dyn_tertiary_container to tertiaryContainer,
        R.color.m3_dyn_on_tertiary_container to onTertiaryContainer,
        R.color.m3_dyn_error to error,
        R.color.m3_dyn_on_error to onError,
        R.color.m3_dyn_error_container to errorContainer,
        R.color.m3_dyn_on_error_container to onErrorContainer,
        R.color.m3_dyn_background to background,
        R.color.m3_dyn_on_background to onBackground,
        R.color.m3_dyn_surface to surface,
        R.color.m3_dyn_on_surface to onSurface,
        R.color.m3_dyn_surface_variant to surfaceVariant,
        R.color.m3_dyn_on_surface_variant to onSurfaceVariant,
        R.color.m3_dyn_surface_dim to surfaceDim,
        R.color.m3_dyn_surface_bright to surfaceBright,
        R.color.m3_dyn_surface_container_lowest to surfaceContainerLowest,
        R.color.m3_dyn_surface_container_low to surfaceContainerLow,
        R.color.m3_dyn_surface_container to surfaceContainer,
        R.color.m3_dyn_surface_container_high to surfaceContainerHigh,
        R.color.m3_dyn_surface_container_highest to surfaceContainerHighest,
        R.color.m3_dyn_inverse_surface to inverseSurface,
        R.color.m3_dyn_inverse_on_surface to inverseOnSurface,
        R.color.m3_dyn_outline to outline,
        R.color.m3_dyn_outline_variant to outlineVariant,
    )
}

object MaterialSchemeEngine {

    /**
     * Exact 20 curated HCT seed colors from aShellYou's AppSeedColors.
     */
    val SEED_COLORS: List<MaterialSeedPreset> = listOf(
        MaterialSeedPreset(0xFFB5353F.toInt(), "Crimson", "Kızıl"),
        MaterialSeedPreset(0xFFF06435.toInt(), "Tangerine", "Mandalina"),
        MaterialSeedPreset(0xFFE07200.toInt(), "Sunset Orange", "Gün Batımı"),
        MaterialSeedPreset(0xFFC78100.toInt(), "Honey Amber", "Bal Kehribar"),
        MaterialSeedPreset(0xFFB28B00.toInt(), "Ochre Gold", "Altın Okra"),
        MaterialSeedPreset(0xFF999419.toInt(), "Olive Citron", "Zeytin Limon"),
        MaterialSeedPreset(0xFF7D9B36.toInt(), "Moss Green", "Yosun Yeşili"),
        MaterialSeedPreset(0xFF5BA053.toInt(), "Sage Meadow", "Adaçayı"),
        MaterialSeedPreset(0xFF30A370.toInt(), "Emerald Bamboo", "Zümrüt Bambu"),
        MaterialSeedPreset(0xFF00A38C.toInt(), "Jade Teal", "Yeşim Turkuaz"),
        MaterialSeedPreset(0xFF00A1A3.toInt(), "Lagoon Cyan", "Lagün Camgöbeği"),
        MaterialSeedPreset(0xFF169EB7.toInt(), "Cerulean", "Gök Turkuaz"),
        MaterialSeedPreset(0xFF389AC7.toInt(), "Azure Sky", "Açık Gök"),
        MaterialSeedPreset(0xFF5695D2.toInt(), "Ocean Blue", "Okyanus Mavisi"),
        MaterialSeedPreset(0xFF728FD8.toInt(), "Periwinkle", "Cezayir Menekşesi"),
        MaterialSeedPreset(0xFF8C88D8.toInt(), "Lavender Indigo", "Lavanta İndigo"),
        MaterialSeedPreset(0xFFA282D1.toInt(), "Amethyst Violet", "Ametist Menekşe"),
        MaterialSeedPreset(0xFFB67CC2.toInt(), "Orchid Lilac", "Orkide Leylak"),
        MaterialSeedPreset(0xFFC677AD.toInt(), "Blossom Rose", "Çiçek Gülü"),
        MaterialSeedPreset(0xFFB23268.toInt(), "Berry Magenta", "Böğürtlen"),
    )

    fun createDynamicScheme(
        primarySeedArgb: Int,
        paletteStyle: MaterialPaletteStyle,
        isDark: Boolean,
        contrastLevel: Double = 0.0,
    ): DynamicScheme {
        val hct = Hct.fromInt(0xFF000000.toInt() or primarySeedArgb)
        return when (paletteStyle) {
            MaterialPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, isDark, contrastLevel)
            MaterialPaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, isDark, contrastLevel)
            MaterialPaletteStyle.VIBRANT -> SchemeVibrant(hct, isDark, contrastLevel)
            MaterialPaletteStyle.MONOCHROME -> SchemeMonochrome(hct, isDark, contrastLevel)
            MaterialPaletteStyle.RAINBOW -> SchemeRainbow(hct, isDark, contrastLevel)
            MaterialPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, isDark, contrastLevel)
            MaterialPaletteStyle.NEUTRAL -> SchemeNeutral(hct, isDark, contrastLevel)
            MaterialPaletteStyle.FIDELITY -> SchemeFidelity(hct, isDark, contrastLevel)
            MaterialPaletteStyle.CONTENT -> SchemeContent(hct, isDark, contrastLevel)
        }
    }

    fun buildTonalColors(
        primarySeedArgb: Int,
        paletteStyle: MaterialPaletteStyle,
        isDark: Boolean,
        isAmoled: Boolean = false,
        isPureAmoled: Boolean = false,
    ): MaterialTonalColors {
        val scheme = createDynamicScheme(
            primarySeedArgb = primarySeedArgb,
            paletteStyle = paletteStyle,
            isDark = isDark,
            contrastLevel = if (isDark && isAmoled) 0.5 else 0.0,
        )
        val baseSurface = if (isDark && (isAmoled || isPureAmoled)) {
            Color.BLACK
        } else {
            scheme.neutralPalette.resolve(6, 98, isDark)
        }
        val scLowest = if (isDark && (isAmoled || isPureAmoled)) {
            Color.BLACK
        } else {
            scheme.neutralVariantPalette.resolve(4, 100, isDark)
        }
        val scLow = when {
            isDark && isPureAmoled -> Color.BLACK
            isDark && isAmoled -> scheme.neutralVariantPalette.tone(6)
            else -> scheme.neutralVariantPalette.resolve(10, 96, isDark)
        }
        val sc = when {
            isDark && isPureAmoled -> scheme.neutralVariantPalette.tone(6)
            isDark && isAmoled -> scheme.neutralVariantPalette.tone(10)
            else -> scheme.neutralVariantPalette.resolve(12, 94, isDark)
        }
        val scHigh = when {
            isDark && isPureAmoled -> scheme.neutralVariantPalette.tone(10)
            isDark && isAmoled -> scheme.neutralVariantPalette.tone(12)
            else -> scheme.neutralVariantPalette.resolve(17, 92, isDark)
        }
        val scHighest = when {
            isDark && isPureAmoled -> scheme.neutralVariantPalette.tone(14)
            isDark && isAmoled -> scheme.neutralVariantPalette.tone(17)
            else -> scheme.neutralVariantPalette.resolve(22, 90, isDark)
        }

        return MaterialTonalColors(
            primary = scheme.primaryPalette.resolve(80, 40, isDark),
            onPrimary = scheme.primaryPalette.resolve(20, 100, isDark),
            primaryContainer = scheme.primaryPalette.resolve(30, 90, isDark),
            onPrimaryContainer = scheme.primaryPalette.resolve(90, 10, isDark),
            inversePrimary = scheme.primaryPalette.resolve(40, 80, isDark),
            secondary = scheme.secondaryPalette.resolve(80, 40, isDark),
            onSecondary = scheme.secondaryPalette.resolve(20, 100, isDark),
            secondaryContainer = scheme.secondaryPalette.resolve(30, 90, isDark),
            onSecondaryContainer = scheme.secondaryPalette.resolve(90, 10, isDark),
            tertiary = scheme.tertiaryPalette.resolve(80, 40, isDark),
            onTertiary = scheme.tertiaryPalette.resolve(20, 100, isDark),
            tertiaryContainer = scheme.tertiaryPalette.resolve(30, 90, isDark),
            onTertiaryContainer = scheme.tertiaryPalette.resolve(90, 10, isDark),
            error = scheme.errorPalette.resolve(80, 40, isDark),
            onError = scheme.errorPalette.resolve(20, 100, isDark),
            errorContainer = scheme.errorPalette.resolve(30, 90, isDark),
            onErrorContainer = scheme.errorPalette.resolve(90, 10, isDark),
            background = baseSurface,
            onBackground = scheme.neutralPalette.resolve(90, 10, isDark),
            surface = baseSurface,
            onSurface = scheme.neutralPalette.resolve(90, 10, isDark),
            surfaceVariant = scheme.neutralVariantPalette.resolve(30, 90, isDark),
            onSurfaceVariant = scheme.neutralVariantPalette.resolve(80, 30, isDark),
            surfaceDim = scheme.neutralPalette.resolve(6, 87, isDark),
            surfaceBright = scheme.neutralPalette.resolve(24, 98, isDark),
            surfaceContainerLowest = scLowest,
            surfaceContainerLow = scLow,
            surfaceContainer = sc,
            surfaceContainerHigh = scHigh,
            surfaceContainerHighest = scHighest,
            inverseSurface = scheme.neutralPalette.resolve(90, 20, isDark),
            inverseOnSurface = scheme.neutralPalette.resolve(20, 95, isDark),
            outline = scheme.neutralVariantPalette.resolve(60, 50, isDark),
            outlineVariant = scheme.neutralVariantPalette.resolve(30, 80, isDark),
        )
    }

    /**
     * Extracts 3 representative mid-tone colors (primary, secondary, tertiary) for the
     * aShellYou-style PaletteWheel swatch display.
     */
    fun getPaletteKeyColors(
        primarySeedArgb: Int,
        paletteStyle: MaterialPaletteStyle,
    ): PaletteKeyColors {
        val scheme = createDynamicScheme(primarySeedArgb, paletteStyle, isDark = false)
        return PaletteKeyColors(
            primary = modifyColorForDisplay(scheme.primaryPalette.tone(60), toneFactor = 1.0f),
            secondary = modifyColorForDisplay(scheme.secondaryPalette.tone(60), toneFactor = 1.35f),
            tertiary = modifyColorForDisplay(scheme.tertiaryPalette.tone(60), toneFactor = 0.78f),
        )
    }

    fun applyDynamicMaterialOverlay(
        context: Context,
        seedArgb: Int,
        paletteStyle: MaterialPaletteStyle,
        isDark: Boolean,
        isAmoled: Boolean,
        isPureAmoled: Boolean,
    ): Boolean {
        val override = ColorResourcesOverride.getInstance() ?: return false
        val colors = buildTonalColors(
            primarySeedArgb = seedArgb,
            paletteStyle = paletteStyle,
            isDark = isDark,
            isAmoled = isAmoled,
            isPureAmoled = isPureAmoled,
        )
        return runCatching {
            override.applyIfPossible(context, colors.toResourceOverrideMap())
        }.getOrDefault(false)
    }

    fun findSeedName(seedArgb: Int, isTr: Boolean): String {
        val masked = 0xFF000000.toInt() or seedArgb
        val match = SEED_COLORS.firstOrNull { it.seedArgb == masked }
        return match?.label(isTr) ?: String.format("#%06X", 0xFFFFFF and seedArgb)
    }

    private fun modifyColorForDisplay(
        argb: Int,
        toneFactor: Float = 1.35f,
        chromaFactor: Float = 1.15f,
    ): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hsv[1] = (hsv[1] * chromaFactor).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * toneFactor).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun TonalPalette.resolve(darkTone: Int, lightTone: Int, isDark: Boolean): Int {
        return tone(if (isDark) darkTone else lightTone)
    }
}
