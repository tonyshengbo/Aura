package com.auracode.assistant.toolwindow.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.Disposable

/**
 * 工具窗口拥有 Compose 场景：隐藏/移动只会暂时移除 Swing 层级，不能销毁场景。
 * 保留场景中的输入状态和撤销历史，最终释放统一在工具窗口内容销毁时执行。
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class ToolWindowComposeHost : Disposable {
    val panel = ComposePanel().apply {
        isDisposeOnRemove = false
    }
    private var disposed = false

    override fun dispose() {
        if (disposed) return
        disposed = true
        // 先完成 Swing 的 removeNotify，再销毁 Compose，避免释放和挂载交错。
        panel.parent?.remove(panel)
        panel.dispose()
    }
}
