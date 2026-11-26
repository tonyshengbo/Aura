package com.auracode.assistant.toolwindow.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantUiTokens
import com.intellij.openapi.project.Project
import java.awt.Color

internal val LocalNativeInputProject = compositionLocalOf<Project?> { null }
internal val LocalNativeInputVisible = compositionLocalOf { true }
internal val LocalMainInputController = compositionLocalOf<NativeInputController?> { null }

/** Compose 只负责布局和颜色，文本输入及焦点目标始终是同一个原生组件。 */
@Composable
internal fun NativeTextInput(
    draft: NativeInputDraft,
    palette: DesignPalette,
    onValueChange: (NativeInputDraft) -> Unit,
    modifier: Modifier = Modifier,
    identityKey: Any? = Unit,
    maxLines: Int = 6,
    placeholder: String = "",
    secret: Boolean = false,
    controller: NativeInputController? = null,
    highlights: List<NativeInputHighlight> = emptyList(),
    resolveCommand: (NativeInputKey, TextFieldValue) -> NativeInputCommand? = { _, _ -> null },
    onCaretBoundsChange: (Rect?) -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
    onDropFiles: ((List<String>) -> Unit)? = null,
) {
    val project = LocalNativeInputProject.current
    val visible = LocalNativeInputVisible.current
    val density = LocalDensity.current
    val tokens = assistantUiTokens()
    key(identityKey, secret) {
        var height by remember { mutableStateOf(tokens.type.body.value * 1.5f) }
        val host = remember {
            if (secret) NativePasswordInput(draft) else NativeEditorInput(draft, maxLines, project)
        }
        DisposableEffect(host) {
            onDispose { host.dispose() }
        }
        DisposableEffect(host, controller) {
            controller?.host = host
            onDispose {
                if (controller?.host === host) controller.host = null
            }
        }
        val style = NativeInputStyle(
            background = Color(palette.timelineCardBg.toArgb(), true),
            foreground = Color(palette.textPrimary.toArgb(), true),
            muted = Color(palette.textMuted.toArgb(), true),
            accent = Color(palette.accent.toArgb(), true),
            highlight = Color(palette.topStripBg.toArgb(), true),
            fontSize = tokens.type.body.value * density.fontScale,
        )
        // Swing 互操作层高于 Compose 绘制层。遮罩打开时卸载视图，保留编辑器和撤销状态，
        // 防止输入框浮在设置抽屉、全屏图表上方并继续接收键盘输入。
        // 使用完整条件分支：当前 Kotlin/Compose 编译链会为 key 内的标签返回残留非法字节码。
        if (!visible) {
            Box(modifier.fillMaxWidth().height(height.dp))
        } else {
            SwingPanel(
                factory = { host.component },
                background = palette.timelineCardBg,
                modifier = modifier.fillMaxWidth().height(height.dp),
                update = {
                    host.update(
                        draft,
                        style,
                        placeholder,
                        highlights,
                        NativeInputCallbacks(
                            onValueChange = onValueChange,
                            resolveCommand = resolveCommand,
                            onLayout = { nativeHeight, caret ->
                                height = nativeHeight.toFloat().coerceAtLeast(tokens.type.body.value * 1.5f)
                                onCaretBoundsChange(caret?.let {
                                    Rect(
                                        it.x * density.density,
                                        it.y * density.density,
                                        (it.x + it.width) * density.density,
                                        (it.y + it.height) * density.density,
                                    )
                                })
                            },
                            onFocusChanged = onFocusChanged,
                            onDropFiles = onDropFiles,
                        ),
                    )
                },
            )
        }
    }
}
