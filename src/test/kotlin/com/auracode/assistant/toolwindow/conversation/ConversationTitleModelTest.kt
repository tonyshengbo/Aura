package com.auracode.assistant.toolwindow.conversation

import com.auracode.assistant.toolwindow.conversation.title.conversationTitleModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationTitleModelTest {
    @Test
    fun `visible target resolves only its own text and never the ellipsis`() {
        val model = conversationTitleModel("Read 文件.kt now", "文件.kt", "/src/文件.kt")

        assertEquals("Read 文件.kt now", model.text)
        assertEquals("/src/文件.kt", model.targetAt(5, model.text.length))
        assertEquals("/src/文件.kt", model.targetAt(9, model.text.length))
        assertNull(model.targetAt(4, model.text.length))
        assertNull(model.targetAt(10, model.text.length))
        assertNull(model.targetAt(5, visibleEndExclusive = 5))
        assertNull(model.targetAt(6, visibleEndExclusive = 6))
        assertEquals("/src/文件.kt", model.targetAt(5, visibleEndExclusive = 6))
    }

    @Test
    fun `target at beginning is not duplicated and suffix is preserved`() {
        val model = conversationTitleModel("File.kt: read", "File.kt", "/File.kt")

        assertEquals("File.kt: read", model.text)
        assertEquals(0, model.targetStart)
        assertEquals(7, model.targetEndExclusive)
    }

    @Test
    fun `missing target label is appended once`() {
        val model = conversationTitleModel("Read", "File.kt", "/File.kt")

        assertEquals("Read File.kt", model.text)
        assertEquals(5, model.targetStart)
        assertEquals("/File.kt", model.targetAt(5, model.text.length))
    }

    @Test
    fun `missing target metadata leaves plain title`() {
        for ((label, path) in listOf(null to "/File.kt", "File.kt" to null, "" to "/File.kt")) {
            val model = conversationTitleModel("Read", label, path)
            assertEquals("Read", model.text)
            assertNull(model.targetAt(0, model.text.length))
        }
    }
}
