package com.auracode.assistant.toolwindow.settings

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsTextInputStateTest {
    @Test
    fun `delayed store echoes cannot replace newer composition or move its caret`() {
        val state = SettingsTextInputState("")
        state.value = TextFieldValue("n", TextRange(1), TextRange(0, 1))
        state.value = TextFieldValue("ni", TextRange(2), TextRange(0, 2))

        state.acceptExternal("n", revision = 0)
        assertEquals(TextFieldValue("ni", TextRange(2), TextRange(0, 2)), state.value)

        state.value = TextFieldValue("你", TextRange(1))
        state.acceptExternal("ni", revision = 0)
        assertEquals(TextFieldValue("你", TextRange(1)), state.value)
    }

    @Test
    fun `initial persisted snapshot can arrive after mounting`() {
        val state = SettingsTextInputState("")
        state.acceptExternal("已保存的提示词", revision = 0)

        assertEquals("已保存的提示词", state.value.text)
        assertEquals(TextRange(7), state.value.selection)
    }

    @Test
    fun `explicit discard waits for IME and old echoes cannot undo it`() {
        val state = SettingsTextInputState("原路径")
        state.value = TextFieldValue("原路径ni", TextRange(5), TextRange(3, 5))
        state.acceptExternal("检测路径", revision = 1)
        assertEquals("原路径ni", state.value.text)

        state.value = TextFieldValue("原路径你", TextRange(4))
        assertEquals("检测路径", state.value.text)
        assertNull(state.value.composition)
        state.acceptExternal("原路径ni", revision = 0)
        state.acceptExternal("原路径你", revision = 1)
        assertEquals("检测路径", state.value.text)
    }

    @Test
    fun `explicit replacements work even when replacement matches an earlier typed value`() {
        val state = SettingsTextInputState("")
        state.value = TextFieldValue("旧路径")
        state.value = TextFieldValue("新路径")

        state.acceptExternal("旧路径", revision = 1)
        assertEquals("旧路径", state.value.text)
    }
}
