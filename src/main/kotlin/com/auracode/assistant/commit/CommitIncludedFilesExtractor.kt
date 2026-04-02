package com.auracode.assistant.commit

import com.intellij.openapi.vcs.changes.Change

/**
 * Extracts stable file paths from the changes included in the current commit workflow.
 */
internal object CommitIncludedFilesExtractor {
    fun fromChanges(changes: List<Change>): List<String> {
        return changes.asSequence()
            .mapNotNull { change ->
                change.afterRevision?.file?.path
                    ?: change.beforeRevision?.file?.path
            }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }
}
