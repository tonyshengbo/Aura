package com.auracode.assistant.provider.codex

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject

/**
 * Verifies that app-server notifications preserve wire order while being dispatched to higher layers.
 */
class CodexRuntimeSessionTest {
    @Test
    fun `EOF releases a live request waiting for its acknowledgement`() = runBlocking {
        val session = CodexProcessRuntimeSession(ScriptedProcess(emptyList()), {})
        val request = async {
            assertFailsWith<EOFException> { session.request("turn/steer", buildJsonObject {}) }
        }
        // Let the request register before the finite scripted stdout reaches EOF.
        delay(20)
        session.start()
        withTimeout(2_000) { request.await() }
        session.close()
    }


    /**
     * Ensures later notifications cannot overtake earlier ones when the first handler suspends.
     */
    @Test
    fun `notifications are dispatched in receive order`() = runBlocking {
        val notificationOrder = Collections.synchronizedList(mutableListOf<String>())
        val completed = CompletableDeferred<Unit>()
        val process = ScriptedProcess(
            lines = listOf(
                """{"method":"turn/started","params":{"threadId":"thread-1","turn":{"id":"turn-1","status":"inProgress"}}}""",
                """{"method":"turn/completed","params":{"threadId":"thread-1","turn":{"id":"turn-1","status":"completed"}}}""",
            ),
        )
        val session = CodexProcessRuntimeSession(
            process = process,
            diagnosticLogger = {},
            onNotification = { method, _ ->
                if (method == "turn/started") {
                    delay(100)
                }
                notificationOrder += method
                if (notificationOrder.size == 2 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            },
        )

        session.start()

        withTimeout(2_000) {
            completed.await()
        }

        assertEquals(
            listOf("turn/started", "turn/completed"),
            notificationOrder,
        )
    }
}

/**
 * Provides a fixed JSONL input stream for session dispatch tests without spawning a real process.
 */
private class ScriptedProcess(
    lines: List<String>,
) : Process() {
    private val input = ByteArrayInputStream(lines.joinToString(separator = "\n", postfix = "\n").toByteArray())
    private val empty = ByteArrayInputStream(ByteArray(0))
    private val output = ByteArrayOutputStream()

    override fun getOutputStream() = output

    override fun getInputStream() = input

    override fun getErrorStream() = empty

    override fun waitFor(): Int = 0

    override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = true

    override fun exitValue(): Int = 0

    override fun destroy() = Unit

    override fun destroyForcibly(): Process = this

    override fun isAlive(): Boolean = false
}
