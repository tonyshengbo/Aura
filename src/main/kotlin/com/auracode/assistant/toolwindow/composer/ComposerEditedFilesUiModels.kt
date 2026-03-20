package com.auracode.assistant.toolwindow.composer

internal data class EditedFilesPanelUiModel(
    val summary: EditedFilesSummaryUiModel,
    val files: List<EditedFileRowUiModel>,
)

internal data class EditedFilesSummaryUiModel(
    val totalFiles: Int,
    val totalEdits: Int,
)

internal data class EditedFileRowUiModel(
    val path: String,
    val displayName: String,
    val parentPath: String,
    val editCount: Int,
    val latestAddedLines: Int?,
    val latestDeletedLines: Int?,
)

internal fun ComposerAreaState.toEditedFilesPanelUiModel(): EditedFilesPanelUiModel {
    return EditedFilesPanelUiModel(
        summary = EditedFilesSummaryUiModel(
            totalFiles = editedFilesSummary.total,
            totalEdits = editedFilesSummary.totalEdits,
        ),
        files = editedFiles.map { file ->
            EditedFileRowUiModel(
                path = file.path,
                displayName = file.displayName,
                parentPath = file.path.toParentDisplayPath(),
                editCount = file.editCount,
                latestAddedLines = file.latestAddedLines,
                latestDeletedLines = file.latestDeletedLines,
            )
        },
    )
}

private fun String.toParentDisplayPath(): String {
    val normalized = replace('\\', '/')
    val segments = normalized.split('/').filter { it.isNotBlank() }
    if (segments.size <= 1) return ""
    val parentSegments = segments.dropLast(1)
    return parentSegments.takeLast(3).joinToString("/")
}
