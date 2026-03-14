package com.codex.assistant.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextArea
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JWindow
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FileMentionInputArea(private val project: Project) : JBTextArea() {
    private var mentionPopup: JWindow? = null
    private var mentionStartPos: Int = -1
    private val onFileSelected = mutableListOf<(VirtualFile) -> Unit>()
    private val composerHint = ToolWindowUiText.COMPOSER_HINT

    init {
        emptyText.text = ""
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = repaint()
            override fun focusLost(e: FocusEvent?) = repaint()
        })
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = repaint()
            override fun removeUpdate(e: DocumentEvent?) = repaint()
            override fun changedUpdate(e: DocumentEvent?) = repaint()
        })
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

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (!shouldShowHint(hasFocus(), text)) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.color = AssistantUiTheme.TEXT_SECONDARY
            g2.font = font
            val fm = g2.fontMetrics
            val x = insets.left + 2
            val y = insets.top + fm.ascent + 1
            g2.drawString(composerHint, x, y)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        internal fun shouldShowHint(hasFocus: Boolean, text: String): Boolean {
            return text.isBlank() || !hasFocus
        }
    }
}
