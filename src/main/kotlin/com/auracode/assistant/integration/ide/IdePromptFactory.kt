package com.auracode.assistant.integration.ide

/**
 * Builds deterministic prompts for IDE-driven high-frequency actions.
 */
object IdePromptFactory {
    fun explainSelection(
        filePath: String,
        startLine: Int?,
        endLine: Int?,
    ): String {
        val location = buildString {
            appendLine("File: $filePath")
            val range = when {
                startLine == null || endLine == null -> null
                startLine == endLine -> "Line: $startLine"
                else -> "Lines: $startLine-$endLine"
            }
            range?.let(::appendLine)
        }.trim()

        return buildString {
            appendLine("Explain the selected code in context.")
            appendLine("Focus on what it does, why it exists, and any risks or follow-up changes worth considering.")
            appendLine()
            appendLine(location)
        }.trim()
    }

    fun explainFile(filePath: String): String {
        return buildString {
            appendLine("Explain this file in context.")
            appendLine("Focus on its key responsibilities, important flows, dependencies, and any obvious risks.")
            appendLine()
            appendLine("File: $filePath")
        }.trim()
    }
}
