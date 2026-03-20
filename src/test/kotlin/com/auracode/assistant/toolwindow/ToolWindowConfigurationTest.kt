package com.auracode.assistant.toolwindow

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolWindowConfigurationTest {
    @Test
    fun `plugin registers only aura code tool window`() {
        val pluginXml = Files.readString(Path.of("src/main/resources/META-INF/plugin.xml"))

        assertTrue(pluginXml.contains("""id="Aura Code""""))
        assertFalse(pluginXml.contains("""id="Codex Chat""""))
    }
}
