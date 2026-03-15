package com.codex.assistant.protocol

import com.codex.assistant.model.EngineEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineEventBridgeTest {
    @Test
    fun `maps command proposal to command exec item`() {
        val event = EngineEventBridge.map(
            EngineEvent.CommandProposal(command = "./gradlew test", cwd = "."),
        )

        val item = assertIs<UnifiedEvent.ItemUpdated>(event).item
        assertEquals(ItemKind.COMMAND_EXEC, item.kind)
        assertEquals("./gradlew test", item.command)
        assertEquals(".", item.cwd)
        assertEquals(ItemStatus.RUNNING, item.status)
    }

    @Test
    fun `maps turn usage to successful turn completion`() {
        val event = EngineEventBridge.map(
            EngineEvent.TurnUsage(
                inputTokens = 100,
                cachedInputTokens = 40,
                outputTokens = 20,
            ),
        )

        val completed = assertIs<UnifiedEvent.TurnCompleted>(event)
        assertEquals(TurnOutcome.SUCCESS, completed.outcome)
        assertEquals(100, completed.usage?.inputTokens)
        assertEquals(40, completed.usage?.cachedInputTokens)
        assertEquals(20, completed.usage?.outputTokens)
    }
}
