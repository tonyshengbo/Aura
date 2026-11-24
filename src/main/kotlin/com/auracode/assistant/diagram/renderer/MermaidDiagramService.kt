package com.auracode.assistant.diagram.renderer

import com.auracode.assistant.diagram.cache.CachedDiagramRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/** One bounded cache and one browser serve all projects; application disposal releases JCEF. */
@Service(Service.Level.APP)
internal class MermaidDiagramService : DiagramRenderer, Disposable {
    private val browserRenderer = JcefMermaidRenderer()
    private val renderer = CachedDiagramRenderer(browserRenderer)

    override suspend fun render(request: DiagramRenderRequest) = renderer.render(request)

    override fun dispose() = browserRenderer.dispose()

    companion object {
        fun getInstance(): MermaidDiagramService =
            ApplicationManager.getApplication().getService(MermaidDiagramService::class.java)
    }
}
