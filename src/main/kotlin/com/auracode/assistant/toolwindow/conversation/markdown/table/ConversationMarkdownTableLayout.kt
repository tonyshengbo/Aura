package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val TABLE_MIN_COLUMN_WIDTH = 120.dp
internal val TABLE_MAX_COLUMN_WIDTH = 320.dp

/**
 * Caps long columns so their text wraps, while preserving readable widths in narrow viewports.
 * Spare viewport space is shared by every column; overflow is handled by the table scroll host.
 */
internal fun conversationMarkdownTableColumnWidths(
    preferredWidths: List<Dp>,
    viewportWidth: Dp,
    minColumnWidth: Dp = TABLE_MIN_COLUMN_WIDTH,
    maxColumnWidth: Dp = TABLE_MAX_COLUMN_WIDTH,
): List<Dp> {
    if (preferredWidths.isEmpty()) return emptyList()

    val widths = preferredWidths.map { it.coerceIn(minColumnWidth, maxColumnWidth) }
    val totalWidth = widths.fold(0.dp, Dp::plus)
    val extraWidth = if (viewportWidth.value.isFinite() && viewportWidth > totalWidth) {
        (viewportWidth - totalWidth) / widths.size
    } else {
        0.dp
    }
    return widths.map { it + extraWidth }
}
