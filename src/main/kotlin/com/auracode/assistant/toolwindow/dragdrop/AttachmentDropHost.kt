package com.auracode.assistant.toolwindow.dragdrop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import com.auracode.assistant.toolwindow.shared.DesignPalette

/**
 * 工具窗口级别的文件拖放宿主：拖入本地文件并松手时，把路径交给 [onDropPaths]。
 *
 * 为什么挂在 Compose 层：
 * - `ComposePanel` 初始化时已用 `setDropTarget` / `setTransferHandler` 独占 AWT 投放，外层 Swing 容器收不到事件；
 * - `Modifier.dragAndDropTarget` 走 Compose 自己的拖放派发，深层节点（例如输入框的文本拖放）优先，互不干扰。
 *
 * 行为约定：
 * - 只在载荷「可能携带本地文件」时参与拖拽，纯文本拖拽不显示高亮；
 * - 松手后解析不出可附件文件时返回 false，把事件交还 Compose 继续派发；
 * - 目录与非本地资源由 [AttachmentDropResolver] 过滤，附件数量上限沿用输入框既有逻辑。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun AttachmentDropHost(
    palette: DesignPalette,
    onDropPaths: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isDragActive by remember { mutableStateOf(false) }
    val currentOnDropPaths by rememberUpdatedState(onDropPaths)

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragActive = true
            }

            override fun onEntered(event: DragAndDropEvent) {
                isDragActive = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragActive = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragActive = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragActive = false
                val paths = AttachmentDropResolver.resolve(event.awtTransferable)
                if (paths.isEmpty()) return false
                currentOnDropPaths(paths)
                return true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> AttachmentDropResolver.isFileDrag(event.awtTransferable) },
                target = dropTarget,
            ),
    ) {
        content()
        if (isDragActive) {
            AttachmentDropOverlay(palette = palette)
        }
    }
}
