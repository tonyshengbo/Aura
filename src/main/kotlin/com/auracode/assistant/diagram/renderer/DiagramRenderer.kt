package com.auracode.assistant.diagram.renderer

internal enum class DiagramTheme { LIGHT, DARK }

internal data class DiagramRenderRequest(val source: String, val theme: DiagramTheme)

/** Retains the original SVG and a bounded Chromium raster for Skia versions with partial SVG support. */
internal data class DiagramImage(val svg: String, val width: Float, val height: Float, val png: ByteArray)

internal enum class DiagramFailure { UNAVAILABLE, INVALID, TIMEOUT, RENDER_ERROR }

internal sealed interface DiagramRenderResult {
    data class Ready(val image: DiagramImage) : DiagramRenderResult
    data class Failed(val reason: DiagramFailure) : DiagramRenderResult
}

internal fun interface DiagramRenderer {
    suspend fun render(request: DiagramRenderRequest): DiagramRenderResult
}
