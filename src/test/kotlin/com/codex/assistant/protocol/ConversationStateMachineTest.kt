package com.codex.assistant.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversationStateMachineTest {
    @Test
    fun `reduces thread turn and item lifecycle`() {
        val machine = ConversationStateMachine()

        machine.accept(UnifiedEvent.ThreadStarted(threadId = "th_1"))
        machine.accept(UnifiedEvent.TurnStarted(turnId = "tu_1", threadId = "th_1"))
        machine.accept(
            UnifiedEvent.ItemUpdated(
                item = UnifiedItem(
                    id = "it_1",
                    kind = ItemKind.TOOL_CALL,
                    status = ItemStatus.RUNNING,
                    name = "shell",
                    text = "./gradlew test",
                ),
            ),
        )
        machine.accept(
            UnifiedEvent.ItemUpdated(
                item = UnifiedItem(
                    id = "it_1",
                    kind = ItemKind.TOOL_CALL,
                    status = ItemStatus.SUCCESS,
                    name = "shell",
                    text = "BUILD SUCCESSFUL",
                ),
            ),
        )
        machine.accept(
            UnifiedEvent.TurnCompleted(
                turnId = "tu_1",
                outcome = TurnOutcome.SUCCESS,
                usage = TurnUsage(
                    inputTokens = 90,
                    cachedInputTokens = 30,
                    outputTokens = 12,
                ),
            ),
        )

        val state = machine.state
        assertEquals("th_1", state.threadId)
        assertFalse(state.isRunning)
        assertEquals(1, state.turns.size)
        assertEquals(TurnOutcome.SUCCESS, state.turns.single().outcome)
        assertNotNull(state.turns.single().usage)
        assertEquals(ItemStatus.SUCCESS, state.turns.single().items.single().status)
    }

    @Test
    fun `tracks plan update and approval nodes as first class items`() {
        val machine = ConversationStateMachine()
        machine.accept(UnifiedEvent.ThreadStarted(threadId = "th_2"))
        machine.accept(UnifiedEvent.TurnStarted(turnId = "tu_2", threadId = "th_2"))
        machine.accept(
            UnifiedEvent.ItemUpdated(
                item = UnifiedItem(
                    id = "it_plan",
                    kind = ItemKind.PLAN_UPDATE,
                    status = ItemStatus.RUNNING,
                    text = "Plan: introduce protocol layer",
                ),
            ),
        )
        machine.accept(
            UnifiedEvent.ItemUpdated(
                item = UnifiedItem(
                    id = "it_approval",
                    kind = ItemKind.APPROVAL_REQUEST,
                    status = ItemStatus.RUNNING,
                    approvalDecision = ApprovalDecision.PENDING,
                    text = "./gradlew build",
                ),
            ),
        )

        val state = machine.state
        val turn = state.turns.single()
        assertTrue(turn.items.any { it.kind == ItemKind.PLAN_UPDATE })
        assertTrue(
            turn.items.any {
                it.kind == ItemKind.APPROVAL_REQUEST && it.approvalDecision == ApprovalDecision.PENDING
            },
        )
    }
}
