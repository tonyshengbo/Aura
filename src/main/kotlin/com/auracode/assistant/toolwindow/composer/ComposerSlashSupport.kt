package com.auracode.assistant.toolwindow.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.eventing.ComposerMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal data class SlashQueryMatch(
    val query: String,
    val start: Int,
    val end: Int,
)

internal data class SlashSkillDescriptor(
    val name: String,
    val description: String,
)

internal sealed interface SlashSuggestionItem {
    val title: String
    val description: String

    data class Command(
        val command: String,
        override val title: String,
        override val description: String,
        val enabled: Boolean = true,
    ) : SlashSuggestionItem

    data class Skill(
        val name: String,
        override val description: String,
    ) : SlashSuggestionItem {
        override val title: String = "\$$name"
    }
}

/**
 * Centralizes slash command metadata so the store can stay focused on state transitions
 * while command titles, enablement, and descriptions remain easy to evolve.
 */
internal enum class ComposerSlashCommand(
    val token: String,
) {
    PLAN("/plan"),
    AUTO("/auto"),
    NEW("/new"),
}

internal fun normalizeSlashCommand(command: String): String = command.trim().removePrefix("/")

internal fun buildSlashCommandSuggestions(
    query: String,
    state: ComposerAreaState,
): List<SlashSuggestionItem.Command> {
    val normalizedQuery = query.trim()
    return ComposerSlashCommand.entries
        .map { command -> command.toSuggestion(state) }
        .filter { suggestion ->
            normalizedQuery.isBlank() || suggestion.command.removePrefix("/").contains(normalizedQuery, ignoreCase = true)
        }
}

private fun ComposerSlashCommand.toSuggestion(state: ComposerAreaState): SlashSuggestionItem.Command {
    return when (this) {
        ComposerSlashCommand.PLAN -> SlashSuggestionItem.Command(
            command = token,
            title = token,
            description = if (state.planEnabled) {
                AuraCodeBundle.message("composer.slash.plan.disable")
            } else {
                AuraCodeBundle.message("composer.slash.plan.enable")
            },
            enabled = state.planModeAvailable,
        )
        ComposerSlashCommand.AUTO -> SlashSuggestionItem.Command(
            command = token,
            title = token,
            description = if (state.executionMode == ComposerMode.AUTO) {
                AuraCodeBundle.message("composer.slash.auto.disable")
            } else {
                AuraCodeBundle.message("composer.slash.auto.enable")
            },
        )
        ComposerSlashCommand.NEW -> SlashSuggestionItem.Command(
            command = token,
            title = token,
            description = AuraCodeBundle.message("composer.slash.new.description"),
        )
    }
}

internal fun findSlashQuery(
    value: TextFieldValue,
    mentions: List<MentionEntry>,
): SlashQueryMatch? {
    if (value.composition != null) return null
    if (!value.selection.collapsed) return null
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    if (mentions.any { cursor in it.start until it.endExclusive }) return null

    val text = value.text
    if (!text.startsWith('/')) return null
    val tokenEndExclusive = text.indexOfFirst(Char::isWhitespace).let { index ->
        if (index >= 0) index else text.length
    }
    if (cursor <= 0 || cursor > tokenEndExclusive) return null
    val rawQuery = text.substring(1, cursor)
    if (rawQuery.any { !it.isLetterOrDigit() && it != '.' && it != '_' && it != '-' }) {
        return null
    }
    return SlashQueryMatch(
        query = rawQuery,
        start = 0,
        end = tokenEndExclusive,
    )
}

internal fun slashSkillToken(name: String): String = "\$$name "

internal fun replaceSlashQuery(
    document: TextFieldValue,
    mentions: List<MentionEntry>,
    replacement: String,
): TextFieldValue? {
    val match = findSlashQuery(document, mentions) ?: return null
    val nextText = buildString {
        append(replacement)
        append(document.text.substring(match.end))
    }
    return document.copy(
        text = nextText,
        selection = TextRange(replacement.length),
    )
}

internal fun discoverAvailableSkills(): List<SlashSkillDescriptor> {
    val home = System.getProperty("user.home").orEmpty().trim()
    if (home.isBlank()) return emptyList()
    val roots = listOf(
        Path.of(home, ".codex", "skills"),
        Path.of(home, ".codex", "superpowers", "skills"),
        Path.of(home, ".agents", "skills"),
    )
    val discovered = linkedMapOf<String, SlashSkillDescriptor>()
    roots.forEach { root ->
        if (!Files.isDirectory(root)) return@forEach
        runCatching {
            Files.walk(root).use { paths ->
                paths
                    .filter { it.isRegularFile() && it.name.equals("SKILL.md", ignoreCase = true) }
                    .forEach { skillFile ->
                        parseSkillDescriptor(skillFile)?.let { descriptor ->
                            discovered.putIfAbsent(descriptor.name, descriptor)
                        }
                    }
            }
        }
    }
    return discovered.values.sortedWith(
        compareBy<SlashSkillDescriptor>({ slashSkillSortPriority(it.name) }, { it.name.lowercase(Locale.ROOT) }),
    )
}

private fun parseSkillDescriptor(skillFile: Path): SlashSkillDescriptor? {
    val lines = runCatching { Files.readAllLines(skillFile) }.getOrNull() ?: return null
    val frontMatter = extractFrontMatter(lines) ?: return null
    val name = frontMatter["name"]?.trim()?.trim('"')?.takeIf(String::isNotBlank) ?: return null
    val description = frontMatter["description"]?.trim()?.trim('"').orEmpty()
    return SlashSkillDescriptor(
        name = name,
        description = description.ifBlank { name },
    )
}

private fun extractFrontMatter(lines: List<String>): Map<String, String>? {
    if (lines.firstOrNull()?.trim() != "---") return null
    val fields = linkedMapOf<String, String>()
    for (line in lines.drop(1)) {
        val trimmed = line.trim()
        if (trimmed == "---") return fields
        val separatorIndex = trimmed.indexOf(':')
        if (separatorIndex <= 0) continue
        val key = trimmed.substring(0, separatorIndex).trim()
        val value = trimmed.substring(separatorIndex + 1).trim()
        fields[key] = value
    }
    return null
}

private fun slashSkillSortPriority(name: String): Int = when (name) {
    "using-superpowers" -> 0
    "brainstorming" -> 1
    "systematic-debugging" -> 2
    "test-driven-development" -> 3
    "writing-plans" -> 4
    "executing-plans" -> 5
    "verification-before-completion" -> 6
    else -> 100
}
