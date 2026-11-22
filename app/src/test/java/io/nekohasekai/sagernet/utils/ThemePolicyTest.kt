package io.nekohasekai.sagernet.utils

import io.nekohasekai.sagernet.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePolicyTest {

    private val customSeedIds = listOf(
        Theme.RED,
        Theme.PINK_SSR,
        Theme.PINK,
        Theme.PURPLE,
        Theme.DEEP_PURPLE,
        Theme.INDIGO,
        Theme.BLUE,
        Theme.LIGHT_BLUE,
        Theme.CYAN,
        Theme.TEAL,
        Theme.GREEN,
        Theme.LIGHT_GREEN,
        Theme.LIME,
        Theme.YELLOW,
        Theme.AMBER,
        Theme.ORANGE,
        Theme.DEEP_ORANGE,
        Theme.BROWN,
        Theme.GREY,
        Theme.BLUE_GREY,
        Theme.VERDANT_MINT,
    )

    private val materialOriginalIds = listOf(
        Theme.MATERIAL_BASELINE,
        Theme.MATERIAL_BLUE,
        Theme.MATERIAL_TEAL,
        Theme.MATERIAL_GREEN,
        Theme.MATERIAL_ROSE,
        Theme.MATERIAL_CORAL,
        Theme.MATERIAL_INDIGO,
        Theme.MATERIAL_AMBER,
        Theme.MATERIAL_DYNAMIC,
    )

    @Test
    fun themeFamiliesAreProperlyPartitioned() {
        assertTrue(Theme.isMaterialTheme(Theme.MONET))
        for (id in materialOriginalIds) {
            assertTrue("Expected $id to be Material theme", Theme.isMaterialTheme(id))
            assertTrue("Expected $id to be valid theme", Theme.isValidTheme(id))
            assertTrue("Expected $id to be colorful seed", Theme.isColorfulSeed(id))
        }
        for (id in customSeedIds) {
            assertFalse("Expected $id not to be Material theme", Theme.isMaterialTheme(id))
            assertTrue("Expected $id to be valid theme", Theme.isValidTheme(id))
            assertTrue("Expected $id to be colorful seed", Theme.isColorfulSeed(id))
        }
        assertFalse(Theme.isMaterialTheme(Theme.CUSTOM))
        assertTrue(Theme.isValidTheme(Theme.CUSTOM))
        assertTrue(Theme.isColorfulSeed(Theme.CUSTOM))
        for (mono in listOf(Theme.BLACK, Theme.WHITE, Theme.LIGHT_GRAY)) {
            assertFalse(Theme.isMaterialTheme(mono))
            assertTrue(Theme.isValidTheme(mono))
            assertFalse(Theme.isColorfulSeed(mono))
        }
        assertFalse(Theme.isValidTheme(999))
    }

    @Test
    fun allThemeIdsResolveToDistinctNonZeroStyles() {
        val mainStyles = (customSeedIds + materialOriginalIds + Theme.MONET + Theme.BLACK + Theme.WHITE + Theme.LIGHT_GRAY)
            .map { id -> id to Theme.getTheme(id) }
        assertEquals(mainStyles.size, mainStyles.map { it.second }.toSet().size)
        for ((id, style) in mainStyles) {
            assertNotEquals("Theme $id style must be non-zero", 0, style)
        }

        val dialogStyles = (customSeedIds + materialOriginalIds + Theme.MONET + Theme.BLACK + Theme.WHITE + Theme.LIGHT_GRAY)
            .map { id -> id to Theme.getDialogTheme(id) }
        assertEquals(dialogStyles.size, dialogStyles.map { it.second }.toSet().size)
        for ((id, style) in dialogStyles) {
            assertNotEquals("Dialog theme $id style must be non-zero", 0, style)
        }

        Theme.currentNightMode = Theme.NIGHT_AMOLED
        val tintedAmoledStyles = (customSeedIds + materialOriginalIds).map { Theme.getAmoledTheme(it) }
        assertEquals(tintedAmoledStyles.size, tintedAmoledStyles.toSet().size)
        assertFalse(R.style.Theme_SagerNet_Black in tintedAmoledStyles)

        Theme.currentNightMode = Theme.NIGHT_PURE_AMOLED
        val pureAmoledStyles = (customSeedIds + materialOriginalIds).map { Theme.getAmoledTheme(it) }
        assertEquals(pureAmoledStyles.size, pureAmoledStyles.toSet().size)
        assertFalse(R.style.Theme_SagerNet_Black in pureAmoledStyles)
    }

    @Test
    fun materialSchemeEngineGeneratesDistinctHctPalettesAcrossStyles() {
        assertEquals(20, MaterialSchemeEngine.SEED_COLORS.size)
        assertEquals(9, MaterialPaletteStyle.entries.size)

        val seed = MaterialSchemeEngine.SEED_COLORS[0].seedArgb
        for (style in MaterialPaletteStyle.entries) {
            val light = MaterialSchemeEngine.buildTonalColors(seed, style, isDark = false)
            val dark = MaterialSchemeEngine.buildTonalColors(seed, style, isDark = true)
            val amoled = MaterialSchemeEngine.buildTonalColors(seed, style, isDark = true, isAmoled = true)
            val pure = MaterialSchemeEngine.buildTonalColors(seed, style, isDark = true, isPureAmoled = true)

            assertEquals(34, light.toResourceOverrideMap().size)
            assertEquals(34, dark.toResourceOverrideMap().size)
            assertEquals(android.graphics.Color.BLACK, amoled.surface)
            assertEquals(android.graphics.Color.BLACK, pure.surfaceContainerLow)
            assertNotEquals(light.surface, dark.surface)
        }

        val tonalSpot = MaterialSchemeEngine.buildTonalColors(seed, MaterialPaletteStyle.TONAL_SPOT, isDark = true)
        val expressive = MaterialSchemeEngine.buildTonalColors(seed, MaterialPaletteStyle.EXPRESSIVE, isDark = true)
        val monochrome = MaterialSchemeEngine.buildTonalColors(seed, MaterialPaletteStyle.MONOCHROME, isDark = true)
        assertNotEquals(tonalSpot.tertiary, expressive.tertiary)
        assertNotEquals(tonalSpot.primary, monochrome.primary)
    }
}
