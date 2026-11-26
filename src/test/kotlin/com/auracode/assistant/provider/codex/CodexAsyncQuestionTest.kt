package com.auracode.assistant.provider.codex

import com.auracode.assistant.protocol.ProviderEvent
import com.auracode.assistant.provider.session.ProviderProtocolDomainMapper
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.session.kernel.SessionRunStatus
import com.auracode.assistant.session.kernel.SessionState
import com.auracode.assistant.session.kernel.SessionStateReducer
import com.auracode.assistant.session.projection.execution.ExecutionUiProjectionBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CodexAsyncQuestionTest {
    private val item = """{"type":"agentMessage","id":"call-question","delivery":"async","phase":"final_answer","text":"Which kind?","questions":[{"title":"Which kind of pending?","options":["During a run","While idle"]},{"title":"Anything else?"}]}"""

    @Test
    fun `async questions suppress narrative deltas and emit one structured request`() {
        val parser = CodexRuntimeProvider.CodexRuntimeNotificationParser("request-1", {})
        parser.parseNotification("turn/started", obj("""{"threadId":"thread-1","turn":{"id":"turn-1"}}"""))
        assertTrue(parser.parseNotification("item/started", obj("""{"item":$item}""")).isEmpty())
        assertTrue(parser.parseNotification("item/agentMessage/delta", obj("""{"itemId":"call-question","delta":"Which kind?"}""")).isEmpty())
        val events = parser.parseNotification("item/completed", obj("""{"item":$item}"""))
        val requested = assertIs<ProviderEvent.ToolUserInputRequested>(events.single())
        assertEquals("call-question", requested.prompt.requestId)
        assertEquals("thread-1", requested.prompt.threadId)
        assertEquals(2, requested.prompt.questions.size)
        assertEquals(listOf("During a run", "While idle"), requested.prompt.questions.first().options.map { it.label })
        assertTrue(requested.prompt.questions.all { it.isOther })
        assertTrue(parser.parseNotification("item/completed", obj("""{"item":$item}""")).isEmpty())
        val domain = assertIs<SessionDomainEvent.ToolUserInputRequested>(ProviderProtocolDomainMapper().map(events.single()).single())
        assertEquals(SessionInputDelivery.ASYNC, domain.request.delivery)
    }

    @Test
    fun `historical async questions survive completion without marking an idle turn as running`() {
        val parser = CodexRuntimeProvider.CodexRuntimeNotificationParser("history:codex", {})
        val mapper = ProviderProtocolDomainMapper()
        val events = parser.parseHistoricalTurn(obj("""{"id":"turn-1","threadId":"thread-1","items":[$item],"status":"completed"}"""))
            .flatMap(mapper::map)
        val state = SessionStateReducer().reduceAll(SessionState.empty("session-1", "codex"), events)
        assertEquals(SessionRunStatus.IDLE, state.runtime.runStatus)
        assertEquals("call-question", state.execution.toolUserInputs.values.single().requestId)
        val projection = ExecutionUiProjectionBuilder().project(state)
        assertEquals(SessionInputDelivery.ASYNC, projection.toolUserInputs.single().delivery)
        assertEquals(null, projection.turnStatus)
    }

    @Test
    fun `ordinary chat questions remain ordinary messages`() {
        val parser = CodexRuntimeProvider.CodexRuntimeNotificationParser("request-1", {})
        val events = parser.parseNotification("item/completed", obj("""{"item":{"type":"agentMessage","id":"message-1","text":"Which option?"}}"""))
        assertIs<ProviderEvent.ItemUpdated>(events.single())
    }

    private fun obj(value: String) = Json.parseToJsonElement(value).jsonObject
}
