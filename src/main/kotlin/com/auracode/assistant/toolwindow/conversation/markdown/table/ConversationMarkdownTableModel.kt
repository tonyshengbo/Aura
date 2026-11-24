package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.ui.text.style.TextAlign
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.TABLE_SEPARATOR

internal data class ConversationMarkdownTableRow(
    val cells: List<ASTNode>,
    val isHeader: Boolean,
)

internal data class ConversationMarkdownTableModel(
    val rows: List<ConversationMarkdownTableRow>,
    val columnAlignments: List<TextAlign>,
) {
    val columnCount: Int get() = columnAlignments.size
}

/** Reads the existing Markdown AST so escaped pipes and inline formatting stay intact. */
internal fun conversationMarkdownTableModel(content: String, node: ASTNode): ConversationMarkdownTableModel {
    val rows = node.children
        .filter { it.type == HEADER || it.type == ROW }
        .map { row ->
            ConversationMarkdownTableRow(
                cells = row.children.filter { it.type == CELL },
                isHeader = row.type == HEADER,
            )
        }
    val columnCount = rows.maxOfOrNull { it.cells.size } ?: 0
    val separator = node.children.firstOrNull { it.type == TABLE_SEPARATOR }
    val alignmentMarkers = separator
        ?.let { content.substring(it.startOffset, it.endOffset) }
        ?.trim()
        ?.trim('|')
        ?.split('|')
        .orEmpty()

    return ConversationMarkdownTableModel(
        rows = rows,
        columnAlignments = List(columnCount) { index ->
            val marker = alignmentMarkers.getOrNull(index)?.trim().orEmpty()
            when {
                marker.startsWith(':') && marker.endsWith(':') -> TextAlign.Center
                marker.endsWith(':') -> TextAlign.End
                else -> TextAlign.Start
            }
        },
    )
}
