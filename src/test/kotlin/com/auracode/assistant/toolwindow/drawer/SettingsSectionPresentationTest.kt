package com.auracode.assistant.toolwindow.drawer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSectionPresentationTest {
    @Test
    fun `general section uses header layout without side panel`() {
        val presentation = SettingsSection.GENERAL.presentation()

        assertEquals("settings.section.general", presentation.titleKey)
        assertEquals("settings.section.general.subtitle", presentation.subtitleKey)
        assertTrue(presentation.showHeader)
        assertFalse(presentation.showSidePanel)
    }
}
