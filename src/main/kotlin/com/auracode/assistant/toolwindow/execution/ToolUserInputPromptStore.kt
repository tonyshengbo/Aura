package com.auracode.assistant.toolwindow.execution

import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ToolUserInputPromptStore {
    // Preserve answers and cursor position while projections switch between sessions or questions.
    private val draftsByPromptKey = object : LinkedHashMap<String, ToolUserInputPromptState>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ToolUserInputPromptState>): Boolean = size > 128
    }
    // Blocking JSON-RPC request IDs can be reused; the native item ID separates their answer drafts.
    private fun ToolUserInputPromptUiModel.key() = "$threadId:$requestId:$itemId"
    private fun rememberCurrent() {
        _state.value.current?.let { draftsByPromptKey[it.key()] = _state.value }
    }

    private val _state = MutableStateFlow(ToolUserInputPromptState())
    val state: StateFlow<ToolUserInputPromptState> = _state.asStateFlow()

    fun restoreState(state: ToolUserInputPromptState) {
        _state.value = state
    }

    fun onEvent(event: AppEvent) {
        rememberCurrent()
        when (event) {
            is AppEvent.ToolUserInputSubmissionUpdated -> {
                val key = draftsByPromptKey.entries.firstOrNull { (_, draft) ->
                    val prompt = draft.current
                    prompt?.threadId == event.threadId && prompt.requestId == event.requestId &&
                        prompt.delivery == SessionInputDelivery.ASYNC
                }?.key ?: return
                val cached = draftsByPromptKey.getValue(key)
                val next = cached.copy(submitting = event.submitting, error = event.error)
                draftsByPromptKey[key] = next
                if (_state.value.current?.key() == key) _state.value = next
            }
            is AppEvent.ToolUserInputRequested -> enqueue(event.prompt)
            is AppEvent.ToolUserInputResolved -> resolve(event.requestId)
            is AppEvent.ExecutionUiProjectionUpdated -> replaceQueue(event.toolUserInputs)
            AppEvent.ClearToolUserInputs,
            is AppEvent.PromptAccepted,
            -> replaceQueue(_state.value.queue.filter { it.delivery == SessionInputDelivery.ASYNC })
            AppEvent.ConversationReset -> _state.value = ToolUserInputPromptState()

            is AppEvent.UiIntentPublished -> handleUiIntent(event.intent)
            else -> Unit
        }
    }

    private fun enqueue(prompt: ToolUserInputPromptUiModel) {
        val previous = _state.value
        val queue = (previous.queue + prompt).reindexed()
        val current = previous.current ?: queue.firstOrNull()
        _state.value = ToolUserInputPromptState(
            queue = queue,
            current = current,
            answerDrafts = if (previous.current == null) {
                current?.emptyDrafts().orEmpty()
            } else {
                previous.answerDrafts
            },
            activeQuestionIndex = 0,
            visible = current != null,
        ).syncSelection()
    }

    private fun resolve(requestId: String) {
        val previous = _state.value
        previous.queue.filter { it.requestId == requestId }.forEach { draftsByPromptKey.remove(it.key()) }
        val wasCurrent = previous.current?.requestId == requestId
        val queue = previous.queue.filterNot { it.requestId == requestId }.reindexed()
        val current = if (wasCurrent) queue.firstOrNull() else previous.current
        _state.value = ToolUserInputPromptState(
            queue = queue,
            current = current,
            answerDrafts = if (wasCurrent) {
                current?.emptyDrafts().orEmpty()
            } else {
                previous.answerDrafts
            },
            activeQuestionIndex = 0,
            visible = current != null,
        ).syncSelection()
    }

    /** Replaces the projected tool-input queue while preserving the current draft when possible. */
    private fun replaceQueue(projectedQueue: List<ToolUserInputPromptUiModel>) {
        val previous = _state.value
        val queue = projectedQueue.sortedBy { it.delivery.ordinal }.reindexed()
        val blocking = queue.firstOrNull { it.delivery == SessionInputDelivery.BLOCKING }
        val current = blocking ?: queue.firstOrNull { it.key() == previous.current?.key() } ?: queue.firstOrNull()
        val cached = current?.let { draftsByPromptKey[it.key()] }
        _state.value = (cached ?: ToolUserInputPromptState()).copy(
            queue = queue,
            current = current,
            answerDrafts = cached?.answerDrafts ?: current?.emptyDrafts().orEmpty(),
            visible = current != null,
        ).syncSelection()
    }

    private fun handleUiIntent(intent: UiIntent) {
        val current = _state.value.current ?: return
        if (_state.value.submitting && intent !is UiIntent.EditToolUserInputAnswer) return
        when (intent) {
            is UiIntent.SelectToolUserInputOption -> {
                val question = current.questions.firstOrNull { it.id == intent.questionId } ?: return
                val choiceIndex = question.presentedChoices().indexOfFirst { it.label == intent.optionLabel }
                val currentDraft = _state.value.answerDrafts[intent.questionId].orEmpty()
                val nextDraft = currentDraft.copy(
                    selectedOptionLabel = intent.optionLabel,
                    textValue = if (question.optionRequiresText(intent.optionLabel)) {
                        currentDraft.textValue
                    } else {
                        ""
                    },
                )
                _state.value = _state.value.copy(
                    answerDrafts = _state.value.answerDrafts + (intent.questionId to nextDraft),
                    activeChoiceIndex = choiceIndex.takeIf { it >= 0 } ?: _state.value.activeChoiceIndex,
                ).syncSelection()
            }

            is UiIntent.EditToolUserInputAnswer -> {
                val question = current.questions.firstOrNull { it.id == intent.questionId } ?: return
                val currentDraft = _state.value.answerDrafts[intent.questionId].orEmpty()
                val activeChoice = _state.value.takeIf { it.activeQuestion?.id == intent.questionId }?.activeChoice
                val selected = when {
                    question.options.isEmpty() -> null
                    activeChoice?.kind == ToolUserInputChoiceKind.FREEFORM -> activeChoice.label
                    currentDraft.selectedOptionLabel == null -> TOOL_USER_INPUT_OTHER_OPTION
                    else -> currentDraft.selectedOptionLabel
                }
                _state.value = _state.value.copy(
                    answerDrafts = _state.value.answerDrafts + (
                        intent.questionId to currentDraft.copy(
                            selectedOptionLabel = selected,
                            textValue = intent.value,
                        )
                    ),
                ).syncSelection()
            }

            UiIntent.MoveToolUserInputSelectionNext -> {
                val choices = _state.value.activeQuestion?.presentedChoices().orEmpty()
                if (choices.isEmpty()) return
                _state.value = _state.value.copy(
                    activeChoiceIndex = (_state.value.activeChoiceIndex + 1) % choices.size,
                ).syncSelection()
            }

            UiIntent.MoveToolUserInputSelectionPrevious -> {
                val choices = _state.value.activeQuestion?.presentedChoices().orEmpty()
                if (choices.isEmpty()) return
                _state.value = _state.value.copy(
                    activeChoiceIndex = (_state.value.activeChoiceIndex - 1 + choices.size) % choices.size,
                ).syncSelection()
            }

            UiIntent.AdvanceToolUserInputPrompt -> {
                val state = _state.value
                if (!state.canAdvance) return
                _state.value = state.copy(
                    activeQuestionIndex = (state.activeQuestionIndex + 1).coerceAtMost(current.questions.lastIndex),
                    activeChoiceIndex = 0,
                    freeformActive = false,
                ).syncSelection()
            }

            UiIntent.RetreatToolUserInputPrompt -> {
                val state = _state.value
                if (!state.canRetreat) return
                _state.value = state.copy(
                    activeQuestionIndex = (state.activeQuestionIndex - 1).coerceAtLeast(0),
                    activeChoiceIndex = 0,
                    freeformActive = false,
                ).syncSelection()
            }

            else -> Unit
        }
    }

    private fun ToolUserInputAnswerDraftUiModel?.orEmpty(): ToolUserInputAnswerDraftUiModel {
        return this ?: ToolUserInputAnswerDraftUiModel()
    }

    private fun List<ToolUserInputPromptUiModel>.reindexed(): List<ToolUserInputPromptUiModel> {
        val total = size
        return mapIndexed { index, prompt ->
            prompt.copy(queuePosition = index + 1, queueSize = total)
        }
    }
}
