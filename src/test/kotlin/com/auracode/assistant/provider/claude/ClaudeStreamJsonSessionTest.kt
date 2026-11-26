package com.auracode.assistant.provider.claude

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ClaudeStreamJsonSessionTest {
    @Test
    fun `concurrent user and control writes remain complete lines`() = runBlocking {
        val output = ByteArrayOutputStream()
        val session = ProcessClaudeStreamJsonSession(OutputProcess(output))
        val messages = (1..80).map { "{\"id\":$it,\"text\":\"${"x".repeat(4096)}\"}" }
        coroutineScope {
            messages.forEach { message -> launch(Dispatchers.Default) { session.writeStdin(message) } }
        }
        assertEquals(messages.toSet(), output.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).toSet())
    }

    @Test
    fun `a broken stdin pipe is reported to the sender`() = runBlocking {
        val session = ProcessClaudeStreamJsonSession(OutputProcess(object : OutputStream() {
            override fun write(value: Int) { throw IOException("broken pipe") }
        }))
        assertFailsWith<IOException> { session.writeStdin("{}") }
        Unit
    }

    private class OutputProcess(private val output: OutputStream) : Process() {
        override fun getOutputStream() = output
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun destroy() = Unit
    }
}
