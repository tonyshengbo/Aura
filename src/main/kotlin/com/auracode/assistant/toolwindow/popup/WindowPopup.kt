package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import javax.swing.JComponent

/** 当前 Compose 坐标系对应的 Swing 根组件；嵌套弹窗会切换到自己的根组件。 */
internal val LocalWindowPopupOwner = compositionLocalOf<JComponent?> { null }
internal val LocalWindowPopupFocused = compositionLocalOf { false }
internal const val PopupWindowPadding = 8

/** 内容由 Compose 官方透明窗口渲染，跨越 SwingPanel 的绘制层。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WindowPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    val owner = checkNotNull(LocalWindowPopupOwner.current) { "WindowPopup requires a Compose owner component" }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val currentContent = rememberUpdatedState(content)
    val currentLocals = rememberUpdatedState(currentCompositionLocalContext)
    val currentDismiss = rememberUpdatedState(onDismissRequest)

    // 与 Compose Popup 相同，以调用位置的父布局为锚点，本身不占布局空间。
    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { coordinates ->
            coordinates.parentLayoutCoordinates?.let { parent ->
                anchor = Rect(parent.positionInRoot(), parent.size.toSize())
            }
        },
    ) { _, _ -> layout(0, 0) {} }

    val bounds = anchor
    if (bounds != null) {
        val host = remember(owner, properties) { WindowPopupHost(owner, properties) { currentDismiss.value() } }
        SideEffect { host.updateAnchor(bounds, density.density, direction, popupPositionProvider) }
        DisposableEffect(host) {
            host.window.setContent {
                // 继承业务主题和状态，但保留新场景自己的焦点、输入法、窗口及坐标服务。
                val windowLocals = currentCompositionLocalContext
                CompositionLocalProvider(currentLocals.value) {
                    CompositionLocalProvider(windowLocals) {
                        CompositionLocalProvider(
                            LocalWindowPopupOwner provides host.panel,
                            LocalWindowPopupFocused provides host.focused,
                        ) {
                            PopupContentLayout(
                                maxSize = host.maxContentSize,
                                onSize = host::updateContentSize,
                                properties = properties,
                                onDismiss = host::dismiss,
                                content = { currentContent.value() },
                            )
                        }
                    }
                }
            }
            try {
                host.mount()
            } catch (failure: Throwable) {
                host.close()
                throw failure
            }
            onDispose { host.close() }
        }
    }
}

/** 内容可独立于当前窗口大小重新测量，过滤补全或新增自定义模型后窗口也会随之缩放。 */
@Composable
private fun PopupContentLayout(
    maxSize: IntSize,
    onSize: (IntSize) -> Unit,
    properties: PopupProperties,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val requester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val focused = LocalWindowPopupFocused.current
    LaunchedEffect(focused) {
        if (focused && !hasFocus) requester.requestFocus()
    }
    val modifier = Modifier
        .focusRequester(requester)
        .onFocusChanged { hasFocus = it.hasFocus }
        .onPreviewKeyEvent {
            if (properties.dismissOnBackPress && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else false
        }
        .focusable(enabled = properties.focusable)
    Layout(
        content = { Box(Modifier.padding(PopupWindowPadding.dp)) { content() } },
        modifier = modifier,
    ) { measurables, _ ->
        val child = measurables.single().measure(Constraints(maxWidth = maxSize.width, maxHeight = maxSize.height))
        onSize(IntSize(child.width, child.height))
        layout(child.width, child.height) { child.place(0, 0) }
    }
}
