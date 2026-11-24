package com.auracode.assistant.diagram.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.auracode.assistant.diagram.renderer.DiagramRenderer
import com.auracode.assistant.diagram.renderer.MermaidDiagramService

/** Keeps the Markdown UI independent of the IDE service and allows deterministic render tests. */
internal val LocalDiagramRenderer = staticCompositionLocalOf<DiagramRenderer> {
    DiagramRenderer { request -> MermaidDiagramService.getInstance().render(request) }
}
