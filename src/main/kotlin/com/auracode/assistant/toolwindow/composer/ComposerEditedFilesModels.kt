package com.auracode.assistant.toolwindow.composer

import com.auracode.assistant.toolwindow.timeline.TimelineFileChange

internal data class EditedFileAggregate(
    val path: String,
    val displayName: String,
    val updateKeys: Set<String>,
    val editCount: Int,
    val latestAddedLines: Int?,
    val latestDeletedLines: Int?,
    val lastUpdatedAt: Long,
    val latestChange: TimelineFileChange,
)

internal data class EditedFilesSummary(
    val total: Int = 0,
    val totalEdits: Int = 0,
)
