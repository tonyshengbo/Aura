package com.codex.assistant.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextArea
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JWindow
import javax.swing.JList
import javax.swing.DefaultListModel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import java.awt.Point
import java.awt.Dimension
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectFileIndex

class FileMentionInputArea(private val project: Project) : JBTextArea() {
    private var mentionPopup: JWindow? = null
    private var mentionStartPos: Int = -1
    private val onFileSelected = mutableListOf<(VirtualFile) -> Unit>()

    init {
        emptyText.text = ToolWindowUiText.COMPOSER_HINT
        addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                if (e.keyChar == '@') {
                    mentionStartPos = caretPosition
                    showFileSuggestions("")
                }
            }

            override fun keyReleased(e: KeyEvent) {
                if (mentionStartPos >= 0 && e.keyCode != KeyEvent.VK_ESCAPE) {
                    updateSuggestions()
                }
            }
        })
    }

    fun onFileSelected(handler: (VirtualFile) -> Unit) {
        onFileSelected.add(handler)
    }

    private fun updateSuggestions() {
        if (mentionStartPos < 0 || caretPosition < mentionStartPos) {
            hidePopup()
            return
        }

        val query = text.substring(mentionStartPos + 1, caretPosition)
        if (query.contains('\n')) {
            hidePopup()
            mentionStartPos = -1
            return
        }

        showFileSuggestions(query)
    }

    private fun showFileSuggestions(query: String) {
        val files = findMatchingFiles(query)
        if (files.isEmpty()) {
            hidePopup()
            return
        }

        val listModel = DefaultListModel<VirtualFile>()
        files.take(10).forEach { listModel.addElement(it) }

        val list = JList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
            cellRenderer = FileListCellRenderer(project)
        }

        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        list.selectedValue?.let { selectFile(it) }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        hidePopup()
                        mentionStartPos = -1
                        e.consume()
                    }
                }
            }
        })

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 1) {
                    list.selectedValue?.let { selectFile(it) }
                }
            }
        })

        hidePopup()
        mentionPopup = JWindow(javax.swing.SwingUtilities.getWindowAncestor(this)).apply {
            add(JScrollPane(list))
            size = Dimension(400, 200)

            val pos = getCaretScreenPosition()
            location = Point(pos.x, pos.y + 20)
            isVisible = true
        }

        list.requestFocusInWindow()
    }

    private fun selectFile(file: VirtualFile) {
        val fileName = file.name
        val beforeMention = text.substring(0, mentionStartPos)
        val afterCaret = text.substring(caretPosition)

        text = "$beforeMention@$fileName $afterCaret"
        caretPosition = mentionStartPos + fileName.length + 2

        hidePopup()
        mentionStartPos = -1

        onFileSelected.forEach { it(file) }
        requestFocusInWindow()
    }

    private fun hidePopup() {
        mentionPopup?.dispose()
        mentionPopup = null
    }

    private fun findMatchingFiles(query: String): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val lowerQuery = query.lowercase()

        // Recent files first
        FileEditorManager.getInstance(project).openFiles.forEach { file ->
            if (file.name.lowercase().contains(lowerQuery)) {
                result.add(file)
            }
        }

        // Project files
        val projectIndex = ProjectFileIndex.getInstance(project)

        projectIndex.iterateContent { file ->
            if (!file.isDirectory &&
                file.name.lowercase().contains(lowerQuery) &&
                !result.contains(file) &&
                result.size < 50) {
                result.add(file)
            }
            true
        }

        return result
    }

    private fun getCaretScreenPosition(): Point {
        try {
            val rect = modelToView2D(caretPosition)
            val screenPos = locationOnScreen
            return Point(screenPos.x + rect.x.toInt(), screenPos.y + rect.y.toInt())
        } catch (e: Exception) {
            return locationOnScreen
        }
    }
}
