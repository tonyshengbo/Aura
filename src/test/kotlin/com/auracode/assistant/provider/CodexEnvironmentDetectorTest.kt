package com.auracode.assistant.provider

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexEnvironmentDetectorTest {
    @Test
    fun `auto detect finds codex and node from shell path`() {
        val binDir = Files.createTempDirectory("codex-env-bin")
        val codex = Files.createFile(binDir.resolve("codex")).toFile().apply { setExecutable(true) }
        val node = Files.createFile(binDir.resolve("node")).toFile().apply { setExecutable(true) }
        val detector = CodexEnvironmentDetector(
            shellEnvironmentLoader = {
                mapOf(
                    "PATH" to binDir.toString(),
                )
            },
        )

        val result = detector.autoDetect(
            configuredCodexPath = "",
            configuredNodePath = "",
        )

        assertEquals(codex.absolutePath, result.codexPath)
        assertEquals(node.absolutePath, result.nodePath)
        assertEquals(CodexEnvironmentStatus.DETECTED, result.codexStatus)
        assertEquals(CodexEnvironmentStatus.DETECTED, result.nodeStatus)
    }

    @Test
    fun `configured missing node path fails immediately`() {
        val detector = CodexEnvironmentDetector(
            shellEnvironmentLoader = { emptyMap() },
        )

        val result = detector.autoDetect(
            configuredCodexPath = "",
            configuredNodePath = "/definitely/missing/node",
        )

        assertEquals(CodexEnvironmentStatus.FAILED, result.nodeStatus)
    }

    @Test
    fun `launch resolution prepends explicit node parent directory`() {
        val binDir = Files.createTempDirectory("codex-launch-bin")
        val node = Files.createFile(binDir.resolve("node")).toFile().apply { setExecutable(true) }
        val detector = CodexEnvironmentDetector(
            shellEnvironmentLoader = {
                mapOf(
                    "PATH" to "/usr/bin:/bin",
                    "HOME" to "/Users/test",
                )
            },
        )

        val resolution = detector.resolveForLaunch(
            configuredCodexPath = "codex",
            configuredNodePath = node.absolutePath,
        )

        assertEquals("${binDir}:/usr/bin:/bin", resolution.environmentOverrides["PATH"])
        assertEquals("/Users/test", resolution.environmentOverrides["HOME"])
    }
}
