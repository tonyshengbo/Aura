package com.auracode.assistant.toolwindow.input

import androidx.compose.ui.text.input.TextFieldValue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import java.awt.event.KeyEvent
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * 原生文档事务完成后再向业务层发布快照；业务快捷键执行前同步刷新，保证发送内容最新。
 * 此层从不消费 KEY_TYPED，也不自行生成、去重或提交输入法字符。
 */
internal abstract class NativeInputSession(initialDraft: NativeInputDraft) : NativeInputHost {
    protected val disposable = Disposer.newDisposable("Aura native input")
    protected var composing = false
        private set
    protected var callbacks: NativeInputCallbacks? = null
        private set
    protected var disposed = false
        private set
    private val synchronizer = NativeDraftSynchronizer(initialDraft)
    private var publishScheduled = false
    private var applyingExternal = false

    final override fun update(
        draft: NativeInputDraft,
        style: NativeInputStyle,
        placeholder: String,
        highlights: List<NativeInputHighlight>,
        callbacks: NativeInputCallbacks,
    ) {
        if (disposed) return
        this.callbacks = callbacks
        synchronizer.acceptExternal(draft, composing)?.let(::applyExternal)
        configure(style, placeholder, highlights)
        reportLayout()
    }

    protected abstract fun readValue(): TextFieldValue
    protected abstract fun writeValue(value: TextFieldValue)
    protected abstract fun configure(style: NativeInputStyle, placeholder: String, highlights: List<NativeInputHighlight>)
    protected abstract fun reportLayout()

    protected fun schedulePublish() {
        if (disposed || applyingExternal || publishScheduled) return
        publishScheduled = true
        SwingUtilities.invokeLater {
            publishScheduled = false
            if (!disposed) {
                if (!composing) synchronizer.finishComposition()?.let(::applyExternal)
                flushValue()
                reportLayout()
            }
        }
    }

    private fun flushValue() {
        if (disposed || applyingExternal) return
        val value = readValue()
        synchronizer.publish(value)?.let { callbacks?.onValueChange?.invoke(it) }
    }

    private fun applyExternal(value: TextFieldValue) {
        applyingExternal = true
        try {
            writeValue(value)
        } finally {
            applyingExternal = false
        }
    }

    final override fun prepareSubmission(): Boolean {
        if (disposed) return false
        // 点击外部发送按钮也必须跨过这一同步边界，不能依赖下一次 Compose 重组。
        flushValue()
        return !composing
    }

    protected fun attachNativeListeners(target: JComponent) {
        val inputListener = object : InputMethodListener {
            override fun inputMethodTextChanged(event: InputMethodEvent) {
                // 原生编辑器先处理事件，随后才通知此观察者；不修改事件或输入上下文。
                val textLength = event.text?.let { it.endIndex - it.beginIndex } ?: 0
                composing = textLength > event.committedCharacterCount
                schedulePublish()
            }

            override fun caretPositionChanged(event: InputMethodEvent) = Unit
        }
        val focusListener = object : FocusAdapter() {
            override fun focusGained(event: FocusEvent) {
                callbacks?.onFocusChanged?.invoke(true)
            }

            override fun focusLost(event: FocusEvent) {
                callbacks?.onFocusChanged?.invoke(false)
            }
        }
        target.addInputMethodListener(inputListener)
        target.addFocusListener(focusListener)
        Disposer.register(disposable) {
            target.removeInputMethodListener(inputListener)
            target.removeFocusListener(focusListener)
        }
        installCommands(target)
    }

    private fun installCommands(target: JComponent) {
        val keys = listOf(
            KeyEvent.VK_ENTER, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT,
            KeyEvent.VK_RIGHT, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_DELETE, KeyEvent.VK_ESCAPE,
        ).flatMap { code ->
            listOf(NativeInputKey(code), NativeInputKey(code, shift = true))
        } + listOf(
            NativeInputKey(KeyEvent.VK_V, control = true),
            NativeInputKey(KeyEvent.VK_V, meta = true),
        )
        keys.forEach { key ->
            val action = object : DumbAwareAction() {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(event: AnActionEvent) {
                    event.presentation.isEnabled = !disposed && !composing &&
                        callbacks?.resolveCommand?.invoke(key, readValue()) != null
                }

                override fun actionPerformed(event: AnActionEvent) {
                    if (disposed || composing) return
                    flushValue()
                    when (val command = callbacks?.resolveCommand?.invoke(key, readValue())) {
                        is NativeInputCommand.Edit -> {
                            applyExternal(command.value)
                            flushValue()
                            reportLayout()
                        }
                        is NativeInputCommand.Invoke -> command.action()
                        null -> Unit
                    }
                }
            }
            val modifiers = (if (key.shift) InputEvent.SHIFT_DOWN_MASK else 0) or
                (if (key.control) InputEvent.CTRL_DOWN_MASK else 0) or
                (if (key.meta) InputEvent.META_DOWN_MASK else 0)
            action.registerCustomShortcutSet(
                CustomShortcutSet(KeyStroke.getKeyStroke(key.code, modifiers)),
                target,
                disposable,
            )
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        callbacks = null
        // 先断开业务回调，再让平台结束组合输入和释放编辑器，避免卸载后写回其他会话。
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner === component || (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, component))) {
            component.inputContext?.endComposition()
        }
        Disposer.dispose(disposable)
    }
}
