package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class InputDocumentChangeTest {
    @Test
    fun `deleting a duplicate mention uses the intended caret instead of a matching suffix`() {
        assertEquals(
            InputDocumentChange(0, 4, ""),
            inputDocumentChange("@foo@foo", TextFieldValue("@foo", TextRange.Zero)),
        )
        assertEquals(
            InputDocumentChange(4, 8, ""),
            inputDocumentChange("@foo@foo", TextFieldValue("@foo", TextRange(4))),
        )
    }
}
