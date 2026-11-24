package com.auracode.assistant.diagram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.auracode.assistant.toolwindow.shared.DesignPalette

internal val LocalDiagramFullscreenHost = staticCompositionLocalOf<DiagramFullscreenState?> { null }

internal class DiagramFullscreenState {
    var content by mutableStateOf<DiagramContent?>(null)
        private set

    fun open(value: DiagramContent) { content = value }
    fun close() { content = null }
    fun closeIfOwner(owner: DiagramContent) {
        if (content === owner) close()
    }
}

/** Hosts fullscreen in the existing Compose root, outside the conversation's selection hierarchy. */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun DiagramFullscreenHost(
    palette: DesignPalette,
    modifier: Modifier = Modifier,
    state: DiagramFullscreenState = remember { DiagramFullscreenState() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDiagramFullscreenHost provides state) {
        Box(modifier.onPreviewKeyEvent { event ->
            // Keep Escape available even when a zoom button becomes disabled and loses focus.
            if (state.content != null && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                state.close()
                true
            } else false
        }.focusable()) {
            content()
            val diagram = state.content
            if (diagram != null) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(diagram) { focusRequester.requestFocus() }
                Box(
                    Modifier.fillMaxSize().background(palette.appBg)
                        .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) {
                            focusRequester.requestFocus()
                        }
                        .pointerInput(Unit) {
                            // Block background chat gestures after the diagram controls receive input.
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                                }
                            }
                        }
                        .focusRequester(focusRequester)
                        .focusable(),
                ) {
                    DiagramSurfaceContent(
                        diagram, palette, fullscreen = true, onToggleFullscreen = state::close,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
        }
    }
}
