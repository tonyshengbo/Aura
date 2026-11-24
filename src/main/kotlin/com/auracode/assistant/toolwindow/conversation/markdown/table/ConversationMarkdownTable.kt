package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import org.intellij.markdown.ast.ASTNode

/** Renders one table in its own viewport, sharing horizontal movement across all rows. */
@Composable
internal fun ConversationMarkdownTable(
    content: String,
    node: ASTNode,
    style: TextStyle,
    scrollState: ScrollState = rememberScrollState(),
) {
    // Streaming updates may add columns or complete cells; never cache the first AST indefinitely.
    val table = remember(content, node) { conversationMarkdownTableModel(content, node) }
    if (table.columnCount == 0) return

    val colors = LocalMarkdownColors.current
    val dimens = LocalMarkdownDimens.current
    val settings = annotatorSettings()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val cellTexts = table.rows.map { row ->
        val cellStyle = if (row.isHeader) style.copy(fontWeight = FontWeight.Bold) else style
        row.cells.map { cell ->
            content.buildMarkdownAnnotatedString(cell, cellStyle, settings)
        }
    }
    val preferredWidths = remember(cellTexts, style, density, textMeasurer, dimens.tableCellPadding) {
        val maxTextWidth = with(density) {
            (TABLE_MAX_COLUMN_WIDTH - dimens.tableCellPadding * 2).coerceAtLeast(1.dp).roundToPx()
        }
        List(table.columnCount) { column ->
            val textWidth = table.rows.indices.maxOf { row ->
                val cellStyle = if (table.rows[row].isHeader) style.copy(fontWeight = FontWeight.Bold) else style
                textMeasurer.measure(
                    text = cellTexts[row].getOrElse(column) { AnnotatedString("") },
                    style = cellStyle,
                    softWrap = false,
                    constraints = Constraints(maxWidth = maxTextWidth),
                ).size.width
            }
            with(density) { textWidth.toDp() } + dimens.tableCellPadding * 2
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidths = conversationMarkdownTableColumnWidths(preferredWidths, maxWidth)
        val tableWidth = columnWidths.fold(0.dp) { total, width -> total + width }
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.tableCornerSize))
                    .background(colors.tableBackground)
                    .horizontalScroll(scrollState)
                    .width(tableWidth),
            ) {
                table.rows.forEachIndexed { rowIndex, row ->
                    val cellStyle = if (row.isHeader) style.copy(fontWeight = FontWeight.Bold) else style
                    Row {
                        columnWidths.forEachIndexed { column, width ->
                            // Fixed column widths keep short/incomplete streaming rows aligned.
                            MarkdownBasicText(
                                text = cellTexts[rowIndex].getOrElse(column) { AnnotatedString("") },
                                style = cellStyle,
                                color = colors.tableText,
                                textAlign = table.columnAlignments[column],
                                modifier = Modifier.width(width).padding(dimens.tableCellPadding),
                                softWrap = true,
                                maxLines = Int.MAX_VALUE,
                            )
                        }
                    }
                    if (row.isHeader) MarkdownDivider(Modifier.fillMaxWidth())
                }
            }

            // A separate track remains draggable without covering selectable cell text.
            if (scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = LocalScrollbarStyle.current.copy(
                        unhoverColor = colors.tableText.copy(alpha = 0.30f),
                        hoverColor = colors.tableText.copy(alpha = 0.60f),
                    ),
                )
            }
        }
    }
}
