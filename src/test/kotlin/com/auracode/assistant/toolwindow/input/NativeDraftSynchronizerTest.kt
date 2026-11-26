package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NativeDraftSynchronizerTest {
    @Test
    fun `out of order acknowledgements cannot overwrite newer native text`() {
        val synchronizer = NativeDraftSynchronizer(draft(""))
        val composing = assertNotNull(synchronizer.publish(TextFieldValue("ni", TextRange(2), TextRange(0, 2))))
        val committed = assertNotNull(synchronizer.publish(TextFieldValue("你", TextRange(1))))

        assertNull(synchronizer.acceptExternal(committed, composing = false))
        assertNull(synchronizer.acceptExternal(composing, composing = false))
        assertNull(synchronizer.publish(committed.value))
    }

    @Test
    fun `send can clear draft even when typing and clear occur before a rendered acknowledgement`() {
        val synchronizer = NativeDraftSynchronizer(draft(""))
        synchronizer.publish(TextFieldValue("你好", TextRange(2)))

        assertEquals(TextFieldValue(""), synchronizer.acceptExternal(draft("", revision = 2), composing = false))
    }

    @Test
    fun `programmatic changes wait until native composition finishes`() {
        val synchronizer = NativeDraftSynchronizer(draft(""))
        synchronizer.publish(TextFieldValue("ni", TextRange(2), TextRange(0, 2)))
        val external = draft("补全结果", revision = 2)

        assertNull(synchronizer.acceptExternal(external, composing = true))
        assertEquals(external.value, synchronizer.finishComposition())
        assertNull(synchronizer.finishComposition())
    }

    @Test
    fun `another editor origin is treated as a real document replacement`() {
        val first = NativeDraftSynchronizer(draft(""))
        val second = NativeDraftSynchronizer(draft(""))
        val update = assertNotNull(first.publish(TextFieldValue("另一会话")))

        assertEquals(update.value, second.acceptExternal(update, composing = false))
    }

    @Test
    fun `native replacements keep untouched prefix and suffix including chinese`() {
        val before = "你好 @原文件 结束"
        val after = "你好 @新文件 结束"
        val change = nativeDocumentChange(before, after)
        assertEquals(after, before.replaceRange(change.start, change.end, change.replacement))
        assertEquals("原", before.substring(change.start, change.end))
        assertEquals("新", change.replacement)
    }

    private fun draft(text: String, revision: Long = 0) = NativeInputDraft(TextFieldValue(text), revision)
}
