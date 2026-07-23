package com.auracode.assistant.toolwindow.eventing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Describes the provider work represented by one projected UI refresh. */
internal data class SessionUiFlushBatch(
    val eventCount: Int,
    val requestCount: Int,
    val oldestRequestNanos: Long,
    val slices: Set<SessionProjectionSlice>,
    val conversationEntryIds: Set<String>?,
)

internal enum class SessionProjectionSlice {
    CONVERSATION,
    EXECUTION,
    SUBMISSION,
    NAVIGATION,
    ;

    companion object {
        val ALL: Set<SessionProjectionSlice> = entries.toSet()
    }
}

/**
 * Coalesces high-frequency projection requests per session while allowing interaction-critical
 * state changes to flush immediately.
 */
internal class SessionUiUpdateScheduler(
    private val scope: CoroutineScope,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onFlush: (sessionId: String, batch: SessionUiFlushBatch) -> Unit,
) {
    private data class PendingUpdate(
        val generation: Long,
        val oldestRequestNanos: Long,
        val eventCount: Int,
        val requestCount: Int,
        val slices: Set<SessionProjectionSlice>,
        val conversationEntryIds: Set<String>?,
        val job: Job?,
    )

    private val lock = Any()
    private val pendingBySessionId = mutableMapOf<String, PendingUpdate>()
    private var nextGeneration = 0L

    fun request(
        sessionId: String,
        eventCount: Int,
        slices: Set<SessionProjectionSlice>,
        immediate: Boolean,
        conversationEntryIds: Set<String>? = null,
    ) {
        require(eventCount > 0) { "eventCount must be positive" }
        if (slices.isEmpty()) return
        if (immediate || intervalMs <= 0L) {
            flushNow(sessionId, eventCount, slices, conversationEntryIds)
            return
        }

        synchronized(lock) {
            val current = pendingBySessionId[sessionId]
            if (current != null) {
                pendingBySessionId[sessionId] = current.copy(
                    eventCount = current.eventCount + eventCount,
                    requestCount = current.requestCount + 1,
                    slices = current.slices + slices,
                    conversationEntryIds = mergeConversationEntryIds(
                        currentSlices = current.slices,
                        currentEntryIds = current.conversationEntryIds,
                        incomingSlices = slices,
                        incomingEntryIds = conversationEntryIds,
                    ),
                )
                return
            }

            val generation = ++nextGeneration
            val requestedAt = nanoTime()
            val job = scope.launch {
                delay(intervalMs)
                flushGeneration(sessionId, generation)
            }
            pendingBySessionId[sessionId] = PendingUpdate(
                generation = generation,
                oldestRequestNanos = requestedAt,
                eventCount = eventCount,
                requestCount = 1,
                slices = slices,
                conversationEntryIds = conversationEntryIds,
                job = job,
            )
        }
    }

    fun flush(sessionId: String) {
        flushNow(sessionId, 0, emptySet(), emptySet())
    }

    fun drop(sessionId: String) {
        synchronized(lock) {
            pendingBySessionId.remove(sessionId)?.job?.cancel()
        }
    }

    fun cancel() {
        synchronized(lock) {
            pendingBySessionId.values.forEach { it.job?.cancel() }
            pendingBySessionId.clear()
        }
    }

    private fun flushNow(
        sessionId: String,
        additionalEventCount: Int,
        additionalSlices: Set<SessionProjectionSlice>,
        additionalConversationEntryIds: Set<String>?,
    ) {
        val batch = synchronized(lock) {
            val pending = pendingBySessionId.remove(sessionId)
            pending?.job?.cancel()
            if (pending == null && additionalEventCount == 0) {
                null
            } else {
                SessionUiFlushBatch(
                    eventCount = (pending?.eventCount ?: 0) + additionalEventCount,
                    requestCount = (pending?.requestCount ?: 0) + if (additionalEventCount > 0) 1 else 0,
                    oldestRequestNanos = pending?.oldestRequestNanos ?: nanoTime(),
                    slices = pending?.slices.orEmpty() + additionalSlices,
                    conversationEntryIds = if (pending == null) {
                        additionalConversationEntryIds
                    } else {
                        mergeConversationEntryIds(
                            currentSlices = pending.slices,
                            currentEntryIds = pending.conversationEntryIds,
                            incomingSlices = additionalSlices,
                            incomingEntryIds = additionalConversationEntryIds,
                        )
                    },
                )
            }
        }
        batch?.let { onFlush(sessionId, it) }
    }

    private fun flushGeneration(sessionId: String, generation: Long) {
        val batch = synchronized(lock) {
            val pending = pendingBySessionId[sessionId]
            if (pending?.generation != generation) {
                null
            } else {
                pendingBySessionId.remove(sessionId)
                SessionUiFlushBatch(
                    eventCount = pending.eventCount,
                    requestCount = pending.requestCount,
                    oldestRequestNanos = pending.oldestRequestNanos,
                    slices = pending.slices,
                    conversationEntryIds = pending.conversationEntryIds,
                )
            }
        }
        batch?.let { onFlush(sessionId, it) }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 32L
    }

    private fun mergeConversationEntryIds(
        currentSlices: Set<SessionProjectionSlice>,
        currentEntryIds: Set<String>?,
        incomingSlices: Set<SessionProjectionSlice>,
        incomingEntryIds: Set<String>?,
    ): Set<String>? {
        val currentHasConversation = SessionProjectionSlice.CONVERSATION in currentSlices
        val incomingHasConversation = SessionProjectionSlice.CONVERSATION in incomingSlices
        return when {
            !currentHasConversation -> incomingEntryIds
            !incomingHasConversation -> currentEntryIds
            currentEntryIds == null || incomingEntryIds == null -> null
            else -> currentEntryIds + incomingEntryIds
        }
    }
}

/** Returns whether an event must be visible without waiting for the streaming coalescing window. */
internal fun requiresImmediateProjection(event: com.auracode.assistant.session.kernel.SessionDomainEvent): Boolean {
    return when (event) {
        is com.auracode.assistant.session.kernel.SessionDomainEvent.MessageAppended ->
            event.role != com.auracode.assistant.session.kernel.SessionMessageRole.ASSISTANT
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ThreadStarted,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.TurnStarted,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ApprovalRequested,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ApprovalResolved,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUserInputRequested,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUserInputResolved,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.EngineSwitched,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ErrorAppended,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.TurnCompleted,
        -> true
        else -> false
    }
}

/** Resolves the smallest safe set of UI projection slices affected by one domain event. */
internal fun projectionSlicesFor(
    event: com.auracode.assistant.session.kernel.SessionDomainEvent,
): Set<SessionProjectionSlice> {
    return when (event) {
        is com.auracode.assistant.session.kernel.SessionDomainEvent.MessageAppended,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ReasoningUpdated,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.CommandUpdated,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUpdated,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.RunningPlanUpdated,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.EngineSwitched,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ContextCompactionUpdated,
        -> setOf(SessionProjectionSlice.CONVERSATION)

        is com.auracode.assistant.session.kernel.SessionDomainEvent.FileChangesUpdated ->
            setOf(SessionProjectionSlice.CONVERSATION, SessionProjectionSlice.SUBMISSION)

        is com.auracode.assistant.session.kernel.SessionDomainEvent.ErrorAppended -> if (event.terminal) {
            setOf(
                SessionProjectionSlice.CONVERSATION,
                SessionProjectionSlice.EXECUTION,
                SessionProjectionSlice.SUBMISSION,
            )
        } else {
            setOf(SessionProjectionSlice.CONVERSATION)
        }

        is com.auracode.assistant.session.kernel.SessionDomainEvent.ApprovalRequested,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ApprovalResolved,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUserInputRequested,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUserInputResolved,
        -> setOf(SessionProjectionSlice.CONVERSATION, SessionProjectionSlice.EXECUTION)

        is com.auracode.assistant.session.kernel.SessionDomainEvent.EditedFilesTracked,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.UsageUpdated,
        -> setOf(SessionProjectionSlice.SUBMISSION)

        is com.auracode.assistant.session.kernel.SessionDomainEvent.SubagentsUpdated ->
            setOf(SessionProjectionSlice.NAVIGATION)

        is com.auracode.assistant.session.kernel.SessionDomainEvent.ThreadStarted,
        is com.auracode.assistant.session.kernel.SessionDomainEvent.TurnStarted,
        -> setOf(
            SessionProjectionSlice.CONVERSATION,
            SessionProjectionSlice.EXECUTION,
            SessionProjectionSlice.SUBMISSION,
        )

        is com.auracode.assistant.session.kernel.SessionDomainEvent.TurnCompleted -> SessionProjectionSlice.ALL
    }
}

internal fun projectionSlicesFor(
    events: Iterable<com.auracode.assistant.session.kernel.SessionDomainEvent>,
): Set<SessionProjectionSlice> = events.flatMapTo(linkedSetOf(), ::projectionSlicesFor)

/**
 * Returns changed conversation entry ids for the incremental projection fast path.
 * A null result requests a full conversation rebuild.
 */
internal fun incrementalConversationEntryIdsFor(
    events: Iterable<com.auracode.assistant.session.kernel.SessionDomainEvent>,
): Set<String>? {
    val changedIds = linkedSetOf<String>()
    events.forEach { event ->
        if (SessionProjectionSlice.CONVERSATION !in projectionSlicesFor(event)) return@forEach
        when (event) {
            is com.auracode.assistant.session.kernel.SessionDomainEvent.MessageAppended -> changedIds += event.messageId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.ReasoningUpdated -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.CommandUpdated -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.ToolUpdated -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.FileChangesUpdated -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.RunningPlanUpdated -> {
                changedIds += listOfNotNull(
                    "plan",
                    event.plan.turnId?.takeIf { it.isNotBlank() },
                ).joinToString(":")
            }
            is com.auracode.assistant.session.kernel.SessionDomainEvent.EngineSwitched -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.ContextCompactionUpdated -> changedIds += event.itemId
            is com.auracode.assistant.session.kernel.SessionDomainEvent.ErrorAppended -> {
                if (event.terminal) return null
                changedIds += event.itemId
            }
            is com.auracode.assistant.session.kernel.SessionDomainEvent.ThreadStarted -> Unit
            else -> return null
        }
    }
    return changedIds
}

/** Aggregates projection timing so diagnostics do not add one log write per provider event. */
internal class SessionUiProjectionPerformanceTracker(
    private val reportIntervalNanos: Long = 1_000_000_000L,
    private val nanoTime: () -> Long = System::nanoTime,
    private val report: (String) -> Unit,
) {
    private var windowStartedNanos = nanoTime()
    private var eventCount = 0L
    private var requestCount = 0L
    private var projectionCount = 0L
    private var projectionTotalNanos = 0L
    private var projectionMaxNanos = 0L
    private var queueMaxNanos = 0L

    @Synchronized
    fun record(batch: SessionUiFlushBatch, projectionNanos: Long) {
        val now = nanoTime()
        eventCount += batch.eventCount
        requestCount += batch.requestCount
        projectionCount += 1
        projectionTotalNanos += projectionNanos
        projectionMaxNanos = maxOf(projectionMaxNanos, projectionNanos)
        queueMaxNanos = maxOf(queueMaxNanos, now - batch.oldestRequestNanos)
        if (now - windowStartedNanos < reportIntervalNanos) return

        val averageProjectionMs = projectionTotalNanos.toDouble() / projectionCount.coerceAtLeast(1) / 1_000_000.0
        report(
            "Session UI projection performance: events=$eventCount requests=$requestCount " +
                "projections=$projectionCount coalesced=${(requestCount - projectionCount).coerceAtLeast(0)} " +
                "avgMs=${"%.2f".format(averageProjectionMs)} " +
                "maxMs=${"%.2f".format(projectionMaxNanos / 1_000_000.0)} " +
                "maxQueueMs=${"%.2f".format(queueMaxNanos / 1_000_000.0)}",
        )
        windowStartedNanos = now
        eventCount = 0
        requestCount = 0
        projectionCount = 0
        projectionTotalNanos = 0
        projectionMaxNanos = 0
        queueMaxNanos = 0
    }
}
