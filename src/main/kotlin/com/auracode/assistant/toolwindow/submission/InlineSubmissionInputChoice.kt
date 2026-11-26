package com.auracode.assistant.toolwindow.submission

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.auracode.assistant.toolwindow.input.NativeInputCommand
import com.auracode.assistant.toolwindow.input.NativeInputController
import com.auracode.assistant.toolwindow.input.NativeInputDraft
import com.auracode.assistant.toolwindow.input.NativeTextInput
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantPainterResource
import com.auracode.assistant.toolwindow.shared.assistantUiTokens
import java.awt.event.KeyEvent

internal const val INLINE_INPUT_MAX_LINES: Int = 4

internal enum class InlineInputKey {
    UP,
    DOWN,
}

internal enum class InlineInputKeyAction {
    NONE,
    MOVE_PREVIOUS,
    MOVE_NEXT,
}

internal fun canSubmitInlineTextField(value: TextFieldValue): Boolean = value.composition == null

internal fun inlineInputKeyAction(
    value: TextFieldValue,
    key: InlineInputKey,
): InlineInputKeyAction {
    if (value.composition != null) return InlineInputKeyAction.NONE
    return when (key) {
        InlineInputKey.UP -> InlineInputKeyAction.MOVE_PREVIOUS
        InlineInputKey.DOWN -> InlineInputKeyAction.MOVE_NEXT
    }
}

internal fun shouldShowToolUserInputKeyboardHint(
    kind: com.auracode.assistant.toolwindow.execution.ToolUserInputChoiceKind,
    index: Int,
    activeChoiceIndex: Int,
): Boolean {
    return index == activeChoiceIndex
}

internal fun shouldShowPlanCompletionKeyboardHint(
    action: PlanCompletionAction,
    selectedAction: PlanCompletionAction,
): Boolean {
    return action == selectedAction
}

@Composable
internal fun rememberInlineInputDraft(
    identityKey: Any,
    text: String,
): MutableState<NativeInputDraft> {
    val state = remember(identityKey) {
        mutableStateOf(
            NativeInputDraft(TextFieldValue(text, TextRange(text.length))),
        )
    }
    // 同一问题/回合内由原生输入拥有草稿；异步业务回传只作持久化，不覆盖正在编辑的文字。
    // 切换问题或回合通过 identityKey 初始化恢复草稿。
    return state
}

@Composable
internal fun InlineSubmissionInputChoice(
    value: NativeInputDraft,
    placeholder: String,
    emphasized: Boolean,
    isSecret: Boolean,
    p: DesignPalette,
    modifier: Modifier = Modifier,
    showKeyboardHintIcon: Boolean = false,
    focusRequester: NativeInputController? = null,
    identityKey: Any = Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onValueChange: (NativeInputDraft) -> Unit,
    onMovePrevious: (() -> Boolean)? = null,
    onMoveNext: (() -> Boolean)? = null,
    onSubmit: () -> Boolean,
    onCancel: () -> Boolean,
) {
    val t = assistantUiTokens()
    val borderColor = if (emphasized) p.accent else p.markdownDivider.copy(alpha = 0.9f)
    Column(
        modifier = modifier
            .background(p.appBg, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = t.spacing.sm, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            NativeTextInput(
                draft = value,
                identityKey = identityKey,
                palette = p.copy(timelineCardBg = p.appBg),
                placeholder = placeholder,
                secret = isSecret,
                maxLines = INLINE_INPUT_MAX_LINES,
                controller = focusRequester,
                onValueChange = onValueChange,
                onFocusChanged = { onFocusChanged?.invoke(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (showKeyboardHintIcon) t.controls.iconMd + t.spacing.sm else 0.dp)
                    .padding(vertical = 2.dp),
                resolveCommand = { key, document ->
                    if (document.composition != null) {
                        null
                    } else {
                        when (key.code) {
                            KeyEvent.VK_UP -> onMovePrevious?.let { action -> NativeInputCommand.Invoke { action() } }
                            KeyEvent.VK_DOWN -> onMoveNext?.let { action -> NativeInputCommand.Invoke { action() } }
                            KeyEvent.VK_ENTER -> NativeInputCommand.Invoke { onSubmit() }
                            KeyEvent.VK_ESCAPE -> NativeInputCommand.Invoke { onCancel() }
                            else -> null
                        }
                    }
                },
            )
            if (showKeyboardHintIcon) {
                Icon(
                    painter = assistantPainterResource("/icons/swap-vert.svg"),
                    contentDescription = null,
                    tint = p.textMuted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(t.controls.iconMd),
                )
            }
        }
    }
}
