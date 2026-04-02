package com.auracode.assistant.commit

import com.auracode.assistant.context.GitContextProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change

@Service(Service.Level.PROJECT)
internal class CommitMessageContextService(
    project: Project? = null,
    private val stagedDiffProvider: () -> String? = {
        project?.let { GitContextProvider.getInstance(it).getStagedChanges() }
    },
    private val branchNameProvider: () -> String? = {
        project?.let { GitContextProvider.getInstance(it).getCurrentBranchName() }
    },
    private val includedFilePathsProvider: (List<Change>) -> List<String> = CommitIncludedFilesExtractor::fromChanges,
) {
    fun collect(
        includedChanges: List<Change>,
        includedUnversionedFiles: List<String> = emptyList(),
    ): CommitMessageGenerationContext? {
        val includedFilePaths = includedFilePathsProvider(includedChanges)
            .plus(includedUnversionedFiles)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val stagedDiff = stagedDiffProvider()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val branchName = branchNameProvider()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (stagedDiff == null && includedFilePaths.isEmpty()) return null
        return CommitMessageGenerationContext(
            branchName = branchName,
            stagedDiff = stagedDiff,
            includedFilePaths = includedFilePaths,
        )
    }
}
