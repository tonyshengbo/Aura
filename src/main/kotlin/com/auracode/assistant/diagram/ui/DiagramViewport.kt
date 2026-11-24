package com.auracode.assistant.diagram.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.auracode.assistant.i18n.AuraCodeBundle
import kotlin.math.roundToInt

/** Draws into a fixed canvas: zoom changes the draw transform, never the layout or texture size. */
@Composable
internal fun DiagramViewport(
    bitmap: ImageBitmap,
    imageSize: Size,
    state: DiagramViewState,
    modifier: Modifier = Modifier,
) {
    val description = AuraCodeBundle.message("diagram.description")
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val geometry = diagramViewGeometry(imageSize, Size(viewportSize.width.toFloat(), viewportSize.height.toFloat()), state.zoom)
    val dragModifier = if (geometry.panLimits != Offset.Zero) {
        Modifier.pointerInput(bitmap, imageSize, state) {
            detectDragGestures { change, delta ->
                change.consume()
                val viewport = Size(size.width.toFloat(), size.height.toFloat())
                state.panBy(delta, diagramViewGeometry(imageSize, viewport, state.zoom))
            }
        }
    } else Modifier
    Canvas(
        modifier.semantics { contentDescription = description }
            .onSizeChanged { viewportSize = it }
            // At the fitted scale the conversation keeps its normal vertical scrolling gestures.
            .then(dragModifier),
    ) {
        val geometry = diagramViewGeometry(imageSize, size, state.zoom)
        if (geometry.fittedSize == Size.Zero) return@Canvas
        val targetSize = IntSize(
            geometry.fittedSize.width.roundToInt().coerceAtLeast(1),
            geometry.fittedSize.height.roundToInt().coerceAtLeast(1),
        )
        val centerOffset = Offset(
            (size.width - targetSize.width * state.zoom) / 2f,
            (size.height - targetSize.height * state.zoom) / 2f,
        )
        val pan = Offset(
            state.panFraction.x * geometry.panLimits.x,
            state.panFraction.y * geometry.panLimits.y,
        )
        clipRect {
            withTransform({
                translate(centerOffset.x + pan.x, centerOffset.y + pan.y)
                scale(state.zoom, state.zoom, pivot = Offset.Zero)
            }) {
                drawImage(bitmap, dstSize = targetSize)
            }
        }
    }
}
