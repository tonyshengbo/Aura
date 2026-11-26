package com.auracode.assistant.toolwindow.submission

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.input.InputCommand
import com.auracode.assistant.toolwindow.input.InputKey
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SubmissionEditorCommandsTest {
    @Test
    fun `ime composition owns enter escape and navigation even when suggestions are visible`() {
        val state = SubmissionAreaState(mentionPopupVisible = true)
        val composing = TextFieldValue("ni", TextRange(2), TextRange(0, 2))
        for (key in listOf(KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_UP, KeyEvent.VK_DOWN)) {
            assertNull(resolveSubmissionEditorCommand(state, composing, InputKey(key)) { error("Unexpected intent") })
        }
    }

    @Test
    fun `enter sends and shift enter stays with compose editing`() {
        val intents = mutableListOf<UiIntent>()
        val state = SubmissionAreaState()
        val document = TextFieldValue("你好", TextRange(2))
        val send = assertIs<InputCommand.Invoke>(
            resolveSubmissionEditorCommand(state, document, InputKey(KeyEvent.VK_ENTER), intents::add),
        )
        send.action()
        assertEquals(listOf<UiIntent>(UiIntent.SendPrompt), intents)
        assertNull(resolveSubmissionEditorCommand(state, document, InputKey(KeyEvent.VK_ENTER, shift = true), intents::add))
    }

    @Test
    fun `disabled slash suggestion never falls through to sending the prompt`() {
        val state = SubmissionAreaState(
            slashPopupVisible = true,
            slashSuggestions = listOf(SlashSuggestionItem.Command("plan", "Plan", "", enabled = false)),
        )
        val command = assertIs<InputCommand.Invoke>(
            resolveSubmissionEditorCommand(state, TextFieldValue("/plan"), InputKey(KeyEvent.VK_ENTER)) {
                error("Disabled command dispatched $it")
            },
        )
        command.action()
    }

    @Test
    fun `mention deletion uses live selection instead of the previous rendered cursor`() {
        val mention = MentionEntry("file", MentionEntryKind.FILE, "/file", "文件", 0, 3)
        val state = SubmissionAreaState(document = TextFieldValue("@文件 abc", TextRange(7)), mentionEntries = listOf(mention))
        val live = TextFieldValue("@文件 abc", TextRange(3))
        val command = assertIs<InputCommand.Edit>(
            resolveSubmissionEditorCommand(state, live, InputKey(KeyEvent.VK_BACK_SPACE)) {},
        )
        assertEquals(" abc", command.value.text)
        assertEquals(TextRange(0), command.value.selection)
    }
}
