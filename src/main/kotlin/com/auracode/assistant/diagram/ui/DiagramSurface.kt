package com.auracode.assistant.diagram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.auracode.assistant.diagram.renderer.DiagramImage
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.shared.DesignPalette
import org.jetbrains.skia.Image

/** The inline and fullscreen surfaces share one decoded bitmap and one transform state. */
internal data class DiagramContent(
    val image: DiagramImage,
    val bitmap: ImageBitmap,
    val state: DiagramViewState,
)

@Composable
internal fun DiagramSurface(image: DiagramImage, palette: DesignPalette) {
    val content = remember(image) {
        runCatching {
            val bitmap = Image.makeFromEncoded(image.png).use { it.toComposeImageBitmap() }
            DiagramContent(image, bitmap, DiagramViewState())
        }.getOrNull()
    }
    if (content == null) {
        Text(AuraCodeBundle.message("diagram.failed"), color = palette.textMuted)
        return
    }
    val host = LocalDiagramFullscreenHost.current
    DisposableEffect(content, host) {
        onDispose { host?.closeIfOwner(content) }
    }
    DiagramSurfaceContent(
        content, palette, fullscreen = false,
        onToggleFullscreen = host?.let { { it.open(content) } },
    )
}

/** All controls are excluded from Markdown selection, including fullscreen tooltips. */
@Composable
internal fun DiagramSurfaceContent(
    content: DiagramContent,
    palette: DesignPalette,
    fullscreen: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    DisableSelection {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(palette.markdownCodeBg).padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                DiagramToolbar(content.state, palette, fullscreen, onToggleFullscreen)
            }
            val imageSize = with(LocalDensity.current) {
                Size(content.image.width.dp.toPx(), content.image.height.dp.toPx())
            }
            if (fullscreen) {
                DiagramViewport(content.bitmap, imageSize, content.state, Modifier.weight(1f).fillMaxWidth())
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val height = (maxWidth.value * content.image.height / content.image.width)
                        .coerceIn(120f, 360f).dp
                    DiagramViewport(
                        content.bitmap, imageSize, content.state,
                        Modifier.fillMaxWidth().height(height),
                    )
                }
            }
        }
    }
}
