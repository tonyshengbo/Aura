package com.auracode.assistant.toolwindow.conversation.markdown.table

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.mikepenz.markdown.annotator.DefaultAnnotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes.TABLE
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationMarkdownTableModelTest {
    @Test
    fun `table cells preserve escaped pipes and rich text annotations`() {
        val content = """
            | Name | Details |
            | --- | --- |
            | a\|b | **bold** and [link](https://example.com) |
        """.trimIndent()
        val model = parseTable(content)
        val cells = model.rows[1].cells.map { cell ->
            content.buildMarkdownAnnotatedString(cell, TextStyle.Default, DefaultAnnotatorSettings(TextStyle.Default))
        }

        assertEquals(2, model.columnCount)
        assertEquals("a|b", cells[0].text.trim())
        assertEquals("bold and link", cells[1].text.trim())
        assertTrue(cells[1].spanStyles.isNotEmpty())
        val link = cells[1].getLinkAnnotations(0, cells[1].length).single().item as LinkAnnotation.Url
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun `separator alignment applies to every column`() {
        val model = parseTable("""
            | Left | Center | Right |
            | :--- | :---: | ---: |
            | a | b | c |
        """.trimIndent())

        assertEquals(listOf(TextAlign.Start, TextAlign.Center, TextAlign.End), model.columnAlignments)
        assertTrue(model.rows.first().isHeader)
        assertEquals(2, model.rows.size)
    }

    @Test
    fun `streaming updates rebuild columns and preserve unfinished rows`() {
        val initial = parseTable("| First |\n| --- |\n| value |")
        val updated = parseTable("| First | Second | Third |\n| --- | --- | --- |\n| value | partial")

        assertEquals(1, initial.columnCount)
        assertEquals(3, updated.columnCount)
        assertEquals(2, updated.rows.last().cells.size)
    }

    /** Uses the same GFM parser as production instead of constructing simplified AST fixtures. */
    private fun parseTable(content: String): ConversationMarkdownTableModel {
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        return conversationMarkdownTableModel(content, requireNotNull(findTable(root)))
    }

    private fun findTable(node: ASTNode): ASTNode? {
        if (node.type == TABLE) return node
        return node.children.firstNotNullOfOrNull(::findTable)
    }
}
