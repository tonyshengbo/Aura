package com.codex.assistant.toolwindow.drawer

internal enum class SettingsSection {
    GENERAL,
    AGENTS,
    TOKEN_USAGE,
    MODEL_PROVIDERS,
}

internal data class SettingsSectionPresentation(
    val titleKey: String,
    val subtitleKey: String,
    val showHeader: Boolean = true,
    val showSidePanel: Boolean = false,
)

internal fun SettingsSection.presentation(): SettingsSectionPresentation = when (this) {
    SettingsSection.GENERAL -> SettingsSectionPresentation(
        titleKey = "settings.section.general",
        subtitleKey = "settings.section.general.subtitle",
    )
    SettingsSection.AGENTS -> SettingsSectionPresentation(
        titleKey = "settings.section.agents",
        subtitleKey = "settings.section.agents.subtitle",
    )
    SettingsSection.TOKEN_USAGE -> SettingsSectionPresentation(
        titleKey = "settings.section.usage",
        subtitleKey = "settings.section.usage.subtitle",
    )
    SettingsSection.MODEL_PROVIDERS -> SettingsSectionPresentation(
        titleKey = "settings.section.providers",
        subtitleKey = "settings.section.providers.subtitle",
    )
}
