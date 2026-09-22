package com.auracode.assistant.settings.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemPromptProfileTest {
    @Test
    fun `resolved content is null while the profile is disabled`() {
        val profile = SystemPromptProfile(enabled = false, content = "You are a reviewer.")

        assertNull(profile.resolvedContent())
    }

    @Test
    fun `resolved content is null when the content is blank`() {
        val profile = SystemPromptProfile(enabled = true, content = "   \n  ")

        assertNull(profile.resolvedContent())
    }

    @Test
    fun `resolved content trims the stored role text`() {
        val profile = SystemPromptProfile(enabled = true, content = "\n  You are a reviewer.  \n")

        assertEquals("You are a reviewer.", profile.resolvedContent())
    }
}
