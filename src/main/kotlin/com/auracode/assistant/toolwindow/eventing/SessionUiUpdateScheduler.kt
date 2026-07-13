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
)

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
        val job: Job?,
    )

    private val lock = Any()
    private val pendingBySessionId = mutableMapOf<String, PendingUpdate>()
    private var nextGeneration = 0L

    fun request(sessionId: String, eventCount: Int, immediate: Boolean) {
        require(eventCount > 0) { "eventCount must be positive" }
        if (immediate || intervalMs <= 0L) {
            flushNow(sessionId, eventCount)
            return
        }

        synchronized(lock) {
            val current = pendingBySessionId[sessionId]
            if (current != null) {
                pendingBySessionId[sessionId] = current.copy(
                    eventCount = current.eventCount + eventCount,
                    requestCount = current.requestCount + 1,
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
                job = job,
            )
        }
    }

    fun flush(sessionId: String) {
        flushNow(sessionId, additionalEventCount = 0)
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

    private fun flushNow(sessionId: String, additionalEventCount: Int) {
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
                )
            }
        }
        batch?.let { onFlush(sessionId, it) }
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS = 32L
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
