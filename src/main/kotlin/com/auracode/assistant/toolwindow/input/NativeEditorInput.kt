package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** 使用 IDE 与代码编辑区相同的文档、IME 和撤销实现，不经过 Compose 文本输入服务。 */
internal class NativeEditorInput(
    initialDraft: NativeInputDraft,
    private val maxLines: Int,
    private val project: Project?,
) : NativeInputSession(initialDraft) {
    private var nativeEditor: EditorEx? = null
    private var selection = initialDraft.value.selection
    private var lastCommitted = initialDraft.value.copy(composition = null)
    private var style: NativeInputStyle? = null
    private var highlights = emptyList<NativeInputHighlight>()
    private var paintedHighlights = emptyList<RangeHighlighter>()
    private val document = EditorFactory.getInstance().createDocument(initialDraft.value.text)
    private val field = object : EditorTextField(document, project, FileTypes.PLAIN_TEXT, false, false) {
        override fun shouldHaveBorder(): Boolean = false

        override fun createEditor(): EditorEx = super.createEditor().also(::editorCreated)
    }
    override val component = JPanel(BorderLayout()).apply {
        isOpaque = true
        border = BorderFactory.createEmptyBorder()
        add(field, BorderLayout.CENTER)
    }

    init {
        field.setDisposedWith(disposable)
        field.setSupplementary(true)
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = schedulePublish()
        }, disposable)
    }

    private fun editorCreated(editor: EditorEx) {
        nativeEditor = editor
        editor.settings.apply {
            isUseSoftWraps = true
            isCaretRowShown = false
            isVirtualSpace = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
        }
        editor.setHorizontalScrollbarVisible(false)
        editor.setVerticalScrollbarVisible(true)
        editor.scrollPane.border = BorderFactory.createEmptyBorder()
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = schedulePublish()
        }, disposable)
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) = schedulePublish()
        }, disposable)
        editor.scrollingModel.addVisibleAreaListener({ reportLayout() }, disposable)
        editor.contentComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = reportLayout()
        })
        attachNativeListeners(editor.contentComponent)
        installNativeInputFileDrop(editor.contentComponent, disposable) { callbacks?.onDropFiles }
        restoreSelection(selection)
        style?.let(::applyStyle)
        paintHighlights()
        schedulePublish()
    }

    override fun readValue(): TextFieldValue {
        val editor = nativeEditor
        val caret = editor?.caretModel?.offset ?: selection.end
        val selected = editor?.selectionModel
        val range = if (selected?.hasSelection() == true) {
            if (caret == selected.selectionStart) TextRange(selected.selectionEnd, caret)
            else TextRange(selected.selectionStart, caret)
        } else {
            TextRange(caret)
        }
        if (composing && editor != null) {
            val snapshot = composingNativeSnapshot(editor.contentComponent, lastCommitted)
            lastCommitted = snapshot.copy(composition = null)
            return snapshot
        }
        return TextFieldValue(document.text, range).also { lastCommitted = it }
    }

    override fun writeValue(value: TextFieldValue) {
        if (document.text != value.text) {
            val change = nativeDocumentChange(document.text, value.text)
            // 补全、提及删除等程序编辑也进入 IDE 命令栈，保留原生撤销和 RangeMarker。
            WriteCommandAction.runWriteCommandAction(project, "Edit Aura draft", null, Runnable {
                document.replaceString(change.start, change.end, change.replacement)
            })
        }
        selection = value.selection
        restoreSelection(selection)
    }

    private fun restoreSelection(range: TextRange) {
        val editor = nativeEditor ?: return
        val start = range.start.coerceIn(0, document.textLength)
        val end = range.end.coerceIn(0, document.textLength)
        editor.caretModel.moveToOffset(end)
        if (start == end) editor.selectionModel.removeSelection()
        else editor.selectionModel.setSelection(start, end)
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    override fun configure(style: NativeInputStyle, placeholder: String, highlights: List<NativeInputHighlight>) {
        if (this.style != style) {
            this.style = style
            component.background = style.background
            field.background = style.background
            nativeEditor?.let { applyStyle(style) }
        }
        field.setPlaceholder(placeholder)
        field.setShowPlaceholderWhenFocused(true)
        if (this.highlights != highlights) {
            this.highlights = highlights
            paintHighlights()
        }
    }

    private fun applyStyle(style: NativeInputStyle) {
        val editor = nativeEditor ?: return
        // EditorImpl 的临时代理色板不能 clone；从全局方案克隆，避免修改其他编辑器的主题。
        val scheme = EditorColorsManager.getInstance().globalScheme.clone() as EditorColorsScheme
        scheme.setAttributes(HighlighterColors.TEXT, TextAttributes(style.foreground, null, null, null, Font.PLAIN))
        scheme.setColor(EditorColors.CARET_COLOR, style.accent)
        scheme.setColor(EditorColors.SELECTION_BACKGROUND_COLOR, style.highlight)
        scheme.setColor(EditorColors.SELECTION_FOREGROUND_COLOR, style.foreground)
        scheme.editorFontName = field.font.family
        scheme.editorFontSize = style.fontSize.toInt()
        scheme.lineSpacing = 1.5f
        editor.colorsScheme = scheme
        editor.backgroundColor = style.background
        editor.setPlaceholderAttributes(TextAttributes(style.muted, null, null, null, Font.PLAIN))
        paintHighlights()
    }

    private fun paintHighlights() {
        val editor = nativeEditor ?: return
        val colors = style ?: return
        paintedHighlights.forEach { editor.markupModel.removeHighlighter(it) }
        paintedHighlights = highlights.filter { it.start >= 0 && it.end > it.start && it.end <= document.textLength }
            .map { range ->
                editor.markupModel.addRangeHighlighter(
                    range.start,
                    range.end,
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                    TextAttributes(colors.foreground, colors.highlight, null, null, Font.PLAIN),
                    HighlighterTargetArea.EXACT_RANGE,
                )
            }
    }

    override fun reportLayout() {
        if (disposed) return
        val editor = nativeEditor ?: return
        val height = editor.contentComponent.preferredSize.height.coerceIn(editor.lineHeight, editor.lineHeight * maxLines)
        val point = editor.visualPositionToXY(editor.caretModel.visualPosition)
        val caret = SwingUtilities.convertRectangle(
            editor.contentComponent,
            Rectangle(point.x, point.y, 1, editor.lineHeight),
            component,
        )
        callbacks?.onLayout?.invoke(height, caret)
    }

    override fun requestInputFocus() {
        (nativeEditor?.contentComponent ?: field).requestFocusInWindow()
    }
}

internal data class NativeDocumentChange(val start: Int, val end: Int, val replacement: String)

/** 只替换变化区间，不能每次草稿同步都 setText，否则会破坏编辑器的撤销与位置标记。 */
internal fun nativeDocumentChange(previous: String, updated: String): NativeDocumentChange {
    val prefix = previous.commonPrefixWith(updated).length
    val suffix = previous.substring(prefix).commonSuffixWith(updated.substring(prefix)).length
    return NativeDocumentChange(prefix, previous.length - suffix, updated.substring(prefix, updated.length - suffix))
}
