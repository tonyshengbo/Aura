package com.auracode.assistant.diagram.cache

import com.auracode.assistant.diagram.renderer.DiagramFailure
import com.auracode.assistant.diagram.renderer.DiagramImage
import com.auracode.assistant.diagram.renderer.DiagramRenderRequest
import com.auracode.assistant.diagram.renderer.DiagramRenderResult
import com.auracode.assistant.diagram.renderer.DiagramRenderer
import com.auracode.assistant.diagram.renderer.DiagramTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedDiagramRendererTest {
    private val ready = DiagramRenderResult.Ready(DiagramImage("<svg/>", 10f, 10f, byteArrayOf(1, 2)))

    @Test
    fun `concurrent identical requests render once and themes remain independent`() = runBlocking {
        var calls = 0
        val renderer = CachedDiagramRenderer(DiagramRenderer {
            calls++
            delay(10)
            ready
        })
        List(4) { async { renderer.render(request("a")) } }.awaitAll()
        assertEquals(1, calls)

        renderer.render(request("a").copy(theme = DiagramTheme.DARK))
        assertEquals(2, calls)
    }

    @Test
    fun `least recently used diagrams are evicted`() = runBlocking {
        var calls = 0
        val renderer = CachedDiagramRenderer(DiagramRenderer { calls++; ready }, maxEntries = 2)
        for (source in listOf("a", "b", "a", "c", "b")) renderer.render(request(source))
        assertEquals(4, calls)
    }

    @Test
    fun `oversized results are displayed without retaining them`() = runBlocking {
        var calls = 0
        val renderer = CachedDiagramRenderer(DiagramRenderer { calls++; ready }, maxBytes = 1)
        repeat(2) { renderer.render(request("a")) }
        assertEquals(2, calls)
    }

    @Test
    fun `temporary failures are retried instead of cached`() = runBlocking {
        var calls = 0
        val renderer = CachedDiagramRenderer(DiagramRenderer {
            calls++
            if (calls == 1) DiagramRenderResult.Failed(DiagramFailure.TIMEOUT) else ready
        })
        renderer.render(request("a"))
        assertEquals(ready, renderer.render(request("a")))
        assertEquals(ready, renderer.render(request("a")))
        assertEquals(2, calls)
    }

    private fun request(source: String) = DiagramRenderRequest(source, DiagramTheme.LIGHT)
}
