package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DraftSynchronizerTest {
    @Test
    fun `out of order acknowledgements cannot overwrite newer live text`() {
        val synchronizer = DraftSynchronizer(draft(""))
        val composing = assertNotNull(synchronizer.publish(TextFieldValue("ni", TextRange(2), TextRange(0, 2))))
        val committed = assertNotNull(synchronizer.publish(TextFieldValue("你", TextRange(1))))

        assertNull(synchronizer.acceptExternal(committed, composing = false))
        assertNull(synchronizer.acceptExternal(composing, composing = false))
        assertNull(synchronizer.publish(committed.value))
    }

    @Test
    fun `send can clear draft even when typing and clear occur before a rendered acknowledgement`() {
        val synchronizer = DraftSynchronizer(draft(""))
        synchronizer.publish(TextFieldValue("你好", TextRange(2)))

        assertEquals(TextFieldValue(""), synchronizer.acceptExternal(draft("", revision = 2), composing = false))
    }

    @Test
    fun `programmatic changes wait until composition finishes`() {
        val synchronizer = DraftSynchronizer(draft(""))
        synchronizer.publish(TextFieldValue("ni", TextRange(2), TextRange(0, 2)))
        val external = draft("补全结果", revision = 2)

        assertNull(synchronizer.acceptExternal(external, composing = true))
        assertEquals(external.value, synchronizer.finishComposition())
        assertNull(synchronizer.finishComposition())
    }

    @Test
    fun `another editor origin is treated as a real document replacement`() {
        val first = DraftSynchronizer(draft(""))
        val second = DraftSynchronizer(draft(""))
        val update = assertNotNull(first.publish(TextFieldValue("另一会话")))

        assertEquals(update.value, second.acceptExternal(update, composing = false))
    }

    @Test
    fun `older external revisions cannot undo a newer completion`() {
        val synchronizer = DraftSynchronizer(draft("", revision = 1))
        val latest = draft("@新文件", revision = 10)

        assertEquals(latest.value, synchronizer.acceptExternal(latest, composing = false))
        assertNull(synchronizer.acceptExternal(draft("@旧文件", revision = 9), composing = false))
    }

    @Test
    fun `latest external edit wins when multiple commands arrive during composition`() {
        val synchronizer = DraftSynchronizer(draft(""))
        assertNull(synchronizer.acceptExternal(draft("第一次补全", revision = 2), composing = true))
        val latest = draft("最终补全", revision = 3)
        assertNull(synchronizer.acceptExternal(latest, composing = true))
        assertNull(synchronizer.acceptExternal(draft("过期补全", revision = 1), composing = true))

        assertEquals(latest.value, synchronizer.finishComposition())
    }

    private fun draft(text: String, revision: Long = 0) = InputDraft(TextFieldValue(text), revision)
}
