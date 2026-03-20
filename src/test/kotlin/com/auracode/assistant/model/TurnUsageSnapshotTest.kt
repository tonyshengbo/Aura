package com.auracode.assistant.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TurnUsageSnapshotTest {
    @Test
    fun `used percent returns rounded bounded percentage of used context`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 100,
            inputTokens = 46,
            cachedInputTokens = 10,
            outputTokens = 24,
        )

        assertEquals(70, snapshot.usedPercent())
    }

    @Test
    fun `used percent returns null when context window is not positive`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 0,
            inputTokens = 10,
            cachedInputTokens = 0,
            outputTokens = 5,
        )

        assertNull(snapshot.usedPercent())
    }

    @Test
    fun `used percent wraps by context window when usage exceeds context window`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 100,
            inputTokens = 90,
            cachedInputTokens = 0,
            outputTokens = 35,
        )

        assertEquals(25, snapshot.usedPercent())
    }

    @Test
    fun `used fraction returns wrapped ratio for progress rendering`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 200,
            inputTokens = 60,
            cachedInputTokens = 10,
            outputTokens = 40,
        )

        assertEquals(0.5f, snapshot.usedFraction())
    }

    @Test
    fun `used fraction wraps when total usage exceeds context window`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 100,
            inputTokens = 180,
            cachedInputTokens = 0,
            outputTokens = 45,
        )

        assertEquals(0.25f, snapshot.usedFraction())
    }

    @Test
    fun `context usage tooltip shows wrapped usage against context window`() {
        val snapshot = TurnUsageSnapshot(
            model = "gpt-5.4",
            contextWindow = 200_000,
            inputTokens = 260_000,
            cachedInputTokens = 12_000,
            outputTokens = 10_000,
        )

        val tooltip = snapshot.contextUsageTooltipText()

        assertTrue(tooltip.contains("Used 35%"))
        assertTrue(tooltip.contains("70,000 / 200,000 tokens"))
        assertTrue(tooltip.contains("Input 260,000"))
        assertTrue(tooltip.contains("Output 10,000"))
        assertTrue(tooltip.contains("Cached 12,000"))
        assertTrue(tooltip.contains("Model gpt-5.4"))
        assertTrue(!tooltip.contains("No completed turn yet"))
    }
}
