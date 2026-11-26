package com.auracode.assistant.toolwindow.conversation.title

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantPainterResource

/**
 * 标题与目标共用一个文本布局；只在用户点击时解析可见字符偏移。
 * 不为逻辑链接创建第二层布局节点，避免 Compose 1.7 对被省略字符求边界时崩溃。
 */
@Composable
internal fun ConversationCardTitle(
    title: String,
    titleIconPath: String?,
    titleTargetLabel: String?,
    titleTargetPath: String?,
    palette: DesignPalette,
    onOpenTitleTarget: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val model = remember(title, titleTargetLabel, titleTargetPath, onOpenTitleTarget != null) {
        conversationTitleModel(title, titleTargetLabel, titleTargetPath.takeIf { onOpenTitleTarget != null })
    }
    val layout = remember(model) { mutableStateOf<TextLayoutResult?>(null) }
    val openTarget = rememberUpdatedState(onOpenTitleTarget)
    val annotated = remember(model, palette.linkColor) {
        buildAnnotatedString {
            append(model.text)
            if (model.targetPath != null) {
                addStyle(
                    SpanStyle(color = palette.linkColor, textDecoration = TextDecoration.Underline),
                    model.targetStart,
                    model.targetEndExclusive,
                )
            }
        }
    }
    val targetModifier = if (model.targetPath == null) {
        Modifier
    } else {
        Modifier
            .semantics {
                onClick(label = titleTargetLabel) {
                    openTarget.value?.invoke(model.targetPath)
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.Spacebar) {
                    if (event.type == KeyEventType.KeyUp) openTarget.value?.invoke(model.targetPath)
                    true
                } else false
            }
            .focusable()
            .pointerInput(model) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val target = titleTargetAtPosition(model, layout.value, down.position)
                    if (target != null) {
                        down.consume()
                        val up = waitForUpOrCancellation()
                        if (up != null && titleTargetAtPosition(model, layout.value, up.position) == target) {
                            up.consume()
                            openTarget.value?.invoke(target)
                        }
                    }
                }
            }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        titleIconPath?.let { iconPath ->
            Icon(
                painter = assistantPainterResource(iconPath),
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = annotated,
            color = palette.timelineCardText,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = {
                layout.value = it
                onTextLayout(it)
            },
            modifier = targetModifier,
        )
    }
}

/** 被省略、尚未排版及空宽度内容都没有命中区，点击普通标题继续交给外层折叠动作。 */
internal fun titleTargetAtPosition(
    model: ConversationTitleModel,
    layout: TextLayoutResult?,
    position: Offset,
): String? {
    if (layout == null || layout.layoutInput.text.text != model.text || layout.lineCount == 0) return null
    if (position.x < 0 || position.x >= layout.size.width) return null
    if (position.y < 0 || position.y >= layout.size.height) return null
    if (position.x < layout.getLineLeft(0) || position.x >= layout.getLineRight(0)) return null
    val offset = layout.getOffsetForPosition(position)
    return model.targetAt(offset, layout.getLineEnd(0, visibleEnd = true))
}
