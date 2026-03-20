package com.auracode.assistant.settings.skills

import com.auracode.assistant.settings.AgentSettingsService
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSkillCatalogTest {
    @Test
    fun `list skills resolves effective source by configured priority and disabled state`() {
        val home = createTempDirectory("skills-home")
        val settings = AgentSettingsService().apply {
            loadState(
                AgentSettingsService.State(
                    disabledSkillNames = linkedSetOf("brainstorming"),
                ),
            )
        }
        createSkill(home, ".codex/skills/brainstorming", "brainstorming", "local")
        createSkill(home, ".codex/superpowers/skills/brainstorming", "brainstorming", "superpowers")
        createSkill(home, ".agents/skills/systematic-debugging", "systematic-debugging", "agents")

        val catalog = LocalSkillCatalog(settings = settings, homeDir = home)

        val skills = catalog.listSkills()

        assertEquals(2, skills.size)
        assertEquals("brainstorming", skills[0].name)
        assertEquals(SkillSource.LOCAL, skills[0].effectiveSource)
        assertEquals(2, skills[0].sourceCount)
        assertFalse(skills[0].enabled)
        assertEquals("systematic-debugging", skills[1].name)
        assertEquals(SkillSource.AGENTS, skills[1].effectiveSource)
        assertTrue(skills[1].enabled)
    }

    @Test
    fun `importing directory copies skill into managed local root and rejects duplicates`() {
        val home = createTempDirectory("skills-import-home")
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        val importDir = createTempDirectory("skills-import-src")
        createSkill(importDir, "my-skill", "my-skill", "from import")
        val sourcePath = importDir.resolve("my-skill")

        val catalog = LocalSkillCatalog(settings = settings, homeDir = home)

        val first = catalog.importSkill(sourcePath.toString())
        val second = catalog.importSkill(sourcePath.toString())

        assertTrue(first.success)
        assertTrue(home.resolve(".codex/skills/my-skill/SKILL.md").toFile().exists())
        assertFalse(second.success)
    }

    @Test
    fun `deleting local skill falls back to lower priority source`() {
        val home = createTempDirectory("skills-delete-home")
        val settings = AgentSettingsService().apply { loadState(AgentSettingsService.State()) }
        createSkill(home, ".codex/skills/brainstorming", "brainstorming", "local")
        createSkill(home, ".codex/superpowers/skills/brainstorming", "brainstorming", "superpowers")

        val catalog = LocalSkillCatalog(settings = settings, homeDir = home)

        assertTrue(catalog.deleteSkill("brainstorming"))
        val remaining = catalog.listSkills().single()

        assertEquals(SkillSource.SUPERPOWERS, remaining.effectiveSource)
        assertFalse(remaining.deletable)
    }

    private fun createSkill(root: java.nio.file.Path, relativeDir: String, name: String, description: String) {
        val dir = root.resolve(relativeDir)
        dir.createDirectories()
        dir.resolve("SKILL.md").writeText(
            """
            ---
            name: $name
            description: "$description"
            ---
            """.trimIndent(),
        )
    }
}
