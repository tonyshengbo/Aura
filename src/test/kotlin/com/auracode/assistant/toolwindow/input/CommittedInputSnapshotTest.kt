package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class CommittedInputSnapshotTest {
    @Test
    fun `partial commit preserves chinese prefix and suffix while excluding preedit`() {
        val live = TextFieldValue("你好guo！", TextRange(5), TextRange(2, 5))

        assertEquals(
            TextFieldValue("你好！", TextRange(2), TextRange(2)),
            committedInputSnapshot(live),
        )
    }

    @Test
    fun `selection endpoints map across preedit without changing selection direction`() {
        val live = TextFieldValue("前ni后", TextRange(4, 1), TextRange(1, 3))

        assertEquals(
            TextFieldValue("前后", TextRange(2, 1), TextRange(1)),
            committedInputSnapshot(live),
        )
    }

    @Test
    fun `committed text retains its real selection`() {
        val committed = TextFieldValue("你好世界", TextRange(3, 1))
        assertEquals(committed, committedInputSnapshot(committed))
    }
}
