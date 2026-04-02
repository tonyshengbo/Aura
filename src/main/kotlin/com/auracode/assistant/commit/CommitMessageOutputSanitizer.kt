package com.auracode.assistant.commit

/**
 * Normalizes model output into a single Conventional Commit subject line.
 */
internal object CommitMessageOutputSanitizer {
    private val conventionalCommitRegex = Regex("""([a-z]+(?:\([^)]+\))?!?:\s+.+)""")

    fun sanitize(raw: String): String {
        val line = raw.lineSequence()
            .map { it.trim() }
            .map { it.removePrefix("- ").trim() }
            .firstNotNullOfOrNull { current ->
                conventionalCommitRegex.find(current)?.groupValues?.get(1)
            }
            ?.trim()
            ?.trimEnd('.')
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Aura did not return a valid Conventional Commit subject.")
        return line
    }
}
