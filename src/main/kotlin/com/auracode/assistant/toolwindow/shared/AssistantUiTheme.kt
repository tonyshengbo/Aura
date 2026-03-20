package com.auracode.assistant.toolwindow.shared

import java.awt.Color

internal data class SwingThemePalette(
    val chromeBg: Color,
    val chromeRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
)

internal object AssistantUiTheme {
    fun palette(theme: EffectiveTheme): SwingThemePalette {
        return when (theme) {
            EffectiveTheme.DARK -> SwingThemePalette(
                chromeBg = Color(0x1A, 0x1E, 0x26),
                chromeRaised = Color(0x20, 0x24, 0x2A),
                textPrimary = Color(0xE6, 0xEB, 0xF4),
                textSecondary = Color(0xAF, 0xB8, 0xC9),
                accent = Color(0x4F, 0x8B, 0xFF),
            )

            EffectiveTheme.LIGHT -> SwingThemePalette(
                chromeBg = Color(0xEC, 0xEF, 0xF6),
                chromeRaised = Color(0xE1, 0xE7, 0xF2),
                textPrimary = Color(0x18, 0x20, 0x30),
                textSecondary = Color(0x4B, 0x58, 0x70),
                accent = Color(0x2E, 0x6B, 0xDE),
            )
        }
    }
}
