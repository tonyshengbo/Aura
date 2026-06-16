package com.auracode.assistant.toolwindow.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthUnit
import org.jetbrains.skia.svg.SVGPreserveAspectRatio
import org.jetbrains.skia.svg.SVGPreserveAspectRatioAlign
import java.io.InputStream

@Composable
internal fun assistantPainterResource(resourcePath: String): Painter {
    val density = LocalDensity.current
    return remember(resourcePath, density) {
        openAssistantResource(resourcePath).use { input ->
            val bytes = input.readAllBytes()
            when (resourcePath.substringAfterLast('.').lowercase()) {
                "svg" -> AssistantSvgPainter(bytes, density)
                else -> BitmapPainter(decodeAssistantImageBitmap(bytes))
            }
        }
    }
}

internal fun loadAssistantImageBitmap(input: InputStream): ImageBitmap =
    decodeAssistantImageBitmap(input.readAllBytes())

private fun decodeAssistantImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

private fun openAssistantResource(resourcePath: String): InputStream {
    val normalized = resourcePath.removePrefix("/")
    val contextLoader = Thread.currentThread().contextClassLoader
    return contextLoader?.getResourceAsStream(normalized)
        ?: AssistantSvgPainter::class.java.classLoader.getResourceAsStream(normalized)
        ?: AssistantSvgPainter::class.java.getResourceAsStream(resourcePath)
        ?: error("Resource $resourcePath not found")
}

private class AssistantSvgPainter(
    bytes: ByteArray,
    private val density: Density,
) : Painter() {
    private val dom = SVGDOM(Data.makeFromBytes(bytes))
    private val root = dom.root
    private val defaultSizePx: Size = run {
        val width = root?.width?.withUnit(SVGLengthUnit.PX)?.value ?: 0f
        val height = root?.height?.withUnit(SVGLengthUnit.PX)?.value ?: 0f
        if (width == 0f && height == 0f) Size.Unspecified else Size(width, height)
    }

    init {
        if (root?.viewBox == null && defaultSizePx.isSpecified) {
            root?.viewBox = Rect.makeXYWH(0f, 0f, defaultSizePx.width, defaultSizePx.height)
        }
    }

    override val intrinsicSize: Size
        get() = if (defaultSizePx.isSpecified) defaultSizePx * density.density else Size.Unspecified

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            @Suppress("NAME_SHADOWING")
            val size = size
            root?.width = SVGLength(size.width, SVGLengthUnit.PX)
            root?.height = SVGLength(size.height, SVGLengthUnit.PX)
            root?.preserveAspectRatio = SVGPreserveAspectRatio(SVGPreserveAspectRatioAlign.NONE)
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.saveLayer(null, null)
            dom.render(canvas.nativeCanvas)
            canvas.nativeCanvas.restore()
            canvas.nativeCanvas.restore()
        }
    }
}
