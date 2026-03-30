package com.auracode.assistant.toolwindow.composer

import com.auracode.assistant.settings.SavedAgentDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ComposerPopupContentTest {
    @Test
    fun `slash popup content maps selected suggestion after section headers`() {
        val content = buildComposerPopupContent(
            slashSuggestions = listOf(
                SlashSuggestionItem.Command(
                    command = "/plan",
                    title = "/plan",
                    description = "Switch the composer into plan mode.",
                    enabled = true,
                ),
                SlashSuggestionItem.Skill(
                    name = "brainstorming",
                    description = "Explore requirements.",
                ),
            ),
            activeSlashIndex = 1,
            mentionSuggestions = emptyList(),
            activeMentionIndex = 0,
            agentSuggestions = emptyList(),
            activeAgentIndex = 0,
            mode = ComposerPopupMode.SLASH,
        )

        assertEquals(4, content.rows.size)
        assertEquals(3, content.selectedRowIndex)
        assertIs<ComposerPopupRow.Header>(content.rows[0])
        assertIs<ComposerPopupRow.SlashItem>(content.rows[1])
        assertIs<ComposerPopupRow.Header>(content.rows[2])
        assertIs<ComposerPopupRow.SlashItem>(content.rows[3])
    }

    @Test
    fun `mention popup content keeps selected row aligned with suggestion index`() {
        val content = buildComposerPopupContent(
            slashSuggestions = emptyList(),
            activeSlashIndex = 0,
            mentionSuggestions = listOf(
                ContextEntry(path = "/tmp/Foo.kt", displayName = "Foo.kt", tailPath = ""),
                ContextEntry(path = "/tmp/Bar.kt", displayName = "Bar.kt", tailPath = ""),
            ),
            activeMentionIndex = 1,
            agentSuggestions = emptyList(),
            activeAgentIndex = 0,
            mode = ComposerPopupMode.MENTION,
        )

        assertEquals(2, content.rows.size)
        assertEquals(1, content.selectedRowIndex)
        assertIs<ComposerPopupRow.MentionItem>(content.rows[1])
    }

    @Test
    fun `agent popup content keeps selected row aligned with suggestion index`() {
        val content = buildComposerPopupContent(
            slashSuggestions = emptyList(),
            activeSlashIndex = 0,
            mentionSuggestions = emptyList(),
            activeMentionIndex = 0,
            agentSuggestions = listOf(
                SavedAgentDefinition(id = "a1", name = "Planner", prompt = "Plan."),
                SavedAgentDefinition(id = "a2", name = "Reviewer", prompt = "Review."),
            ),
            activeAgentIndex = 1,
            mode = ComposerPopupMode.AGENT,
        )

        assertEquals(2, content.rows.size)
        assertEquals(1, content.selectedRowIndex)
        assertIs<ComposerPopupRow.AgentItem>(content.rows[1])
    }
}
