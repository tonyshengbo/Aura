package com.auracode.assistant.settings.skills

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalSkillInstallPolicyTest {
    @Test
    fun `managed Aura Code skill is uninstallable by SKILL md path`() {
        val home = createTempDirectory("skill-install-policy-home")
        val skillFile = createSkill(home, ".codex/skills/brainstorming")
        val policy = LocalSkillInstallPolicy(homeDir = home)

        val resolved = policy.resolveInstalledSkillDir(skillFile.toString())

        assertNotNull(resolved)
        assertEquals(home.resolve(".codex/skills/brainstorming"), resolved)
        assertTrue(policy.canUninstall(skillFile.toString()))
    }

    @Test
    fun `local agent skill is uninstallable by skill directory path`() {
        val home = createTempDirectory("skill-install-policy-agents")
        val skillFile = createSkill(home, ".agents/skills/reviewer")
        val policy = LocalSkillInstallPolicy(homeDir = home)

        val resolved = policy.resolveInstalledSkillDir(skillFile.parent.toString())

        assertNotNull(resolved)
        assertEquals(skillFile.parent, resolved)
        assertTrue(policy.canUninstall(skillFile.parent.toString()))
    }

    @Test
    fun `superpowers skill is not uninstallable`() {
        val home = createTempDirectory("skill-install-policy-superpowers")
        val skillFile = createSkill(home, ".codex/superpowers/skills/brainstorming")
        val policy = LocalSkillInstallPolicy(homeDir = home)

        assertNull(policy.resolveInstalledSkillDir(skillFile.toString()))
        assertFalse(policy.canUninstall(skillFile.toString()))
    }

    private fun createSkill(root: java.nio.file.Path, relativeDir: String): java.nio.file.Path {
        val dir = root.resolve(relativeDir)
        dir.createDirectories()
        val skillFile = dir.resolve("SKILL.md")
        skillFile.writeText(
            """
            ---
            name: ${dir.fileName}
            description: "test skill"
            ---
            """.trimIndent(),
        )
        return skillFile
    }
}
