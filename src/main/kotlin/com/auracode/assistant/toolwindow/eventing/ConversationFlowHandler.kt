package com.auracode.assistant.toolwindow.eventing

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.integration.ide.IdeExternalRequest
import com.auracode.assistant.model.AgentApprovalMode
import com.auracode.assistant.model.AgentCollaborationMode
import com.auracode.assistant.model.ContextFile
import com.auracode.assistant.model.FileAttachment
import com.auracode.assistant.model.ImageAttachment
import com.auracode.assistant.persistence.chat.PersistedAttachmentKind
import com.auracode.assistant.protocol.ItemStatus
import com.auracode.assistant.provider.input.LiveInput
import com.auracode.assistant.provider.input.LiveInputResult
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionTurnOutcome
import com.auracode.assistant.toolwindow.shared.UiText
import com.auracode.assistant.toolwindow.submission.ConversationSubmission
import com.auracode.assistant.toolwindow.submission.SubmissionAreaState
import com.auracode.assistant.toolwindow.submission.SubmissionDraftSnapshot
import com.auracode.assistant.toolwindow.submission.draftSnapshot
import com.auracode.assistant.toolwindow.submission.supportsInitSlashCommand
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal class ConversationFlowHandler(
    private val context: ToolWindowCoordinatorContext,
    private val workspaceHandler: WorkspaceInteractionHandler,
) {
    private val submittingSessions = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val AGENTS_MD_FILE_NAME: String = "AGENTS.md"
    }

    fun deleteSession(onRestoreHistory: () -> Unit, sessionId: String) {
        if (!context.chatService.deleteSession(sessionId)) return
        context.dropSessionViewState(sessionId)
        context.sessionAttentionStore.drop(sessionId)
        context.activePlanRunContexts.remove(sessionId)
        context.publishSessionSnapshot()
        context.publishSettingsSnapshot()
        context.publishConversationCapabilities()
        onRestoreHistory()
    }

    fun switchSession(sessionId: String, onRestoreHistory: () -> Unit) {
        val previousSessionId = context.activeSessionId()
        if (previousSessionId == sessionId) return
        context.captureSessionViewState(previousSessionId)
        if (!context.chatService.switchSession(sessionId)) return
        context.sessionAttentionStore.clear(sessionId)
        context.publishSessionSnapshot()
        context.publishSettingsSnapshot()
        context.publishConversationCapabilities()
        if (!context.restoreSessionViewState(sessionId)) {
            onRestoreHistory()
        }
    }

    fun submitPromptIfAllowed() {
        val submissionState = context.submissionStore.state.value
        val submission = buildConversationSubmission(submissionState) ?: return
        dispatchSubmissionOrAppend(submissionState, submission)
    }

    /** Routes IDE-originated requests through the same submission pipeline as composer prompts. */
    fun submitExternalRequest(request: IdeExternalRequest) {
        context.submissionStore.onEvent(
            AppEvent.UiIntentPublished(
                UiIntent.UpdateDocument(TextFieldValue(request.prompt, TextRange(request.prompt.length))),
            ),
        )
        val submission = buildExternalSubmission(request)
        dispatchSubmissionOrAppend(context.submissionStore.state.value, submission)
    }

    /** Submits the shared Aura `/init` prompt without consuming the current composer draft. */
    fun submitInitSlashCommand() {
        val submissionState = context.submissionStore.state.value
        if (submissionState.sessionIsRunning || context.conversationStore.state.value.isRunning) {
            context.eventHub.publish(
                AppEvent.StatusTextUpdated(
                    UiText.bundle("composer.slash.init.disabled"),
                ),
            )
            return
        }
        if (!supportsInitSlashCommand(submissionState.selectedEngineId)) {
            context.eventHub.publish(
                AppEvent.StatusTextUpdated(
                    UiText.bundle("composer.slash.init.unsupported", submissionState.selectedEngineId),
                ),
            )
            return
        }
        val targetPath = Path.of(context.chatService.currentWorkingDirectory(), AGENTS_MD_FILE_NAME)
        if (Files.exists(targetPath)) {
            context.eventHub.publish(
                AppEvent.StatusTextUpdated(
                    UiText.bundle("composer.slash.init.exists", AGENTS_MD_FILE_NAME),
                ),
            )
            return
        }
        dispatchSubmission(buildInitSlashSubmission(submissionState))
    }

    fun cancelPromptRun(onResetPlanFlowState: () -> Unit) {
        val activeTurnId = resolveActiveTurnIdForCancellation()
        context.chatService.cancelCurrent()
        onResetPlanFlowState()
        context.eventHub.publish(AppEvent.ActiveRunCancelled)
        context.applySessionDomainEvents(
            context.activeSessionId(),
            listOf(
                SessionDomainEvent.TurnCompleted(
                    turnId = activeTurnId,
                    outcome = SessionTurnOutcome.CANCELLED,
                    completedAtMs = System.currentTimeMillis(),
                ),
            ),
        )
    }

    fun requestAgentSuggestions(query: String, documentVersion: Long, mentionLimit: Int) {
        val normalizedQuery = query.trim()
        val suggestions = context.settingsService.savedAgents()
            .filter { agent -> normalizedQuery.isBlank() || agent.name.contains(normalizedQuery, ignoreCase = true) }
            .take(mentionLimit)
        context.eventHub.publish(
            AppEvent.AgentSuggestionsUpdated(
                query = normalizedQuery,
                documentVersion = documentVersion,
                suggestions = suggestions,
            ),
        )
    }

    private fun buildConversationSubmission(
        submissionState: SubmissionAreaState,
    ): ConversationSubmission? {
        if (submissionState.document.composition != null) return null
        val disabledSkills = context.engineSkillsService.findDisabledSkillMentions(
            engineId = submissionState.selectedEngineId,
            cwd = context.chatService.currentWorkingDirectory(),
            text = submissionState.inputText,
        )
        if (disabledSkills.isNotEmpty()) {
            context.eventHub.publish(
                AppEvent.StatusTextUpdated(UiText.raw("Disabled skills cannot be used: ${disabledSkills.joinToString(", ")}")),
            )
            return null
        }
        val prompt = submissionState.serializedPrompt()
        val systemInstructions = submissionState.serializedSystemInstructions()
        if (prompt.isBlank() && systemInstructions.isEmpty()) return null
        val stagedAttachments = workspaceHandler.stageAttachments(
            sessionId = context.chatService.getCurrentSessionId(),
            attachments = submissionState.attachments,
        )
        return ConversationSubmission(
            engineId = submissionState.selectedEngineId,
            prompt = prompt,
            systemInstructions = systemInstructions,
            contextFiles = workspaceHandler.buildContextFiles(
                contextEntries = submissionState.contextEntries,
                attachments = stagedAttachments,
            ),
            imageAttachments = stagedAttachments.filter { it.kind == PersistedAttachmentKind.IMAGE }.map {
                ImageAttachment(path = it.assetPath, name = it.displayName, mimeType = it.mimeType.ifBlank { "image/png" })
            },
            fileAttachments = stagedAttachments.filter { it.kind == PersistedAttachmentKind.FILE }.map {
                FileAttachment(path = it.assetPath, name = it.displayName, mimeType = it.mimeType.ifBlank { "application/octet-stream" })
            },
            stagedAttachments = stagedAttachments,
            selectedModel = submissionState.selectedModel,
            selectedReasoning = submissionState.selectedReasoning,
            executionMode = submissionState.executionMode,
            planEnabled = submissionState.planEnabled,
        )
    }

    /** IDE entrypoints may supply their own explicit context files while reusing the active composer settings. */
    private fun buildExternalSubmission(request: IdeExternalRequest): ConversationSubmission {
        val submissionState = context.submissionStore.state.value
        return ConversationSubmission(
            engineId = submissionState.selectedEngineId,
            prompt = request.prompt,
            systemInstructions = submissionState.serializedSystemInstructions(),
            contextFiles = request.contextFiles,
            imageAttachments = emptyList(),
            fileAttachments = emptyList(),
            stagedAttachments = emptyList(),
            selectedModel = submissionState.selectedModel,
            selectedReasoning = submissionState.selectedReasoning,
            executionMode = submissionState.executionMode,
            planEnabled = false,
        )
    }

    /** Builds one provider-agnostic `/init` submission while preserving the current draft state. */
    private fun buildInitSlashSubmission(
        submissionState: SubmissionAreaState,
    ): ConversationSubmission {
        return ConversationSubmission(
            engineId = submissionState.selectedEngineId,
            prompt = SlashInitCommandPrompt.load(),
            systemInstructions = submissionState.serializedSystemInstructions(),
            contextFiles = emptyList<ContextFile>(),
            imageAttachments = emptyList(),
            fileAttachments = emptyList<FileAttachment>(),
            stagedAttachments = emptyList(),
            selectedModel = submissionState.selectedModel,
            selectedReasoning = submissionState.selectedReasoning,
            executionMode = submissionState.executionMode,
            planEnabled = submissionState.planEnabled,
            clearSubmissionDraftOnAccept = false,
        )
    }

    /** Sends once to the active runtime; only a confirmed inactive runtime permits a new turn. */
    private fun dispatchSubmissionOrAppend(
        submissionState: SubmissionAreaState,
        submission: ConversationSubmission,
    ) {
        val sessionId = context.activeSessionId()
        if (sessionId in submittingSessions) return
        if (context.chatService.listSessions().none { it.id == sessionId && it.isRunning }) {
            dispatchSubmission(submission, sessionId)
            return
        }
        if (!submittingSessions.add(sessionId)) return
        val draft = submissionState.draftSnapshot()
        context.dispatchSessionEvent(sessionId, AppEvent.LiveInputSending(true))
        context.coroutineLauncher.launch("liveInput:$sessionId") {
            try {
                when (val result = context.chatService.submitLiveInput(
                    sessionId,
                    LiveInput(
                        prompt = submission.prompt,
                        systemInstructions = submission.systemInstructions,
                        contextFiles = submission.contextFiles,
                        imageAttachments = submission.imageAttachments,
                        fileAttachments = submission.fileAttachments,
                    ),
                )) {
                    is LiveInputResult.Accepted -> {
                        val message = context.chatService.recordUserMessage(
                            sessionId = sessionId,
                            prompt = submission.prompt,
                            turnId = result.turnId,
                            attachments = submission.stagedAttachments,
                        )
                        message?.let {
                            context.publishLocalUserMessage(
                                sessionId, it.sourceId, it.prompt, it.timestamp, it.turnId, it.attachments, false,
                            )
                        }
                        context.dispatchSessionEvent(sessionId, AppEvent.SubmissionDraftAccepted(draft))
                        context.publishSessionSnapshot()
                    }
                    LiveInputResult.Inactive -> dispatchSubmission(submission, sessionId, draft)
                    is LiveInputResult.Rejected -> reportSendFailure(sessionId, result.message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportSendFailure(sessionId, error.message.orEmpty())
            } finally {
                submittingSessions.remove(sessionId)
                context.dispatchSessionEvent(sessionId, AppEvent.LiveInputSending(false))
            }
        }
    }

    private fun reportSendFailure(sessionId: String, message: String) {
        context.dispatchSessionEvent(
            sessionId,
            AppEvent.StatusTextUpdated(UiText.bundle("composer.send.failed", message)),
        )
    }

    /** A question response never consumes the composer draft or another outstanding interaction. */
    suspend fun sendAsyncAnswer(
        sessionId: String,
        threadId: String,
        answerText: String,
        settings: SubmissionAreaState,
    ): LiveInputResult {
        if (!submittingSessions.add(sessionId)) return LiveInputResult.Rejected("Another message is being sent. Please try again.")
        try {
            if (!context.chatService.ownsAsyncQuestion(sessionId, threadId)) return LiveInputResult.Inactive
            val result = context.chatService.submitLiveInput(sessionId, LiveInput(answerText))
            if (result is LiveInputResult.Accepted) {
                context.chatService.recordUserMessage(sessionId, answerText, result.turnId)?.let { message ->
                    context.publishLocalUserMessage(
                        sessionId, message.sourceId, message.prompt, message.timestamp, message.turnId, message.attachments, false,
                    )
                }
                context.publishSessionSnapshot()
                return result
            }
            if (result is LiveInputResult.Rejected) return result
            if (!context.chatService.ownsAsyncQuestion(sessionId, threadId)) return LiveInputResult.Inactive
            val started = CompletableDeferred<LiveInputResult>()
            dispatchSubmission(
                ConversationSubmission(
                    prompt = answerText,
                    systemInstructions = emptyList(),
                    contextFiles = emptyList(),
                    imageAttachments = emptyList(),
                    fileAttachments = emptyList(),
                    stagedAttachments = emptyList(),
                    selectedModel = settings.selectedModel,
                    selectedReasoning = settings.selectedReasoning,
                    executionMode = settings.executionMode,
                    planEnabled = settings.planEnabled,
                    engineId = "codex",
                    clearSubmissionDraftOnAccept = false,
                ),
                sessionId = sessionId,
                preserveInteractions = true,
                deferUserMessage = true,
                onRunEvent = { event ->
                    when (event) {
                        is SessionDomainEvent.TurnStarted -> started.complete(LiveInputResult.Accepted(event.turnId))
                        is SessionDomainEvent.ErrorAppended -> if (event.terminal) started.complete(LiveInputResult.Rejected(event.message))
                        is SessionDomainEvent.TurnCompleted -> started.complete(LiveInputResult.Rejected("The task ended before accepting the answer."))
                        else -> Unit
                    }
                },
                onRunStopped = { started.complete(LiveInputResult.Rejected("The engine stopped before accepting the answer.")) },
            )
            val accepted = try {
                withTimeout(30_000) { started.await() }
            } catch (error: TimeoutCancellationException) {
                LiveInputResult.Rejected("Answer delivery is unconfirmed. It was not sent again.")
            }
            if (accepted is LiveInputResult.Accepted) {
                context.chatService.recordUserMessage(sessionId, answerText, accepted.turnId)?.let { message ->
                    context.publishLocalUserMessage(
                        sessionId, message.sourceId, message.prompt, message.timestamp, message.turnId, message.attachments, false,
                    )
                }
                context.publishSessionSnapshot()
            }
            return accepted
        } finally {
            submittingSessions.remove(sessionId)
        }
    }

    private fun dispatchSubmission(
        submission: ConversationSubmission,
        sessionId: String = context.activeSessionId(),
        draft: SubmissionDraftSnapshot? = null,
        preserveInteractions: Boolean = false,
        deferUserMessage: Boolean = false,
        onRunEvent: (SessionDomainEvent) -> Unit = {},
        onRunStopped: () -> Unit = {},
    ): String {
        val localTurnId = ToolWindowCoordinatorIds.newLocalTurnId()
        val localMessage = if (submission.prompt.isBlank() || deferUserMessage) {
            null
        } else {
            context.chatService.recordUserMessage(
                sessionId = sessionId,
                prompt = submission.prompt,
                turnId = localTurnId,
                attachments = submission.stagedAttachments,
            )
        }
        if (submission.planEnabled) {
            context.activePlanRunContexts[sessionId] = ActivePlanRunContext(
                localTurnId = localTurnId,
                preferredExecutionMode = submission.executionMode,
            )
        } else {
            context.activePlanRunContexts.remove(sessionId)
        }
        context.dispatchSessionEvent(sessionId, AppEvent.PlanCompletionPromptUpdated(prompt = null))
        if (!preserveInteractions) {
            context.dispatchSessionEvent(sessionId, AppEvent.ClearToolUserInputs)
            context.dispatchSessionEvent(
                sessionId,
                AppEvent.PromptAccepted(
                    prompt = submission.prompt,
                    localTurnId = localTurnId,
                    clearSubmissionDraft = submission.clearSubmissionDraftOnAccept && draft == null,
                ),
            )
        }
        draft?.let { context.dispatchSessionEvent(sessionId, AppEvent.SubmissionDraftAccepted(it)) }
        localMessage?.let { message ->
            context.publishLocalUserMessage(
                sessionId,
                message.sourceId,
                message.prompt,
                message.timestamp,
                message.turnId,
                message.attachments,
                true,
            )
        }
        context.publishSessionSnapshot()
        context.chatService.runAgent(
            sessionId = sessionId,
            engineId = submission.engineId,
            model = submission.selectedModel,
            reasoningEffort = submission.selectedReasoning.effort,
            prompt = submission.prompt,
            systemInstructions = submission.systemInstructions,
            localTurnId = localTurnId,
            contextFiles = submission.contextFiles,
            imageAttachments = submission.imageAttachments,
            fileAttachments = submission.fileAttachments,
            approvalMode = submission.executionMode.toApprovalMode(),
            collaborationMode = if (submission.planEnabled) AgentCollaborationMode.PLAN else AgentCollaborationMode.DEFAULT,
            onTurnPersisted = { context.publishSessionSnapshot() },
            onSessionDomainEvents = { events ->
                events.forEach(onRunEvent)
                context.applySessionDomainEvents(sessionId, events)
            },
            onRunStateChanged = {
                context.publishSessionSnapshot()
                if (context.chatService.listSessions().none { it.id == sessionId && it.isRunning }) onRunStopped()
            },
        )
        return localTurnId
    }

    /** Resolves the best-known live turn id before cancellation clears runtime state. */
    private fun resolveActiveTurnIdForCancellation(): String {
        val projectedTurnId = context.executionStatusStore.state.value.turnStatus?.turnId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (projectedTurnId != null) {
            return projectedTurnId
        }
        return context.conversationStore.state.value.nodes
            .asReversed()
            .firstNotNullOfOrNull { node ->
                node.turnId
                    ?.trim()
                    ?.takeIf { turnId -> turnId.isNotBlank() && node.status == ItemStatus.RUNNING }
            }
            .orEmpty()
    }

    private fun SubmissionMode.toApprovalMode(): AgentApprovalMode {
        return when (this) {
            SubmissionMode.AUTO -> AgentApprovalMode.AUTO
            SubmissionMode.APPROVAL -> AgentApprovalMode.REQUIRE_CONFIRMATION
        }
    }
}
