package com.auracode.assistant.toolwindow.eventing

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.ArrayDeque

/**
 * Bounded event mailbox. Draft-like state updates are conflated until a command creates an
 * ordering barrier; interaction commands remain FIFO and can never grow the heap without limit.
 */
internal class ToolWindowEventHub(
    private val recorder: ((AppEvent) -> Unit)? = null,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val overflowHandler: (AppEvent) -> Unit = { event ->
        LOG.warn("Tool-window event mailbox is full; rejecting ${event::class.java.simpleName}")
    },
) {
    private data class PendingEvent(
        var event: AppEvent,
        val coalescingKey: String?,
    )

    private val lock = Any()
    private val pending = ArrayDeque<PendingEvent>()
    private val coalescedByKey = mutableMapOf<String, PendingEvent>()
    private val signal = Channel<Unit>(capacity = Channel.CONFLATED)
    private var closed = false

    val stream: Flow<AppEvent> = flow {
        for (ignored in signal) {
            while (true) {
                val event = synchronized(lock) { removeFirstOrNull() } ?: break
                emit(event)
            }
        }
    }

    internal val pendingEventCount: Int
        get() = synchronized(lock) { pending.size }

    fun publishUiIntent(intent: UiIntent) {
        publish(AppEvent.UiIntentPublished(intent))
    }

    fun publish(event: AppEvent) {
        recorder?.invoke(event)
        val accepted = synchronized(lock) {
            if (closed) return
            enqueue(event)
        }
        if (!accepted) {
            overflowHandler(event)
            return
        }
        signal.trySend(Unit)
    }

    fun close() {
        synchronized(lock) {
            closed = true
            pending.clear()
            coalescedByKey.clear()
        }
        signal.close()
    }

    private fun enqueue(event: AppEvent): Boolean {
        val key = event.coalescingKey()
        if (key != null) {
            coalescedByKey[key]?.let { existing ->
                existing.event = event
                return true
            }
        } else {
            // Commands form an ordering boundary. A later draft update must not replace the state
            // that appeared before this command (for example UpdateDocument -> SendPrompt).
            coalescedByKey.clear()
        }
        if (pending.size >= capacity) return false
        val queued = PendingEvent(event = event, coalescingKey = key)
        pending.addLast(queued)
        if (key != null) coalescedByKey[key] = queued
        return true
    }

    private fun removeFirstOrNull(): AppEvent? {
        val queued = pending.pollFirst() ?: return null
        queued.coalescingKey?.let { key ->
            if (coalescedByKey[key] === queued) coalescedByKey.remove(key)
        }
        return queued.event
    }

    private fun AppEvent.coalescingKey(): String? {
        return when (this) {
            is AppEvent.UiIntentPublished -> when (val intent = intent) {
                is UiIntent.UpdateDocument,
                is UiIntent.InputChanged,
                -> "draft:submission"
                is UiIntent.EditHistorySearchQuery -> "draft:history-search"
                is UiIntent.EditCustomModelDraft -> "draft:custom-model"
                is UiIntent.UpdateFocusedContextFile -> "state:focused-context"
                is UiIntent.RequestMentionSuggestions -> "request:mention-suggestions"
                is UiIntent.RequestAgentSuggestions -> "request:agent-suggestions"
                is UiIntent.EditToolUserInputAnswer -> "draft:tool-input:${intent.questionId}"
                is UiIntent.EditPlanRevisionDraft -> "draft:plan-revision"
                else -> null
            }
            is AppEvent.SessionSnapshotUpdated -> "state:sessions"
            is AppEvent.MentionSuggestionsUpdated -> "state:mention-suggestions"
            is AppEvent.AgentSuggestionsUpdated -> "state:agent-suggestions"
            is AppEvent.SettingsSnapshotUpdated -> "state:settings"
            is AppEvent.ConversationCapabilitiesUpdated -> "state:capabilities"
            is AppEvent.CodexEnvironmentCheckRunning -> "state:codex-environment-running"
            is AppEvent.CodexEnvironmentCheckUpdated -> "state:codex-environment"
            is AppEvent.CodexCliVersionSnapshotUpdated -> "state:codex-version"
            is AppEvent.ClaudeCliVersionSnapshotUpdated -> "state:claude-version"
            is AppEvent.RuntimeSkillsSnapshotUpdated -> "state:runtime-skills:${engineId}:${cwd}"
            is AppEvent.StatusTextUpdated -> "state:status-text"
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 1_024
        val LOG: Logger = Logger.getInstance(ToolWindowEventHub::class.java)
    }
}
