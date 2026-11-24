package com.auracode.assistant.diagram.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.awt.EventQueue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds

/** Lazily starts JCEF only when a completed Mermaid block becomes visible. */
internal class JcefMermaidRenderer : DiagramRenderer, Disposable {
    private var session: MermaidBrowserSession? = null
    @Volatile private var disposed = false

    override suspend fun render(request: DiagramRenderRequest): DiagramRenderResult {
        if (request.source.length > 20_000) return DiagramRenderResult.Failed(DiagramFailure.INVALID)
        try {
            return withTimeout(15_000.milliseconds) {
                val current = onEdt {
                    check(!disposed)
                    if (!JBCefApp.isSupported()) null else session ?: MermaidBrowserSession().also { session = it }
                } ?: return@withTimeout DiagramRenderResult.Failed(DiagramFailure.UNAVAILABLE)
                current.ready.await()
                val (id, result) = onEdt { current.submit(request) }
                try {
                    result.await()
                } finally {
                    current.forget(id)
                }
            }
        } catch (_: TimeoutCancellationException) {
            reset()
            return DiagramRenderResult.Failed(DiagramFailure.TIMEOUT)
        } catch (error: CancellationException) {
            reset()
            throw error
        } catch (error: Exception) {
            LOG.warn("Mermaid browser could not render a diagram", error)
            reset()
            return DiagramRenderResult.Failed(DiagramFailure.RENDER_ERROR)
        } catch (_: LinkageError) {
            reset()
            return DiagramRenderResult.Failed(DiagramFailure.UNAVAILABLE)
        }
    }

    /** Queued before the next render submission, so cancellation cannot reuse a stale browser. */
    private fun reset() {
        if (EventQueue.isDispatchThread()) disposeSession() else EventQueue.invokeLater(::disposeSession)
    }

    override fun dispose() {
        disposed = true
        // Application shutdown checks for leaks immediately after service disposal returns.
        if (EventQueue.isDispatchThread()) disposeSession() else EventQueue.invokeAndWait(::disposeSession)
    }

    private fun disposeSession() {
        session?.dispose()
        session = null
    }

    companion object {
        private val LOG = Logger.getInstance(JcefMermaidRenderer::class.java)
    }
}

private suspend fun <T> onEdt(action: () -> T): T = suspendCoroutine { continuation ->
    val task = Runnable {
        try {
            continuation.resume(action())
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
        }
    }
    if (EventQueue.isDispatchThread()) task.run() else EventQueue.invokeLater(task)
}
