package com.auracode.assistant.session.kernel

/**
 * Retains the minimum event sequence required to rebuild the current session state.
 * High-frequency snapshot events replace the previous version for the same semantic entity.
 */
internal class SessionEventJournal {
    private val events = mutableListOf<SessionDomainEvent>()
    private val indexByCompactionKey = mutableMapOf<String, Int>()

    val size: Int
        get() = events.size

    fun append(event: SessionDomainEvent) {
        val key = event.compactionKey()
        val existingIndex = key?.let(indexByCompactionKey::get)
        if (existingIndex != null) {
            events[existingIndex] = event
            return
        }
        val index = events.size
        events += event
        if (key != null) indexByCompactionKey[key] = index
    }

    fun replaceAll(source: Iterable<SessionDomainEvent>) {
        clear()
        source.forEach(::append)
    }

    fun prepend(olderEvents: Iterable<SessionDomainEvent>) {
        val current = events.toList()
        clear()
        olderEvents.forEach(::append)
        current.forEach(::append)
    }

    fun snapshot(): List<SessionDomainEvent> = events.toList()

    private fun clear() {
        events.clear()
        indexByCompactionKey.clear()
    }
}

/** Returns the identity of snapshot-style events whose older versions are replay-redundant. */
private fun SessionDomainEvent.compactionKey(): String? {
    return when (this) {
        is SessionDomainEvent.MessageAppended -> "message:${turnId.orEmpty()}:$messageId"
        is SessionDomainEvent.ReasoningUpdated -> "reasoning:${turnId.orEmpty()}:$itemId"
        is SessionDomainEvent.CommandUpdated -> "command:${turnId.orEmpty()}:$itemId"
        is SessionDomainEvent.ToolUpdated -> "tool:${turnId.orEmpty()}:$itemId"
        is SessionDomainEvent.FileChangesUpdated -> "file-changes:${turnId.orEmpty()}:$itemId"
        is SessionDomainEvent.RunningPlanUpdated -> plan.turnId
            ?.takeIf { it.isNotBlank() }
            ?.let { "running-plan:$it" }
        is SessionDomainEvent.EditedFilesTracked -> "edited-files:$threadId:$turnId"
        is SessionDomainEvent.UsageUpdated -> "usage"
        is SessionDomainEvent.SubagentsUpdated -> "subagents:${threadId.orEmpty()}:${turnId.orEmpty()}"
        is SessionDomainEvent.ContextCompactionUpdated -> "context-compaction:${turnId.orEmpty()}:$itemId"
        is SessionDomainEvent.ThreadStarted,
        is SessionDomainEvent.TurnStarted,
        is SessionDomainEvent.ApprovalRequested,
        is SessionDomainEvent.ApprovalResolved,
        is SessionDomainEvent.ToolUserInputRequested,
        is SessionDomainEvent.ToolUserInputResolved,
        is SessionDomainEvent.EngineSwitched,
        is SessionDomainEvent.ErrorAppended,
        is SessionDomainEvent.TurnCompleted,
        -> null
    }
}
