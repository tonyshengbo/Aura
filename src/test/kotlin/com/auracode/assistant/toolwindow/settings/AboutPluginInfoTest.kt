package com.auracode.assistant.toolwindow.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the stable metadata helpers used by the About settings page. */
class AboutPluginInfoTest {
    /** Ensures the About page displays the locally defined plugin version. */
    @Test
    fun `uses static plugin version`() {
        assertEquals("1.0.1", AboutPluginInfo.pluginVersion)
    }

    /** Ensures the community entry opens the new join URL instead of exposing a group number. */
    @Test
    fun `uses join url for community entry`() {
        assertTrue(AboutPluginInfo.communityJoinUrl.contains("qm.qq.com"))
    }
}
