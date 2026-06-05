package com.auracode.assistant.toolwindow.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationFileTargetTest {
    @Test
    fun `parses plain absolute path without line target`() {
        assertEquals(
            ConversationFileTarget(
                path = "/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt",
            ),
            parseConversationFileTarget("/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt"),
        )
    }

    @Test
    fun `preserves local path characters that are valid outside markdown targets`() {
        assertEquals(
            ConversationFileTarget(
                path = "/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main#draft?.kt",
            ),
            parseConversationFileTarget("/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main#draft?.kt"),
        )
    }

    @Test
    fun `parses absolute path with line and column target`() {
        assertEquals(
            ConversationFileTarget(
                path = "/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt",
                line = 42,
            ),
            parseConversationFileTarget("/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt:42"),
        )
        assertEquals(
            ConversationFileTarget(
                path = "/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt",
                line = 42,
                column = 7,
            ),
            parseConversationFileTarget("/Users/tonysheng/StudioProject/Aura/src/main/kotlin/Main.kt:42:7"),
        )
    }

    @Test
    fun `parses windows absolute path without treating drive colon as line target`() {
        assertEquals(
            ConversationFileTarget(path = "C:\\Users\\tony\\Aura\\Main.kt"),
            parseConversationFileTarget("C:\\Users\\tony\\Aura\\Main.kt"),
        )
        assertEquals(
            ConversationFileTarget(
                path = "C:\\Users\\tony\\Aura\\Main.kt",
                line = 42,
                column = 7,
            ),
            parseConversationFileTarget("C:\\Users\\tony\\Aura\\Main.kt:42:7"),
        )
    }

    @Test
    fun `rejects blank file target`() {
        assertNull(parseConversationFileTarget("   "))
    }

    @Test
    fun `rejects invalid line numbers 0`() {
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:0"),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:0"),
        )
    }

    @Test
    fun `rejects invalid column numbers 0 in line-column format`() {
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:42:0"),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:42:0"),
        )
    }

    @Test
    fun `rejects negative line numbers`() {
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:-1"),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:-1"),
        )
    }

    @Test
    fun `accepts large line numbers`() {
        assertEquals(
            ConversationFileTarget(
                path = "/Users/tonysheng/Main.kt",
                line = 100000000,
            ),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:100000000"),
        )
    }

    @Test
    fun `preserves extra colons in path portion when suffix target is valid`() {
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:", line = 42),
            parseConversationFileTarget("/Users/tonysheng/Main.kt::42"),
        )
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:42:", line = 7),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:42::7"),
        )
        assertEquals(
            ConversationFileTarget(path = "/Users/tonysheng/Main.kt:1", line = 2, column = 3),
            parseConversationFileTarget("/Users/tonysheng/Main.kt:1:2:3"),
        )
    }
}
