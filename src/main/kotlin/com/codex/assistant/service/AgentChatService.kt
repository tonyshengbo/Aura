package com.codex.assistant.service

import com.codex.assistant.conversation.ConversationHistoryPage
import com.codex.assistant.conversation.ConversationRef
import com.codex.assistant.conversation.ConversationSummaryPage
import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.model.AgentAction
import com.codex.assistant.model.AgentApprovalMode
import com.codex.assistant.model.AgentRequest
import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.ContextFile
import com.codex.assistant.model.FileAttachment
import com.codex.assistant.model.ImageAttachment
import com.codex.assistant.model.MessageRole
import com.codex.assistant.model.TurnUsageSnapshot
import com.codex.assistant.persistence.chat.ChatSessionRepository
import com.codex.assistant.persistence.chat.PersistedMessageAttachment
import com.codex.assistant.persistence.chat.PersistedSessionAsset
import com.codex.assistant.persistence.chat.PersistedChatSession
import com.codex.assistant.persistence.chat.SQLiteChatSessionRepository
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnUsage
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.protocol.UnifiedItem
import com.codex.assistant.protocol.UnifiedMessageAttachment
import com.codex.assistant.provider.CodexProviderFactory
import com.codex.assistant.provider.EngineDescriptor
import com.codex.assistant.provider.ProviderRegistry
import com.codex.assistant.settings.AgentSettingsService
import com.codex.assistant.toolwindow.approval.ApprovalAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service(Service.Level.PROJECT)
class AgentChatService private constructor(
    private val project: Project?,
    private val repository: ChatSessionRepository,
    private val settings: AgentSettingsService,
    private val registry: ProviderRegistry,
    private val workingDirectoryProvider: () -> String,
    private val diagnosticLogger: (String) -> Unit,
) : Disposable {
    constructor(project: Project) : this(
        project = project,
        repository = SQLiteChatSessionRepository(defaultDatabasePath(project)),
        settings = AgentSettingsService.getInstance(),
        registry = ProviderRegistry(AgentSettingsService.getInstance()),
        workingDirectoryProvider = { project.basePath ?: "." },
        diagnosticLogger = { message -> LOG.info(message) },
    )

    internal constructor(
        repository: ChatSessionRepository,
        registry: ProviderRegistry,
        settings: AgentSettingsService,
        workingDirectoryProvider: () -> String = { "." },
        diagnosticLogger: (String) -> Unit = { message -> LOG.info(message) },
    ) : this(
        project = null,
        repository = repository,
        settings = settings,
        registry = registry,
        workingDirectoryProvider = workingDirectoryProvider,
        diagnosticLogger = diagnosticLogger,
    )

    data class SessionSummary(
        val id: String,
        val title: String,
        val updatedAt: Long,
        val messageCount: Int,
        val remoteConversationId: String,
    )

    private data class SessionData(
        val id: String,
        var providerId: String,
        val createdAt: Long,
        var title: String,
        var updatedAt: Long,
        var messageCount: Int,
        var remoteConversationId: String,
        var usageSnapshot: TurnUsageSnapshot? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    private var currentJob: Job? = null
    private var currentRequestId: String? = null

    private val sessions = linkedMapOf<String, SessionData>()
    private var currentSessionId: String = ""

    companion object {
        private val LOG = Logger.getInstance(AgentChatService::class.java)

        private fun defaultDatabasePath(project: Project): Path {
            val baseDir = Path.of(PathManager.getSystemPath(), "codex-assistant", project.locationHash)
            Files.createDirectories(baseDir)
            return baseDir.resolve("chat-history.db")
        }

        private fun defaultAssetDirectory(sessionId: String): Path {
            return Path.of(PathManager.getSystemPath(), "codex-assistant", "chat-assets", sessionId)
        }
    }

    init {
        loadFromRepository()
    }

    fun getCurrentSessionId(): String = synchronized(stateLock) { currentSessionId }

    fun currentSessionTitle(): String = synchronized(stateLock) {
        sessions[currentSessionId]?.title?.trim().orEmpty().ifBlank { CodexBundle.message("session.new") }
    }

    fun isCurrentSessionEmpty(): Boolean = synchronized(stateLock) {
        (sessions[currentSessionId]?.messageCount ?: 0) == 0
    }

    fun currentUsageSnapshot(): TurnUsageSnapshot? = synchronized(stateLock) {
        sessions[currentSessionId]?.usageSnapshot
    }

    internal suspend fun loadCurrentConversationHistory(limit: Int): ConversationHistoryPage {
        val session = synchronized(stateLock) { sessions[currentSessionId] }
            ?: return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        val conversationId = session.remoteConversationId.trim()
        if (conversationId.isBlank()) {
            return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }
        val provider = registry.providerOrDefault(session.providerId)
        return runCatching {
            provider.loadInitialHistory(
                ref = ConversationRef(providerId = session.providerId, remoteConversationId = conversationId),
                pageSize = limit,
            )
        }.getOrElse {
            ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }.attachLocalAssets(session.id)
    }

    internal suspend fun loadOlderConversationHistory(cursor: String, limit: Int): ConversationHistoryPage {
        val session = synchronized(stateLock) { sessions[currentSessionId] }
            ?: return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        val conversationId = session.remoteConversationId.trim()
        if (conversationId.isBlank()) {
            return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }
        val provider = registry.providerOrDefault(session.providerId)
        return runCatching {
            provider.loadOlderHistory(
                ref = ConversationRef(providerId = session.providerId, remoteConversationId = conversationId),
                cursor = cursor,
                pageSize = limit,
            )
        }.getOrElse {
            ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }.attachLocalAssets(session.id)
    }

    fun listSessions(): List<SessionSummary> {
        return synchronized(stateLock) {
            sessions.values
                .sortedByDescending { it.updatedAt }
                .map {
                    SessionSummary(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt,
                        messageCount = it.messageCount,
                        remoteConversationId = it.remoteConversationId,
                    )
                }
        }
    }

    internal suspend fun loadRemoteConversationSummaries(
        limit: Int,
        cursor: String? = null,
        searchTerm: String? = null,
    ): ConversationSummaryPage {
        val engineId = synchronized(stateLock) {
            sessions[currentSessionId]?.providerId ?: registry.defaultEngineId()
        }
        val provider = registry.providerOrDefault(engineId)
        return runCatching {
            provider.listRemoteConversations(
                pageSize = limit,
                cursor = cursor,
                cwd = workingDirectoryProvider(),
                searchTerm = searchTerm,
            )
        }.getOrElse {
            ConversationSummaryPage(conversations = emptyList(), nextCursor = null)
        }
    }

    fun createSession(): String {
        val session = PersistedChatSession(
            id = UUID.randomUUID().toString(),
            providerId = CodexProviderFactory.ENGINE_ID,
            title = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messageCount = 0,
            remoteConversationId = "",
            usageSnapshot = null,
            isActive = true,
        )
        synchronized(stateLock) {
            sessions[session.id] = session.toSessionData()
            currentSessionId = session.id
        }
        repository.upsertSession(session)
        repository.markActiveSession(session.id)
        return session.id
    }

    fun switchSession(sessionId: String): Boolean {
        val switched = synchronized(stateLock) {
            if (!sessions.containsKey(sessionId)) {
                false
            } else {
                currentSessionId = sessionId
                true
            }
        }
        if (!switched) return false
        repository.markActiveSession(sessionId)
        persistSessionSnapshot(sessionId)
        return true
    }

    fun openRemoteConversation(
        remoteConversationId: String,
        suggestedTitle: String = "",
        providerId: String = registry.defaultEngineId(),
    ): String? {
        val normalizedRemoteId = remoteConversationId.trim()
        if (normalizedRemoteId.isBlank()) return null
        val sessionId = synchronized(stateLock) {
            val existing = sessions.values.firstOrNull {
                it.providerId == providerId && it.remoteConversationId == normalizedRemoteId
            }
            when {
                existing != null -> {
                    existing.updatedAt = System.currentTimeMillis()
                    if (existing.title.isBlank() && suggestedTitle.isNotBlank()) {
                        existing.title = suggestedTitle.trim()
                    }
                    currentSessionId = existing.id
                    existing.id
                }
                currentSessionId.isNotBlank() && sessions[currentSessionId]?.let { current ->
                    current.messageCount == 0 && current.remoteConversationId.isBlank()
                } == true -> {
                    val current = sessions.getValue(currentSessionId)
                    current.providerId = providerId
                    current.remoteConversationId = normalizedRemoteId
                    current.updatedAt = System.currentTimeMillis()
                    if (suggestedTitle.isNotBlank()) {
                        current.title = suggestedTitle.trim()
                    }
                    current.id
                }
                else -> {
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    sessions[id] = SessionData(
                        id = id,
                        providerId = providerId,
                        createdAt = now,
                        title = suggestedTitle.trim(),
                        updatedAt = now,
                        messageCount = 0,
                        remoteConversationId = normalizedRemoteId,
                    )
                    currentSessionId = id
                    id
                }
            }
        }
        repository.markActiveSession(sessionId)
        persistSessionSnapshot(sessionId)
        return sessionId
    }

    fun deleteSession(sessionId: String): Boolean {
        val fallbackSessionId = synchronized(stateLock) {
            if (!sessions.containsKey(sessionId)) {
                return false
            }
            sessions.remove(sessionId)
            if (sessions.isEmpty()) {
                currentSessionId = ""
                null
            } else {
                if (currentSessionId == sessionId) {
                    currentSessionId = sessions.values.maxByOrNull { it.updatedAt }?.id ?: sessions.keys.first()
                }
                currentSessionId
            }
        }
        repository.deleteSession(sessionId)
        deleteSessionAssets(sessionId)
        if (fallbackSessionId == null) {
            createSession()
            return true
        }
        repository.markActiveSession(fallbackSessionId)
        persistSessionSnapshot(fallbackSessionId)
        return true
    }

    internal data class LocalUserMessage(
        val sourceId: String,
        val prompt: String,
        val turnId: String?,
        val timestamp: Long,
        val attachments: List<PersistedMessageAttachment>,
    )

    internal fun recordUserMessage(
        prompt: String,
        turnId: String = "",
        attachments: List<PersistedMessageAttachment> = emptyList(),
    ): LocalUserMessage? {
        val sessionId = synchronized(stateLock) { currentSessionId }
        if (sessionId.isBlank()) return null
        val message = ChatMessage(role = MessageRole.USER, content = prompt)
        repository.saveSessionAssets(
            sessionId = sessionId,
            turnId = turnId,
            messageRole = MessageRole.USER,
            attachments = attachments,
            createdAt = message.timestamp,
        )
        synchronized(stateLock) {
            sessions[sessionId]?.applyMessage(message)
        }
        persistSessionSnapshot(sessionId)
        return LocalUserMessage(
            sourceId = message.id,
            prompt = prompt,
            turnId = turnId.ifBlank { null },
            timestamp = message.timestamp,
            attachments = attachments,
        )
    }

    fun runAgent(
        engineId: String,
        model: String,
        reasoningEffort: String? = null,
        prompt: String,
        systemInstructions: List<String> = emptyList(),
        localTurnId: String? = null,
        contextFiles: List<ContextFile>,
        imageAttachments: List<ImageAttachment> = emptyList(),
        fileAttachments: List<FileAttachment> = emptyList(),
        approvalMode: AgentApprovalMode = AgentApprovalMode.AUTO,
        onTurnPersisted: () -> Unit = {},
        onUnifiedEvent: (UnifiedEvent) -> Unit = {},
    ) {
        cancelCurrent()
        val resolvedModel = resolveModel(engineId, model)
        val remoteConversationId = currentRemoteConversationId()
        val request = AgentRequest(
            engineId = engineId,
            action = AgentAction.CHAT,
            model = resolvedModel,
            reasoningEffort = reasoningEffort?.trim()?.takeIf { it.isNotBlank() },
            prompt = prompt,
            systemInstructions = systemInstructions,
            contextFiles = contextFiles,
            imageAttachments = imageAttachments,
            fileAttachments = fileAttachments,
            workingDirectory = workingDirectoryProvider(),
            remoteConversationId = remoteConversationId,
            approvalMode = approvalMode,
        )
        if (engineId == CodexProviderFactory.ENGINE_ID) {
            logCodexChain(
                "Codex chain request: sessionId=${sessionIdForLog()} requestId=${request.requestId} " +
                    "mode=${if (remoteConversationId == null) "start-thread" else "resume-thread"} " +
                    "model=${resolvedModel ?: "<auto>"} " +
                    "remoteConversationId=${remoteConversationId ?: "<none>"} contextFiles=${contextFiles.size} " +
                    "images=${imageAttachments.size} files=${fileAttachments.size}",
            )
        }
        val provider = registry.providerOrDefault(engineId)
        val job = scope.launch {
            val assistantBuffer = StringBuilder()
            val sessionId = synchronized(stateLock) { currentSessionId }
            var activeTurnId = localTurnId?.trim().orEmpty()
            val countedAssistantItems = mutableSetOf<String>()
            try {
                provider.stream(request).collect { unified ->
                    collectAssistantOutput(unified, assistantBuffer)
                    val persistResult = persistUnifiedEvent(
                        sessionId = sessionId,
                        activeTurnId = activeTurnId,
                        event = unified,
                    )
                    activeTurnId = persistResult.turnId
                    if (unified is UnifiedEvent.ItemUpdated &&
                        unified.item.kind == ItemKind.NARRATIVE &&
                        narrativeRole(unified.item) == MessageRole.ASSISTANT &&
                        assistantBuffer.isNotBlank() &&
                        countedAssistantItems.add(unified.item.id)
                    ) {
                        synchronized(stateLock) {
                            sessions[sessionId]?.applyMessage(
                                ChatMessage(
                                    role = MessageRole.ASSISTANT,
                                    content = assistantBuffer.toString().ifBlank { prompt },
                                    timestamp = System.currentTimeMillis(),
                                ),
                            )
                        }
                        persistSessionSnapshot(sessionId)
                    }
                    if (unified is UnifiedEvent.TurnCompleted && unified.usage != null) {
                        updateCurrentUsageSnapshot(
                            model = resolvedModel ?: model,
                            usage = unified.usage,
                            engineId = engineId,
                        )
                    }
                    onUnifiedEvent(unified)
                }
                onTurnPersisted()
            } finally {
                synchronized(stateLock) {
                    if (currentRequestId == request.requestId) {
                        currentRequestId = null
                        currentJob = null
                    }
                }
            }
        }
        synchronized(stateLock) {
            currentRequestId = request.requestId
            currentJob = job
        }
    }

    fun cancelCurrent() {
        val (job, requestId) = synchronized(stateLock) {
            val activeJob = currentJob
            val activeRequestId = currentRequestId
            currentJob = null
            currentRequestId = null
            activeJob to activeRequestId
        }
        job?.cancel()
        requestId?.let { id ->
            runCatching {
                registry.cancel(id)
            }
        }
    }

    fun submitApprovalDecision(requestId: String, decision: ApprovalAction): Boolean {
        return registry.submitApprovalDecision(requestId, decision)
    }

    fun availableEngines(): List<EngineDescriptor> = registry.engines()

    fun defaultEngineId(): String = registry.defaultEngineId()

    fun engineDescriptor(engineId: String): EngineDescriptor? = registry.engine(engineId)

    fun executeCommand(command: String, workingDirectory: String): Pair<Int, String> {
        val cli = if (System.getProperty("os.name").lowercase().contains("win")) {
            GeneralCommandLine("cmd", "/c", command)
        } else {
            GeneralCommandLine("sh", "-lc", command)
        }
        cli.withWorkDirectory(workingDirectory)
        val output = CapturingProcessHandler(cli).runProcess(60_000)
        val text = buildString {
            if (output.stdout.isNotBlank()) {
                append(output.stdout)
            }
            if (output.stderr.isNotBlank()) {
                if (isNotEmpty()) {
                    append("\n")
                }
                append(output.stderr)
            }
        }.trim()
        return output.exitCode to text
    }

    override fun dispose() {
        cancelCurrent()
        scope.cancel()
    }

    private fun loadFromRepository() {
        val persistedSessions = repository.listSessions()
        if (persistedSessions.isEmpty()) {
            createSession()
            return
        }

        synchronized(stateLock) {
            sessions.clear()
            persistedSessions.forEach { session ->
                sessions[session.id] = session.toSessionData()
            }
            currentSessionId = persistedSessions.firstOrNull { it.isActive }?.id
                ?: persistedSessions.maxByOrNull { it.updatedAt }?.id
                ?: sessions.keys.first()
        }
        repository.markActiveSession(getCurrentSessionId())
    }

    private fun persistSessionSnapshot(sessionId: String) {
        val snapshot = synchronized(stateLock) {
            sessions[sessionId]?.toPersisted(isActive = sessionId == currentSessionId)
        } ?: return
        repository.upsertSession(snapshot)
    }

    private fun collectAssistantOutput(
        event: UnifiedEvent,
        assistantBuffer: StringBuilder,
    ) {
        when (event) {
            is UnifiedEvent.ItemUpdated -> {
                val item = event.item
                if (item.kind == ItemKind.NARRATIVE &&
                    narrativeRole(item) == MessageRole.ASSISTANT &&
                    !item.text.isNullOrBlank()
                ) {
                    assistantBuffer.clear()
                    assistantBuffer.append(item.text)
                }
            }
            else -> Unit
        }
    }

    private fun persistUnifiedEvent(
        sessionId: String,
        activeTurnId: String,
        event: UnifiedEvent,
    ): PersistResult {
        var nextTurnId = activeTurnId
        when (event) {
            is UnifiedEvent.ApprovalRequested -> Unit
            is UnifiedEvent.ThreadStarted -> updateCurrentRemoteConversationId(event.threadId)
            is UnifiedEvent.TurnStarted -> {
                val incoming = event.turnId.trim()
                if (incoming.isNotBlank()) {
                    repository.replaceSessionAssetTurnId(sessionId, nextTurnId, incoming)
                    nextTurnId = incoming
                }
            }

            is UnifiedEvent.ItemUpdated -> {
                val item = event.item
                if (item.kind == ItemKind.NARRATIVE && narrativeRole(item) == MessageRole.USER) {
                    // user messages are restored from remote history but not duplicated into local persistence
                }
            }
            is UnifiedEvent.TurnCompleted -> if (event.turnId.isNotBlank()) nextTurnId = event.turnId
            is UnifiedEvent.Error -> Unit
        }
        return PersistResult(
            turnId = nextTurnId,
        )
    }

    private fun currentRemoteConversationId(): String? = synchronized(stateLock) {
        sessions[currentSessionId]?.remoteConversationId?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun updateCurrentRemoteConversationId(remoteConversationId: String) {
        val trimmed = remoteConversationId.trim()
        if (trimmed.isBlank()) return
        val sessionId = synchronized(stateLock) {
            val sessionId = currentSessionId
            sessions[sessionId]?.remoteConversationId = trimmed
            sessions[sessionId]?.updatedAt = System.currentTimeMillis()
            sessionId
        }
        logCodexChain("Codex chain stored remote conversation: sessionId=${sessionIdForLog()} remoteConversationId=$trimmed")
        persistSessionSnapshot(sessionId)
    }

    private fun updateCurrentUsageSnapshot(
        model: String?,
        usage: TurnUsage,
        engineId: String,
    ) {
        val snapshot = TurnUsageSnapshot(
            model = model?.trim().orEmpty(),
            contextWindow = ModelContextWindows.resolve(model),
            inputTokens = usage.inputTokens,
            cachedInputTokens = usage.cachedInputTokens,
            outputTokens = usage.outputTokens,
        )
        val sessionId = synchronized(stateLock) {
            val sessionId = currentSessionId
            sessions[sessionId]?.usageSnapshot = snapshot
            sessions[sessionId]?.updatedAt = snapshot.capturedAt
            sessionId
        }
        if (engineId == CodexProviderFactory.ENGINE_ID) {
            logCodexChain(
                "Codex chain usage captured: sessionId=${sessionIdForLog()} " +
                    "model=${snapshot.model.ifBlank { "<unknown>" }} contextWindow=${snapshot.contextWindow} " +
                    "inputTokens=${snapshot.inputTokens} cachedInputTokens=${snapshot.cachedInputTokens} " +
                    "outputTokens=${snapshot.outputTokens}",
            )
        }
        persistSessionSnapshot(sessionId)
    }

    private fun logCodexChain(message: String) {
        diagnosticLogger(message)
    }

    private fun sessionIdForLog(): String = synchronized(stateLock) { currentSessionId.ifBlank { "<none>" } }

    private fun resolveModel(engineId: String, selectedModel: String): String? {
        val trimmed = selectedModel.trim()
        return if (engineId == "codex") trimmed.ifBlank { null } else trimmed.ifBlank { null }
    }

    private fun PersistedChatSession.toSessionData(): SessionData {
        return SessionData(
            id = id,
            providerId = providerId,
            createdAt = createdAt,
            title = title.trim(),
            updatedAt = updatedAt,
            messageCount = messageCount,
            remoteConversationId = remoteConversationId,
            usageSnapshot = usageSnapshot,
        )
    }

    private fun SessionData.toPersisted(isActive: Boolean): PersistedChatSession {
        return PersistedChatSession(
            id = id,
            providerId = providerId,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messageCount = messageCount,
            remoteConversationId = remoteConversationId,
            usageSnapshot = usageSnapshot,
            isActive = isActive,
        )
    }

    private fun SessionData.applyMessage(message: ChatMessage) {
        if (message.role == MessageRole.USER && title.isBlank()) {
            title = deriveTitle(message.content)
        }
        messageCount += 1
        updatedAt = maxOf(updatedAt, message.timestamp)
    }

    private fun deriveTitle(firstUserMessage: String): String {
        val trimmed = firstUserMessage.trim()
        return if (trimmed.isBlank()) "" else trimmed.take(36)
    }

    private fun deleteSessionAssets(sessionId: String) {
        val dir = defaultAssetDirectory(sessionId)
        if (!Files.exists(dir)) return
        runCatching {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach { path -> Files.deleteIfExists(path) }
        }
    }

    private data class PersistResult(
        val turnId: String,
    )

    private fun narrativeRole(item: UnifiedItem): MessageRole {
        return when (item.name) {
            "user_message" -> MessageRole.USER
            "system_message" -> MessageRole.SYSTEM
            else -> MessageRole.ASSISTANT
        }
    }

    private fun ConversationHistoryPage.attachLocalAssets(sessionId: String): ConversationHistoryPage {
        if (events.isEmpty()) return this
        val assetsByTurn = repository.loadSessionAssets(sessionId)
            .filter { it.messageRole == MessageRole.USER && it.turnId.isNotBlank() }
            .groupBy { it.turnId }
            .mapValues { (_, value) ->
                value.map { asset ->
                    UnifiedMessageAttachment(
                        id = asset.attachment.id,
                        kind = asset.attachment.kind.name.lowercase(),
                        displayName = asset.attachment.displayName,
                        assetPath = asset.attachment.assetPath,
                        originalPath = asset.attachment.originalPath,
                        mimeType = asset.attachment.mimeType,
                        sizeBytes = asset.attachment.sizeBytes,
                        status = asset.attachment.status,
                    )
                }
            }
        if (assetsByTurn.isEmpty()) return this
        var activeTurnId: String? = null
        return copy(
            events = events.map { event ->
                when (event) {
                    is UnifiedEvent.TurnStarted -> {
                        activeTurnId = event.turnId
                        event
                    }
                    is UnifiedEvent.ItemUpdated -> {
                        val turnId = activeTurnId
                        if (
                            event.item.kind == ItemKind.NARRATIVE &&
                            narrativeRole(event.item) == MessageRole.USER &&
                            !turnId.isNullOrBlank()
                        ) {
                            val attachments = assetsByTurn[turnId].orEmpty()
                            if (attachments.isNotEmpty()) {
                                event.copy(item = event.item.copy(attachments = attachments))
                            } else {
                                event
                            }
                        } else {
                            event
                        }
                    }
                    else -> event
                }
            },
        )
    }
}
