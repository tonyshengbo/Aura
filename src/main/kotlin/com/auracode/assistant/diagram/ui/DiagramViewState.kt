package com.auracode.assistant.diagram.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Shares zoom and relative pan between the inline view and its fullscreen host. */
internal class DiagramViewState {
    var zoom by mutableStateOf(1f)
        private set
    var panFraction by mutableStateOf(Offset.Zero)
        private set

    fun zoomBy(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        zoom = (zoom * factor).coerceIn(1f, 8f)
        if (zoom == 1f) panFraction = Offset.Zero
    }

    fun panBy(delta: Offset, geometry: DiagramViewGeometry) {
        if (!delta.x.isFinite() || !delta.y.isFinite()) return
        val limits = geometry.panLimits
        panFraction = Offset(
            panAxis(panFraction.x, delta.x, limits.x),
            panAxis(panFraction.y, delta.y, limits.y),
        )
    }

    private fun panAxis(current: Float, delta: Float, limit: Float): Float =
        if (limit > 0f) (current + delta / limit).coerceIn(-1f, 1f) else 0f
}

internal data class DiagramViewGeometry(val fittedSize: Size, val panLimits: Offset)

/** Finite viewport geometry prevents invalid draw bounds during fullscreen/window resize. */
internal fun diagramViewGeometry(image: Size, viewport: Size, zoom: Float): DiagramViewGeometry {
    if (!image.width.isFinite() || !image.height.isFinite() || image.width <= 0 || image.height <= 0 ||
        !viewport.width.isFinite() || !viewport.height.isFinite() || viewport.width <= 0 || viewport.height <= 0
    ) {
        return DiagramViewGeometry(Size.Zero, Offset.Zero)
    }
    val scale = minOf(viewport.width / image.width, viewport.height / image.height, 1f)
    val fitted = image * scale
    val safeZoom = if (zoom.isFinite()) zoom.coerceIn(1f, 8f) else 1f
    return DiagramViewGeometry(
        fittedSize = fitted,
        panLimits = Offset(
            ((fitted.width * safeZoom - viewport.width) / 2f).coerceAtLeast(0f),
            ((fitted.height * safeZoom - viewport.height) / 2f).coerceAtLeast(0f),
        ),
    )
}
