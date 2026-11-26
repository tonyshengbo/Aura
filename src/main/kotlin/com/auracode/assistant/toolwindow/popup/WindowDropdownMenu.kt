package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** Material 菜单内容与窗口承载解耦；业务菜单保留原有 DropdownMenuItem 和主题。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WindowDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (expanded) {
        val density = LocalDensity.current
        val provider = remember(offset, density) {
            DropdownPopupPositionProvider(with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) })
        }
        WindowPopup(provider, onDismissRequest, properties) {
            val focus = LocalFocusManager.current
            val inputMode = LocalInputModeManager.current
            val requester = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            val windowFocused = LocalWindowPopupFocused.current
            LaunchedEffect(windowFocused) {
                if (windowFocused && !hasFocus) requester.requestFocus()
            }
            PopupSurface(
                modifier = modifier.focusRequester(requester).onFocusChanged { hasFocus = it.hasFocus }.onKeyEvent {
                    if (it.type == KeyEventType.KeyDown) {
                        when (it.key) {
                            Key.DirectionDown, Key.DirectionUp -> {
                                inputMode.requestInputMode(InputMode.Keyboard)
                                val direction = if (it.key == Key.DirectionDown) {
                                    FocusDirection.Next
                                } else {
                                    FocusDirection.Previous
                                }
                                focus.moveFocus(direction)
                            }
                            else -> false
                        }
                    } else false
                }.focusable(enabled = properties.focusable),
            ) {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max).verticalScroll(scrollState).padding(vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}
