package com.auracode.assistant.toolwindow.conversation.markdown.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.auracode.assistant.diagram.renderer.DiagramFailure
import com.auracode.assistant.diagram.renderer.DiagramRenderRequest
import com.auracode.assistant.diagram.renderer.DiagramRenderResult
import com.auracode.assistant.diagram.renderer.DiagramTheme
import com.auracode.assistant.diagram.ui.DiagramSurface
import com.auracode.assistant.diagram.ui.LocalDiagramRenderer
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.intellij.markdown.ast.ASTNode

/** Overrides Mermaid fences only; other languages keep their original code renderer. */
@Composable
internal fun ConversationDiagramCodeFence(content: String, node: ASTNode, palette: DesignPalette) {
    val fence = remember(content, node) { markdownDiagramFence(content, node) }
    if (fence == null) {
        MarkdownCodeFence(content, node)
        return
    }
    val renderer = LocalDiagramRenderer.current
    val theme = if (palette.timelineCardBg.luminance() < 0.5f) DiagramTheme.DARK else DiagramTheme.LIGHT
    val result by produceState<DiagramRenderResult?>(null, fence, theme, renderer) {
        value = null
        if (fence.closed && fence.supported) {
            // Only completed blocks enter JCEF; nearby streamed updates are coalesced.
            delay(150)
            value = try {
                renderer.render(DiagramRenderRequest(fence.source, theme))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DiagramRenderResult.Failed(DiagramFailure.UNAVAILABLE)
            }
        }
    }
    val image = (result as? DiagramRenderResult.Ready)?.image
    if (image != null) {
        DiagramSurface(image, palette)
        return
    }
    Column(Modifier.fillMaxWidth().background(palette.markdownCodeBg).padding(12.dp)) {
        DisableSelection {
            val status = when {
                !fence.closed -> "diagram.waiting"
                !fence.supported -> "diagram.unsupported"
                result == null -> "diagram.rendering"
                result is DiagramRenderResult.Failed -> when ((result as DiagramRenderResult.Failed).reason) {
                    DiagramFailure.UNAVAILABLE -> "diagram.unavailable"
                    DiagramFailure.TIMEOUT -> "diagram.timeout"
                    else -> "diagram.failed"
                }
                else -> null
            }
            if (status != null) Text(AuraCodeBundle.message(status), color = palette.textMuted)
        }
        MarkdownBasicText(
            text = AnnotatedString(fence.source),
            style = LocalMarkdownTypography.current.code,
            color = palette.markdownCodeText,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        )
    }
}
