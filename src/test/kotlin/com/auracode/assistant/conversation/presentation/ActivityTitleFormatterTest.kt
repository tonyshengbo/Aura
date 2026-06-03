package com.auracode.assistant.conversation.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityTitleFormatterTest {
    @Test
    fun `formats sed print command as read`() {
        assertEquals(
            "Read Timeline.kt",
            ActivityTitleFormatter.commandTitle(
                command = "sed -n '1,120p' src/timeline/Timeline.kt",
            ),
        )
    }

    @Test
    fun `formats quoted read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "cat '/Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md'",
            ),
        )
    }

    @Test
    fun `formats unquoted claude read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "cat /Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `does not merge multiple absolute read paths`() {
        assertEquals(
            "Read a.txt",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/a.txt /tmp/b.txt",
            ),
        )
    }

    @Test
    fun `does not merge absolute and relative read paths`() {
        assertEquals(
            "Read a.txt",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/a.txt b.txt",
            ),
        )
    }

    @Test
    fun `does not merge absolute and nested relative read paths`() {
        assertEquals(
            "Read a.txt",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/a.txt src/b.txt",
            ),
        )
    }

    @Test
    fun `does not merge extensionless absolute read path with nested relative path`() {
        assertEquals(
            "Read LICENSE",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/LICENSE src/b.txt",
            ),
        )
    }

    @Test
    fun `does not merge readme absolute read path with relative path`() {
        assertEquals(
            "Read README",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/README b.txt",
            ),
        )
    }

    @Test
    fun `does not merge makefile absolute read path with relative path`() {
        assertEquals(
            "Read Makefile",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/Makefile src/build.txt",
            ),
        )
    }

    @Test
    fun `does not merge dockerfile absolute read path with relative path`() {
        assertEquals(
            "Read Dockerfile",
            ActivityTitleFormatter.commandTitle(
                command = "cat /tmp/Dockerfile src/build.txt",
            ),
        )
    }

    @Test
    fun `does not merge multiple relative read paths`() {
        assertEquals(
            "Read a.txt",
            ActivityTitleFormatter.commandTitle(
                command = "cat src/a.txt src/b.txt",
            ),
        )
    }

    @Test
    fun `formats escaped read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "cat /Users/tonysheng/Documents/New\\ project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `formats type read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "type /Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `formats head read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "head -n 20 /Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `formats tail read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "tail -n 20 /Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `formats numbered read path with spaces`() {
        assertEquals(
            "Read review-code.md",
            ActivityTitleFormatter.commandTitle(
                command = "nl /Users/tonysheng/Documents/New project/cloudview-spec/skills/bugfix-workflow/bindings/review-code.md",
            ),
        )
    }

    @Test
    fun `does not treat ripgrep pattern alternation as read pipeline`() {
        assertEquals(
            "Search files",
            ActivityTitleFormatter.commandTitle(
                command = """/bin/zsh -lc 'rg -n '"'"'quote|escape|shell.*arg|cat \"\${'$'}filePath.takeIf'"'"' src/main/kotlin src/test/kotlin'""",
            ),
        )
    }

    @Test
    fun `formats real read pipeline as read`() {
        assertEquals(
            "Read Timeline.kt",
            ActivityTitleFormatter.commandTitle(
                command = "cat src/timeline/Timeline.kt | rg ActivityTitleFormatter",
            ),
        )
    }

    @Test
    fun `keeps ripgrep pipeline title on search command`() {
        assertEquals(
            "Search files",
            ActivityTitleFormatter.commandTitle(
                command = "rg ActivityTitleFormatter src/main/kotlin | head -20",
            ),
        )
    }

    @Test
    fun `keeps ripgrep pipeline title when output is counted`() {
        assertEquals(
            "Search files",
            ActivityTitleFormatter.commandTitle(
                command = "rg ActivityTitleFormatter src/main/kotlin | wc -l",
            ),
        )
    }

    @Test
    fun `formats sed in-place command as edit`() {
        assertEquals(
            "Edit Timeline.kt",
            ActivityTitleFormatter.commandTitle(
                command = "sed -i '' 's/old/new/g' src/timeline/Timeline.kt",
            ),
        )
    }

    @Test
    fun `formats updated file change titles as edited`() {
        assertEquals(
            "Edited Timeline.kt",
            ActivityTitleFormatter.fileChangeTitle(
                changes = listOf(
                    ActivityTitleFormatter.FileChangeSummary(
                        path = "src/timeline/Timeline.kt",
                        kind = "updated",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `formats plural updated file changes as edited`() {
        assertEquals(
            "Edited 2 files",
            ActivityTitleFormatter.fileChangeTitle(
                changes = listOf(
                    ActivityTitleFormatter.FileChangeSummary(
                        path = "src/timeline/Timeline.kt",
                        kind = "updated",
                    ),
                    ActivityTitleFormatter.FileChangeSummary(
                        path = "src/timeline/TimelineRow.kt",
                        kind = "updated",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `formats mcp tool title from structured tool name`() {
        assertEquals(
            "Call MCP · cloudview-gray · get_figma_node",
            ActivityTitleFormatter.toolTitle(
                explicitName = "mcp:cloudview-gray",
                body = """
                    - Server: `cloudview-gray`
                    - Tool: `get_figma_node`
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `extracts mcp server name only from valid mcp tool names`() {
        assertEquals("cloudview-gray", ActivityTitleFormatter.mcpServerName("mcp:cloudview-gray"))
        assertNull(ActivityTitleFormatter.mcpServerName("Tool Call"))
        assertNull(ActivityTitleFormatter.mcpServerName("mcp:"))
    }
}
