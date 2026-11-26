package com.auracode.assistant.toolwindow.input

import com.auracode.assistant.toolwindow.dragdrop.AttachmentDropResolver
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import javax.swing.JComponent

/** 原生控件位于 Compose 拖放层上方，需要把文件投放显式接回附件流程。 */
internal fun installNativeInputFileDrop(
    component: JComponent,
    disposable: Disposable,
    onFiles: () -> ((List<String>) -> Unit)?,
) {
    val previous = component.dropTarget
    val target = DropTarget(component, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetListener {
        private fun accepts(event: DropTargetDragEvent): Boolean =
            onFiles() != null && AttachmentDropResolver.isFileDrag(event.transferable)

        override fun dragEnter(event: DropTargetDragEvent) {
            if (accepts(event)) event.acceptDrag(DnDConstants.ACTION_COPY)
            else previous?.dragEnter(event) ?: event.rejectDrag()
        }

        override fun dragOver(event: DropTargetDragEvent) {
            if (accepts(event)) event.acceptDrag(DnDConstants.ACTION_COPY)
            else previous?.dragOver(event) ?: event.rejectDrag()
        }

        override fun dropActionChanged(event: DropTargetDragEvent) = dragOver(event)

        override fun dragExit(event: DropTargetEvent) {
            previous?.dragExit(event)
        }

        override fun drop(event: DropTargetDropEvent) {
            val handler = onFiles()
            if (handler == null || !AttachmentDropResolver.isFileDrag(event.transferable)) {
                previous?.drop(event) ?: event.rejectDrop()
                return
            }
            event.acceptDrop(DnDConstants.ACTION_COPY)
            val paths = AttachmentDropResolver.resolve(event.transferable)
            if (paths.isNotEmpty()) handler(paths)
            event.dropComplete(paths.isNotEmpty())
        }
    }, true)
    Disposer.register(disposable) {
        if (component.dropTarget === target) component.dropTarget = previous
    }
}
