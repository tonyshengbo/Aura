package com.auracode.assistant.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexAppServerLaunchConfigTest {
    @Test
    fun `launch config only starts app server`() {
        val launch = buildCodexAppServerLaunchConfig(
            binary = "/usr/local/bin/codex",
        )

        assertEquals(listOf("/usr/local/bin/codex", "app-server"), launch.command)
        assertTrue(launch.environmentOverrides.isEmpty())
    }

    @Test
    fun `launch config keeps explicit environment overrides`() {
        val launch = buildCodexAppServerLaunchConfig(
            binary = "codex",
            environmentOverrides = mapOf("PATH" to "/opt/homebrew/bin:/usr/bin"),
        )

        assertEquals(listOf("codex", "app-server"), launch.command)
        assertEquals("/opt/homebrew/bin:/usr/bin", launch.environmentOverrides["PATH"])
    }

    @Test
    fun `launch environment prepends explicit node directory to shell path`() {
        val overrides = buildLaunchEnvironmentOverrides(
            shellEnvironment = mapOf("PATH" to "/usr/bin:/bin", "HOME" to "/Users/test"),
            nodePath = "/opt/homebrew/bin/node",
        )

        assertEquals("/opt/homebrew/bin:/usr/bin:/bin", overrides["PATH"])
        assertEquals("/Users/test", overrides["HOME"])
    }
}
