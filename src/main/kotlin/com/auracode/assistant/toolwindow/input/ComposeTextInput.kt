package com.auracode.assistant.toolwindow.input

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.Text
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantBodyTextStyle
import com.auracode.assistant.toolwindow.shared.assistantUiTokens
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

internal val LocalInputEnabled = compositionLocalOf { true }
internal val LocalMainInputController = compositionLocalOf<InputController?> { null }

/** 同一会话内保持 TextFieldState；业务快照从不回写正在进行的逐字输入。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
internal fun ComposeTextInput(
    draft: InputDraft,
    palette: DesignPalette,
    onValueChange: (InputDraft) -> Unit,
    modifier: Modifier = Modifier,
    identityKey: Any? = Unit,
    maxLines: Int = 6,
    placeholder: String = "",
    outlinedLabel: String? = null,
    secret: Boolean = false,
    controller: InputController? = null,
    highlights: List<InputHighlight> = emptyList(),
    resolveCommand: (InputKey, TextFieldValue) -> InputCommand? = { _, _ -> null },
    normalizeValue: (TextFieldValue, TextFieldValue) -> TextFieldValue = { _, value -> value },
    onCaretBoundsChange: (Rect?) -> Unit = {},
    onTextPositioned: (LayoutCoordinates) -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val tokens = assistantUiTokens()
    val enabled = LocalInputEnabled.current
    key(identityKey, secret) {
        val session = remember { ComposeInputSession(draft) }
        val focus = remember { FocusRequester() }
        val scroll = rememberScrollState()
        val interactionSource = remember { MutableInteractionSource() }
        val lineBreakKey = remember { ComposeLineBreakKey() }
        val currentResolve by rememberUpdatedState(resolveCommand)
        val currentCaret by rememberUpdatedState(onCaretBoundsChange)
        var layoutGetter by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
        SideEffect {
            session.onValueChange = onValueChange
            session.normalizeValue = normalizeValue
            session.requestFocus = { if (enabled) focus.requestFocus() }
            session.acceptExternal(draft)
        }
        DisposableEffect(session, controller) {
            controller?.session = session
            onDispose {
                if (controller?.session === session) controller.session = null
            }
        }
        DisposableEffect(session) {
            onDispose { session.dispose() }
        }
        LaunchedEffect(session) {
            snapshotFlow { session.value() }.collect { session.flush() }
        }
        LaunchedEffect(session, scroll) {
            snapshotFlow {
                layoutGetter?.invoke()?.let { layout ->
                    layout.getCursorRect(session.state.selection.end.coerceIn(0, layout.layoutInput.text.length))
                        .translate(Offset(0f, if (secret) 0f else -scroll.value.toFloat()))
                }
            }.collect { currentCaret(it) }
        }
        val style = assistantBodyTextStyle(tokens).copy(color = palette.textPrimary)
        val decorator = TextFieldDecorator { inner ->
            val textContent: @Composable () -> Unit = {
                Box(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned(onTextPositioned)
                        .clipToBounds()
                        .drawBehind {
                            val layout = layoutGetter?.invoke() ?: return@drawBehind
                            val composition = session.state.composition
                            fun liveOffset(offset: Int, start: Boolean) = if (composition != null &&
                                (offset > composition.min || (start && offset == composition.min))) {
                                offset + composition.length
                            } else offset
                            translate(top = if (secret) 0f else -scroll.value.toFloat()) {
                                highlights.forEach { highlight ->
                                    val start = liveOffset(highlight.start, start = true)
                                    val end = liveOffset(highlight.end, start = false)
                                    if (start >= 0 && end > start && end <= layout.layoutInput.text.length) {
                                        drawPath(layout.getPathForRange(start, end), palette.topStripBg)
                                    }
                                }
                            }
                        },
                ) { inner() }
            }
            if (outlinedLabel != null) {
                // 与旧版相同的官方 Outlined 控件装饰，浮动标签、边框缺口及聚焦动画由 Material 管理。
                TextFieldDefaults.OutlinedTextFieldDecorationBox(
                    value = session.state.text.toString(),
                    innerTextField = textContent,
                    enabled = enabled,
                    singleLine = false,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    label = { Text(outlinedLabel, color = palette.textMuted, style = MaterialTheme.typography.body2) },
                    shape = RoundedCornerShape(tokens.spacing.sm),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = palette.textPrimary,
                        backgroundColor = palette.timelineCardBg,
                        cursorColor = palette.textPrimary,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.markdownDivider.copy(alpha = 0.55f),
                        focusedLabelColor = palette.textSecondary,
                        unfocusedLabelColor = palette.textMuted,
                    ),
                )
            } else {
                Box {
                    if (session.state.text.isEmpty()) Text(placeholder, color = palette.textMuted, style = style)
                    textContent()
                }
            }
        }
        val inputModifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (lineBreakKey.forwarding) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown || event.isAltPressed || session.state.composition != null) {
                    return@onPreviewKeyEvent false
                }
                if (!secret && event.key in listOf(Key.Enter, Key.NumPadEnter) &&
                    event.isShiftPressed && !event.isCtrlPressed && !event.isMetaPressed) {
                    return@onPreviewKeyEvent lineBreakKey.insert()
                }
                val code = when (event.key) {
                    Key.Enter, Key.NumPadEnter -> KeyEvent.VK_ENTER
                    Key.DirectionUp -> KeyEvent.VK_UP
                    Key.DirectionDown -> KeyEvent.VK_DOWN
                    Key.DirectionLeft -> KeyEvent.VK_LEFT
                    Key.DirectionRight -> KeyEvent.VK_RIGHT
                    Key.Backspace -> KeyEvent.VK_BACK_SPACE
                    Key.Delete -> KeyEvent.VK_DELETE
                    Key.Escape -> KeyEvent.VK_ESCAPE
                    Key.V -> KeyEvent.VK_V
                    else -> return@onPreviewKeyEvent false
                }
                session.flush()
                val command = currentResolve(
                    InputKey(code, event.isShiftPressed, event.isCtrlPressed, event.isMetaPressed),
                    session.value(),
                )
                when (command) {
                    is InputCommand.Invoke -> { command.action(); true }
                    is InputCommand.Edit -> {
                        val before = session.value()
                        if (command.value.text != before.text && code in listOf(KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE)) {
                            // 先选择整个提及，再由 Compose 的标准删除事务执行，保留撤销历史。
                            val change = inputDocumentChange(before.text, command.value)
                            if (change.replacement.isEmpty()) {
                                session.state.edit { selection = TextRange(change.start, change.end) }
                                false
                            } else {
                                session.replace(command.value)
                                session.flush()
                                true
                            }
                        } else {
                            session.replace(command.value)
                            session.flush()
                            true
                        }
                    }
                    null -> false
                }
            }
        if (secret) {
            BasicSecureTextField(
                state = session.state,
                modifier = inputModifier,
                enabled = enabled,
                interactionSource = interactionSource,
                textStyle = style,
                textObfuscationMode = TextObfuscationMode.Hidden,
                cursorBrush = SolidColor(palette.accent),
                decorator = decorator,
                onTextLayout = { layoutGetter = it },
            )
        } else {
            BasicTextField(
                state = session.state,
                modifier = inputModifier,
                enabled = enabled,
                interactionSource = interactionSource,
                textStyle = style,
                cursorBrush = SolidColor(if (outlinedLabel != null) palette.textPrimary else palette.accent),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = maxLines),
                scrollState = scroll,
                decorator = decorator,
                onTextLayout = { layoutGetter = it },
            )
        }
    }
}

/** Compose 1.8.2 未映射 Shift+Enter；将它作为普通换行交给同一画布的标准编辑事务。 */
private class ComposeLineBreakKey {
    var forwarding = false
        private set

    fun insert(): Boolean {
        val canvas = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        forwarding = true
        try {
            canvas.dispatchEvent(KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n'))
            canvas.dispatchEvent(KeyEvent(canvas, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n'))
        } finally {
            forwarding = false
        }
        return true
    }
}

internal data class InputDocumentChange(val start: Int, val end: Int, val replacement: String)

internal fun inputDocumentChange(previous: String, updatedValue: TextFieldValue): InputDocumentChange {
    val updated = updatedValue.text
    val removedLength = previous.length - updated.length
    val deletionStart = updatedValue.selection.start
    if (removedLength > 0 && updatedValue.selection.collapsed &&
        deletionStart + removedLength <= previous.length &&
        previous.removeRange(deletionStart, deletionStart + removedLength) == updated) {
        return InputDocumentChange(deletionStart, deletionStart + removedLength, "")
    }
    val prefix = previous.commonPrefixWith(updated).length
    val suffix = previous.drop(prefix).commonSuffixWith(updated.drop(prefix)).length
    return InputDocumentChange(prefix, previous.length - suffix, updated.substring(prefix, updated.length - suffix))
}
