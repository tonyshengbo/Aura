package com.codex.assistant.toolwindow.eventing

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.AgentApprovalMode
import com.codex.assistant.model.ContextFile
import com.codex.assistant.model.FileAttachment
import com.codex.assistant.model.ImageAttachment
import com.codex.assistant.model.MessageRole
import com.codex.assistant.context.MentionFileWhitelist
import com.codex.assistant.persistence.chat.PersistedAttachmentKind
import com.codex.assistant.persistence.chat.PersistedMessageAttachment
import com.codex.assistant.protocol.TurnOutcome
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.settings.SavedAgentDefinition
import com.codex.assistant.toolwindow.approval.ApprovalAction
import com.codex.assistant.toolwindow.approval.ApprovalAreaStore
import com.codex.assistant.toolwindow.approval.toUiModel
import com.codex.assistant.toolwindow.composer.AttachmentEntry
import com.codex.assistant.service.AgentChatService
import com.codex.assistant.settings.AgentSettingsService
import com.codex.assistant.toolwindow.composer.ContextEntry
import com.codex.assistant.toolwindow.composer.AttachmentKind
import com.codex.assistant.toolwindow.composer.ComposerAreaStore
import com.codex.assistant.toolwindow.drawer.RightDrawerKind
import com.codex.assistant.toolwindow.drawer.RightDrawerAreaStore
import com.codex.assistant.toolwindow.header.HeaderAreaStore
import com.codex.assistant.toolwindow.status.StatusAreaStore
import com.codex.assistant.toolwindow.timeline.TimelineAreaStore
import com.codex.assistant.toolwindow.timeline.TimelineFileChange
import com.codex.assistant.toolwindow.timeline.TimelineMutation
import com.codex.assistant.toolwindow.timeline.TimelineNodeMapper
import com.codex.assistant.toolwindow.timeline.TimelineNodeReducer
import com.intellij.codeInsight.navigation.LOG
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import java.security.MessageDigest
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

internal class ToolWindowCoordinator(
    private val chatService: AgentChatService,
    private val settingsService: AgentSettingsService,
    private val eventHub: ToolWindowEventHub,
    private val headerStore: HeaderAreaStore,
    private val statusStore: StatusAreaStore,
    private val timelineStore: TimelineAreaStore,
    private val composerStore: ComposerAreaStore,
    private val rightDrawerStore: RightDrawerAreaStore,
    private val approvalStore: ApprovalAreaStore = ApprovalAreaStore(),
    private val pickAttachments: () -> List<String> = { emptyList() },
    private val searchProjectFiles: (String, Int) -> List<String> = { _, _ -> emptyList() },
    private val isMentionCandidateFile: (String) -> Boolean = { path -> MentionFileWhitelist.allowPath(path) },
    private val readFileContent: (String) -> String? = { path -> readFileContentDefault(path) },
    private val openTimelineFileChange: (TimelineFileChange) -> Unit = {},
    private val openTimelineFilePath: (String) -> Unit = {},
    private val onSessionSnapshotPublished: () -> Unit = {},
    private val historyPageSize: Int = 40,
) : Disposable {
    companion object {
        private const val MENTION_LIMIT: Int = 10
    }
    private val ceh = CoroutineExceptionHandler { ctx, e ->
        LOG.error("Coroutine failed: $ctx", e)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default+ceh).apply {

    }
    private val recentFocusedFiles = ArrayDeque<String>()

    init {
        scope.launch {
            eventHub.stream.collect { event ->
                headerStore.onEvent(event)
                statusStore.onEvent(event)
                timelineStore.onEvent(event)
                composerStore.onEvent(event)
                rightDrawerStore.onEvent(event)
                approvalStore.onEvent(event)
                if (event is AppEvent.UiIntentPublished) {
                    handleUiIntent(event.intent)
                } else if (event is AppEvent.UnifiedEventPublished) {
                    handleUnifiedEvent(event.event)
                }
            }
        }

        publishSessionSnapshot()
        publishSettingsSnapshot()
        restoreCurrentSessionHistory()
    }

    private fun handleUiIntent(intent: UiIntent) {
        when (intent) {
            UiIntent.ToggleSettings -> {
                if (rightDrawerStore.state.value.kind == RightDrawerKind.SETTINGS) {
                    publishSettingsSnapshot()
                }
            }

            UiIntent.ToggleHistory -> {
                if (rightDrawerStore.state.value.kind == RightDrawerKind.HISTORY) {
                    loadHistoryConversations(reset = true)
                }
            }

            UiIntent.SendPrompt -> submitPromptIfAllowed()
            UiIntent.CancelRun -> cancelPromptRun()
            is UiIntent.DeleteSession -> deleteSession(intent.sessionId)
            is UiIntent.SwitchSession -> switchSession(intent.sessionId)
            UiIntent.LoadHistoryConversations -> loadHistoryConversations(reset = true)
            UiIntent.LoadMoreHistoryConversations -> loadHistoryConversations(reset = false)
            is UiIntent.EditHistorySearchQuery -> loadHistoryConversations(reset = true)
            is UiIntent.OpenRemoteConversation -> openRemoteConversation(intent.remoteConversationId, intent.title)
            is UiIntent.OpenTimelineFileChange -> openTimelineFileChange(intent.change)
            is UiIntent.OpenTimelineFilePath -> openTimelineFilePath(intent.path)
            UiIntent.LoadOlderMessages -> loadOlderMessages()
            UiIntent.OpenAttachmentPicker -> {
                val selected = pickAttachments()
                if (selected.isNotEmpty()) {
                    eventHub.publishUiIntent(UiIntent.AddAttachments(selected))
                }
            }
            UiIntent.PasteImageFromClipboard -> pasteImageFromClipboard()
            is UiIntent.OpenEditedFileDiff -> openEditedFileDiff(intent.path)
            is UiIntent.RevertEditedFile -> revertEditedFile(intent.path)
            UiIntent.RevertAllEditedFiles -> revertAllEditedFiles()
            is UiIntent.RequestMentionSuggestions -> {
                val query = intent.query.trim()
                val paths = if (query.isBlank()) {
                    recentFocusedFiles.toList().take(MENTION_LIMIT)
                } else {
                    searchProjectFiles(query, MENTION_LIMIT)
                }
                val suggestions = paths.map { toContextEntry(it) }
                eventHub.publish(
                    AppEvent.MentionSuggestionsUpdated(
                        query = query,
                        documentVersion = intent.documentVersion,
                        suggestions = suggestions,
                    ),
                )
            }
            is UiIntent.RequestAgentSuggestions -> {
                val query = intent.query.trim()
                val suggestions = settingsService.savedAgents()
                    .filter { agent ->
                        query.isBlank() || agent.name.contains(query, ignoreCase = true)
                    }
                    .take(MENTION_LIMIT)
                eventHub.publish(
                    AppEvent.AgentSuggestionsUpdated(
                        query = query,
                        documentVersion = intent.documentVersion,
                        suggestions = suggestions,
                    ),
                )
            }
            is UiIntent.UpdateFocusedContextFile -> recordFocusedFile(intent.path)
            is UiIntent.EditSettingsLanguageMode -> applyLanguagePreview(intent.mode)
            is UiIntent.EditSettingsThemeMode -> applyThemePreview(intent.mode)
            is UiIntent.SubmitApprovalAction -> submitApprovalDecision(intent.action)
            UiIntent.SaveAgentDraft -> saveAgentDraft()
            is UiIntent.DeleteSavedAgent -> deleteSavedAgent(intent.id)
            UiIntent.SaveSettings -> saveSettings()
            else -> Unit
        }
    }

    private fun handleUnifiedEvent(event: UnifiedEvent) {
        when (event) {
            is UnifiedEvent.ApprovalRequested -> {
                eventHub.publish(AppEvent.ApprovalRequested(event.request.toUiModel()))
            }

            is UnifiedEvent.TurnCompleted -> {
                eventHub.publish(AppEvent.ClearApprovals)
            }

            else -> Unit
        }
    }

    private fun applyLanguagePreview(mode: com.codex.assistant.settings.UiLanguageMode) {
        if (settingsService.uiLanguageMode() == mode) return
        settingsService.setUiLanguageMode(mode)
        settingsService.notifyLanguageChanged()
        publishSettingsSnapshot()
    }

    private fun applyThemePreview(mode: com.codex.assistant.settings.UiThemeMode) {
        if (settingsService.uiThemeMode() == mode) return
        settingsService.setUiThemeMode(mode)
        settingsService.notifyAppearanceChanged()
        publishSettingsSnapshot()
    }

    private fun saveSettings() {
        val drawerState = rightDrawerStore.state.value
        val oldLanguage = settingsService.uiLanguageMode()
        val oldTheme = settingsService.uiThemeMode()
        val state = settingsService.state
        state.setExecutablePathFor("codex", drawerState.codexCliPath.trim())
        settingsService.setUiLanguageMode(drawerState.languageMode)
        settingsService.setUiThemeMode(drawerState.themeMode)
        if (oldLanguage != settingsService.uiLanguageMode()) {
            settingsService.notifyLanguageChanged()
        }
        if (oldTheme != settingsService.uiThemeMode()) {
            settingsService.notifyAppearanceChanged()
        }
        publishSettingsSnapshot()
    }

    private fun saveAgentDraft() {
        val drawerState = rightDrawerStore.state.value
        val id = drawerState.editingAgentId?.takeIf { it.isNotBlank() }
        val name = drawerState.agentDraftName.trim()
        val prompt = drawerState.agentDraftPrompt.trim()
        if (name.isBlank() || prompt.isBlank()) {
            eventHub.publish(AppEvent.StatusTextUpdated(com.codex.assistant.toolwindow.shared.UiText.raw("Agent name and prompt are required.")))
            return
        }
        val state = settingsService.state
        val duplicate = state.savedAgents.any { agent ->
            agent.id != id && agent.name.trim().equals(name, ignoreCase = true)
        }
        if (duplicate) {
            eventHub.publish(AppEvent.StatusTextUpdated(com.codex.assistant.toolwindow.shared.UiText.raw("Agent name must be unique.")))
            return
        }
        val saved = SavedAgentDefinition(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = name,
            prompt = prompt,
        )
        val updated = state.savedAgents.toMutableList()
        val index = updated.indexOfFirst { it.id == saved.id }
        if (index >= 0) {
            updated[index] = saved
        } else {
            updated += saved
        }
        state.savedAgents = updated
        publishSettingsSnapshot()
        eventHub.publishUiIntent(UiIntent.SelectSavedAgentForEdit(saved.id))
    }

    private fun deleteSavedAgent(id: String) {
        val state = settingsService.state
        val updated = state.savedAgents.filterNot { it.id == id }.toMutableList()
        if (updated.size == state.savedAgents.size) return
        state.savedAgents = updated
        publishSettingsSnapshot()
    }

    private fun deleteSession(sessionId: String) {
        if (!chatService.deleteSession(sessionId)) return
        publishSessionSnapshot()
        restoreCurrentSessionHistory()
    }

    private fun switchSession(sessionId: String) {
        if (!chatService.switchSession(sessionId)) return
        publishSessionSnapshot()
        restoreCurrentSessionHistory()
    }

    private fun openRemoteConversation(remoteConversationId: String, title: String) {
        val sessionId = chatService.openRemoteConversation(
            remoteConversationId = remoteConversationId,
            suggestedTitle = title,
        ) ?: return
        publishSessionSnapshot()
        restoreCurrentSessionHistory()
        eventHub.publishUiIntent(UiIntent.CloseRightDrawer)
        onSessionSnapshotPublished()
    }

    private fun recordFocusedFile(path: String?) {
        val normalized = path?.trim().orEmpty()
        if (normalized.isBlank() || !isMentionCandidateFile(normalized)) return
        recentFocusedFiles.remove(normalized)
        recentFocusedFiles.addFirst(normalized)
        while (recentFocusedFiles.size > 64) {
            recentFocusedFiles.removeLast()
        }
    }

    private fun submitPromptIfAllowed() {
        if (timelineStore.state.value.isRunning) return
        val composerState = composerStore.state.value
        val prompt = composerState.serializedPrompt()
        val systemInstructions = composerState.serializedSystemInstructions()
        if (prompt.isBlank() && systemInstructions.isEmpty()) return
        val localTurnId = "local-turn-${System.currentTimeMillis()}"
        val storedAttachments = stageAttachments(
            sessionId = chatService.getCurrentSessionId(),
            attachments = composerState.attachments,
        )

        val localMessage = if (prompt.isBlank()) {
            null
        } else {
            chatService.recordUserMessage(
                prompt = prompt,
                turnId = localTurnId,
                attachments = storedAttachments,
            )
        }
        eventHub.publish(AppEvent.PromptAccepted(prompt))
        localMessage?.let { message ->
            eventHub.publish(
                AppEvent.TimelineMutationApplied(
                    TimelineNodeMapper.localUserMessageMutation(
                        sourceId = message.sourceId,
                        text = message.prompt,
                        timestamp = message.timestamp,
                        turnId = message.turnId,
                        attachments = message.attachments,
                    ),
                ),
            )
        }
        publishSessionSnapshot()

        val contextFiles = buildContextFiles(
            contextEntries = composerState.contextEntries,
            attachments = storedAttachments,
        )
        val imageAttachments = storedAttachments.filter { it.kind == PersistedAttachmentKind.IMAGE }.map {
            ImageAttachment(path = it.assetPath, name = it.displayName, mimeType = it.mimeType.ifBlank { "image/png" })
        }
        val fileAttachments = storedAttachments.filter { it.kind == PersistedAttachmentKind.FILE }.map {
            FileAttachment(path = it.assetPath, name = it.displayName, mimeType = it.mimeType.ifBlank { "application/octet-stream" })
        }

        chatService.runAgent(
            engineId = chatService.defaultEngineId(),
            model = composerState.selectedModel,
            reasoningEffort = composerState.selectedReasoning.effort,
            prompt = prompt,
            systemInstructions = systemInstructions,
            localTurnId = localTurnId,
            contextFiles = contextFiles,
            imageAttachments = imageAttachments,
            fileAttachments = fileAttachments,
            approvalMode = when (composerState.selectedMode) {
                ComposerMode.AUTO -> AgentApprovalMode.AUTO
                ComposerMode.APPROVAL -> AgentApprovalMode.REQUIRE_CONFIRMATION
            },
            onTurnPersisted = { publishSessionSnapshot() },
            onUnifiedEvent = ::publishUnifiedEvent,
        )
    }

    private fun cancelPromptRun() {
        chatService.cancelCurrent()
        publishUnifiedEvent(
            UnifiedEvent.TurnCompleted(
                turnId = "",
                outcome = TurnOutcome.CANCELLED,
                usage = null,
            ),
        )
    }

    private fun publishSessionSnapshot() {
        eventHub.publish(
            AppEvent.SessionSnapshotUpdated(
                sessions = chatService.listSessions(),
                activeSessionId = chatService.getCurrentSessionId(),
            ),
        )
        onSessionSnapshotPublished()
    }

    private fun publishSettingsSnapshot() {
        val state = settingsService.state
        eventHub.publish(
            AppEvent.SettingsSnapshotUpdated(
                codexCliPath = state.executablePathFor("codex"),
                languageMode = settingsService.uiLanguageMode(),
                themeMode = settingsService.uiThemeMode(),
                savedAgents = state.savedAgents.toList(),
            ),
        )
    }

    private fun loadHistoryConversations(reset: Boolean) {
        val drawerState = rightDrawerStore.state.value
        if (drawerState.historyLoading) return
        if (!reset && drawerState.historyNextCursor == null) return
        eventHub.publish(
            AppEvent.HistoryConversationsUpdated(
                conversations = if (reset) emptyList() else drawerState.historyConversations,
                nextCursor = drawerState.historyNextCursor,
                isLoading = true,
                append = !reset,
            ),
        )
        scope.launch {
            val page = chatService.loadRemoteConversationSummaries(
                limit = historyPageSize,
                cursor = if (reset) null else drawerState.historyNextCursor,
                searchTerm = drawerState.historyQuery.trim().takeIf { it.isNotBlank() },
            )
            eventHub.publish(
                AppEvent.HistoryConversationsUpdated(
                    conversations = page.conversations,
                    nextCursor = page.nextCursor,
                    isLoading = false,
                    append = !reset,
                ),
            )
        }
    }

    fun onSessionActivated() {
        publishSessionSnapshot()
        restoreCurrentSessionHistory()
    }

    fun onSessionSwitched(sessionId: String) {
        eventHub.publishUiIntent(UiIntent.SwitchSession(sessionId))
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun toContextEntry(path: String): ContextEntry {
        val p = runCatching { Path.of(path) }.getOrNull()
        val name = p?.name ?: path.substringAfterLast('/').substringAfterLast('\\')
        val tail = p?.parent?.fileName?.toString().orEmpty()
        return ContextEntry(path = path, displayName = name.ifBlank { path }, tailPath = tail)
    }

    private fun pasteImageFromClipboard() {
        val image = readImageFromClipboard() ?: return
        val tempPath = writeClipboardImage(image) ?: return
        eventHub.publishUiIntent(UiIntent.AddAttachments(listOf(tempPath)))
    }

    private fun openEditedFileDiff(path: String) {
        val change = composerStore.state.value.editedFiles.firstOrNull { it.path == path }?.latestChange ?: return
        openTimelineFileChange(change)
    }

    private fun revertEditedFile(path: String) {
        val aggregate = composerStore.state.value.editedFiles.firstOrNull { it.path == path } ?: return
        val result = revertAggregate(aggregate)
        if (result.isSuccess) {
            eventHub.publishUiIntent(UiIntent.AcceptEditedFile(path))
            eventHub.publish(AppEvent.StatusTextUpdated(com.codex.assistant.toolwindow.shared.UiText.raw(result.getOrDefault(""))))
        } else {
            eventHub.publish(AppEvent.StatusTextUpdated(com.codex.assistant.toolwindow.shared.UiText.raw(result.exceptionOrNull()?.message ?: "Revert failed.")))
        }
    }

    private fun revertAllEditedFiles() {
        val aggregates = composerStore.state.value.editedFiles
        if (aggregates.isEmpty()) return
        var success = 0
        var failed = 0
        aggregates.forEach { aggregate ->
            val result = revertAggregate(aggregate)
            if (result.isSuccess) {
                success += 1
                eventHub.publishUiIntent(UiIntent.AcceptEditedFile(aggregate.path))
            } else {
                failed += 1
            }
        }
        eventHub.publish(
            AppEvent.StatusTextUpdated(
                com.codex.assistant.toolwindow.shared.UiText.raw(
                    if (failed == 0) {
                        "Reverted $success files."
                    } else {
                        "Reverted $success files, failed $failed."
                    },
                ),
            ),
        )
    }

    private fun revertAggregate(aggregate: com.codex.assistant.toolwindow.composer.EditedFileAggregate): Result<String> {
        return runCatching {
            val change = aggregate.latestChange
            val path = Path.of(aggregate.path)
            when (change.kind) {
                com.codex.assistant.toolwindow.timeline.TimelineFileChangeKind.CREATE -> {
                    Files.deleteIfExists(path)
                    "Reverted ${aggregate.displayName}."
                }

                com.codex.assistant.toolwindow.timeline.TimelineFileChangeKind.UPDATE,
                com.codex.assistant.toolwindow.timeline.TimelineFileChangeKind.DELETE,
                com.codex.assistant.toolwindow.timeline.TimelineFileChangeKind.UNKNOWN,
                -> {
                    val oldContent = change.oldContent
                        ?: throw IllegalStateException("No previous content available for ${aggregate.displayName}.")
                    path.parent?.let { Files.createDirectories(it) }
                    Files.writeString(
                        path,
                        oldContent,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE,
                    )
                    "Reverted ${aggregate.displayName}."
                }
            }
        }
    }

    private fun readImageFromClipboard(): BufferedImage? {
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
            val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image ?: return null
            if (image is BufferedImage) return image
            val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
            val g = buffered.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()
            buffered
        }.getOrNull()
    }

    private fun writeClipboardImage(image: BufferedImage): String? {
        val path = runCatching {
            Files.createTempFile("codex-clip-", ".png")
        }.getOrNull() ?: return null
        return runCatching {
            Files.newOutputStream(path, StandardOpenOption.WRITE).use { out ->
                ImageIO.write(image, "png", out)
            }
            path.toAbsolutePath().toString()
        }.getOrNull()
    }

    private fun restoreCurrentSessionHistory() {
        eventHub.publish(AppEvent.ConversationReset)
        scope.launch {
            val page = chatService.loadCurrentConversationHistory(limit = historyPageSize)
            eventHub.publish(
                AppEvent.TimelineHistoryLoaded(
                    nodes = restoreNodes(page.events),
                    oldestCursor = page.olderCursor,
                    hasOlder = page.hasOlder,
                    prepend = false,
                ),
            )
        }
    }

    private fun loadOlderMessages() {
        val state = timelineStore.state.value
        if (!state.hasOlder || state.isLoadingOlder) return
        val beforeCursor = state.oldestCursor ?: return
        eventHub.publish(AppEvent.TimelineOlderLoadingChanged(loading = true))
        scope.launch {
            val page = chatService.loadOlderConversationHistory(
                cursor = beforeCursor,
                limit = historyPageSize,
            )
            eventHub.publish(
                AppEvent.TimelineHistoryLoaded(
                    nodes = restoreNodes(page.events),
                    oldestCursor = page.olderCursor,
                    hasOlder = page.hasOlder,
                    prepend = true,
                ),
            )
        }
    }

    private fun publishUnifiedEvent(event: UnifiedEvent) {
        eventHub.publishUnifiedEvent(event)
        TimelineNodeMapper.fromUnifiedEvent(event)?.let { mutation ->
            eventHub.publish(AppEvent.TimelineMutationApplied(mutation))
        }
    }

    private fun submitApprovalDecision(explicitAction: ApprovalAction?) {
        val current = approvalStore.state.value.current ?: return
        val action = explicitAction ?: approvalStore.state.value.selectedAction
        if (!chatService.submitApprovalDecision(current.requestId, action)) return
        eventHub.publish(AppEvent.ApprovalResolved(current.requestId))
        eventHub.publish(
            AppEvent.TimelineMutationApplied(
                TimelineMutation.UpsertApproval(
                    sourceId = current.itemId,
                    title = current.title,
                    body = buildResolvedApprovalBody(current.body, action),
                    status = when (action) {
                        ApprovalAction.REJECT -> com.codex.assistant.protocol.ItemStatus.FAILED
                        ApprovalAction.ALLOW,
                        ApprovalAction.ALLOW_FOR_SESSION,
                        -> com.codex.assistant.protocol.ItemStatus.SUCCESS
                    },
                    turnId = current.turnId,
                ),
            ),
        )
    }

    private fun buildResolvedApprovalBody(body: String, action: ApprovalAction): String {
        val decisionLabel = when (action) {
            ApprovalAction.ALLOW -> "Allowed"
            ApprovalAction.REJECT -> "Rejected"
            ApprovalAction.ALLOW_FOR_SESSION -> "Remembered for session"
        }
        return listOf(body.trim().takeIf { it.isNotBlank() }, decisionLabel).joinToString("\n\n")
    }

    private fun restoreNodes(events: List<UnifiedEvent>): List<com.codex.assistant.toolwindow.timeline.TimelineNode> {
        val reducer = TimelineNodeReducer()
        events.forEach { event ->
            TimelineNodeMapper.fromUnifiedEvent(event)?.let(reducer::accept)
        }
        return reducer.state.nodes.filterNot { it is com.codex.assistant.toolwindow.timeline.TimelineNode.LoadMoreNode }
    }

    private fun buildContextFiles(
        contextEntries: List<ContextEntry>,
        attachments: List<PersistedMessageAttachment>,
    ): List<ContextFile> {
        val merged = LinkedHashMap<String, ContextFile>()
        contextEntries.forEach { entry ->
            readFileContent(entry.path)?.let { content ->
                merged.putIfAbsent(entry.path, ContextFile(path = entry.path, content = content))
            }
        }
        attachments
            .filter { it.kind == PersistedAttachmentKind.TEXT }
            .forEach { attachment ->
                readFileContent(attachment.assetPath)?.let { content ->
                    val displayPath = attachment.originalPath.ifBlank { attachment.assetPath }
                    merged.putIfAbsent(displayPath, ContextFile(path = displayPath, content = content))
                }
            }
        return merged.values.toList()
    }

    private fun stageAttachments(
        sessionId: String,
        attachments: List<AttachmentEntry>,
    ): List<PersistedMessageAttachment> {
        if (sessionId.isBlank() || attachments.isEmpty()) return emptyList()
        return attachments.mapNotNull { attachment ->
            val source = runCatching { Path.of(attachment.path) }.getOrNull() ?: return@mapNotNull null
            if (!source.isRegularFile()) return@mapNotNull null
            val bytes = runCatching { Files.readAllBytes(source) }.getOrNull() ?: return@mapNotNull null
            val sha = sha256(bytes)
            val ext = attachment.displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val fileName = buildString {
                append(sha)
                if (!ext.isNullOrBlank()) {
                    append('.')
                    append(ext)
                }
            }
            val targetDir = Path.of(PathManager.getSystemPath(), "codex-assistant", "chat-assets", sessionId)
            runCatching { Files.createDirectories(targetDir) }.getOrNull() ?: return@mapNotNull null
            val target = targetDir.resolve(fileName)
            runCatching {
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }.getOrNull() ?: return@mapNotNull null

            PersistedMessageAttachment(
                kind = attachment.kind.toPersistedAttachmentKind(),
                displayName = attachment.displayName,
                assetPath = target.toAbsolutePath().toString(),
                originalPath = attachment.path,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                sha256 = sha,
            )
        }
    }

    private fun AttachmentKind.toPersistedAttachmentKind(): PersistedAttachmentKind {
        return when (this) {
            AttachmentKind.IMAGE -> PersistedAttachmentKind.IMAGE
            AttachmentKind.TEXT -> PersistedAttachmentKind.TEXT
            AttachmentKind.BINARY -> PersistedAttachmentKind.FILE
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

private fun readFileContentDefault(path: String): String? {
    val maxFileBytes = 128 * 1024
    val file = runCatching { Path.of(path) }.getOrNull() ?: return null
    if (!file.isRegularFile()) return null
    val bytes = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return null
    if (bytes.isEmpty() || bytes.any { it == 0.toByte() }) return null
    val clipped = if (bytes.size > maxFileBytes) bytes.copyOf(maxFileBytes) else bytes
    return runCatching { clipped.toString(Charsets.UTF_8) }.getOrNull()
}
