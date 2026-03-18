package com.codex.assistant.toolwindow.shared

import com.codex.assistant.settings.UiThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AssistantThemeTest {
    @Test
    fun `follow ide resolves to current ide darkness`() {
        assertEquals(EffectiveTheme.DARK, resolveEffectiveTheme(UiThemeMode.FOLLOW_IDE, ideDark = true))
        assertEquals(EffectiveTheme.LIGHT, resolveEffectiveTheme(UiThemeMode.FOLLOW_IDE, ideDark = false))
    }

    @Test
    fun `manual theme overrides ide darkness`() {
        assertEquals(EffectiveTheme.DARK, resolveEffectiveTheme(UiThemeMode.DARK, ideDark = false))
        assertEquals(EffectiveTheme.LIGHT, resolveEffectiveTheme(UiThemeMode.LIGHT, ideDark = true))
    }

    @Test
    fun `light and dark palettes use different key colors`() {
        val light = assistantPalette(EffectiveTheme.LIGHT)
        val dark = assistantPalette(EffectiveTheme.DARK)

        assertNotEquals(light.appBg, dark.appBg)
        assertNotEquals(light.textPrimary, dark.textPrimary)
        assertNotEquals(light.userBubbleBg, dark.userBubbleBg)
    }

    @Test
    fun `dark palette stays close to ide surfaces and avoids harsh contrast`() {
        val dark = assistantPalette(EffectiveTheme.DARK)

        assertTrue(dark.appBg.red > 0.08f)
        assertTrue(dark.topBarBg.red > dark.appBg.red)
        assertTrue(dark.textPrimary.red < 0.9f)
        assertTrue(dark.timelinePlainText.red < dark.textPrimary.red)
        assertTrue(dark.markdownCodeBg.red > dark.appBg.red)
        assertTrue(dark.markdownTableBg.red >= dark.markdownCodeBg.red)
        assertTrue(dark.userBubbleBg.blue > dark.userBubbleBg.red)
        assertTrue((dark.userBubbleBg.blue - dark.userBubbleBg.red) < 0.14f)
        assertTrue(dark.timelineCardBg.red > dark.appBg.red)
    }
}
