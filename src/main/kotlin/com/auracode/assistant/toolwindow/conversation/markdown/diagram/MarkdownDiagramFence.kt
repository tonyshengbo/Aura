package com.auracode.assistant.toolwindow.conversation.markdown.diagram

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode

internal data class MarkdownDiagramFence(val source: String, val closed: Boolean, val supported: Boolean)

/** Reads fence tokens directly so incomplete streamed blocks and nested indentation are preserved. */
internal fun markdownDiagramFence(content: String, node: ASTNode): MarkdownDiagramFence? {
    val language = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
        ?.let { content.substring(it.startOffset, it.endOffset) }?.trim()
    if (!language.equals("mermaid", ignoreCase = true)) return null

    val source = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
        .joinToString("\n") { content.substring(it.startOffset, it.endOffset) }
        .trimIndent().trim()
    val declaration = source.lineSequence().map(String::trim)
        .firstOrNull { it.isNotBlank() && !it.startsWith("%%") }.orEmpty()
    return MarkdownDiagramFence(
        source = source,
        closed = node.children.any { it.type == MarkdownTokenTypes.CODE_FENCE_END },
        supported = Regex("^(flowchart|graph)(\\s|$)").containsMatchIn(declaration),
    )
}
