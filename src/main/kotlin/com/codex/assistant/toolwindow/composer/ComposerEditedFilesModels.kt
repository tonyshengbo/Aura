package com.codex.assistant.toolwindow.composer

import com.codex.assistant.toolwindow.timeline.TimelineFileChange
import com.codex.assistant.toolwindow.timeline.TimelineFileChangeKind

internal enum class EditedFilesFilter {
    ALL,
    CREATED,
    UPDATED,
    DELETED,
}

internal enum class EditedFileAggregateKind {
    CREATED,
    UPDATED,
    DELETED,
    MIXED,
}

internal data class EditedFileAggregate(
    val path: String,
    val displayName: String,
    val latestKind: TimelineFileChangeKind,
    val kindSet: Set<TimelineFileChangeKind>,
    val aggregateKind: EditedFileAggregateKind,
    val editCount: Int,
    val latestAddedLines: Int?,
    val latestDeletedLines: Int?,
    val lastUpdatedAt: Long,
    val latestChange: TimelineFileChange,
)

internal data class EditedFilesSummary(
    val total: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
)

internal fun EditedFileAggregate.matches(filter: EditedFilesFilter): Boolean {
    return when (filter) {
        EditedFilesFilter.ALL -> true
        EditedFilesFilter.CREATED -> aggregateKind == EditedFileAggregateKind.CREATED
        EditedFilesFilter.UPDATED -> aggregateKind == EditedFileAggregateKind.UPDATED
        EditedFilesFilter.DELETED -> aggregateKind == EditedFileAggregateKind.DELETED
    }
}
