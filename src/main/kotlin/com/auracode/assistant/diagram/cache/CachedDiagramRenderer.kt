package com.auracode.assistant.diagram.cache

import com.auracode.assistant.diagram.renderer.DiagramRenderRequest
import com.auracode.assistant.diagram.renderer.DiagramRenderResult
import com.auracode.assistant.diagram.renderer.DiagramRenderer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the shared browser and bounds retained SVG/PNG data by entry count and byte size. */
internal class CachedDiagramRenderer(
    private val delegate: DiagramRenderer,
    private val maxEntries: Int = 48,
    private val maxBytes: Int = 8 * 1024 * 1024,
) : DiagramRenderer {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<DiagramRenderRequest, DiagramRenderResult.Ready>(16, 0.75f, true)
    private var bytes = 0

    override suspend fun render(request: DiagramRenderRequest): DiagramRenderResult = mutex.withLock {
        entries[request]?.let { return@withLock it }
        val result = delegate.render(request)
        if (result is DiagramRenderResult.Ready && weight(request, result) <= maxBytes) {
            entries[request] = result
            bytes += weight(request, result)
            while (entries.size > maxEntries || bytes > maxBytes) {
                val eldest = entries.entries.iterator().next()
                bytes -= weight(eldest.key, eldest.value)
                entries.remove(eldest.key)
            }
        }
        result
    }

    private fun weight(request: DiagramRenderRequest, result: DiagramRenderResult.Ready): Int =
        (request.source.length + result.image.svg.length) * 2 + result.image.png.size
}
