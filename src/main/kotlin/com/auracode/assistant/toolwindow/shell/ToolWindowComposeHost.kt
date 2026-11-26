package com.auracode.assistant.toolwindow.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.Disposable

/**
 * 工具窗口拥有 Compose 场景：隐藏/移动只会暂时移除 Swing 层级，不能销毁场景。
 * 若沿用 ComposePanel 的自动销毁，Swing 子组件还会在下次 addNotify 时先于新场景被挂载，
 * 导致已经释放的原生编辑器被再次创建。最终释放统一在工具窗口内容销毁时执行。
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
        // 先完成 Swing 的 removeNotify，再销毁 Compose 与原生编辑器，避免释放和挂载交错。
        panel.parent?.remove(panel)
        panel.dispose()
    }
}
