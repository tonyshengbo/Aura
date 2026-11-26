package com.auracode.assistant.toolwindow.submission

import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.input.NativeInputCommand
import com.auracode.assistant.toolwindow.input.NativeInputKey
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent

/**
 * 提及与补全属于业务命令，和原生文本编辑分离。所有编辑决策使用编辑器实时快照，
 * 不读取上次重组时的 document；返回 null 时由 IDE 继续执行标准编辑动作。
 */
internal fun resolveSubmissionEditorCommand(
    state: SubmissionAreaState,
    document: TextFieldValue,
    key: NativeInputKey,
    onIntent: (UiIntent) -> Unit,
): NativeInputCommand? {
    if (document.composition != null) return null
    fun dispatch(intent: UiIntent) = NativeInputCommand.Invoke { onIntent(intent) }

    if (key.code == KeyEvent.VK_V && (key.meta || key.control)) {
        val hasImage = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)
        }.getOrDefault(false)
        return if (hasImage) dispatch(UiIntent.PasteImageFromClipboard) else null
    }
    if (key.control || key.meta) return null
    if (state.slashPopupVisible && key.code == KeyEvent.VK_ENTER && !key.shift) {
        val selected = state.slashSuggestions.getOrNull(state.activeSlashIndex)
        if (selected is SlashSuggestionItem.Command && !selected.enabled) {
            return NativeInputCommand.Invoke { }
        }
    }

    val popupCommand = when {
        state.slashPopupVisible -> when (key.code) {
            KeyEvent.VK_DOWN -> UiIntent.MoveSlashSelectionNext
            KeyEvent.VK_UP -> UiIntent.MoveSlashSelectionPrevious
            KeyEvent.VK_ESCAPE -> UiIntent.DismissSlashPopup
            KeyEvent.VK_ENTER -> if (!key.shift) {
                when (val selected = state.slashSuggestions.getOrNull(state.activeSlashIndex)) {
                    is SlashSuggestionItem.Command -> if (selected.enabled) UiIntent.SelectSlashCommand(selected.command) else null
                    is SlashSuggestionItem.Skill -> UiIntent.SelectSlashSkill(selected.name)
                    null -> null
                }
            } else null
            else -> null
        }
        state.mentionPopupVisible -> when (key.code) {
            KeyEvent.VK_DOWN -> UiIntent.MoveMentionSelectionNext
            KeyEvent.VK_UP -> UiIntent.MoveMentionSelectionPrevious
            KeyEvent.VK_ESCAPE -> UiIntent.DismissMentionPopup
            KeyEvent.VK_ENTER -> if (!key.shift) {
                when (val selected = state.mentionSuggestions.getOrNull(state.activeMentionIndex)) {
                    is MentionSuggestion.File -> UiIntent.SelectMentionFile(selected.entry.path)
                    is MentionSuggestion.Agent -> UiIntent.SelectSessionSubagentMention(selected.agent.threadId)
                    null -> null
                }
            } else null
            else -> null
        }
        state.agentPopupVisible -> when (key.code) {
            KeyEvent.VK_DOWN -> UiIntent.MoveAgentSelectionNext
            KeyEvent.VK_UP -> UiIntent.MoveAgentSelectionPrevious
            KeyEvent.VK_ESCAPE -> UiIntent.DismissAgentPopup
            KeyEvent.VK_ENTER -> if (!key.shift) {
                state.agentSuggestions.getOrNull(state.activeAgentIndex)?.let { UiIntent.SelectAgent(it) }
            } else null
            else -> null
        }
        else -> null
    }
    if (popupCommand != null) return dispatch(popupCommand)

    if (!key.shift) {
        val edit = when (key.code) {
            KeyEvent.VK_LEFT -> moveCursorLeftAcrossMention(document, state.mentionEntries)
            KeyEvent.VK_RIGHT -> moveCursorRightAcrossMention(document, state.mentionEntries)
            KeyEvent.VK_BACK_SPACE -> if (document.selection.collapsed) {
                removeMentionByBackspace(document, state.mentionEntries)?.first
            } else {
                removeMentionSelection(document, state.mentionEntries)?.first
            }
            KeyEvent.VK_DELETE -> if (document.selection.collapsed) {
                removeMentionByDelete(document, state.mentionEntries)?.first
            } else {
                removeMentionSelection(document, state.mentionEntries)?.first
            }
            else -> null
        }
        if (edit != null) return NativeInputCommand.Edit(edit)
    }
    if (key.code == KeyEvent.VK_ENTER) {
        return if (key.shift) NativeInputCommand.Edit(insertLineBreak(document)) else dispatch(UiIntent.SendPrompt)
    }
    // 粘贴、撤销和普通字符全部使用 IDE 默认实现。
    return null
}
