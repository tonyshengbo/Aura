package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.model.MessageRole
import com.codex.assistant.persistence.chat.PersistedTimelineEntry
import com.codex.assistant.protocol.ItemKind
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.protocol.UnifiedItem

internal object TimelineNodeMapper {
    fun fromHistory(entry: PersistedTimelineEntry): TimelineNode {
        return when (entry.recordType) {
            com.codex.assistant.persistence.chat.PersistedTimelineRecordType.MESSAGE -> {
                TimelineNode.MessageNode(
                    id = historyNodeId(entry.cursor, entry.id),
                    sourceId = entry.sourceId,
                    role = entry.role ?: MessageRole.ASSISTANT,
                    text = entry.body,
                    status = entry.status,
                    timestamp = entry.createdAt,
                    turnId = entry.turnId.ifBlank { null },
                    cursor = entry.cursor,
                    attachments = entry.attachments.map { attachment ->
                        TimelineMessageAttachment(
                            id = attachment.id,
                            kind = attachment.kind.toTimelineAttachmentKind(),
                            displayName = attachment.displayName,
                            assetPath = attachment.assetPath,
                            originalPath = attachment.originalPath,
                            mimeType = attachment.mimeType,
                            sizeBytes = attachment.sizeBytes,
                            status = attachment.status,
                        )
                    },
                )
            }

            com.codex.assistant.persistence.chat.PersistedTimelineRecordType.ACTIVITY -> {
                TimelineNode.ActivityNode(
                    id = historyNodeId(entry.cursor, entry.id),
                    sourceId = entry.sourceId,
                    kind = when (entry.activityKind) {
                        com.codex.assistant.persistence.chat.PersistedActivityKind.TOOL -> TimelineActivityKind.TOOL
                        com.codex.assistant.persistence.chat.PersistedActivityKind.COMMAND -> TimelineActivityKind.COMMAND
                        com.codex.assistant.persistence.chat.PersistedActivityKind.DIFF -> TimelineActivityKind.DIFF
                        com.codex.assistant.persistence.chat.PersistedActivityKind.APPROVAL -> TimelineActivityKind.APPROVAL
                        com.codex.assistant.persistence.chat.PersistedActivityKind.PLAN -> TimelineActivityKind.PLAN
                        com.codex.assistant.persistence.chat.PersistedActivityKind.UNKNOWN,
                        null,
                        -> TimelineActivityKind.UNKNOWN
                    },
                    title = entry.title,
                    body = entry.body,
                    status = entry.status,
                    turnId = entry.turnId.ifBlank { null },
                )
            }

            com.codex.assistant.persistence.chat.PersistedTimelineRecordType.ATTACHMENT ->
                error("Attachment records should be grouped into parent message nodes before mapping")
        }
    }

    fun localUserMessageMutation(entry: PersistedTimelineEntry): TimelineMutation.UpsertMessage {
        return TimelineNode.MessageNode(
            id = historyNodeId(entry.cursor, entry.id),
            sourceId = entry.sourceId,
            role = entry.role ?: MessageRole.USER,
            text = entry.body,
            status = entry.status,
            timestamp = entry.createdAt,
            turnId = entry.turnId.ifBlank { null },
            cursor = entry.cursor,
            attachments = entry.attachments.map { attachment ->
                TimelineMessageAttachment(
                    id = attachment.id,
                    kind = attachment.kind.toTimelineAttachmentKind(),
                    displayName = attachment.displayName,
                    assetPath = attachment.assetPath,
                    originalPath = attachment.originalPath,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes,
                    status = attachment.status,
                )
            },
        ).let { node ->
            TimelineMutation.UpsertMessage(
                sourceId = node.sourceId,
                role = node.role,
                text = node.text,
                status = node.status,
                timestamp = node.timestamp,
                turnId = node.turnId,
                cursor = node.cursor,
                attachments = node.attachments,
            )
        }
    }

    fun fromUnifiedEvent(event: UnifiedEvent): TimelineMutation? {
        return when (event) {
            is UnifiedEvent.ThreadStarted -> TimelineMutation.ThreadStarted(threadId = event.threadId)
            is UnifiedEvent.TurnStarted -> TimelineMutation.TurnStarted(turnId = event.turnId, threadId = event.threadId)
            is UnifiedEvent.TurnCompleted -> TimelineMutation.TurnCompleted(
                turnId = event.turnId,
                outcome = event.outcome,
            )

            is UnifiedEvent.Error -> TimelineMutation.Error(message = event.message)
            is UnifiedEvent.ItemUpdated -> event.item.toTimelineMutation()
        }
    }

    private fun UnifiedItem.toTimelineMutation(): TimelineMutation? {
        return when (kind) {
            ItemKind.NARRATIVE -> {
                val content = text?.takeIf { it.isNotBlank() } ?: return null
                TimelineMutation.UpsertMessage(
                    sourceId = id,
                    role = narrativeRole(),
                    text = content,
                    status = status,
                    attachments = emptyList(),
                )
            }

            else -> {
                val resolvedBody = bodyText()
                TimelineMutation.UpsertActivity(
                    sourceId = id,
                    kind = kind.toTimelineActivityKind(),
                    title = titleText(),
                    body = resolvedBody,
                    status = status,
                )
            }
        }
    }

    private fun UnifiedItem.narrativeRole(): MessageRole {
        return when (name) {
            "user_message" -> MessageRole.USER
            "system_message" -> MessageRole.SYSTEM
            else -> MessageRole.ASSISTANT
        }
    }

    private fun UnifiedItem.titleText(): String {
        val candidate = name?.trim().orEmpty()
        if (candidate.isNotBlank()) {
            return candidate
                .split('_', '-', ' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { token ->
                    token.replaceFirstChar { ch -> ch.uppercase() }
                }
        }
        return when (kind.toTimelineActivityKind()) {
            TimelineActivityKind.TOOL -> "Tool Call"
            TimelineActivityKind.COMMAND -> "Exec Command"
            TimelineActivityKind.DIFF -> "Apply Diff"
            TimelineActivityKind.APPROVAL -> "Approval"
            TimelineActivityKind.PLAN -> "Plan Update"
            TimelineActivityKind.UNKNOWN -> "Activity"
        }
    }

    private fun UnifiedItem.bodyText(): String {
        return listOfNotNull(
            text?.takeIf { it.isNotBlank() },
            command?.takeIf { it.isNotBlank() },
            filePath?.takeIf { it.isNotBlank() },
            name?.takeIf { it.isNotBlank() },
        ).joinToString("\n").ifBlank { id }
    }

    private fun historyNodeId(cursor: Long, messageId: String): String = "history-$cursor-$messageId"
}
