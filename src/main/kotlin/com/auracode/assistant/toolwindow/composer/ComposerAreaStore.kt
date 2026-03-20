package com.auracode.assistant.toolwindow.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.model.ContextFile
import com.auracode.assistant.model.FileAttachment
import com.auracode.assistant.model.ImageAttachment
import com.auracode.assistant.model.TurnUsageSnapshot
import com.auracode.assistant.persistence.chat.PersistedMessageAttachment
import com.auracode.assistant.provider.CodexModelCatalog
import com.auracode.assistant.settings.SavedAgentDefinition
import com.auracode.assistant.toolwindow.approval.PendingApprovalRequestUiModel
import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.ComposerMode
import com.auracode.assistant.toolwindow.eventing.ComposerReasoning
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.plan.PlanCompletionPromptUiModel
import com.auracode.assistant.toolwindow.plan.compactPreview
import com.auracode.assistant.toolwindow.timeline.TimelineFileChange
import com.auracode.assistant.toolwindow.timeline.TimelineMutation
import com.auracode.assistant.toolwindow.timeline.TimelineFileChangePreview
import com.auracode.assistant.toolwindow.toolinput.ToolUserInputPromptUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.name
import kotlin.math.min

internal data class ContextEntry(
    val path: String,
    val displayName: String,
    val tailPath: String = "",
    val selectedText: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val isSelectionContext: Boolean = false,
)

internal data class FocusedContextSnapshot(
    val path: String,
    val selectedText: String? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
)

internal data class MentionEntry(
    val id: String,
    val path: String,
    val displayName: String,
    val start: Int = 0,
    val endExclusive: Int = 0,
)

internal enum class AttachmentKind {
    TEXT,
    IMAGE,
    BINARY,
}

internal data class AttachmentEntry(
    val id: String,
    val path: String,
    val displayName: String,
    val tailPath: String = "",
    val kind: AttachmentKind,
    val sizeBytes: Long = 0L,
    val mimeType: String = "",
)

internal data class MentionLookupRequest(
    val query: String,
    val documentVersion: Long,
)

internal data class ComposerLookupRequest(
    val mention: MentionLookupRequest? = null,
    val agentQuery: String? = null,
    val documentVersion: Long = 0L,
)

internal data class AgentContextEntry(
    val id: String,
    val name: String,
    val prompt: String,
)

internal data class ComposerPlanCompletionState(
    val turnId: String,
    val threadId: String?,
    val preferredExecutionMode: ComposerMode,
    val planTitle: String,
    val planSummary: String,
    val planBody: String,
    val revisionDraft: String = "",
    val selectedAction: PlanCompletionAction = PlanCompletionAction.EXECUTE,
)

internal enum class ComposerRunningPlanStepStatus {
    COMPLETED,
    IN_PROGRESS,
    PENDING,
}

internal data class ComposerRunningPlanStep(
    val step: String,
    val status: ComposerRunningPlanStepStatus,
)

internal data class ComposerRunningPlanState(
    val threadId: String?,
    val turnId: String,
    val explanation: String?,
    val steps: List<ComposerRunningPlanStep>,
)

internal data class ComposerModelOption(
    val id: String,
    val isCustom: Boolean,
)

internal enum class PlanCompletionAction {
    EXECUTE,
    CANCEL,
    REVISION,
}

internal enum class ComposerInteractionCardKind {
    APPROVAL,
    TOOL_USER_INPUT,
    PLAN_COMPLETION,
}

internal data class ComposerInteractionCard(
    val kind: ComposerInteractionCardKind,
)

internal data class PendingComposerSubmission(
    val id: String,
    val prompt: String,
    val systemInstructions: List<String>,
    val contextFiles: List<ContextFile>,
    val imageAttachments: List<ImageAttachment>,
    val fileAttachments: List<FileAttachment>,
    val stagedAttachments: List<PersistedMessageAttachment>,
    val selectedModel: String,
    val selectedReasoning: ComposerReasoning,
    val executionMode: ComposerMode,
    val planEnabled: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val summary: String
        get() = prompt.lineSequence().firstOrNull { it.isNotBlank() }
            ?: systemInstructions.firstOrNull { it.isNotBlank() }
            ?: AuraCodeBundle.message("composer.pending.summary.empty")

    val totalAttachmentCount: Int
        get() = stagedAttachments.size
}

internal data class ComposerAreaState(
    val document: TextFieldValue = TextFieldValue(""),
    val documentVersion: Long = 0L,
    val executionMode: ComposerMode = ComposerMode.AUTO,
    val planEnabled: Boolean = false,
    val planModeAvailable: Boolean = true,
    val selectedModel: String = CodexModelCatalog.defaultModel,
    val selectedReasoning: ComposerReasoning = ComposerReasoning.MEDIUM,
    val autoContextEnabled: Boolean = true,
    val customModelIds: List<String> = emptyList(),
    val addingCustomModel: Boolean = false,
    val customModelDraft: String = "",
    val modelMenuExpanded: Boolean = false,
    val reasoningMenuExpanded: Boolean = false,
    val manualContextEntries: List<ContextEntry> = emptyList(),
    val attachments: List<AttachmentEntry> = emptyList(),
    val focusedContextEntry: ContextEntry? = null,
    val contextEntries: List<ContextEntry> = emptyList(),
    val agentEntries: List<AgentContextEntry> = emptyList(),
    val previewAttachmentId: String? = null,
    val mentionEntries: List<MentionEntry> = emptyList(),
    val mentionQuery: String = "",
    val mentionSuggestions: List<ContextEntry> = emptyList(),
    val mentionPopupVisible: Boolean = false,
    val activeMentionIndex: Int = 0,
    val agentQuery: String = "",
    val agentSuggestions: List<SavedAgentDefinition> = emptyList(),
    val agentPopupVisible: Boolean = false,
    val activeAgentIndex: Int = 0,
    val slashQuery: String = "",
    val slashSuggestions: List<SlashSuggestionItem> = emptyList(),
    val slashPopupVisible: Boolean = false,
    val activeSlashIndex: Int = 0,
    val editedFiles: List<EditedFileAggregate> = emptyList(),
    val editedFilesExpanded: Boolean = false,
    val appliedTurnDiffs: Map<String, String> = emptyMap(),
    val usageSnapshot: TurnUsageSnapshot? = null,
    val approvalQueue: List<PendingApprovalRequestUiModel> = emptyList(),
    val approvalPrompt: PendingApprovalRequestUiModel? = null,
    val toolUserInputQueue: List<ToolUserInputPromptUiModel> = emptyList(),
    val toolUserInputPrompt: ToolUserInputPromptUiModel? = null,
    val runningPlan: ComposerRunningPlanState? = null,
    val runningPlanExpanded: Boolean = true,
    val planCompletion: ComposerPlanCompletionState? = null,
    val pendingSubmissions: List<PendingComposerSubmission> = emptyList(),
) {
    val modelOptions: List<ComposerModelOption>
        get() = composerModelOptions(customModelIds)

    val inputText: String
        get() = normalizePromptBody(removeMentionRanges(document.text, mentionEntries))

    fun serializedPrompt(): String {
        val mentionBlock = mentionEntries.sortedBy { it.start }.joinToString("\n") { it.path.trim() }.trim()
        val textBlock = normalizePromptBody(removeMentionRanges(document.text, mentionEntries))
        return when {
            mentionBlock.isBlank() -> textBlock
            textBlock.isBlank() -> mentionBlock
            else -> "$mentionBlock\n\n$textBlock"
        }
    }

    fun serializedSystemInstructions(): List<String> = agentEntries.mapNotNull { it.prompt.trim().takeIf(String::isNotBlank) }

    fun hasPromptContent(): Boolean = serializedPrompt().isNotBlank() || agentEntries.isNotEmpty()

    val editedFilesSummary: EditedFilesSummary
        get() = EditedFilesSummary(
            total = editedFiles.size,
            totalEdits = editedFiles.sumOf { it.editCount },
        )

    val activeInteractionCard: ComposerInteractionCard?
        get() = when {
            approvalPrompt != null -> ComposerInteractionCard(ComposerInteractionCardKind.APPROVAL)
            toolUserInputPrompt != null -> ComposerInteractionCard(ComposerInteractionCardKind.TOOL_USER_INPUT)
            planCompletion != null -> ComposerInteractionCard(ComposerInteractionCardKind.PLAN_COMPLETION)
            else -> null
        }
}

internal class ComposerAreaStore(
    private val availableSkillsProvider: () -> List<SlashSkillDescriptor> = ::discoverAvailableSkills,
) {
    companion object {
        const val MAX_CONTEXT_FILES: Int = 10
        const val MAX_ATTACHMENTS: Int = 10
        const val MAX_IMAGE_BYTES: Long = 20L * 1024L * 1024L
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
        private val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "md", "txt", "json", "xml", "yaml", "yml", "gradle",
            "js", "ts", "tsx", "jsx", "py", "sql", "properties", "toml", "sh", "log",
        )
    }

    private val _state = MutableStateFlow(ComposerAreaState())
    val state: StateFlow<ComposerAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.UiIntentPublished -> {
                when (val intent = event.intent) {
                    is UiIntent.UpdateDocument -> applyDocumentUpdate(intent.value)
                    is UiIntent.InputChanged -> applyDocumentUpdate(
                        TextFieldValue(intent.value, TextRange(intent.value.length)),
                    )
                    UiIntent.ToggleExecutionMode -> _state.value = _state.value.copy(
                        executionMode = when (_state.value.executionMode) {
                            ComposerMode.AUTO -> ComposerMode.APPROVAL
                            ComposerMode.APPROVAL -> ComposerMode.AUTO
                        },
                    )
                    is UiIntent.SelectMode -> _state.value = _state.value.copy(
                        executionMode = intent.mode,
                    )
                    UiIntent.TogglePlanMode -> _state.value = _state.value.let { current ->
                        if (!current.planModeAvailable) {
                            current
                        } else {
                            current.copy(planEnabled = !current.planEnabled)
                        }
                    }
                    UiIntent.ToggleModelMenu -> _state.value = _state.value.copy(
                        modelMenuExpanded = !_state.value.modelMenuExpanded,
                        addingCustomModel = if (_state.value.modelMenuExpanded) false else _state.value.addingCustomModel,
                        customModelDraft = if (_state.value.modelMenuExpanded) "" else _state.value.customModelDraft,
                        reasoningMenuExpanded = false,
                    )
                    is UiIntent.SelectModel -> _state.value = _state.value.copy(
                        selectedModel = intent.model,
                        modelMenuExpanded = false,
                        addingCustomModel = false,
                        customModelDraft = "",
                    )
                    UiIntent.StartAddingCustomModel -> _state.value = _state.value.copy(
                        addingCustomModel = true,
                        customModelDraft = "",
                    )
                    is UiIntent.EditCustomModelDraft -> _state.value = _state.value.copy(
                        customModelDraft = intent.value,
                    )
                    UiIntent.CancelAddingCustomModel -> _state.value = _state.value.copy(
                        addingCustomModel = false,
                        customModelDraft = "",
                    )
                    UiIntent.ToggleReasoningMenu -> _state.value = _state.value.copy(
                        reasoningMenuExpanded = !_state.value.reasoningMenuExpanded,
                        modelMenuExpanded = false,
                        addingCustomModel = false,
                        customModelDraft = "",
                    )
                    is UiIntent.SelectReasoning -> _state.value = _state.value.copy(
                        selectedReasoning = intent.reasoning,
                        reasoningMenuExpanded = false,
                    )
                    is UiIntent.EditPlanRevisionDraft -> _state.value = _state.value.copy(
                        planCompletion = _state.value.planCompletion?.copy(revisionDraft = intent.value),
                    )
                    is UiIntent.SelectPlanCompletionAction -> _state.value = _state.value.copy(
                        planCompletion = _state.value.planCompletion?.copy(selectedAction = intent.action),
                    )
                    UiIntent.MovePlanCompletionSelectionNext -> _state.value = _state.value.copy(
                        planCompletion = _state.value.planCompletion?.copy(
                            selectedAction = (_state.value.planCompletion?.selectedAction ?: PlanCompletionAction.EXECUTE).next(),
                        ),
                    )
                    UiIntent.MovePlanCompletionSelectionPrevious -> _state.value = _state.value.copy(
                        planCompletion = _state.value.planCompletion?.copy(
                            selectedAction = (_state.value.planCompletion?.selectedAction ?: PlanCompletionAction.EXECUTE).previous(),
                        ),
                    )
                    UiIntent.ToggleRunningPlanExpanded -> {
                        if (_state.value.runningPlan != null) {
                            _state.value = _state.value.copy(
                                runningPlanExpanded = !_state.value.runningPlanExpanded,
                            )
                        }
                    }
                    is UiIntent.AddAttachments -> addAttachments(intent.paths)
                    is UiIntent.AddContextFiles -> addContextFiles(intent.paths)
                    is UiIntent.RemoveAttachment -> _state.value = _state.value.copy(
                        attachments = _state.value.attachments.filterNot { it.id == intent.id },
                        previewAttachmentId = _state.value.previewAttachmentId?.takeUnless { it == intent.id },
                    ).withResolvedContextEntries()
                    is UiIntent.OpenAttachmentPreview -> _state.value = _state.value.copy(previewAttachmentId = intent.id)
                    UiIntent.CloseAttachmentPreview -> _state.value = _state.value.copy(previewAttachmentId = null)
                    is UiIntent.UpdateFocusedContextFile -> {
                        val current = _state.value
                        _state.value = if (!current.autoContextEnabled) {
                            current.copy(focusedContextEntry = null).withResolvedContextEntries()
                        } else {
                            current.copy(
                                focusedContextEntry = intent.snapshot?.toContextEntry(),
                            ).withResolvedContextEntries()
                        }
                    }
                    is UiIntent.EditSettingsAutoContextEnabled -> _state.value = _state.value.copy(
                        autoContextEnabled = intent.enabled,
                        focusedContextEntry = if (intent.enabled) _state.value.focusedContextEntry else null,
                    ).withResolvedContextEntries()
                    is UiIntent.RemoveContextFile -> _state.value = _state.value.copy(
                        manualContextEntries = _state.value.manualContextEntries.filterNot { it.path == intent.path },
                        focusedContextEntry = _state.value.focusedContextEntry?.takeUnless { it.path == intent.path },
                    ).withResolvedContextEntries()
                    UiIntent.ToggleEditedFilesExpanded -> _state.value = _state.value.copy(
                        editedFilesExpanded = !_state.value.editedFilesExpanded && _state.value.editedFiles.isNotEmpty(),
                    )
                    is UiIntent.AcceptEditedFile -> _state.value = _state.value.removeEditedFile(intent.path)
                    UiIntent.AcceptAllEditedFiles -> _state.value = _state.value.copy(
                        editedFiles = emptyList(),
                        editedFilesExpanded = false,
                        appliedTurnDiffs = emptyMap(),
                    )
                    is UiIntent.SelectMentionFile -> addMention(intent.path)
                    is UiIntent.RemoveMentionFile -> removeMention(intent.id)
                    is UiIntent.SelectAgent -> addAgent(intent.agent)
                    is UiIntent.RemoveSelectedAgent -> _state.value = _state.value.copy(
                        agentEntries = _state.value.agentEntries.filterNot { it.id == intent.id },
                    )
                    UiIntent.MoveMentionSelectionNext -> {
                        val size = _state.value.mentionSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeMentionIndex = (_state.value.activeMentionIndex + 1) % size)
                        }
                    }
                    UiIntent.MoveMentionSelectionPrevious -> {
                        val size = _state.value.mentionSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeMentionIndex = (_state.value.activeMentionIndex - 1 + size) % size)
                        }
                    }
                    UiIntent.DismissMentionPopup -> _state.value = _state.value.copy(
                        mentionPopupVisible = false,
                        mentionSuggestions = emptyList(),
                        mentionQuery = "",
                        activeMentionIndex = 0,
                    )
                    UiIntent.MoveAgentSelectionNext -> {
                        val size = _state.value.agentSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeAgentIndex = (_state.value.activeAgentIndex + 1) % size)
                        }
                    }
                    UiIntent.MoveAgentSelectionPrevious -> {
                        val size = _state.value.agentSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeAgentIndex = (_state.value.activeAgentIndex - 1 + size) % size)
                        }
                    }
                    UiIntent.DismissAgentPopup -> _state.value = _state.value.copy(
                        agentPopupVisible = false,
                        agentSuggestions = emptyList(),
                        agentQuery = "",
                        activeAgentIndex = 0,
                    )
                    UiIntent.MoveSlashSelectionNext -> {
                        val size = _state.value.slashSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeSlashIndex = (_state.value.activeSlashIndex + 1) % size)
                        }
                    }
                    UiIntent.MoveSlashSelectionPrevious -> {
                        val size = _state.value.slashSuggestions.size
                        if (size > 0) {
                            _state.value = _state.value.copy(activeSlashIndex = (_state.value.activeSlashIndex - 1 + size) % size)
                        }
                    }
                    UiIntent.DismissSlashPopup -> dismissSlashPopup()
                    is UiIntent.SelectSlashCommand -> applySlashCommand(intent.command)
                    is UiIntent.SelectSlashSkill -> applySlashSkill(intent.name)
                    UiIntent.DismissPlanCompletionPrompt -> _state.value = _state.value.copy(planCompletion = null)
                    else -> Unit
                }
            }
            is AppEvent.MentionSuggestionsUpdated -> {
                val current = _state.value
                if (
                    event.documentVersion != current.documentVersion ||
                    event.query != current.mentionQuery ||
                    current.document.composition != null
                ) {
                    return
                }
                _state.value = current.copy(
                    mentionSuggestions = event.suggestions,
                    mentionPopupVisible = event.suggestions.isNotEmpty(),
                    activeMentionIndex = 0,
                )
            }

            is AppEvent.ConversationCapabilitiesUpdated -> {
                val supportsPlanMode = event.capabilities.supportsPlanMode
                _state.value = _state.value.copy(
                    planModeAvailable = supportsPlanMode,
                    planEnabled = if (supportsPlanMode) _state.value.planEnabled else false,
                )
            }

            is AppEvent.AgentSuggestionsUpdated -> {
                val current = _state.value
                if (
                    event.documentVersion != current.documentVersion ||
                    event.query != current.agentQuery ||
                    current.document.composition != null
                ) {
                    return
                }
                _state.value = current.copy(
                    agentSuggestions = event.suggestions,
                    agentPopupVisible = event.suggestions.isNotEmpty(),
                    activeAgentIndex = 0,
                )
            }

            is AppEvent.TimelineMutationApplied -> {
                if (event.mutation is TimelineMutation.UpsertFileChange) {
                    acceptEditedFileChange(event.mutation)
                }
            }

            is AppEvent.TurnDiffUpdated -> {
                acceptTurnDiffUpdated(event)
            }

            is AppEvent.PromptAccepted -> {
                _state.value = _state.value.clearComposerDraft(clearInteractionQueues = true)
            }

            is AppEvent.ApprovalRequested -> {
                val queue = (_state.value.approvalQueue + event.request).reindexedApprovalQueue()
                _state.value = _state.value.copy(
                    approvalQueue = queue,
                    approvalPrompt = queue.firstOrNull(),
                )
            }

            is AppEvent.ApprovalResolved -> {
                val queue = _state.value.approvalQueue
                    .filterNot { it.requestId == event.requestId }
                    .reindexedApprovalQueue()
                _state.value = _state.value.copy(
                    approvalQueue = queue,
                    approvalPrompt = queue.firstOrNull(),
                )
            }

            AppEvent.ClearApprovals -> _state.value = _state.value.copy(
                approvalQueue = emptyList(),
                approvalPrompt = null,
            )

            is AppEvent.ToolUserInputRequested -> {
                val queue = (_state.value.toolUserInputQueue + event.prompt).reindexedToolInputQueue()
                _state.value = _state.value.copy(
                    toolUserInputQueue = queue,
                    toolUserInputPrompt = queue.firstOrNull(),
                )
            }

            is AppEvent.ToolUserInputResolved -> {
                val queue = _state.value.toolUserInputQueue
                    .filterNot { it.requestId == event.requestId }
                    .reindexedToolInputQueue()
                _state.value = _state.value.copy(
                    toolUserInputQueue = queue,
                    toolUserInputPrompt = queue.firstOrNull(),
                )
            }

            AppEvent.ClearToolUserInputs -> _state.value = _state.value.copy(
                toolUserInputQueue = emptyList(),
                toolUserInputPrompt = null,
            )

            is AppEvent.PlanCompletionPromptUpdated -> {
                _state.value = _state.value.copy(
                    runningPlan = null,
                    runningPlanExpanded = true,
                    planCompletion = event.prompt?.toComposerPlanCompletionState(),
                )
            }

            is AppEvent.RunningPlanUpdated -> {
                val currentTurnId = _state.value.runningPlan?.turnId
                val incomingTurnId = event.plan?.turnId
                _state.value = _state.value.copy(
                    runningPlan = event.plan,
                    runningPlanExpanded = when {
                        event.plan == null -> true
                        currentTurnId == null -> true
                        currentTurnId != incomingTurnId -> true
                        else -> _state.value.runningPlanExpanded
                    },
                )
            }

            is AppEvent.SessionSnapshotUpdated -> {
                val activeSession = event.sessions.firstOrNull { it.id == event.activeSessionId }
                _state.value = _state.value.copy(usageSnapshot = activeSession?.usageSnapshot)
            }

            is AppEvent.PendingSubmissionsUpdated -> {
                _state.value = _state.value
                    .copy(pendingSubmissions = event.submissions)
                    .let { current ->
                        if (event.clearComposerDraft) {
                            current.clearComposerDraft(clearInteractionQueues = false)
                        } else {
                            current
                        }
                    }
            }

            is AppEvent.SettingsSnapshotUpdated -> {
                val availableModelIds = composerModelOptions(event.customModelIds).map { it.id }.toSet()
                val trimmedDraft = _state.value.customModelDraft.trim()
                val selectedModel = event.selectedModel.takeIf { it in availableModelIds } ?: CodexModelCatalog.defaultModel
                val selectedReasoning = ComposerReasoning.entries
                    .firstOrNull { it.effort == event.selectedReasoning }
                    ?: ComposerReasoning.MEDIUM
                _state.value = _state.value.copy(
                    autoContextEnabled = event.autoContextEnabled,
                    focusedContextEntry = if (event.autoContextEnabled) _state.value.focusedContextEntry else null,
                    customModelIds = event.customModelIds,
                    selectedModel = selectedModel,
                    selectedReasoning = selectedReasoning,
                    addingCustomModel = _state.value.addingCustomModel &&
                        trimmedDraft.isNotBlank() &&
                        trimmedDraft !in availableModelIds,
                    customModelDraft = if (
                        _state.value.addingCustomModel &&
                        trimmedDraft.isNotBlank() &&
                        trimmedDraft in availableModelIds
                    ) {
                        ""
                    } else {
                        _state.value.customModelDraft
                    },
                ).withResolvedContextEntries()
            }

            AppEvent.ConversationReset -> _state.value = ComposerAreaState(
                autoContextEnabled = _state.value.autoContextEnabled,
                customModelIds = _state.value.customModelIds,
                selectedModel = _state.value.selectedModel,
                selectedReasoning = _state.value.selectedReasoning,
            )

            else -> Unit
        }
    }

    private fun PlanCompletionPromptUiModel.toComposerPlanCompletionState(): ComposerPlanCompletionState {
        val preview = compactPreview()
        val title = preview.title.ifBlank {
            AuraCodeBundle.message("composer.planCompletion.defaultTitle")
        }
        return ComposerPlanCompletionState(
            turnId = turnId,
            threadId = threadId,
            preferredExecutionMode = preferredExecutionMode,
            planTitle = title,
            planSummary = preview.summary,
            planBody = body,
            revisionDraft = "",
        )
    }

    fun applyDocumentUpdate(value: TextFieldValue): ComposerLookupRequest? {
        val current = _state.value
        val normalizedValue = normalizeMentionSelection(current.document, value, current.mentionEntries)
        val syncedMentions = syncMentions(current.mentionEntries, current.document.text, normalizedValue)
        val next = current.nextDocumentState(document = normalizedValue, mentionEntries = syncedMentions)
        _state.value = next
        return next.activeLookup()
    }

    private fun addAttachments(paths: List<String>, clearMention: Boolean = false) {
        if (paths.isEmpty()) return
        val current = _state.value
        val existingByPath = current.attachments.associateBy { it.path }.toMutableMap()
        paths.mapNotNull { toAttachmentEntry(it) }.forEach { candidate ->
            existingByPath.putIfAbsent(candidate.path, candidate)
        }
        val attachments = existingByPath.values.take(MAX_ATTACHMENTS)
        val nextDocument = if (clearMention) {
            val clearedText = clearTrailingMentionQuery(current.document, current.mentionEntries)
            current.document.copy(text = clearedText, selection = TextRange(clearedText.length))
        } else {
            current.document
        }
        val next = current.copy(
            attachments = attachments,
            document = nextDocument,
            mentionQuery = if (clearMention) "" else current.mentionQuery,
            mentionSuggestions = if (clearMention) emptyList() else current.mentionSuggestions,
            mentionPopupVisible = if (clearMention) false else current.mentionPopupVisible,
            activeMentionIndex = if (clearMention) 0 else current.activeMentionIndex,
        )
        _state.value = next.withResolvedContextEntries()
    }

    private fun addContextFiles(paths: List<String>) {
        if (paths.isEmpty()) return
        val current = _state.value
        val additions = paths.map(::toContextEntry)
        _state.value = current.copy(
            manualContextEntries = mergeManualEntries(current.manualContextEntries, additions),
        ).withResolvedContextEntries()
    }

    private fun addMention(path: String) {
        val current = _state.value
        val entry = toContextEntry(path)
        val inserted = insertMentionLabel(
            document = current.document,
            mentions = current.mentionEntries,
            mentionPath = entry.path,
            displayName = entry.displayName,
        ) ?: return
        val nextValue = inserted.first
        val newMention = inserted.second
        _state.value = current.nextDocumentState(
            document = nextValue,
            mentionEntries = syncMentions(current.mentionEntries, current.document.text, nextValue) + newMention,
        )
    }

    private fun removeMention(id: String) {
        val current = _state.value
        val removed = removeMentionById(current.document, current.mentionEntries, id) ?: return
        _state.value = current.nextDocumentState(document = removed.first, mentionEntries = removed.second)
    }

    private fun addAgent(agent: SavedAgentDefinition) {
        val current = _state.value
        val queryMatch = findAgentQuery(current.document, current.mentionEntries)
        val nextText = clearTrailingAgentQuery(current.document, current.mentionEntries)
        val nextDocument = current.document.copy(
            text = nextText,
            selection = TextRange(queryMatch?.start ?: nextText.length),
        )
        val existing = current.agentEntries.associateBy { it.id }.toMutableMap()
        existing.putIfAbsent(
            agent.id,
            AgentContextEntry(id = agent.id, name = agent.name, prompt = agent.prompt),
        )
        _state.value = current.copy(
            document = nextDocument,
            documentVersion = current.documentVersion + 1,
            agentEntries = existing.values.toList(),
            agentQuery = "",
            agentSuggestions = emptyList(),
            agentPopupVisible = false,
            activeAgentIndex = 0,
            mentionSuggestions = emptyList(),
            mentionPopupVisible = false,
            mentionQuery = if (findMentionQuery(nextDocument, current.mentionEntries) != null) current.mentionQuery else "",
        )
    }

    private fun dismissSlashPopup() {
        _state.value = _state.value.copy(
            slashQuery = "",
            slashSuggestions = emptyList(),
            slashPopupVisible = false,
            activeSlashIndex = 0,
        )
    }

    private fun applySlashCommand(command: String) {
        val current = _state.value
        val normalized = command.trim().removePrefix("/")
        if (normalized != "plan" || !current.planModeAvailable) {
            dismissSlashPopup()
            return
        }
        val nextDocument = replaceSlashQuery(current.document, current.mentionEntries, "") ?: return
        _state.value = current.nextDocumentState(
            document = nextDocument,
            mentionEntries = current.mentionEntries,
        ).copy(
            planEnabled = true,
            slashQuery = "",
            slashSuggestions = emptyList(),
            slashPopupVisible = false,
            activeSlashIndex = 0,
        )
    }

    private fun applySlashSkill(name: String) {
        val current = _state.value
        val nextDocument = replaceSlashQuery(
            document = current.document,
            mentions = current.mentionEntries,
            replacement = slashSkillToken(name),
        ) ?: return
        _state.value = current.nextDocumentState(
            document = nextDocument,
            mentionEntries = current.mentionEntries,
        ).copy(
            slashQuery = "",
            slashSuggestions = emptyList(),
            slashPopupVisible = false,
            activeSlashIndex = 0,
        )
    }

    private fun buildSlashSuggestions(
        query: String,
        planModeAvailable: Boolean,
    ): List<SlashSuggestionItem> {
        val normalized = query.trim()
        val commands = listOf(
            SlashSuggestionItem.Command(
                command = "/plan",
                title = "/plan",
                description = AuraCodeBundle.message("composer.slash.plan.description"),
                enabled = planModeAvailable,
            ),
        )
        return buildList {
            addAll(
                commands.filter { suggestion ->
                    normalized.isBlank() ||
                        suggestion.command.removePrefix("/").contains(normalized, ignoreCase = true)
                },
            )
            addAll(
                availableSkillsProvider()
                    .filter { skill ->
                        normalized.isBlank() ||
                            skill.name.contains(normalized, ignoreCase = true) ||
                            skill.description.contains(normalized, ignoreCase = true)
                    }
                    .map { skill ->
                        SlashSuggestionItem.Skill(
                            name = skill.name,
                            description = skill.description,
                        )
                    },
            )
        }
    }

    private fun mergeManualEntries(existing: List<ContextEntry>, additions: List<ContextEntry>): List<ContextEntry> {
        val merged = LinkedHashMap<String, ContextEntry>()
        existing.forEach { merged[it.path] = it }
        additions.forEach { merged.putIfAbsent(it.path, it) }
        return merged.values.take(MAX_CONTEXT_FILES)
    }

    private fun ComposerAreaState.withResolvedContextEntries(): ComposerAreaState {
        val merged = LinkedHashMap<String, ContextEntry>()
        focusedContextEntry?.let { merged[it.path] = it }
        manualContextEntries.forEach { merged.putIfAbsent(it.path, it) }
        return copy(
            contextEntries = merged.values.take(MAX_CONTEXT_FILES),
        )
    }

    private fun clearTrailingMentionQuery(document: TextFieldValue, mentions: List<MentionEntry>): String {
        val query = findMentionQuery(document, mentions) ?: return document.text
        return buildString {
            append(document.text.substring(0, query.start))
            append(document.text.substring(query.end))
        }.trimEnd()
    }

    private fun clearTrailingAgentQuery(document: TextFieldValue, mentions: List<MentionEntry>): String {
        val query = findAgentQuery(document, mentions) ?: return document.text
        return buildString {
            append(document.text.substring(0, query.start))
            append(document.text.substring(query.end))
        }
    }

    private fun ComposerAreaState.nextDocumentState(
        document: TextFieldValue,
        mentionEntries: List<MentionEntry>,
    ): ComposerAreaState {
        val slashMatch = findSlashQuery(document, mentionEntries)
        val slashQuery = slashMatch?.query.orEmpty()
        val slashSuggestions = if (slashMatch != null) {
            buildSlashSuggestions(slashQuery, planModeAvailable)
        } else {
            emptyList()
        }
        val keepSlashSelection = slashMatch != null && slashQuery == this.slashQuery
        val query = if (slashMatch == null) findMentionQuery(document, mentionEntries)?.query.orEmpty() else ""
        val agentQuery = if (slashMatch == null) findAgentQuery(document, mentionEntries)?.query.orEmpty() else ""
        val keepSuggestions = query.isNotBlank() &&
            query == this.mentionQuery &&
            this.mentionSuggestions.isNotEmpty() &&
            document.composition == null
        val keepAgentSuggestions = agentQuery == this.agentQuery &&
            this.agentSuggestions.isNotEmpty() &&
            document.composition == null &&
            findAgentQuery(document, mentionEntries) != null

        return copy(
            document = document,
            documentVersion = documentVersion + 1,
            mentionEntries = mentionEntries,
            slashQuery = slashQuery,
            slashSuggestions = slashSuggestions,
            slashPopupVisible = slashMatch != null && slashSuggestions.isNotEmpty(),
            activeSlashIndex = if (keepSlashSelection) {
                activeSlashIndex.coerceAtMost((slashSuggestions.size - 1).coerceAtLeast(0))
            } else {
                0
            },
            mentionQuery = query,
            mentionSuggestions = if (slashMatch == null && keepSuggestions) mentionSuggestions else emptyList(),
            mentionPopupVisible = slashMatch == null && keepSuggestions && mentionPopupVisible,
            activeMentionIndex = if (keepSuggestions) activeMentionIndex.coerceAtMost((mentionSuggestions.size - 1).coerceAtLeast(0)) else 0,
            agentQuery = agentQuery,
            agentSuggestions = if (slashMatch == null && keepAgentSuggestions) agentSuggestions else emptyList(),
            agentPopupVisible = slashMatch == null && keepAgentSuggestions && agentPopupVisible,
            activeAgentIndex = if (keepAgentSuggestions) activeAgentIndex.coerceAtMost((agentSuggestions.size - 1).coerceAtLeast(0)) else 0,
        )
    }

    private fun ComposerAreaState.activeLookup(): ComposerLookupRequest? {
        if (document.composition != null) return null
        if (findSlashQuery(document, mentionEntries) != null) return null
        findAgentQuery(document, mentionEntries)?.let { match ->
            return ComposerLookupRequest(agentQuery = match.query, documentVersion = documentVersion)
        }
        val query = mentionQuery
        if (findMentionQuery(document, mentionEntries) == null) return null
        return ComposerLookupRequest(
            mention = MentionLookupRequest(query = query, documentVersion = documentVersion),
            documentVersion = documentVersion,
        )
    }

    private fun acceptEditedFileChange(mutation: TimelineMutation.UpsertFileChange) {
        val current = _state.value
        val updates = current.editedFiles.associateBy { it.path }.toMutableMap()
        mutation.changes.forEach { change ->
            val existing = updates[change.path]
            val resolved = TimelineFileChangePreview.resolve(change)
            val updateKey = mutation.turnId
                ?.takeIf { it.isNotBlank() }
                ?.let { "item:$it:${change.sourceScopedId}" }
                ?: "item:${change.sourceScopedId}"
            val updateKeys = buildSet {
                existing?.let { addAll(it.updateKeys) }
                add(updateKey)
            }
            val isNewChange = existing?.updateKeys?.contains(updateKey) != true
            val latestTimestamp = when {
                change.timestamp != null -> change.timestamp
                isNewChange -> System.currentTimeMillis()
                else -> existing?.lastUpdatedAt ?: System.currentTimeMillis()
            }
            updates[change.path] = EditedFileAggregate(
                path = change.path,
                displayName = change.displayName,
                updateKeys = updateKeys,
                editCount = updateKeys.size,
                latestAddedLines = resolved.addedLines,
                latestDeletedLines = resolved.deletedLines,
                lastUpdatedAt = latestTimestamp,
                latestChange = change.copy(
                    unifiedDiff = change.unifiedDiff,
                    oldContent = resolved.oldContent,
                    newContent = resolved.newContent,
                    addedLines = resolved.addedLines,
                    deletedLines = resolved.deletedLines,
                ),
            )
        }
        _state.value = current.copy(
            editedFiles = updates.values.sortedByDescending { it.lastUpdatedAt },
            editedFilesExpanded = current.editedFilesExpanded && updates.isNotEmpty(),
        )
    }

    private fun acceptTurnDiffUpdated(event: AppEvent.TurnDiffUpdated) {
        val current = _state.value
        val snapshotKey = "${event.threadId}:${event.turnId}"
        if (current.appliedTurnDiffs[snapshotKey] == event.diff) return

        val updates = current.editedFiles.associateBy { it.path }.toMutableMap()
        val timestamp = System.currentTimeMillis()
        TimelineFileChangePreview.parseTurnDiff(event.diff).entries.forEachIndexed { index, (path, parsed) ->
            val existing = updates[path]
            val turnKey = "turn:${event.turnId}"
            val previousKeys = existing?.updateKeys.orEmpty()
                .filterNot { it.startsWith("item:${event.turnId}:") }
                .toSet()
            val updateKeys = previousKeys + turnKey
            val entryTimestamp = timestamp + index
            updates[path] = EditedFileAggregate(
                path = path,
                displayName = parsed.displayName,
                updateKeys = updateKeys,
                editCount = updateKeys.size,
                latestAddedLines = parsed.addedLines,
                latestDeletedLines = parsed.deletedLines,
                lastUpdatedAt = entryTimestamp,
                latestChange = TimelineFileChange(
                    sourceScopedId = turnKey,
                    path = path,
                    displayName = parsed.displayName,
                    kind = parsed.kind,
                    timestamp = entryTimestamp,
                    addedLines = parsed.addedLines,
                    deletedLines = parsed.deletedLines,
                    unifiedDiff = parsed.unifiedDiff,
                    oldContent = parsed.oldContent,
                    newContent = parsed.newContent,
                ),
            )
        }
        _state.value = current.copy(
            editedFiles = updates.values.sortedByDescending { it.lastUpdatedAt },
            editedFilesExpanded = current.editedFilesExpanded && updates.isNotEmpty(),
            appliedTurnDiffs = current.appliedTurnDiffs + (snapshotKey to event.diff),
        )
    }

    private fun ComposerAreaState.removeEditedFile(path: String): ComposerAreaState {
        val nextFiles = editedFiles.filterNot { it.path == path }
        return copy(
            editedFiles = nextFiles,
            editedFilesExpanded = editedFilesExpanded && nextFiles.isNotEmpty(),
        )
    }

    private fun FocusedContextSnapshot.toContextEntry(): ContextEntry? {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) return null
        return toContextEntry(
            path = normalizedPath,
            selectedText = selectedText?.takeIf { it.isNotBlank() },
            startLine = startLine,
            endLine = endLine,
        )
    }

    private fun toContextEntry(path: String): ContextEntry = toContextEntry(
        path = path,
        selectedText = null,
        startLine = null,
        endLine = null,
    )

    private fun toContextEntry(
        path: String,
        selectedText: String?,
        startLine: Int?,
        endLine: Int?,
    ): ContextEntry {
        val normalized = path.trim()
        val p = runCatching { Path.of(normalized) }.getOrNull()
        val name = p?.name ?: normalized.substringAfterLast('/').substringAfterLast('\\').ifBlank { normalized }
        val parent = p?.parent?.fileName?.toString().orEmpty()
        val hasSelection = !selectedText.isNullOrBlank() && startLine != null && endLine != null
        val displayName = when {
            !hasSelection -> name
            startLine == endLine -> "$name:$startLine"
            else -> "$name:$startLine-$endLine"
        }
        return ContextEntry(
            path = normalized,
            displayName = displayName,
            tailPath = parent,
            selectedText = selectedText,
            startLine = startLine,
            endLine = endLine,
            isSelectionContext = hasSelection,
        )
    }

    private fun toAttachmentEntry(path: String): AttachmentEntry? {
        val normalized = path.trim()
        if (normalized.isBlank()) return null
        val p = runCatching { Path.of(normalized) }.getOrNull() ?: return null
        val name = p.name.ifBlank { normalized.substringAfterLast('/').substringAfterLast('\\') }
        val parent = p.parent?.fileName?.toString().orEmpty()
        val size = runCatching { Files.size(p) }.getOrDefault(0L)
        val ext = name.substringAfterLast('.', "").lowercase()
        val kind = when {
            ext in IMAGE_EXTENSIONS -> {
                if (size > MAX_IMAGE_BYTES) return null
                AttachmentKind.IMAGE
            }
            ext in TEXT_EXTENSIONS -> AttachmentKind.TEXT
            isProbablyTextFile(p, size) -> AttachmentKind.TEXT
            else -> AttachmentKind.BINARY
        }
        val mime = runCatching { Files.probeContentType(p).orEmpty() }.getOrDefault("")
        return AttachmentEntry(
            id = UUID.randomUUID().toString(),
            path = normalized,
            displayName = name,
            tailPath = parent,
            kind = kind,
            sizeBytes = size,
            mimeType = mime,
        )
    }

    private fun isProbablyTextFile(path: Path, size: Long): Boolean {
        if (size <= 0L) return false
        val sampleSize = min(size, 4096L).toInt()
        val bytes = runCatching {
            Files.newInputStream(path).use { input ->
                val sample = ByteArray(sampleSize)
                val read = input.read(sample)
                if (read <= 0) ByteArray(0) else sample.copyOf(read)
            }
        }.getOrDefault(ByteArray(0))
        if (bytes.isEmpty()) return false
        if (bytes.any { it == 0.toByte() }) return false
        return runCatching {
            String(bytes, StandardCharsets.UTF_8)
            true
        }.getOrDefault(false)
    }
}

internal fun composerModelOptions(customModelIds: List<String>): List<ComposerModelOption> {
    val builtIns = CodexModelCatalog.ids().map { ComposerModelOption(id = it, isCustom = false) }
    val builtInIds = builtIns.mapTo(linkedSetOf()) { it.id }
    val customs = customModelIds
        .map { it.trim() }
        .filter { it.isNotBlank() && it !in builtInIds }
        .distinct()
        .map { ComposerModelOption(id = it, isCustom = true) }
    return builtIns + customs
}

private fun ComposerAreaState.clearComposerDraft(clearInteractionQueues: Boolean): ComposerAreaState {
    return copy(
        document = TextFieldValue(""),
        documentVersion = documentVersion + 1,
        attachments = emptyList(),
        previewAttachmentId = null,
        mentionEntries = emptyList(),
        mentionQuery = "",
        mentionSuggestions = emptyList(),
        mentionPopupVisible = false,
        activeMentionIndex = 0,
        agentQuery = "",
        agentSuggestions = emptyList(),
        agentPopupVisible = false,
        activeAgentIndex = 0,
        slashQuery = "",
        slashSuggestions = emptyList(),
        slashPopupVisible = false,
        activeSlashIndex = 0,
        approvalQueue = if (clearInteractionQueues) emptyList() else approvalQueue,
        approvalPrompt = if (clearInteractionQueues) null else approvalPrompt,
        toolUserInputQueue = if (clearInteractionQueues) emptyList() else toolUserInputQueue,
        toolUserInputPrompt = if (clearInteractionQueues) null else toolUserInputPrompt,
        planCompletion = if (clearInteractionQueues) null else planCompletion,
    )
}


private fun PlanCompletionAction.next(): PlanCompletionAction {
    return when (this) {
        PlanCompletionAction.EXECUTE -> PlanCompletionAction.CANCEL
        PlanCompletionAction.CANCEL -> PlanCompletionAction.REVISION
        PlanCompletionAction.REVISION -> PlanCompletionAction.EXECUTE
    }
}

private fun PlanCompletionAction.previous(): PlanCompletionAction {
    return when (this) {
        PlanCompletionAction.EXECUTE -> PlanCompletionAction.REVISION
        PlanCompletionAction.CANCEL -> PlanCompletionAction.EXECUTE
        PlanCompletionAction.REVISION -> PlanCompletionAction.CANCEL
    }
}

private fun List<PendingApprovalRequestUiModel>.reindexedApprovalQueue(): List<PendingApprovalRequestUiModel> {
    val total = size
    return mapIndexed { index, request ->
        request.copy(queuePosition = index + 1, queueSize = total)
    }
}

private fun List<ToolUserInputPromptUiModel>.reindexedToolInputQueue(): List<ToolUserInputPromptUiModel> {
    val total = size
    return mapIndexed { index, prompt ->
        prompt.copy(queuePosition = index + 1, queueSize = total)
    }
}
