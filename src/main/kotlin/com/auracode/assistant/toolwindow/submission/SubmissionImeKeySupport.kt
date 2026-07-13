package com.auracode.assistant.toolwindow.submission

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.input.TextFieldValue
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * Prevents Compose Desktop preview-key handling from processing printable AWT typed events while
 * an input method owns the active composition. The committed text still arrives through the
 * BasicTextField value callback.
 */
internal fun shouldConsumeImeTypedEvent(
    value: TextFieldValue,
    event: KeyEvent,
): Boolean {
    if (value.composition == null || event.type != KeyEventType.Unknown) return false
    val codePoint = event.utf16CodePoint
    return Character.isValidCodePoint(codePoint) &&
        codePoint != AwtKeyEvent.CHAR_UNDEFINED.code &&
        !Character.isISOControl(codePoint)
}

/** Pure decision function kept separate from the platform event adapter for deterministic tests. */
internal fun shouldConsumeImeTypedCharacter(
    compositionActive: Boolean,
    eventId: Int,
    keyChar: Char,
): Boolean {
    return compositionActive &&
        eventId == AwtKeyEvent.KEY_TYPED &&
        keyChar != AwtKeyEvent.CHAR_UNDEFINED &&
        !keyChar.isISOControl()
}
