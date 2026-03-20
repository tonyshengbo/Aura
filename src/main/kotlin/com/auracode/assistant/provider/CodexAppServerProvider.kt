package com.auracode.assistant.provider

import com.auracode.assistant.conversation.ConversationCapabilities
import com.auracode.assistant.conversation.ConversationHistoryPage
import com.auracode.assistant.conversation.ConversationRef
import com.auracode.assistant.conversation.ConversationSummary
import com.auracode.assistant.conversation.ConversationSummaryPage
import com.auracode.assistant.model.AgentApprovalMode
import com.auracode.assistant.model.AgentCollaborationMode
import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.protocol.ApprovalDecision
import com.auracode.assistant.protocol.FileChangeMetrics
import com.auracode.assistant.protocol.ItemKind
import com.auracode.assistant.protocol.ItemStatus
import com.auracode.assistant.protocol.TurnOutcome
import com.auracode.assistant.protocol.UnifiedApprovalRequest
import com.auracode.assistant.protocol.UnifiedApprovalRequestKind
import com.auracode.assistant.protocol.TurnUsage
import com.auracode.assistant.protocol.UnifiedEvent
import com.auracode.assistant.protocol.UnifiedFileChange
import com.auracode.assistant.protocol.UnifiedItem
import com.auracode.assistant.protocol.UnifiedRunningPlanStep
import com.auracode.assistant.protocol.UnifiedToolUserInputAnswerDraft
import com.auracode.assistant.protocol.UnifiedToolUserInputOption
import com.auracode.assistant.protocol.UnifiedToolUserInputPrompt
import com.auracode.assistant.protocol.UnifiedToolUserInputQuestion
import com.auracode.assistant.protocol.UnifiedToolUserInputSubmission
import com.auracode.assistant.settings.AgentSettingsService
import com.auracode.assistant.toolwindow.approval.ApprovalAction
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class CodexAppServerProvider(
    private val settings: AgentSettingsService,
    private val environmentDetector: CodexEnvironmentDetector = CodexEnvironmentDetector(),
    private val diagnosticLogger: (String) -> Unit = { message -> LOG.info(message) },
) : AgentProvider {
    private val running = ConcurrentHashMap<String, ActiveRequest>()
    override val providerId: String = CodexProviderFactory.ENGINE_ID

    override fun stream(request: AgentRequest): Flow<UnifiedEvent> = callbackFlow {
        val resolution = environmentDetector.resolveForLaunch(
            configuredCodexPath = settings.getState().executablePathFor(CodexProviderFactory.ENGINE_ID),
            configuredNodePath = settings.nodeExecutablePath(),
        )
        val binary = resolution.codexPath.trim()
        if (binary.isEmpty()) {
            trySend(UnifiedEvent.Error("Aura Code runtime path is not configured."))
            close()
            return@callbackFlow
        }
        if (settings.nodeExecutablePath().isNotBlank() && resolution.nodePath.isNullOrBlank()) {
            trySend(UnifiedEvent.Error("Configured Node Path is not executable. Update Settings and try again."))
            close()
            return@callbackFlow
        }

        val process = try {
            createAppServerProcess(
                binary = binary,
                environmentOverrides = resolution.environmentOverrides,
                workingDirectory = File(request.workingDirectory),
            )
        } catch (t: Throwable) {
            trySend(UnifiedEvent.Error("Failed to start Aura Code app-server: ${t.message}"))
            close()
            return@callbackFlow
        }

        val active = ActiveRequest(process = process)
        running[request.requestId] = active

        launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        active.lastStderrLine = line
                        diagnosticLogger("Codex app-server stderr: ${line.take(4000)}")
                    }
                }
            }
        }

        launch(Dispatchers.IO) {
            val client = CodexAppServerClient(
                request = request,
                process = process,
                active = active,
                diagnosticLogger = diagnosticLogger,
                emitUnified = { event ->
                    trySend(event)
                    if (event is UnifiedEvent.TurnCompleted) {
                        active.turnCompleted.complete(Unit)
                    }
                },
            )
            try {
                client.start()
                runStartupStep("initialize", active, process) { client.initialize() }
                val threadId = runStartupStep("thread/start", active, process) { client.ensureThread() }
                if (request.remoteConversationId?.isBlank() != false) {
                    // thread/start already emits thread/started; only synthesize on resume path.
                } else {
                    trySend(UnifiedEvent.ThreadStarted(threadId = threadId))
                }
                runStartupStep("turn/start", active, process) { client.startTurn(threadId) }
                active.turnCompleted.await()
            } catch (t: Throwable) {
                diagnosticLogger("Codex app-server request failed: requestId=${request.requestId} message=${t.message}")
                trySend(UnifiedEvent.Error(t.message ?: "Unknown app-server error"))
            } finally {
                running.remove(request.requestId)
                process.destroy()
                close()
            }
        }

        awaitClose {
            running.remove(request.requestId)?.cancel()
        }
    }

    override suspend fun loadInitialHistory(ref: ConversationRef, pageSize: Int): ConversationHistoryPage {
        val turns = readThreadTurns(ref.remoteConversationId)
        return turns.toConversationHistoryPage(pageSize = pageSize, cursor = null)
    }

    override suspend fun loadOlderHistory(
        ref: ConversationRef,
        cursor: String,
        pageSize: Int,
    ): ConversationHistoryPage {
        val turns = readThreadTurns(ref.remoteConversationId)
        return turns.toConversationHistoryPage(pageSize = pageSize, cursor = cursor)
    }

    override suspend fun listRemoteConversations(
        pageSize: Int,
        cursor: String?,
        cwd: String?,
        searchTerm: String?,
    ): ConversationSummaryPage {
        val (threads, nextCursor) = readThreadSummaries(
            cursor = cursor,
            limit = pageSize,
            cwd = cwd,
            searchTerm = searchTerm,
        )
        return ConversationSummaryPage(
            conversations = threads.map { thread ->
                ConversationSummary(
                    remoteConversationId = thread.string("id").orEmpty(),
                    title = thread.string("name").orEmpty().ifBlank { thread.string("preview").orEmpty() },
                    createdAt = thread.long("createdAt", "created_at"),
                    updatedAt = thread.long("updatedAt", "updated_at"),
                    status = thread.objectValue("status")?.string("type").orEmpty(),
                )
            }.filter { it.remoteConversationId.isNotBlank() },
            nextCursor = nextCursor,
        )
    }

    override fun capabilities(): ConversationCapabilities {
        return ConversationCapabilities(
            supportsStructuredHistory = true,
            supportsHistoryPagination = true,
            supportsPlanMode = true,
            supportsApprovalRequests = true,
            supportsToolUserInput = true,
            supportsResume = true,
            supportsAttachments = true,
            supportsImageInputs = true,
        )
    }

    override fun cancel(requestId: String) {
        running.remove(requestId)?.cancel()
    }

    override fun submitApprovalDecision(requestId: String, decision: ApprovalAction): Boolean {
        return running.values.any { it.submitApprovalDecision(requestId, decision) }
    }

    override fun submitToolUserInput(
        requestId: String,
        answers: Map<String, UnifiedToolUserInputAnswerDraft>,
    ): Boolean {
        return running.values.any { it.submitToolUserInput(requestId, answers) }
    }

    private suspend fun readThreadTurns(remoteConversationId: String): List<JsonObject> {
        val resolution = environmentDetector.resolveForLaunch(
            configuredCodexPath = settings.getState().executablePathFor(CodexProviderFactory.ENGINE_ID),
            configuredNodePath = settings.nodeExecutablePath(),
        )
        val binary = resolution.codexPath.trim()
        if (binary.isEmpty()) return emptyList()
        val process = createAppServerProcess(
            binary = binary,
            environmentOverrides = resolution.environmentOverrides,
        )
        return try {
            val client = HistoryClient(
                process = process,
                diagnosticLogger = diagnosticLogger,
            )
            client.start()
            withTimeout(APP_SERVER_HANDSHAKE_TIMEOUT_MS) {
                client.initialize()
                client.readThreadTurns(remoteConversationId)
            }
        } finally {
            process.destroy()
        }
    }

    private suspend fun readThreadSummaries(
        cursor: String?,
        limit: Int,
        cwd: String?,
        searchTerm: String?,
    ): Pair<List<JsonObject>, String?> {
        val resolution = environmentDetector.resolveForLaunch(
            configuredCodexPath = settings.getState().executablePathFor(CodexProviderFactory.ENGINE_ID),
            configuredNodePath = settings.nodeExecutablePath(),
        )
        val binary = resolution.codexPath.trim()
        if (binary.isEmpty()) return emptyList<JsonObject>() to null
        val process = createAppServerProcess(
            binary = binary,
            environmentOverrides = resolution.environmentOverrides,
        )
        return try {
            val client = HistoryClient(
                process = process,
                diagnosticLogger = diagnosticLogger,
            )
            client.start()
            withTimeout(APP_SERVER_HANDSHAKE_TIMEOUT_MS) {
                client.initialize()
                client.readThreadSummaries(cursor = cursor, limit = limit, cwd = cwd, searchTerm = searchTerm)
            }
        } finally {
            process.destroy()
        }
    }

    private fun createAppServerProcess(
        binary: String,
        environmentOverrides: Map<String, String> = emptyMap(),
        workingDirectory: File? = null,
    ): Process {
        val launch = buildCodexAppServerLaunchConfig(
            binary = binary,
            environmentOverrides = environmentOverrides,
        )
        return ProcessBuilder(launch.command)
            .apply {
                if (workingDirectory != null) {
                    directory(workingDirectory)
                }
                environment().putAll(launch.environmentOverrides)
                redirectErrorStream(false)
            }
            .start()
    }

    private fun List<JsonObject>.toConversationHistoryPage(
        pageSize: Int,
        cursor: String?,
    ): ConversationHistoryPage {
        if (isEmpty() || pageSize <= 0) {
            return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }
        val endExclusive = cursor?.toIntOrNull()?.coerceIn(0, size) ?: size
        if (endExclusive <= 0) {
            return ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)
        }
        val startInclusive = (endExclusive - pageSize).coerceAtLeast(0)
        val pageTurns = subList(startInclusive, endExclusive)
        val parser = AppServerNotificationParser(
            requestId = "history:$providerId",
            diagnosticLogger = diagnosticLogger,
        )
        return ConversationHistoryPage(
            events = pageTurns.flatMap { parser.parseHistoricalTurn(it) },
            hasOlder = startInclusive > 0,
            olderCursor = startInclusive.takeIf { it > 0 }?.toString(),
        )
    }

    private data class ActiveRequest(
        val process: Process,
        @Volatile var threadId: String? = null,
        @Volatile var turnId: String? = null,
        @Volatile var lastStderrLine: String? = null,
        val turnCompleted: CompletableDeferred<Unit> = CompletableDeferred(),
        val pendingApprovals: ConcurrentHashMap<String, CompletableDeferred<ApprovalAction>> = ConcurrentHashMap(),
        val pendingToolUserInputs: ConcurrentHashMap<String, PendingToolUserInput> = ConcurrentHashMap(),
    ) {
        fun cancel() {
            pendingApprovals.values.forEach { deferred ->
                if (!deferred.isCompleted) {
                    deferred.complete(ApprovalAction.REJECT)
                }
            }
            pendingToolUserInputs.values.forEach { pending ->
                if (!pending.response.isCompleted) {
                    pending.response.complete(UnifiedToolUserInputSubmission(emptyMap()))
                }
            }
            process.destroyForcibly()
        }

        fun submitApprovalDecision(requestId: String, decision: ApprovalAction): Boolean {
            val deferred = pendingApprovals.remove(requestId) ?: return false
            return deferred.complete(decision)
        }

        fun submitToolUserInput(
            requestId: String,
            answers: Map<String, UnifiedToolUserInputAnswerDraft>,
        ): Boolean {
            val pending = pendingToolUserInputs[requestId] ?: return false
            pending.respondedLocally = true
            return pending.response.complete(UnifiedToolUserInputSubmission(answers))
        }
    }

    private suspend fun <T> runStartupStep(
        stepName: String,
        active: ActiveRequest,
        process: Process,
        action: suspend () -> T,
    ): T {
        return try {
            withTimeout(APP_SERVER_HANDSHAKE_TIMEOUT_MS) {
                action()
            }
        } catch (error: Throwable) {
            throw IllegalStateException(startupFailureMessage(stepName, active, process, error), error)
        }
    }

    private fun startupFailureMessage(
        stepName: String,
        active: ActiveRequest,
        process: Process,
        cause: Throwable,
    ): String {
        val stderr = active.lastStderrLine?.trim().orEmpty()
        if (stderr.contains("node: No such file or directory", ignoreCase = true)) {
            return "Codex app-server cannot find Node. Configure Node Path in Settings."
        }
        if (!process.isAlive) {
            return "Codex app-server exited during $stepName${stderr.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        }
        return "Codex app-server failed during $stepName: ${cause.message.orEmpty().ifBlank { "request timed out" }}"
    }

    private data class PendingToolUserInput(
        val rawRequestId: JsonElement,
        val prompt: UnifiedToolUserInputPrompt,
        val response: CompletableDeferred<UnifiedToolUserInputSubmission> = CompletableDeferred(),
        @Volatile var respondedLocally: Boolean = false,
    )

    @OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
    private class HistoryClient(
        private val process: Process,
        private val diagnosticLogger: (String) -> Unit,
    ) {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()

        fun start() {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isBlank()) return@forEach
                            diagnosticLogger("Codex app-server recv: ${trimmed.take(4000)}")
                            val obj = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return@forEach
                            val id = obj.string("id") ?: return@forEach
                            if (obj.containsKey("result") || obj.containsKey("error")) {
                                pending.remove(id)?.complete(obj)
                            }
                        }
                    }
                }.onFailure { error ->
                    diagnosticLogger(
                        "Codex history client reader coroutine failed: ${error.message}\n${error.stackTraceToString()}",
                    )
                }
            }
        }

        suspend fun initialize() {
            request(
                method = "initialize",
                params = buildJsonObject {
                    putJsonObject("clientInfo") {
                        put("name", "aura_code_intellij")
                        put("title", "Aura Code IntelliJ Plugin")
                        put("version", "1.0.0")
                    }
                    putJsonObject("capabilities") {
                        put("experimentalApi", true)
                    }
                },
            )
            notify(method = "initialized", params = buildJsonObject {})
        }

        suspend fun readThreadTurns(threadId: String): List<JsonObject> {
            val result = request(
                method = "thread/read",
                params = buildJsonObject {
                    put("threadId", threadId)
                    put("includeTurns", true)
                },
            )
            return result.objectValue("thread")
                ?.let { thread -> thread["turns"] as? JsonArray }
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }

        suspend fun readThreadSummaries(
            cursor: String?,
            limit: Int,
            cwd: String?,
            searchTerm: String?,
        ): Pair<List<JsonObject>, String?> {
            val result = request(
                method = "thread/list",
                params = buildJsonObject {
                    cursor?.takeIf { it.isNotBlank() }?.let { put("cursor", it) }
                    if (limit > 0) {
                        put("limit", JsonPrimitive(limit))
                    }
                    put("sortKey", "updated_at")
                    cwd?.takeIf { it.isNotBlank() }?.let { put("cwd", it) }
                    searchTerm?.takeIf { it.isNotBlank() }?.let { put("searchTerm", it) }
                },
            )
            val threads = (result["data"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            return threads to result.string("nextCursor")
        }

        private suspend fun request(method: String, params: JsonObject): JsonObject {
            val id = nextId.getAndIncrement().toString()
            val deferred = CompletableDeferred<JsonElement>()
            pending[id] = deferred
            writeJson(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    put("params", params)
                },
            )
            val response = deferred.await()
            pending.remove(id)
            val obj = response as? JsonObject ?: buildJsonObject { }
            obj["error"]?.let { error(it.toString()) }
            return obj.objectValue("result") ?: obj
        }

        private suspend fun notify(method: String, params: JsonObject) {
            writeJson(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", method)
                    put("params", params)
                },
            )
        }

        private suspend fun writeJson(payload: JsonObject) {
            val serialized = json.encodeToString(JsonObject.serializer(), payload)
            diagnosticLogger("Codex app-server send: ${serialized.take(4000)}")
            writer.write(serialized)
            writer.newLine()
            writer.flush()
        }
    }

    @OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
    private class CodexAppServerClient(
        private val request: AgentRequest,
        private val process: Process,
        private val active: ActiveRequest,
        private val diagnosticLogger: (String) -> Unit,
        private val emitUnified: suspend (UnifiedEvent) -> Unit,
    ) {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
        private val parser = AppServerNotificationParser(
            requestId = request.requestId,
            diagnosticLogger = diagnosticLogger,
        )
        private val nextId = AtomicInteger(1)
        private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start() {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            handleLine(line)
                        }
                    }
                }.onFailure { error ->
                    diagnosticLogger(
                        "Codex app-server reader coroutine failed: requestId=${request.requestId} message=${error.message}\n${error.stackTraceToString()}",
                    )
                }
            }
        }

        suspend fun initialize() {
            request(
                method = "initialize",
                params = buildJsonObject {
                    putJsonObject("clientInfo") {
                        put("name", "aura_code_intellij")
                        put("title", "Aura Code IntelliJ Plugin")
                        put("version", "1.0.0")
                    }
                    putJsonObject("capabilities") {
                        put("experimentalApi", true)
                    }
                },
            )
            notify(method = "initialized", params = buildJsonObject { })
        }

        suspend fun ensureThread(): String {
            val existingThreadId = request.remoteConversationId?.trim().orEmpty()
            val result = if (existingThreadId.isBlank()) {
                        request(
                    method = "thread/start",
                    params = buildJsonObject {
                        request.model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
                        put("cwd", request.workingDirectory)
                        put("approvalPolicy", approvalPolicy())
                        put("sandbox", APP_SERVER_THREAD_SANDBOX_MODE)
                    },
                )
            } else {
                request(
                    method = "thread/resume",
                    params = buildJsonObject {
                        put("threadId", existingThreadId)
                        put("cwd", request.workingDirectory)
                        request.model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
                    },
                )
            }
            val threadId = result.objectValue("thread")?.string("id")
                ?: existingThreadId
            if (threadId.isBlank()) {
                error("App-server did not return a thread id.")
            }
            active.threadId = threadId
            return threadId
        }

        suspend fun startTurn(threadId: String) {
            val result = request(
                method = "turn/start",
                params = buildJsonObject {
                    put("threadId", threadId)
                    put("input", buildInputPayload())
                    request.model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
                    request.reasoningEffort?.takeIf { it.isNotBlank() }?.let { put("effort", it) }
                    buildCollaborationModePayload(request.collaborationMode)?.let { put("collaborationMode", it) }
                    put("cwd", request.workingDirectory)
                    put("approvalPolicy", approvalPolicy())
                    putJsonObject("sandboxPolicy") {
                        put("type", APP_SERVER_TURN_SANDBOX_TYPE)
                        put("networkAccess", true)
                        put("writableRoots", buildJsonArray {
                            add(JsonPrimitive(request.workingDirectory))
                        })
                    }
                },
            )
            active.turnId = result.objectValue("turn")?.string("id")
        }

        private suspend fun request(method: String, params: JsonObject): JsonObject {
            val id = nextId.getAndIncrement().toString()
            val deferred = CompletableDeferred<JsonElement>()
            pending[id] = deferred
            writeJson(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    put("params", params)
                },
            )
            val response = deferred.await()
            pending.remove(id)
            val obj = response as? JsonObject ?: buildJsonObject { }
            obj["error"]?.let { error(it.toString()) }
            return obj.objectValue("result") ?: obj
        }

        private suspend fun notify(method: String, params: JsonObject) {
            writeJson(
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", method)
                    put("params", params)
                },
            )
        }

        private suspend fun writeJson(payload: JsonObject) {
            val serialized = json.encodeToString(JsonObject.serializer(), payload)
            diagnosticLogger("Codex app-server send: ${serialized.take(4000)}")
            writer.write(serialized)
            writer.newLine()
            writer.flush()
        }

        private fun handleLine(line: String) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return
            diagnosticLogger("Codex app-server recv: ${trimmed.take(4000)}")
            val obj = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return
            val rawId = obj["id"]
            val id = obj.string("id")
            val method = obj.string("method")
            if (id != null && (obj.containsKey("result") || obj.containsKey("error"))) {
                pending.remove(id)?.complete(obj)
                return
            }
            if (method != null && rawId != null) {
                handleServerRequest(id = rawId, method = method, params = obj.objectValue("params") ?: buildJsonObject { })
                return
            }
            if (method != null) {
                if (method == "serverRequest/resolved") {
                    handleServerRequestResolved(obj.objectValue("params") ?: buildJsonObject { })
                    return
                }
                val events = parser.parseNotification(
                    method = method,
                    params = obj.objectValue("params") ?: buildJsonObject { },
                )
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        events.forEach { event ->
                            when (event) {
                                is UnifiedEvent.ThreadStarted -> active.threadId = event.threadId
                                is UnifiedEvent.TurnStarted -> active.turnId = event.turnId
                                else -> Unit
                            }
                            emitUnified(event)
                        }
                    }
                }
            }
        }

        private fun handleServerRequest(id: JsonElement, method: String, params: JsonObject) {
            val serverRequestId = jsonRpcIdKey(id) ?: return
            if (method == "item/tool/requestUserInput") {
                val prompt = buildToolUserInputPrompt(id, params)
                active.pendingToolUserInputs[serverRequestId] = PendingToolUserInput(
                    rawRequestId = id,
                    prompt = prompt,
                )
                scope.launch {
                    runCatching {
                        emitUnified(UnifiedEvent.ToolUserInputRequested(prompt))
                        val submission = active.pendingToolUserInputs[serverRequestId]?.response?.await()
                            ?: UnifiedToolUserInputSubmission(emptyMap())
                        val response = buildToolUserInputResponse(
                            serverRequestId = id,
                            submission = submission.answers,
                        )
                        writeJson(response)
                    }.onFailure { error ->
                        diagnosticLogger(
                            "Codex app-server user-input response failed: method=$method params=${params.toString().take(500)} message=${error.message}\n${error.stackTraceToString()}",
                        )
                    }
                }
                return
            }
            if (method !in setOf(
                    "item/commandExecution/requestApproval",
                    "item/fileChange/requestApproval",
                    "item/permissions/requestApproval",
                )
            ) {
                val response = buildServerRequestResponse(
                    serverRequestId = id,
                    result = buildJsonObject {
                        put("decision", "accept")
                    },
                )
                scope.launch {
                    runCatching { writeJson(response) }.onFailure {
                        diagnosticLogger(
                            "Codex app-server server request response failed: method=$method params=${params.toString().take(500)} message=${it.message}\n${it.stackTraceToString()}",
                        )
                    }
                }
                return
            }
            val approvalRequest = buildApprovalRequest(serverRequestId = serverRequestId, method = method, params = params)
            active.pendingApprovals[serverRequestId] = CompletableDeferred()
            scope.launch {
                runCatching {
                    emitUnified(UnifiedEvent.ApprovalRequested(approvalRequest))
                    val decision = active.pendingApprovals[serverRequestId]?.await() ?: ApprovalAction.REJECT
                    val response = approvalResponse(serverRequestId = id, method = method, params = params, decision = decision)
                    active.pendingApprovals.remove(serverRequestId)
                    runCatching {
                        writeJson(response)
                    }.onFailure {
                        diagnosticLogger(
                            "Codex app-server approval response failed: method=$method params=${params.toString().take(500)} message=${it.message}\n${it.stackTraceToString()}",
                        )
                    }
                }.onFailure { error ->
                    diagnosticLogger(
                        "Codex app-server approval coroutine failed: method=$method params=${params.toString().take(500)} message=${error.message}\n${error.stackTraceToString()}",
                    )
                }
            }
        }

        private fun handleServerRequestResolved(params: JsonObject) {
            val requestId = params["requestId"]?.let(::jsonRpcIdKey) ?: return
            val pendingToolUserInput = active.pendingToolUserInputs.remove(requestId)
            if (pendingToolUserInput != null) {
                scope.launch {
                    emitUnified(UnifiedEvent.ToolUserInputResolved(requestId))
                }
            }
        }

        private fun approvalPolicy(): String {
            return when (request.approvalMode) {
                AgentApprovalMode.AUTO -> "never"
                AgentApprovalMode.REQUIRE_CONFIRMATION -> "on-request"
            }
        }

        private fun buildCollaborationModePayload(mode: AgentCollaborationMode): JsonObject? {
            return buildCollaborationModePayloadForMode(
                mode = mode,
                model = request.model,
                reasoningEffort = request.reasoningEffort,
            )
        }

        private fun buildApprovalRequest(
            serverRequestId: String,
            method: String,
            params: JsonObject,
        ): UnifiedApprovalRequest {
            val itemId = scopedApprovalItemId(params.string("itemId") ?: params.string("item_id") ?: serverRequestId)
            val turnId = params.string("turnId") ?: params.string("turn_id") ?: active.turnId
            return when (method) {
                "item/commandExecution/requestApproval" -> UnifiedApprovalRequest(
                    requestId = serverRequestId,
                    turnId = turnId,
                    itemId = itemId,
                    kind = UnifiedApprovalRequestKind.COMMAND,
                    title = "Run command",
                    body = listOfNotNull(
                        params.string("reason"),
                        params.string("command"),
                        params.string("cwd")?.let { "cwd: $it" },
                    ).joinToString("\n").ifBlank { "Command execution requires approval." },
                    command = params.string("command"),
                    cwd = params.string("cwd"),
                    allowForSession = true,
                )

                "item/fileChange/requestApproval" -> UnifiedApprovalRequest(
                    requestId = serverRequestId,
                    turnId = turnId,
                    itemId = itemId,
                    kind = UnifiedApprovalRequestKind.FILE_CHANGE,
                    title = "Apply file changes",
                    body = params.string("reason").orEmpty().ifBlank { "File changes require approval." },
                    allowForSession = true,
                )

                else -> UnifiedApprovalRequest(
                    requestId = serverRequestId,
                    turnId = turnId,
                    itemId = itemId,
                    kind = UnifiedApprovalRequestKind.PERMISSIONS,
                    title = "Grant permissions",
                    body = params.string("reason").orEmpty().ifBlank { "Additional permissions requested." },
                    permissions = summarizePermissions(params.objectValue("permissions")),
                    allowForSession = true,
                )
            }
        }

        private fun approvalResponse(
            serverRequestId: JsonElement,
            method: String,
            params: JsonObject,
            decision: ApprovalAction,
        ): JsonObject {
            return buildServerRequestResponse(
                serverRequestId = serverRequestId,
                result = buildJsonObject {
                    when (method) {
                        "item/commandExecution/requestApproval",
                        "item/fileChange/requestApproval",
                        -> {
                            put("decision", commandOrFileDecision(decision))
                        }

                        "item/permissions/requestApproval" -> {
                            put(
                                "permissions",
                                when (decision) {
                                    ApprovalAction.REJECT -> buildJsonObject { }
                                    ApprovalAction.ALLOW,
                                    ApprovalAction.ALLOW_FOR_SESSION,
                                    -> params.objectValue("permissions") ?: buildJsonObject { }
                                },
                            )
                            put(
                                "scope",
                                when (decision) {
                                    ApprovalAction.ALLOW_FOR_SESSION -> "session"
                                    else -> "turn"
                                },
                            )
                        }
                    }
                },
            )
        }

        private fun commandOrFileDecision(decision: ApprovalAction): String {
            return when (decision) {
                ApprovalAction.ALLOW -> "accept"
                ApprovalAction.REJECT -> "decline"
                ApprovalAction.ALLOW_FOR_SESSION -> "acceptForSession"
            }
        }

        private fun summarizePermissions(permissions: JsonObject?): List<String> {
            if (permissions == null) return emptyList()
            val summary = mutableListOf<String>()
            permissions.objectValue("network")?.let { network ->
                if (!network.isEmpty()) summary += "Network access"
            }
            permissions.objectValue("fileSystem")?.let { fileSystem ->
                fileSystem.arrayValue("read")
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { summary += "Read roots: ${it.joinToString(", ")}" }
                fileSystem.arrayValue("write")
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { summary += "Write roots: ${it.joinToString(", ")}" }
            }
            return summary
        }

        private fun scopedApprovalItemId(rawId: String): String {
            val normalized = rawId.trim()
            return if (normalized.isBlank()) rawId else "${request.requestId}:$normalized"
        }

        private fun buildInputPayload(): JsonArray {
            val promptText = buildPrompt(request)
            return buildJsonArray {
                if (promptText.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", promptText)
                        },
                    )
                }
                request.imageAttachments.forEach { image ->
                    add(
                        buildJsonObject {
                            put("type", "localImage")
                            put("path", image.path)
                        },
                    )
                }
            }
        }

    }

    internal class AppServerNotificationParser(
        private val requestId: String,
        private val diagnosticLogger: (String) -> Unit,
    ) {
        private val narrativeBuffers = mutableMapOf<String, StringBuilder>()
        private val activityOutputBuffers = mutableMapOf<String, StringBuilder>()
        private val planBuffers = mutableMapOf<String, StringBuilder>()
        private val itemSnapshots = mutableMapOf<String, UnifiedItem>()
        private val contextCompactionItemIdsByTurnId = mutableMapOf<String, String>()
        private var activeTurnId: String? = null
        private var activeThreadId: String? = null

        fun parseNotification(method: String, params: JsonObject): List<UnifiedEvent> {
            return when (method) {
                "thread/started" -> {
                    val threadId = params.objectValue("thread")?.string("id") ?: return emptyList()
                    activeThreadId = threadId
                    listOf(UnifiedEvent.ThreadStarted(threadId = threadId))
                }

                "turn/started" -> {
                    val turn = params.objectValue("turn")
                    val turnId = turn?.string("id") ?: return emptyList()
                    activeTurnId = turnId
                    val threadId = params.string("threadId")
                        ?: turn.string("threadId")
                    activeThreadId = threadId ?: activeThreadId
                    listOf(UnifiedEvent.TurnStarted(turnId = turnId, threadId = threadId))
                }

                "thread/tokenUsage/updated" -> {
                    val tokenUsage = params.objectValue("tokenUsage") ?: return emptyList()
                    val total = tokenUsage.objectValue("total") ?: return emptyList()
                    val threadId = params.string("threadId") ?: return emptyList()
                    listOf(
                        UnifiedEvent.ThreadTokenUsageUpdated(
                            threadId = threadId,
                            turnId = params.string("turnId"),
                            contextWindow = tokenUsage.int("modelContextWindow", "model_context_window"),
                            inputTokens = total.int("inputTokens", "input_tokens"),
                            cachedInputTokens = total.int("cachedInputTokens", "cached_input_tokens"),
                            outputTokens = total.int("outputTokens", "output_tokens"),
                        ),
                    )
                }

                "turn/diff/updated" -> {
                    val threadId = params.string("threadId") ?: return emptyList()
                    val turnId = params.string("turnId") ?: return emptyList()
                    val diff = params.string("diff") ?: return emptyList()
                    listOf(
                        UnifiedEvent.TurnDiffUpdated(
                            threadId = threadId,
                            turnId = turnId,
                            diff = diff,
                        ),
                    )
                }

                "thread/compacted" -> parseThreadCompacted(params)

                "turn/completed" -> {
                    val turn = params.objectValue("turn")
                    val turnId = turn?.string("id").orEmpty()
                    val status = turn?.string("status") ?: params.string("status")
                    val outcome = when (status?.lowercase()) {
                        "completed", "success" -> TurnOutcome.SUCCESS
                        "interrupted" -> TurnOutcome.CANCELLED
                        "failed", "error" -> TurnOutcome.FAILED
                        else -> TurnOutcome.SUCCESS
                    }
                    val usageObject = params.objectValue("usage")
                    val usage = usageObject?.let {
                        TurnUsage(
                            inputTokens = it.int("inputTokens", "input_tokens"),
                            cachedInputTokens = it.int("cachedInputTokens", "cached_input_tokens"),
                            outputTokens = it.int("outputTokens", "output_tokens"),
                        )
                    }
                    listOf(UnifiedEvent.TurnCompleted(turnId = turnId, outcome = outcome, usage = usage))
                }

                "item/started",
                "item/completed",
                -> {
                    val item = params.objectValue("item") ?: return emptyList()
                    parseItemLifecycle(
                        item = item,
                        method = method,
                        includeUserMessages = false,
                    )
                }

                "item/agentMessage/delta" -> parseNarrativeDelta(
                    params = params,
                    itemName = "message",
                    isThinking = false,
                )

                "item/reasoning/textDelta",
                "item/reasoning/summaryTextDelta",
                -> parseNarrativeDelta(
                    params = params,
                    itemName = "reasoning",
                    isThinking = true,
                )

                "item/commandExecution/outputDelta" -> parseActivityDelta(
                    params = params,
                    kind = ItemKind.COMMAND_EXEC,
                )

                "item/fileChange/outputDelta" -> parseActivityDelta(
                    params = params,
                    kind = ItemKind.DIFF_APPLY,
                )

                "item/plan/delta" -> parsePlanDelta(params)

                "turn/plan/updated" -> {
                    val turnId = params.string("turnId").orEmpty()
                    val explanation = params.string("explanation").orEmpty()
                    val steps = params.arrayValue("plan")
                        ?.mapNotNull { step ->
                            val value = step as? JsonObject ?: return@mapNotNull null
                            val text = value.string("step") ?: return@mapNotNull null
                            UnifiedRunningPlanStep(
                                step = text,
                                status = value.string("status").orEmpty(),
                            )
                        }
                        .orEmpty()
                    val planText = steps.joinToString("\n") { step ->
                        "- [${step.status}] ${step.step}"
                    }
                    val body = listOf(explanation.takeIf { it.isNotBlank() }, planText.takeIf { it.isNotBlank() })
                        .joinToString("\n\n")
                        .ifBlank { "Plan updated" }
                    val planId = planItemId(turnId)
                    planBuffers[planId] = StringBuilder(body)
                    listOf(
                        UnifiedEvent.RunningPlanUpdated(
                            threadId = activeThreadId,
                            turnId = turnId,
                            explanation = explanation.takeIf { it.isNotBlank() },
                            steps = steps,
                            body = body,
                        ),
                    )
                }

                "item/commandExecution/requestApproval",
                "item/fileChange/requestApproval",
                -> {
                    val sourceId = params.string("itemId") ?: scopedId("approval")
                    val body = listOfNotNull(
                        params.string("reason"),
                        params.string("command"),
                        params.string("cwd"),
                    ).joinToString("\n").ifBlank { method }
                    listOf(
                        UnifiedEvent.ItemUpdated(
                            UnifiedItem(
                                id = scopedId(sourceId),
                                kind = ItemKind.APPROVAL_REQUEST,
                                status = ItemStatus.RUNNING,
                                name = "Approval",
                                text = body,
                                approvalDecision = ApprovalDecision.PENDING,
                            ),
                        ),
                    )
                }

                else -> emptyList()
            }
        }

        fun parseHistoricalTurn(turn: JsonObject): List<UnifiedEvent> {
            val turnId = turn.string("id").orEmpty()
            val threadId = turn.string("threadId") ?: turn.string("thread_id")
            activeTurnId = turnId.takeIf { it.isNotBlank() }
            activeThreadId = threadId ?: activeThreadId
            val turnEvents = mutableListOf<UnifiedEvent>()
            if (turnId.isNotBlank()) {
                turnEvents += UnifiedEvent.TurnStarted(turnId = turnId, threadId = threadId)
            }
            turn.arrayValue("items")
                ?.mapNotNull { it as? JsonObject }
                ?.forEach { item ->
                    turnEvents += parseHistoricalItem(item)
                }
            val status = turn.string("status")
            val usageObject = turn.objectValue("usage")
            turnEvents += UnifiedEvent.TurnCompleted(
                turnId = turnId,
                outcome = when (status?.lowercase()) {
                    "interrupted" -> TurnOutcome.CANCELLED
                    "failed", "error" -> TurnOutcome.FAILED
                    else -> TurnOutcome.SUCCESS
                },
                usage = usageObject?.let {
                    TurnUsage(
                        inputTokens = it.int("inputTokens", "input_tokens"),
                        cachedInputTokens = it.int("cachedInputTokens", "cached_input_tokens"),
                        outputTokens = it.int("outputTokens", "output_tokens"),
                    )
                },
            )
            return turnEvents
        }

        private fun parseHistoricalItem(item: JsonObject): List<UnifiedEvent> {
            return parseItemLifecycle(
                item = item,
                method = "item/completed",
                includeUserMessages = true,
            )
        }

        private fun parseItemLifecycle(
            item: JsonObject,
            method: String,
            includeUserMessages: Boolean,
        ): List<UnifiedEvent> {
            val status = if (method == "item/completed") {
                parseStatus(item.string("status"), ItemStatus.SUCCESS)
            } else {
                parseStatus(item.string("status"), ItemStatus.RUNNING)
            }
            val normalizedType = item.string("type").orEmpty().lowercase()
            val sourceId = if (normalizedType == "plan") {
                planItemId(activeTurnId)
            } else {
                scopedId(item.string("id") ?: normalizedType.ifBlank { "item" })
            }
            if (!includeUserMessages && isUserMessageType(normalizedType)) {
                return emptyList()
            }
            val result = when {
                normalizedType.contains("message") -> UnifiedItem(
                    id = sourceId,
                    kind = ItemKind.NARRATIVE,
                    status = status,
                    name = if (normalizedType.contains("user")) "user_message" else "message",
                    text = extractText(item),
                )

                normalizedType.contains("reasoning") -> UnifiedItem(
                    id = sourceId,
                    kind = ItemKind.NARRATIVE,
                    status = status,
                    name = "reasoning",
                    text = extractText(item),
                )

                normalizedType.contains("commandexecution") -> {
                    val previousOutput = activityOutputBuffers[sourceId]?.toString().orEmpty()
                    UnifiedItem(
                        id = sourceId,
                        kind = ItemKind.COMMAND_EXEC,
                        status = status,
                        name = item.string("command") ?: "Exec Command",
                        text = item.string("aggregatedOutput") ?: item.string("aggregated_output") ?: previousOutput,
                        command = item.string("command"),
                        cwd = item.string("cwd"),
                        exitCode = item.intOrNull("exitCode", "exit_code"),
                    )
                }

                normalizedType.contains("filechange") -> {
                    val previousChanges = itemSnapshots[sourceId]?.fileChanges.orEmpty()
                    val changes = extractFileChanges(item = item, sourceId = sourceId)
                        .ifEmpty { previousChanges }
                    UnifiedItem(
                        id = sourceId,
                        kind = ItemKind.DIFF_APPLY,
                        status = status,
                        name = "File Changes",
                        text = extractFileChangeSummary(changes, item),
                        fileChanges = changes,
                    )
                }

                normalizedType.contains("contextcompaction") -> {
                    val runningTurnId = activeTurnId?.takeIf { it.isNotBlank() }
                    if (runningTurnId != null) {
                        contextCompactionItemIdsByTurnId[runningTurnId] = sourceId
                    }
                    UnifiedItem(
                        id = sourceId,
                        kind = ItemKind.CONTEXT_COMPACTION,
                        status = status,
                        name = "Context Compaction",
                        text = contextCompactionText(status),
                    )
                }

                normalizedType == "plan" -> UnifiedItem(
                    id = sourceId,
                    kind = ItemKind.PLAN_UPDATE,
                    status = status,
                    name = "Plan Update",
                    text = extractText(item),
                )

                normalizedType.contains("requestuserinput") || normalizedType.contains("userinput") -> UnifiedItem(
                    id = sourceId,
                    kind = ItemKind.USER_INPUT,
                    status = status,
                    name = "User Input",
                    text = extractUserInputText(item),
                )

                else -> UnifiedItem(
                    id = sourceId,
                    kind = if (normalizedType.contains("tool") || normalizedType.contains("call")) ItemKind.TOOL_CALL else ItemKind.UNKNOWN,
                    status = status,
                    name = item.string("title") ?: item.string("name") ?: item.string("toolName") ?: item.string("tool_name"),
                    text = extractText(item),
                    command = item.string("command"),
                    cwd = item.string("cwd"),
                )
            }
            itemSnapshots[sourceId] = result
            return listOf(UnifiedEvent.ItemUpdated(result))
        }

        private fun parseThreadCompacted(params: JsonObject): List<UnifiedEvent> {
            val turnId = params.string("turnId") ?: activeTurnId
            val threadId = params.string("threadId") ?: activeThreadId
            val sourceId = turnId
                ?.let { contextCompactionItemIdsByTurnId[it] }
                ?: turnId?.let { scopedId("context-compaction:$it") }
                ?: threadId?.let { scopedId("context-compaction:$it") }
                ?: return emptyList()
            val item = UnifiedItem(
                id = sourceId,
                kind = ItemKind.CONTEXT_COMPACTION,
                status = ItemStatus.SUCCESS,
                name = "Context Compaction",
                text = contextCompactionText(ItemStatus.SUCCESS),
            )
            turnId?.takeIf { it.isNotBlank() }?.let { contextCompactionItemIdsByTurnId[it] = sourceId }
            itemSnapshots[sourceId] = item
            return listOf(UnifiedEvent.ItemUpdated(item))
        }

        private fun contextCompactionText(status: ItemStatus): String {
            return when (status) {
                ItemStatus.RUNNING -> "Compacting context"
                ItemStatus.SUCCESS -> "Context compacted"
                ItemStatus.FAILED -> "Context compaction interrupted"
                ItemStatus.SKIPPED -> "Context compaction skipped"
            }
        }

        private fun parseNarrativeDelta(
            params: JsonObject,
            itemName: String,
            isThinking: Boolean,
        ): List<UnifiedEvent> {
            val rawId = params.string("itemId") ?: params.string("item_id") ?: itemName
            val id = scopedId(rawId)
            val delta = params.string("delta")
                ?: params.objectValue("delta")?.string("text")
                ?: params.string("text")
                ?: return emptyList()
            val buffer = narrativeBuffers.getOrPut(id) { StringBuilder() }
            buffer.append(delta)
            val item = UnifiedItem(
                id = id,
                kind = ItemKind.NARRATIVE,
                status = ItemStatus.RUNNING,
                name = if (isThinking) "reasoning" else itemName,
                text = buffer.toString(),
            )
            itemSnapshots[id] = item
            return listOf(UnifiedEvent.ItemUpdated(item))
        }

        private fun parsePlanDelta(params: JsonObject): List<UnifiedEvent> {
            val planId = planItemId(params.string("turnId") ?: activeTurnId)
            val delta = params.string("delta")
                ?: params.objectValue("delta")?.string("text")
                ?: params.string("text")
                ?: return emptyList()
            val buffer = planBuffers.getOrPut(planId) {
                StringBuilder(itemSnapshots[planId]?.text.orEmpty())
            }
            buffer.append(delta)
            val item = UnifiedItem(
                id = planId,
                kind = ItemKind.PLAN_UPDATE,
                status = ItemStatus.RUNNING,
                name = "Plan Update",
                text = buffer.toString(),
            )
            itemSnapshots[planId] = item
            return listOf(UnifiedEvent.ItemUpdated(item))
        }

        private fun parseActivityDelta(
            params: JsonObject,
            kind: ItemKind,
        ): List<UnifiedEvent> {
            val rawId = params.string("itemId") ?: params.string("item_id") ?: return emptyList()
            val id = scopedId(rawId)
            val delta = params.string("delta")
                ?: params.objectValue("delta")?.string("text")
                ?: params.string("output")
                ?: return emptyList()
            val buffer = activityOutputBuffers.getOrPut(id) { StringBuilder() }
            buffer.append(delta)
            val snapshot = itemSnapshots[id]
            val item = UnifiedItem(
                id = id,
                kind = kind,
                status = ItemStatus.RUNNING,
                name = snapshot?.name,
                text = buffer.toString(),
                command = snapshot?.command,
                cwd = snapshot?.cwd,
                fileChanges = snapshot?.fileChanges.orEmpty(),
            )
            itemSnapshots[id] = item
            return listOf(UnifiedEvent.ItemUpdated(item))
        }

        private fun isUserMessageType(normalizedType: String): Boolean {
            return normalizedType == "usermessage" ||
                normalizedType == "user_message" ||
                normalizedType == "user-message"
        }

        private fun extractText(item: JsonObject): String {
            return item.string("text")
                ?: item.string("output")
                ?: item.string("aggregatedOutput")
                ?: item.objectValue("content")?.string("text")
                ?: item.arrayValue("content")?.firstTextBlock()
                ?: ""
        }

        private fun extractUserInputText(item: JsonObject): String {
            val questions = item.arrayValue("questions")
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val answers = item.objectValue("answers")
                ?: item.objectValue("submittedAnswers")
                ?: item.objectValue("submitted_answers")
                ?: item.objectValue("response")

            val answeredBlocks = questions.mapNotNull { question ->
                val questionId = question.string("id").orEmpty()
                if (questionId.isBlank()) return@mapNotNull null
                val title = question.string("header")
                    ?.takeIf { it.isNotBlank() }
                    ?: question.string("question")
                    ?: questionId
                val isSecret = question["isSecret"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                val answerValues = extractUserInputAnswers(answers, questionId)
                when {
                    answerValues.isEmpty() -> null
                    isSecret -> "$title\nPrivate answer provided"
                    else -> "$title\n${answerValues.joinToString(", ")}"
                }
            }
            if (answeredBlocks.isNotEmpty()) {
                return answeredBlocks.joinToString("\n\n")
            }
            if (answers != null) {
                return "No answers were provided."
            }
            return extractText(item).ifBlank {
                if (questions.isNotEmpty()) {
                    "Waiting for input"
                } else {
                    ""
                }
            }
        }

        private fun extractUserInputAnswers(
            answers: JsonObject?,
            questionId: String,
        ): List<String> {
            val answerObject = answers?.objectValue(questionId)
            val answerList = answerObject?.arrayValue("answers")
                ?.mapNotNull { element ->
                    (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                }
                .orEmpty()
            if (answerList.isNotEmpty()) {
                return answerList
            }
            return listOfNotNull(
                answerObject?.string("answer")?.takeIf { it.isNotBlank() },
                answers?.string(questionId)?.takeIf { it.isNotBlank() },
            )
        }

        private fun extractFileChangeSummary(
            changes: List<UnifiedFileChange>,
            item: JsonObject,
        ): String {
            if (changes.isNotEmpty()) {
                return changes.joinToString("\n") { "${it.kind} ${it.path}" }
            }
            return extractText(item)
        }

        private fun extractFileChanges(
            item: JsonObject,
            sourceId: String,
        ): List<UnifiedFileChange> {
            val timestamp = item.longOrNull("updatedAt", "updated_at", "createdAt", "created_at")
                ?: System.currentTimeMillis()
            val changeArrays = listOfNotNull(
                item.arrayValue("changes"),
                item.objectValue("payload")?.arrayValue("changes"),
                item.objectValue("result")?.arrayValue("changes"),
                item.objectValue("proposal")?.arrayValue("changes"),
                item.objectValue("fileChange")?.arrayValue("changes"),
                item.objectValue("file_change")?.arrayValue("changes"),
            )
            changeArrays.forEach { changes ->
                val parsed = changes.mapIndexedNotNull { index, change ->
                    val value = change as? JsonObject ?: return@mapIndexedNotNull null
                    val path = value.string("path") ?: value.string("filePath") ?: value.string("file_path") ?: return@mapIndexedNotNull null
                    val oldContent = value.string("oldContent") ?: value.string("old_content")
                    val newContent = value.string("newContent") ?: value.string("new_content") ?: value.string("content")
                    val computedStats = FileChangeMetrics.fromContents(
                        oldContent = oldContent,
                        newContent = newContent,
                    )
                    UnifiedFileChange(
                        sourceScopedId = "$sourceId:$index",
                        path = path,
                        kind = extractFileChangeKind(value),
                        timestamp = timestamp,
                        addedLines = value.intOrNull("addedLines", "added_lines") ?: computedStats?.addedLines,
                        deletedLines = value.intOrNull("deletedLines", "deleted_lines") ?: computedStats?.deletedLines,
                        unifiedDiff = value.string("diff"),
                        oldContent = oldContent,
                        newContent = newContent,
                    )
                }
                if (parsed.isNotEmpty()) return parsed
            }
            return emptyList()
        }

        private fun parseStatus(status: String?, fallback: ItemStatus): ItemStatus {
            return when (status?.trim()?.lowercase()) {
                "running", "inprogress", "in_progress", "started", "active" -> ItemStatus.RUNNING
                "completed", "success", "succeeded" -> ItemStatus.SUCCESS
                "failed", "error", "declined", "interrupted" -> ItemStatus.FAILED
                "skipped" -> ItemStatus.SKIPPED
                else -> fallback
            }
        }

        private fun scopedId(rawId: String): String {
            val normalized = rawId.trim()
            return if (normalized.isBlank()) rawId else "$requestId:$normalized"
        }

        private fun planItemId(turnId: String?): String {
            val normalizedTurnId = turnId?.trim().orEmpty()
            return if (normalizedTurnId.isBlank()) {
                scopedId("plan")
            } else {
                scopedId("plan:$normalizedTurnId")
            }
        }

        private fun JsonArray.firstTextBlock(): String? {
            return firstNotNullOfOrNull { element ->
                val obj = element as? JsonObject ?: return@firstNotNullOfOrNull null
                obj.string("text")
            }
        }

        private fun JsonObject.string(key: String): String? {
            return this[key]?.jsonPrimitive?.contentOrNull
        }

        private fun JsonObject.objectValue(key: String): JsonObject? {
            return this[key]?.let { runCatching { it.jsonObject }.getOrNull() }
        }

        private fun JsonObject.arrayValue(key: String): JsonArray? {
            return this[key] as? JsonArray
        }

        private fun JsonObject.int(vararg keys: String): Int {
            return intOrNull(*keys) ?: 0
        }

        private fun JsonObject.intOrNull(vararg keys: String): Int? {
            return keys.firstNotNullOfOrNull { key ->
                this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            }
        }

        private fun JsonObject.longOrNull(vararg keys: String): Long? {
            return keys.firstNotNullOfOrNull { key ->
                this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            }
        }

    }

    companion object {
        private val LOG = Logger.getInstance(CodexAppServerProvider::class.java)
    }
}

private fun buildPrompt(request: AgentRequest): String {
    val snippetBlock = request.contextFiles
        .filter { !it.content.isNullOrBlank() }
        .joinToString("\n\n") { file ->
            "FILE: ${file.path}\n${file.content.orEmpty()}"
        }
    val pathOnlyBlock = request.contextFiles
        .filter { it.content.isNullOrBlank() }
        .joinToString("\n") { "- ${it.path}" }
    var contextBlock = buildString {
        if (snippetBlock.isNotBlank()) {
            append("Context snippets:\n")
            append(snippetBlock)
        }
        if (pathOnlyBlock.isNotBlank()) {
            if (isNotBlank()) append("\n\n")
            append("Context files (read by path):\n")
            append(pathOnlyBlock)
        }
    }
    if (request.systemInstructions.isNotEmpty()) {
        contextBlock = buildString {
            if (contextBlock.isNotBlank()) {
                append(contextBlock)
                append("\n\n")
            }
            append("##Agent Role and Instructions\n")
            append(request.systemInstructions.joinToString("\n"))
        }
    }
    val fileBlock = if (request.fileAttachments.isEmpty()) {
        ""
    } else {
        request.fileAttachments.joinToString("\n") { "- ${it.path}" }
    }
    return buildString {
        append(request.prompt)
        if (contextBlock.isNotBlank()) {
            append("\n\nContext files:\n")
            append(contextBlock)
        }
        if (fileBlock.isNotBlank()) {
            append("\n\nAttached non-text files (read by path):\n")
            append(fileBlock)
        }
    }.trim()
}

internal fun buildPromptForTest(request: AgentRequest): String = buildPrompt(request)

internal data class CodexAppServerLaunchConfig(
    val command: List<String>,
    val environmentOverrides: Map<String, String>,
)

internal fun buildCodexAppServerLaunchConfig(
    binary: String,
    environmentOverrides: Map<String, String> = emptyMap(),
): CodexAppServerLaunchConfig {
    val command = listOf(binary, "app-server")
    return CodexAppServerLaunchConfig(
        command = command,
        environmentOverrides = environmentOverrides,
    )
}

internal fun parseAppServerNotificationForTest(
    requestId: String,
    method: String,
    params: JsonObject,
): List<UnifiedEvent> {
    return CodexAppServerProvider.AppServerNotificationParser(
        requestId = requestId,
        diagnosticLogger = {},
    ).parseNotification(method = method, params = params)
}

internal const val APP_SERVER_THREAD_SANDBOX_MODE: String = "workspace-write"
internal const val APP_SERVER_TURN_SANDBOX_TYPE: String = "workspaceWrite"
internal const val APP_SERVER_HANDSHAKE_TIMEOUT_MS: Long = 10000L

internal fun buildCollaborationModePayloadForTest(
    mode: AgentCollaborationMode,
    model: String?,
    reasoningEffort: String?,
): JsonObject? {
    return buildCollaborationModePayloadForMode(
        mode = mode,
        model = model,
        reasoningEffort = reasoningEffort,
    )
}

internal fun extractFileChangeKindForTest(value: JsonObject): String = extractFileChangeKind(value)

internal fun buildRequestUserInputResponseForTest(serverRequestId: JsonElement): JsonObject {
    return buildRequestUserInputResponse(serverRequestId)
}

internal fun buildToolUserInputPromptForTest(
    serverRequestId: JsonElement,
    params: JsonObject,
): UnifiedToolUserInputPrompt = buildToolUserInputPrompt(serverRequestId, params)

internal fun buildToolUserInputResponseForTest(
    serverRequestId: JsonElement,
    submission: Map<String, UnifiedToolUserInputAnswerDraft>,
): JsonObject = buildToolUserInputResponse(serverRequestId, submission)

private fun buildCollaborationModePayloadForMode(
    mode: AgentCollaborationMode,
    model: String?,
    reasoningEffort: String?,
): JsonObject? {
    return buildJsonObject {
        put(
            "mode",
            when (mode) {
                AgentCollaborationMode.DEFAULT -> "default"
                AgentCollaborationMode.PLAN -> "plan"
            },
        )
        putJsonObject("settings") {
            put("model", model?.takeIf { it.isNotBlank() } ?: "")
            when {
                reasoningEffort.isNullOrBlank() -> put("reasoning_effort", JsonNull)
                else -> put("reasoning_effort", reasoningEffort)
            }
            put("developer_instructions", JsonNull)
        }
    }
}

private fun extractFileChangeKind(change: JsonObject): String {
    val rawKind = change["kind"]
    return when (rawKind) {
        is JsonObject -> rawKind.string("type").orEmpty()
        is JsonPrimitive -> rawKind.contentOrNull.orEmpty()
        else -> ""
    }.ifBlank { "update" }
}

private fun buildServerRequestResponse(
    serverRequestId: JsonElement,
    result: JsonObject,
): JsonObject {
    return buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", serverRequestId)
        put("result", result)
    }
}

private fun buildRequestUserInputResponse(serverRequestId: JsonElement): JsonObject {
    return buildServerRequestResponse(
        serverRequestId = serverRequestId,
        result = buildJsonObject {
            put("decision", "cancel")
        },
    )
}

private fun buildToolUserInputPrompt(
    serverRequestId: JsonElement,
    params: JsonObject,
): UnifiedToolUserInputPrompt {
    return UnifiedToolUserInputPrompt(
        requestId = jsonRpcIdKey(serverRequestId).orEmpty(),
        threadId = params.string("threadId").orEmpty(),
        turnId = params.string("turnId"),
        itemId = params.string("itemId").orEmpty(),
        questions = params.arrayValue("questions")
            ?.mapNotNull { element -> element as? JsonObject }
            ?.map { question ->
                UnifiedToolUserInputQuestion(
                    id = question.string("id").orEmpty(),
                    header = question.string("header").orEmpty(),
                    question = question.string("question").orEmpty(),
                    options = question.arrayValue("options")
                        ?.mapNotNull { option -> option as? JsonObject }
                        ?.map { option ->
                            UnifiedToolUserInputOption(
                                label = option.string("label").orEmpty(),
                                description = option.string("description").orEmpty(),
                            )
                        }
                        .orEmpty(),
                    isOther = question["isOther"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                    isSecret = question["isSecret"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
                )
            }
            .orEmpty(),
    )
}

private fun buildToolUserInputResponse(
    serverRequestId: JsonElement,
    submission: Map<String, UnifiedToolUserInputAnswerDraft>,
): JsonObject {
    return buildServerRequestResponse(
        serverRequestId = serverRequestId,
        result = buildJsonObject {
            putJsonObject("answers") {
                submission.forEach { (questionId, answerDraft) ->
                    putJsonObject(questionId) {
                        put(
                            "answers",
                            buildJsonArray {
                                answerDraft.answers.forEach { answer ->
                                    add(JsonPrimitive(answer))
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

private fun jsonRpcIdKey(value: JsonElement): String? {
    val primitive = value as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObjectBuilder.put(key: String, value: String) {
    put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.put(key: String, value: Boolean) {
    put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putJsonObject(key: String, builder: JsonObjectBuilder.() -> Unit) {
    put(key, buildJsonObject(builder))
}

private fun JsonObject.string(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.objectValue(key: String): JsonObject? {
    return this[key]?.let { runCatching { it.jsonObject }.getOrNull() }
}

private fun JsonObject.arrayValue(key: String): JsonArray? {
    return this[key] as? JsonArray
}

private fun JsonObject.long(vararg keys: String): Long {
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    } ?: 0L
}

private fun JsonObject.longOrNull(vararg keys: String): Long? {
    return keys.firstNotNullOfOrNull { key ->
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    }
}
