package com.auracode.assistant.toolwindow.conversation.markdown.diagram

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownDiagramFenceTest {
    @Test
    fun `ordinary code blocks keep the default renderer`() {
        assertNull(parse("```kotlin\nprintln(1)\n```"))
    }

    @Test
    fun `complete mermaid block extracts all source lines`() {
        val fence = requireNotNull(parse("```mermaid\nflowchart LR\n A[开始] --> B{条件}\n B -->|是| C[完成]\n```"))

        assertEquals("flowchart LR\n A[开始] --> B{条件}\n B -->|是| C[完成]", fence.source)
        assertTrue(fence.closed)
        assertTrue(fence.supported)
    }

    @Test
    fun `unfinished streamed blocks retain the last line and wait for closure`() {
        val fence = requireNotNull(parse("```mermaid\nflowchart TD\nA --> B"))

        assertFalse(fence.closed)
        assertEquals("flowchart TD\nA --> B", fence.source)
    }

    @Test
    fun `indented tilde fences and graph aliases are supported`() {
        val fence = requireNotNull(parse("  ~~~MERMAID\n  %% comment\n  graph BT\n  A --> B\n  ~~~"))

        assertTrue(fence.closed)
        assertTrue(fence.supported)
        assertTrue(fence.source.endsWith("A --> B"))
    }

    @Test
    fun `other mermaid diagrams and empty fences fall back to source`() {
        assertFalse(requireNotNull(parse("```mermaid\nsequenceDiagram\nA->>B: hi\n```")).supported)
        assertFalse(requireNotNull(parse("```mermaid\n```")).supported)
    }

    private fun parse(content: String): MarkdownDiagramFence? {
        val root = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(content)
        return markdownDiagramFence(content, requireNotNull(findFence(root)))
    }

    private fun findFence(node: ASTNode): ASTNode? {
        if (node.type == MarkdownElementTypes.CODE_FENCE) return node
        return node.children.firstNotNullOfOrNull(::findFence)
    }
}
