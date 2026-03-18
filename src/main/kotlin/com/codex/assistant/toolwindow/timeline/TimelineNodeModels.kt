package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.model.MessageRole
import com.codex.assistant.persistence.chat.PersistedAttachmentKind
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnOutcome

internal enum class TimelineActivityKind {
    TOOL,
    COMMAND,
    DIFF,
    APPROVAL,
    PLAN,
    UNKNOWN,
}

internal enum class TimelineAttachmentKind {
    IMAGE,
    FILE,
    TEXT,
}

internal data class TimelineMessageAttachment(
    val id: String,
    val kind: TimelineAttachmentKind,
    val displayName: String,
    val assetPath: String,
    val originalPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: ItemStatus,
)

internal sealed interface TimelineNode {
    val id: String

    data class MessageNode(
        override val id: String,
        val sourceId: String,
        val role: MessageRole,
        val text: String,
        val status: ItemStatus,
        val timestamp: Long?,
        val turnId: String?,
        val cursor: Long?,
        val attachments: List<TimelineMessageAttachment> = emptyList(),
    ) : TimelineNode

    data class ActivityNode(
        override val id: String,
        val sourceId: String,
        val kind: TimelineActivityKind,
        val title: String,
        val body: String,
        val status: ItemStatus,
        val turnId: String?,
    ) : TimelineNode

    data class LoadMoreNode(
        override val id: String = LOAD_MORE_NODE_ID,
        val isLoading: Boolean,
    ) : TimelineNode

    companion object {
        const val LOAD_MORE_NODE_ID: String = "timeline-load-more"
    }
}

internal fun PersistedAttachmentKind.toTimelineAttachmentKind(): TimelineAttachmentKind {
    return when (this) {
        PersistedAttachmentKind.IMAGE -> TimelineAttachmentKind.IMAGE
        PersistedAttachmentKind.FILE -> TimelineAttachmentKind.FILE
        PersistedAttachmentKind.TEXT -> TimelineAttachmentKind.TEXT
    }
}

internal sealed interface TimelineMutation {
    data class ThreadStarted(
        val threadId: String,
    ) : TimelineMutation

    data class TurnStarted(
        val turnId: String,
        val threadId: String? = null,
    ) : TimelineMutation

    data class UpsertMessage(
        val sourceId: String,
        val role: MessageRole,
        val text: String,
        val status: ItemStatus,
        val timestamp: Long? = null,
        val turnId: String? = null,
        val cursor: Long? = null,
        val attachments: List<TimelineMessageAttachment> = emptyList(),
    ) : TimelineMutation

    data class UpsertActivity(
        val sourceId: String,
        val kind: TimelineActivityKind,
        val title: String,
        val body: String,
        val status: ItemStatus,
        val turnId: String? = null,
    ) : TimelineMutation

    data class TurnCompleted(
        val turnId: String,
        val outcome: TurnOutcome,
    ) : TimelineMutation

    data class Error(
        val message: String,
    ) : TimelineMutation
}

internal fun ItemKind.toTimelineActivityKind(): TimelineActivityKind {
    return when (this) {
        ItemKind.TOOL_CALL -> TimelineActivityKind.TOOL
        ItemKind.COMMAND_EXEC -> TimelineActivityKind.COMMAND
        ItemKind.DIFF_APPLY -> TimelineActivityKind.DIFF
        ItemKind.APPROVAL_REQUEST -> TimelineActivityKind.APPROVAL
        ItemKind.PLAN_UPDATE -> TimelineActivityKind.PLAN
        ItemKind.UNKNOWN,
        ItemKind.NARRATIVE,
        -> TimelineActivityKind.UNKNOWN
    }
}
