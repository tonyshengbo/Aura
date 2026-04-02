package com.auracode.assistant.commit

internal data class CommitMessageGenerationContext(
    val branchName: String?,
    val stagedDiff: String?,
    val includedFilePaths: List<String>,
)
