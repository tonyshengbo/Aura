package com.auracode.assistant.service.input

import com.auracode.assistant.persistence.input.AsyncQuestionRepository
import com.auracode.assistant.persistence.input.PersistedAsyncQuestion
import com.auracode.assistant.session.kernel.SessionActivityStatus
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.session.kernel.SessionToolUserInputRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Merges local answers with stable native question IDs, across replay pagination and IDE restarts. */
internal class AsyncQuestionHistory(private val repository: AsyncQuestionRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    fun remember(sessionId: String, threadId: String, request: SessionToolUserInputRequest) {
        if (request.delivery != SessionInputDelivery.ASYNC || threadId.isBlank()) return
        val normalized = request.copy(threadId = threadId)
        repository.saveAsyncQuestion(PersistedAsyncQuestion(
            sessionId, threadId, request.requestId, json.encodeToString(normalized),
        ))
    }

    fun resolve(
        sessionId: String,
        threadId: String,
        requestId: String,
        status: SessionActivityStatus,
        summary: String,
    ) {
        val question = repository.loadAsyncQuestions(sessionId, threadId).firstOrNull { it.requestId == requestId }
            ?: error("The question is no longer available.")
        repository.saveAsyncQuestion(question.copy(status = status.name, responseSummary = summary))
    }

    fun replay(
        sessionId: String,
        threadId: String,
        events: List<SessionDomainEvent>,
        includeMissing: Boolean,
    ): List<SessionDomainEvent> {
        events.filterIsInstance<SessionDomainEvent.ToolUserInputRequested>().forEach { remember(sessionId, threadId, it.request) }
        val stored = repository.loadAsyncQuestions(sessionId, threadId).associateBy { it.requestId }
        val seen = mutableSetOf<String>()
        return buildList {
            events.forEach { event ->
                if (event is SessionDomainEvent.ToolUserInputRequested && event.request.delivery == SessionInputDelivery.ASYNC) {
                    seen.add(event.request.requestId)
                    add(event.copy(request = event.request.copy(threadId = threadId)))
                    stored[event.request.requestId]?.resolution()?.let(::add)
                } else add(event)
            }
            // Unanswered questions stay available even when their turn falls outside the latest page.
            if (includeMissing) stored.values.filter { it.status == null && it.requestId !in seen }.forEach { record ->
                val request = runCatching { json.decodeFromString<SessionToolUserInputRequest>(record.requestJson) }.getOrNull()
                if (request != null) add(SessionDomainEvent.ToolUserInputRequested(request))
            }
        }
    }

    private fun PersistedAsyncQuestion.resolution(): SessionDomainEvent.ToolUserInputResolved? {
        val resolved = status?.let { runCatching { SessionActivityStatus.valueOf(it) }.getOrNull() } ?: return null
        return SessionDomainEvent.ToolUserInputResolved(requestId, resolved, responseSummary)
    }
}
