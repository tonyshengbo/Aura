package com.auracode.assistant.provider.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface ClaudeStreamJsonSession {
    suspend fun collect(
        onStdoutLine: suspend (String) -> Unit,
        onStderrLine: suspend (String) -> Unit,
    ): Int

    /** Writes one complete user input or control response; failures must reach the caller. */
    suspend fun writeStdin(line: String)

    suspend fun closeInput() {}

    fun cancel()
}

internal class ProcessClaudeStreamJsonSession(
    private val process: Process,
) : ClaudeStreamJsonSession {
    private val stdinWriter = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val writerMutex = Mutex()

    override suspend fun collect(
        onStdoutLine: suspend (String) -> Unit,
        onStderrLine: suspend (String) -> Unit,
    ): Int = coroutineScope {
        val stderrJob = launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        onStderrLine(line)
                    }
                }
            }
        }
        withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        onStdoutLine(line)
                    }
                }
            }
        }
        val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
        stderrJob.join()
        exitCode
    }

    /** Serializes live input and approval responses so JSON lines cannot interleave. */
    override suspend fun writeStdin(line: String) {
        withContext(Dispatchers.IO) {
            writerMutex.withLock {
                stdinWriter.write(line)
                stdinWriter.newLine()
                stdinWriter.flush()
            }
        }
    }

    override suspend fun closeInput() = withContext(Dispatchers.IO) {
        writerMutex.withLock { stdinWriter.close() }
    }

    override fun cancel() {
        process.destroy()
        if (process.isAlive) {
            process.destroyForcibly()
        }
    }
}
