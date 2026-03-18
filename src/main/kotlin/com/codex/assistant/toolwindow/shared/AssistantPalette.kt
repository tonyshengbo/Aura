package com.codex.assistant.toolwindow.shared

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.codex.assistant.settings.UiThemeMode
import com.intellij.util.ui.StartupUiUtil

internal enum class EffectiveTheme {
    LIGHT,
    DARK,
}

internal data class DesignPalette(
    val appBg: Color,
    val topBarBg: Color,
    val topStripBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val timelineCardBg: Color,
    val timelineCardText: Color,
    val timelinePlainText: Color,
    val userBubbleBg: Color,
    val markdownCodeBg: Color,
    val markdownInlineCodeBg: Color,
    val markdownCodeText: Color,
    val markdownQuoteText: Color,
    val markdownDivider: Color,
    val markdownTableBg: Color,
    val linkColor: Color,
    val composerBg: Color,
    val accent: Color,
    val success: Color,
    val danger: Color,
)

internal fun resolveEffectiveTheme(mode: UiThemeMode, ideDark: Boolean): EffectiveTheme {
    return when (mode) {
        UiThemeMode.FOLLOW_IDE -> if (ideDark) EffectiveTheme.DARK else EffectiveTheme.LIGHT
        UiThemeMode.LIGHT -> EffectiveTheme.LIGHT
        UiThemeMode.DARK -> EffectiveTheme.DARK
    }
}

internal fun currentIdeDarkTheme(): Boolean = StartupUiUtil.isUnderDarcula

internal fun assistantPalette(theme: EffectiveTheme): DesignPalette {
    return when (theme) {
        EffectiveTheme.DARK -> DesignPalette(
            appBg = Color(0xFF1E2229),
            topBarBg = Color(0xFF252A33),
            topStripBg = Color(0xFF222730),
            textPrimary = Color(0xFFD5DBE3),
            textSecondary = Color(0xFF97A2AF),
            textMuted = Color(0xFF707C89),
            timelineCardBg = Color(0xFF262B34),
            timelineCardText = Color(0xFFCDD5DF),
            timelinePlainText = Color(0xFFB8C1CC),
            userBubbleBg = Color(0xFF3A4550),
            markdownCodeBg = Color(0xFF2F3640),
            markdownInlineCodeBg = Color(0xFF353D48),
            markdownCodeText = Color(0xFFCFD8E6),
            markdownQuoteText = Color(0xFFB1BBC8),
            markdownDivider = Color(0xFF46505D),
            markdownTableBg = Color(0xFF343C47),
            linkColor = Color(0xFF7DAAF7),
            composerBg = Color(0xFF20252D),
            accent = Color(0xFF4F8BFF),
            success = Color(0xFF6FCF73),
            danger = Color(0xFFF07F84),
        )

        EffectiveTheme.LIGHT -> DesignPalette(
            appBg = Color(0xFFF5F7FB),
            topBarBg = Color(0xFFECEFF6),
            topStripBg = Color(0xFFE6EAF3),
            textPrimary = Color(0xFF182030),
            textSecondary = Color(0xFF4B5870),
            textMuted = Color(0xFF68758F),
            timelineCardBg = Color(0xFFFFFFFF),
            timelineCardText = Color(0xFF22304A),
            timelinePlainText = Color(0xFF263248),
            userBubbleBg = Color(0xFFDCEAFF),
            markdownCodeBg = Color(0xFFF2F6FF),
            markdownInlineCodeBg = Color(0xFFEAF1FF),
            markdownCodeText = Color(0xFF21406E),
            markdownQuoteText = Color(0xFF52607A),
            markdownDivider = Color(0xFFD4DCEB),
            markdownTableBg = Color(0xFFF8FAFF),
            linkColor = Color(0xFF2E6BDE),
            composerBg = Color(0xFFF0F3FA),
            accent = Color(0xFF2E6BDE),
            success = Color(0xFF3DAA55),
            danger = Color(0xFFD74D58),
        )
    }
}

internal fun assistantMaterialColors(theme: EffectiveTheme, palette: DesignPalette): Colors {
    return when (theme) {
        EffectiveTheme.DARK -> darkColors(
            primary = palette.accent,
            primaryVariant = palette.userBubbleBg,
            secondary = palette.accent,
            secondaryVariant = palette.userBubbleBg,
            background = palette.appBg,
            surface = palette.timelineCardBg,
            error = palette.danger,
            onPrimary = palette.textPrimary,
            onSecondary = palette.textPrimary,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onError = palette.textPrimary,
        )

        EffectiveTheme.LIGHT -> lightColors(
            primary = palette.accent,
            primaryVariant = palette.userBubbleBg,
            secondary = palette.accent,
            secondaryVariant = palette.userBubbleBg,
            background = palette.appBg,
            surface = palette.timelineCardBg,
            error = palette.danger,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onError = Color.White,
        )
    }
}

@Composable
internal fun assistantPalette(mode: UiThemeMode): DesignPalette {
    return assistantPalette(resolveEffectiveTheme(mode, currentIdeDarkTheme()))
}
