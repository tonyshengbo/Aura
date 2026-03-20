package com.auracode.assistant.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import javax.swing.JPanel

class DiffViewerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    fun showDiff(file: VirtualFile, originalContent: String, newContent: String) {
        val contentFactory = DiffContentFactory.getInstance()
        val content1 = contentFactory.create(originalContent, file.fileType)
        val content2 = contentFactory.create(newContent, file.fileType)

        val request = SimpleDiffRequest(
            "Code Changes",
            content1,
            content2,
            "Original",
            "Modified"
        )

        val viewer = DiffManager.getInstance().createRequestPanel(project, this, null)
        viewer.setRequest(request)

        removeAll()
        add(viewer.component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    override fun dispose() {
        // Cleanup resources
    }
}
