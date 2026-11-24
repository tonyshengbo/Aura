package com.auracode.assistant.diagram.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler.ErrorCode
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandler.TerminationStatus
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JWindow

/** Owns one off-screen browser. Creation, script submission and disposal run on the Swing EDT. */
internal class MermaidBrowserSession : Disposable {
    private val browser = JBCefBrowser.createBuilder().setOffScreenRendering(true).build()
    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val sequence = AtomicLong()
    private val requests = ConcurrentHashMap<String, CompletableDeferred<DiagramRenderResult>>()
    private val hostWindow: JWindow
    val ready = CompletableDeferred<Unit>()

    init {
        browser.component.setSize(1600, 1200)
        query.addHandler { payload ->
            runCatching {
                require(payload.length <= 10_100_000)
                acceptResult(Json.parseToJsonElement(payload).jsonObject)
            }.onFailure { error -> failPending(error) }
            null
        }
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                LOG.debug("Mermaid document loaded: ${frame.url}, status=$httpStatusCode")
                if (frame.isMain && frame.url == PAGE_URL && !ready.isCompleted && !browser.isDisposed) {
                    val bootstrap = loadResource("mermaid.min.js") + "\n" + loadResource("runtime.js") +
                        "\nwindow.auraComplete = result => { ${query.inject("JSON.stringify(result)")} };" +
                        "\nwindow.auraComplete({id:'ready'});"
                    cefBrowser.executeJavaScript(bootstrap, PAGE_URL, 0)
                }
            }

            override fun onLoadError(
                browser: CefBrowser, frame: CefFrame, errorCode: ErrorCode, errorText: String, failedUrl: String,
            ) {
                if (frame.isMain && errorCode != ErrorCode.ERR_ABORTED) {
                    ready.completeExceptionally(IllegalStateException("Mermaid document load failed: $errorCode"))
                }
            }
        }, browser.cefBrowser)
        browser.jbCefClient.addRequestHandler(localRequestsOnly(), browser.cefBrowser)
        browser.setOpenLinksInExternalBrowser(false)
        browser.loadHTML(
            """<!doctype html><html><head><meta charset="utf-8">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'none'; img-src blob: data:">
                </head><body><div id="diagram-host"></div></body></html>""".trimIndent(),
            PAGE_URL,
        )
        // macOS JCEF needs a realized native parent even for off-screen browsers. Never show it.
        hostWindow = JWindow().apply {
            focusableWindowState = false
            isAutoRequestFocus = false
            contentPane.add(browser.component)
            setSize(1600, 1200)
            addNotify()
        }
        browser.createImmediately()
    }

    /** JSON serialization keeps diagram text out of executable JavaScript syntax. */
    fun submit(request: DiagramRenderRequest): Pair<String, CompletableDeferred<DiagramRenderResult>> {
        val id = sequence.incrementAndGet().toString()
        val result = CompletableDeferred<DiagramRenderResult>()
        requests[id] = result
        val payload = buildJsonObject {
            put("source", request.source)
            put("dark", request.theme == DiagramTheme.DARK)
        }
        val encodedId = JsonPrimitive(id).toString()
        browser.cefBrowser.executeJavaScript(
            """window.auraDiagram.render($payload)
                .then(result => window.auraComplete({...result, id:$encodedId}))
                .catch(() => window.auraComplete({id:$encodedId, error:true}));""".trimIndent(),
            PAGE_URL,
            0,
        )
        return id to result
    }

    fun forget(id: String) {
        requests.remove(id)?.cancel()
    }

    private fun acceptResult(message: JsonObject) {
        val id = message["id"]?.jsonPrimitive?.content ?: return
        if (id == "ready") {
            LOG.debug("Mermaid runtime ready")
            ready.complete(Unit)
            return
        }
        val pending = requests.remove(id) ?: return
        val result = runCatching {
            if (message.containsKey("error")) {
                DiagramRenderResult.Failed(DiagramFailure.INVALID)
            } else {
                val svg = message.getValue("svg").jsonPrimitive.content
                val width = message.getValue("width").jsonPrimitive.float
                val height = message.getValue("height").jsonPrimitive.float
                val encodedPng = message.getValue("png").jsonPrimitive.content
                require(svg.length <= 2_000_000 && width.isFinite() && height.isFinite())
                require(width > 0 && height > 0 && width <= 32_000 && height <= 32_000)
                require(encodedPng.length <= 8_000_000)
                DiagramRenderResult.Ready(DiagramImage(svg, width, height, Base64.getDecoder().decode(encodedPng)))
            }
        }.getOrElse { DiagramRenderResult.Failed(DiagramFailure.RENDER_ERROR) }
        pending.complete(result)
    }

    private fun failPending(error: Throwable) {
        ready.completeExceptionally(error)
        requests.values.forEach { it.completeExceptionally(error) }
        requests.clear()
    }

    override fun dispose() {
        ready.cancel()
        requests.values.forEach { it.cancel() }
        requests.clear()
        Disposer.dispose(browser)
        hostWindow.dispose()
    }

    private fun localRequestsOnly() = object : CefRequestHandlerAdapter() {
        override fun onRenderProcessTerminated(browser: CefBrowser, status: TerminationStatus) {
            failPending(IllegalStateException("Mermaid browser process terminated: $status"))
        }

        override fun onBeforeBrowse(
            browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean,
        ): Boolean = request.url != PAGE_URL && request.url != "about:blank"

        override fun getResourceRequestHandler(
            browser: CefBrowser, frame: CefFrame, request: CefRequest, isNavigation: Boolean,
            isDownload: Boolean, requestInitiator: String, disableDefaultHandling: BoolRef,
        ): CefResourceRequestHandler? {
            if (request.url == PAGE_URL || request.url.startsWith("blob:") || request.url.startsWith("data:image/")) return null
            return object : CefResourceRequestHandlerAdapter() {
                override fun onBeforeResourceLoad(browser: CefBrowser, frame: CefFrame, request: CefRequest) = true
            }
        }
    }

    companion object {
        // loadHTML registers this virtual file in JCEF; it does not read a file from disk.
        private const val PAGE_URL = "file:///aura-diagram/index.html"
        private val LOG = Logger.getInstance(MermaidBrowserSession::class.java)

        private fun loadResource(name: String): String =
            requireNotNull(MermaidBrowserSession::class.java.getResourceAsStream("/diagram/mermaid/$name"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
