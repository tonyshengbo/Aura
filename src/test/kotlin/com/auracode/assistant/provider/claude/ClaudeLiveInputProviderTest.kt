package com.auracode.assistant.provider.claude

import com.auracode.assistant.model.AgentRequest
import com.auracode.assistant.provider.input.LiveInput
import com.auracode.assistant.provider.input.LiveInputResult
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.settings.AgentSettingsService
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClaudeLiveInputProviderTest {
    @Test
    fun `intermediate result does not complete or cancel a run with accepted follow-ups`() = runBlocking {
        withTimeout(5_000) {
            val wire = ControlledSession()
            val provider = ClaudeCliProvider(
                settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) },
                launcher = object : ClaudeCliLauncher {
                    override fun start(request: AgentRequest, settings: AgentSettingsService) = wire
                },
                diagnosticLogger = {},
            )
            val request = AgentRequest(engineId = "claude", prompt = "first", contextFiles = emptyList(), workingDirectory = ".")
            val events = CopyOnWriteArrayList<SessionDomainEvent>()
            val job = launch { provider.stream(request).toList(events) }
            wire.ready.await()
            assertIs<LiveInputResult.Accepted>(provider.submitLiveInput(request.requestId, LiveInput("second")))
            assertIs<LiveInputResult.Accepted>(provider.submitLiveInput(request.requestId, LiveInput("third")))
            wire.emit("""{"type":"result","subtype":"success","session_id":"session-1","result":"first response","is_error":false}""")
            assertFalse(wire.closed)
            assertEquals(0, wire.cancelCount)
            assertTrue(events.none { it is SessionDomainEvent.TurnCompleted })
            wire.writes.forEach { line ->
                val uuid = Json.parseToJsonElement(line).jsonObject.getValue("uuid").jsonPrimitive.content
                wire.emit("""{"type":"user","uuid":"$uuid","message":{"role":"user","content":[]}}""")
            }
            wire.emit("""{"type":"result","subtype":"success","session_id":"session-1","result":"combined response","is_error":false}""")
            job.join()
            assertTrue(wire.closed)
            assertEquals(1, events.filterIsInstance<SessionDomainEvent.TurnCompleted>().size)
        }
    }

    private class ControlledSession : ClaudeStreamJsonSession {
        val ready = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        private val input = Channel<String>(Channel.UNLIMITED)
        private val processed = Channel<Unit>(Channel.UNLIMITED)
        var closed = false
        var cancelCount = 0

        suspend fun emit(line: String) {
            input.send(line)
            processed.receive()
        }

        override suspend fun collect(onStdoutLine: suspend (String) -> Unit, onStderrLine: suspend (String) -> Unit): Int {
            ready.complete(Unit)
            for (line in input) {
                onStdoutLine(line)
                processed.send(Unit)
            }
            return 0
        }

        override suspend fun writeStdin(line: String) { writes += line }
        override suspend fun closeInput() { closed = true; input.close() }
        override fun cancel() { cancelCount++; input.close() }
    }
}
