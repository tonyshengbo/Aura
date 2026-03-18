package com.codex.assistant.persistence.chat

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.MessageRole
import com.codex.assistant.model.TurnUsageSnapshot
import com.codex.assistant.protocol.ItemStatus
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

internal data class PersistedChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int,
    val remoteThreadId: String,
    val usageSnapshot: TurnUsageSnapshot?,
    val isActive: Boolean,
)

internal enum class PersistedTimelineRecordType {
    MESSAGE,
    ACTIVITY,
    ATTACHMENT,
}

internal enum class PersistedActivityKind {
    TOOL,
    COMMAND,
    DIFF,
    APPROVAL,
    PLAN,
    UNKNOWN,
}

internal enum class PersistedAttachmentKind {
    IMAGE,
    FILE,
    TEXT,
}

internal data class PersistedMessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val kind: PersistedAttachmentKind,
    val displayName: String,
    val assetPath: String,
    val originalPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String = "",
    val status: ItemStatus = ItemStatus.SUCCESS,
)

internal data class PersistedTimelineEntry(
    val id: String,
    val cursor: Long,
    val recordType: PersistedTimelineRecordType,
    val turnId: String,
    val sourceId: String,
    val role: MessageRole?,
    val activityKind: PersistedActivityKind?,
    val title: String,
    val body: String,
    val status: ItemStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val isFinal: Boolean,
    val attachments: List<PersistedMessageAttachment> = emptyList(),
)

internal data class TimelineHistoryPage(
    val entries: List<PersistedTimelineEntry>,
    val hasOlder: Boolean,
)

internal interface ChatSessionRepository {
    fun listSessions(): List<PersistedChatSession>
    fun loadSession(sessionId: String): PersistedChatSession?
    fun loadActiveSession(): PersistedChatSession?
    fun upsertSession(session: PersistedChatSession)
    fun markActiveSession(sessionId: String)
    fun deleteSession(sessionId: String)

    fun insertMessageRecord(
        sessionId: String,
        message: ChatMessage,
        turnId: String = "",
        sourceId: String = message.id,
        attachments: List<PersistedMessageAttachment> = emptyList(),
    ): PersistedTimelineEntry

    fun upsertMessageRecord(
        sessionId: String,
        turnId: String,
        sourceId: String,
        role: MessageRole,
        body: String,
        status: ItemStatus,
        timestamp: Long,
    ): PersistedTimelineEntry

    fun upsertActivityRecord(
        sessionId: String,
        turnId: String,
        sourceId: String,
        kind: PersistedActivityKind,
        title: String,
        body: String,
        status: ItemStatus,
        timestamp: Long,
    ): PersistedTimelineEntry

    fun replaceTurnId(sessionId: String, fromTurnId: String, toTurnId: String)
    fun markTurnRecordsStatus(sessionId: String, turnId: String, fromStatus: ItemStatus, toStatus: ItemStatus)

    fun loadRecentTimeline(sessionId: String, limit: Int): TimelineHistoryPage
    fun loadTimelineBefore(sessionId: String, beforeCursorExclusive: Long, limit: Int): TimelineHistoryPage
}

internal class SQLiteChatSessionRepository(
    private val dbPath: Path,
) : ChatSessionRepository {
    init {
        Class.forName("org.sqlite.JDBC")
        dbPath.parent?.let { Files.createDirectories(it) }
        withConnection { connection ->
            ensureSessionsTable(connection)
            ensureMessagesTable(connection)
            ensureIndexes(connection)
        }
    }

    override fun listSessions(): List<PersistedChatSession> {
        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, created_at, updated_at, message_count, remote_thread_id,
                       usage_model, usage_context_window, usage_input_tokens,
                       usage_cached_input_tokens, usage_output_tokens, usage_captured_at, is_active
                FROM sessions
                ORDER BY updated_at DESC, created_at DESC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toSession())
                        }
                    }
                }
            }
        }
    }

    override fun loadSession(sessionId: String): PersistedChatSession? {
        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, created_at, updated_at, message_count, remote_thread_id,
                       usage_model, usage_context_window, usage_input_tokens,
                       usage_cached_input_tokens, usage_output_tokens, usage_captured_at, is_active
                FROM sessions
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toSession() else null
                }
            }
        }
    }

    override fun loadActiveSession(): PersistedChatSession? {
        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, created_at, updated_at, message_count, remote_thread_id,
                       usage_model, usage_context_window, usage_input_tokens,
                       usage_cached_input_tokens, usage_output_tokens, usage_captured_at, is_active
                FROM sessions
                WHERE is_active = 1
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toSession() else null
                }
            }
        }
    }

    override fun upsertSession(session: PersistedChatSession) {
        withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sessions (
                    id, title, created_at, updated_at, message_count, remote_thread_id,
                    usage_model, usage_context_window, usage_input_tokens,
                    usage_cached_input_tokens, usage_output_tokens, usage_captured_at, is_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    message_count = excluded.message_count,
                    remote_thread_id = excluded.remote_thread_id,
                    usage_model = excluded.usage_model,
                    usage_context_window = excluded.usage_context_window,
                    usage_input_tokens = excluded.usage_input_tokens,
                    usage_cached_input_tokens = excluded.usage_cached_input_tokens,
                    usage_output_tokens = excluded.usage_output_tokens,
                    usage_captured_at = excluded.usage_captured_at,
                    is_active = excluded.is_active
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, session.id)
                statement.setString(2, session.title)
                statement.setLong(3, session.createdAt)
                statement.setLong(4, session.updatedAt)
                statement.setInt(5, session.messageCount)
                statement.setString(6, session.remoteThreadId)
                statement.setString(7, session.usageSnapshot?.model.orEmpty())
                statement.setInt(8, session.usageSnapshot?.contextWindow ?: 0)
                statement.setInt(9, session.usageSnapshot?.inputTokens ?: 0)
                statement.setInt(10, session.usageSnapshot?.cachedInputTokens ?: 0)
                statement.setInt(11, session.usageSnapshot?.outputTokens ?: 0)
                statement.setLong(12, session.usageSnapshot?.capturedAt ?: 0L)
                statement.setInt(13, if (session.isActive) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    override fun markActiveSession(sessionId: String) {
        withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE sessions
                SET is_active = CASE WHEN id = ? THEN 1 ELSE 0 END
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.executeUpdate()
            }
        }
    }

    override fun deleteSession(sessionId: String) {
        withConnection { connection ->
            connection.prepareStatement("DELETE FROM sessions WHERE id = ?").use { statement ->
                statement.setString(1, sessionId)
                statement.executeUpdate()
            }
        }
    }

    override fun insertMessageRecord(
        sessionId: String,
        message: ChatMessage,
        turnId: String,
        sourceId: String,
        attachments: List<PersistedMessageAttachment>,
    ): PersistedTimelineEntry {
        return withConnection { connection ->
            connection.autoCommit = false
            try {
                val recordId = message.id
                insertRecord(
                    connection = connection,
                    id = recordId,
                    sessionId = sessionId,
                    turnId = turnId,
                    recordType = PersistedTimelineRecordType.MESSAGE,
                    parentId = "",
                    role = message.role,
                    sourceId = sourceId,
                    activityKind = null,
                    title = "",
                    body = message.content,
                    status = ItemStatus.SUCCESS,
                    attachment = null,
                    createdAt = message.timestamp,
                    updatedAt = message.timestamp,
                    isFinal = true,
                )
                attachments.forEachIndexed { index, attachment ->
                    insertRecord(
                        connection = connection,
                        id = attachment.id,
                        sessionId = sessionId,
                        turnId = turnId,
                        recordType = PersistedTimelineRecordType.ATTACHMENT,
                        parentId = recordId,
                        role = null,
                        sourceId = "${sourceId}-attachment-$index",
                        activityKind = null,
                        title = "",
                        body = "",
                        status = attachment.status,
                        attachment = attachment,
                        createdAt = message.timestamp,
                        updatedAt = message.timestamp,
                        isFinal = true,
                    )
                }
                val entry = loadEntryById(connection, recordId)
                    ?: error("Inserted message record $recordId was not found")
                connection.commit()
                entry
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun upsertMessageRecord(
        sessionId: String,
        turnId: String,
        sourceId: String,
        role: MessageRole,
        body: String,
        status: ItemStatus,
        timestamp: Long,
    ): PersistedTimelineEntry {
        return upsertTopLevelRecord(
            sessionId = sessionId,
            turnId = turnId,
            recordType = PersistedTimelineRecordType.MESSAGE,
            sourceId = sourceId,
            role = role,
            activityKind = null,
            title = "",
            body = body,
            status = status,
            timestamp = timestamp,
            isFinal = status != ItemStatus.RUNNING,
        )
    }

    override fun upsertActivityRecord(
        sessionId: String,
        turnId: String,
        sourceId: String,
        kind: PersistedActivityKind,
        title: String,
        body: String,
        status: ItemStatus,
        timestamp: Long,
    ): PersistedTimelineEntry {
        return upsertTopLevelRecord(
            sessionId = sessionId,
            turnId = turnId,
            recordType = PersistedTimelineRecordType.ACTIVITY,
            sourceId = sourceId,
            role = null,
            activityKind = kind,
            title = title,
            body = body,
            status = status,
            timestamp = timestamp,
            isFinal = status != ItemStatus.RUNNING,
        )
    }

    override fun replaceTurnId(sessionId: String, fromTurnId: String, toTurnId: String) {
        if (fromTurnId.isBlank() || toTurnId.isBlank() || fromTurnId == toTurnId) return
        withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE messages
                SET turn_id = ?, updated_at = CASE WHEN updated_at > ? THEN updated_at ELSE ? END
                WHERE session_id = ? AND turn_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, toTurnId)
                statement.setLong(2, System.currentTimeMillis())
                statement.setLong(3, System.currentTimeMillis())
                statement.setString(4, sessionId)
                statement.setString(5, fromTurnId)
                statement.executeUpdate()
            }
        }
    }

    override fun markTurnRecordsStatus(sessionId: String, turnId: String, fromStatus: ItemStatus, toStatus: ItemStatus) {
        if (turnId.isBlank()) return
        withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE messages
                SET status = ?, is_final = 1, updated_at = CASE WHEN updated_at > ? THEN updated_at ELSE ? END
                WHERE session_id = ? AND turn_id = ? AND status = ? AND record_type != ?
                """.trimIndent(),
            ).use { statement ->
                val now = System.currentTimeMillis()
                statement.setString(1, toStatus.name)
                statement.setLong(2, now)
                statement.setLong(3, now)
                statement.setString(4, sessionId)
                statement.setString(5, turnId)
                statement.setString(6, fromStatus.name)
                statement.setString(7, PersistedTimelineRecordType.ATTACHMENT.name)
                statement.executeUpdate()
            }
        }
    }

    override fun loadRecentTimeline(sessionId: String, limit: Int): TimelineHistoryPage {
        return loadTimelinePage(sessionId = sessionId, limit = limit, beforeCursorExclusive = null)
    }

    override fun loadTimelineBefore(sessionId: String, beforeCursorExclusive: Long, limit: Int): TimelineHistoryPage {
        return loadTimelinePage(sessionId = sessionId, limit = limit, beforeCursorExclusive = beforeCursorExclusive)
    }

    private fun upsertTopLevelRecord(
        sessionId: String,
        turnId: String,
        recordType: PersistedTimelineRecordType,
        sourceId: String,
        role: MessageRole?,
        activityKind: PersistedActivityKind?,
        title: String,
        body: String,
        status: ItemStatus,
        timestamp: Long,
        isFinal: Boolean,
    ): PersistedTimelineEntry {
        return withConnection { connection ->
            connection.autoCommit = false
            try {
                val existing = findRecordMetadata(
                    connection = connection,
                    sessionId = sessionId,
                    turnId = turnId,
                    parentId = "",
                    sourceId = sourceId,
                    recordType = recordType,
                )
                val id = existing?.id ?: UUID.randomUUID().toString()
                val createdAt = existing?.createdAt ?: timestamp
                insertOrUpdateRecord(
                    connection = connection,
                    id = id,
                    sessionId = sessionId,
                    turnId = turnId,
                    recordType = recordType,
                    parentId = "",
                    role = role,
                    sourceId = sourceId,
                    activityKind = activityKind,
                    title = title,
                    body = body,
                    status = status,
                    attachment = null,
                    createdAt = createdAt,
                    updatedAt = timestamp,
                    isFinal = isFinal,
                )
                val entry = loadEntryById(connection, id)
                    ?: error("Upserted record $id was not found")
                connection.commit()
                entry
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun loadTimelinePage(
        sessionId: String,
        limit: Int,
        beforeCursorExclusive: Long?,
    ): TimelineHistoryPage {
        if (limit <= 0) {
            return TimelineHistoryPage(entries = emptyList(), hasOlder = false)
        }
        return withConnection { connection ->
            val rows = loadTopLevelRows(connection, sessionId, limit, beforeCursorExclusive)
            val hasOlder = rows.size > limit
            val pageRows = if (hasOlder) rows.dropLast(1) else rows
            val topLevel = pageRows.asReversed()
            val attachmentsByParent = loadAttachmentRows(connection, topLevel.map { it.id })
                .groupBy { it.parentId }
                .mapValues { (_, value) -> value.sortedBy { it.cursor }.map(::toAttachment) }

            TimelineHistoryPage(
                entries = topLevel.map { row ->
                    row.toTimelineEntry(
                        attachments = attachmentsByParent[row.id].orEmpty(),
                    )
                },
                hasOlder = hasOlder,
            )
        }
    }

    private fun loadTopLevelRows(
        connection: Connection,
        sessionId: String,
        limit: Int,
        beforeCursorExclusive: Long?,
    ): List<MessageRow> {
        val sql = buildString {
            append(
                """
                SELECT sequence, id, session_id, turn_id, record_type, parent_id, role, source_id,
                       activity_kind, title, body, status, attachment_kind, display_name,
                       asset_path, original_path, mime_type, size_bytes, sha256,
                       is_final, created_at, updated_at
                FROM messages
                WHERE session_id = ? AND parent_id = ''
                """.trimIndent(),
            )
            if (beforeCursorExclusive != null) {
                append(" AND sequence < ?")
            }
            append(" ORDER BY sequence DESC LIMIT ?")
        }
        return connection.prepareStatement(sql).use { statement ->
            var index = 1
            statement.setString(index++, sessionId)
            if (beforeCursorExclusive != null) {
                statement.setLong(index++, beforeCursorExclusive)
            }
            statement.setInt(index, limit + 1)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toMessageRow())
                    }
                }
            }
        }
    }

    private fun loadAttachmentRows(
        connection: Connection,
        parentIds: List<String>,
    ): List<MessageRow> {
        if (parentIds.isEmpty()) return emptyList()
        val placeholders = parentIds.joinToString(",") { "?" }
        val sql =
            """
            SELECT sequence, id, session_id, turn_id, record_type, parent_id, role, source_id,
                   activity_kind, title, body, status, attachment_kind, display_name,
                   asset_path, original_path, mime_type, size_bytes, sha256,
                   is_final, created_at, updated_at
            FROM messages
            WHERE parent_id IN ($placeholders) AND record_type = ?
            ORDER BY sequence ASC
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            parentIds.forEachIndexed { index, id ->
                statement.setString(index + 1, id)
            }
            statement.setString(parentIds.size + 1, PersistedTimelineRecordType.ATTACHMENT.name)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toMessageRow())
                    }
                }
            }
        }
    }

    private fun loadEntryById(connection: Connection, id: String): PersistedTimelineEntry? {
        val row = connection.prepareStatement(
            """
            SELECT sequence, id, session_id, turn_id, record_type, parent_id, role, source_id,
                   activity_kind, title, body, status, attachment_kind, display_name,
                   asset_path, original_path, mime_type, size_bytes, sha256,
                   is_final, created_at, updated_at
            FROM messages
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rs ->
                if (rs.next()) rs.toMessageRow() else null
            }
        } ?: return null
        val attachments = loadAttachmentRows(connection, listOf(id))
            .sortedBy { it.cursor }
            .map(::toAttachment)
        return row.toTimelineEntry(attachments)
    }

    private fun findRecordMetadata(
        connection: Connection,
        sessionId: String,
        turnId: String,
        parentId: String,
        sourceId: String,
        recordType: PersistedTimelineRecordType,
    ): ExistingRecordMetadata? {
        return connection.prepareStatement(
            """
            SELECT id, created_at
            FROM messages
            WHERE session_id = ? AND turn_id = ? AND parent_id = ? AND source_id = ? AND record_type = ?
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.setString(2, turnId)
            statement.setString(3, parentId)
            statement.setString(4, sourceId)
            statement.setString(5, recordType.name)
            statement.executeQuery().use { rs ->
                if (rs.next()) {
                    ExistingRecordMetadata(
                        id = rs.getString("id"),
                        createdAt = rs.getLong("created_at"),
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun insertRecord(
        connection: Connection,
        id: String,
        sessionId: String,
        turnId: String,
        recordType: PersistedTimelineRecordType,
        parentId: String,
        role: MessageRole?,
        sourceId: String,
        activityKind: PersistedActivityKind?,
        title: String,
        body: String,
        status: ItemStatus,
        attachment: PersistedMessageAttachment?,
        createdAt: Long,
        updatedAt: Long,
        isFinal: Boolean,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO messages (
                id, session_id, turn_id, record_type, parent_id, role, source_id,
                activity_kind, title, body, status, attachment_kind, display_name,
                asset_path, original_path, mime_type, size_bytes, sha256,
                is_final, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            bindRecord(
                statement = statement,
                id = id,
                sessionId = sessionId,
                turnId = turnId,
                recordType = recordType,
                parentId = parentId,
                role = role,
                sourceId = sourceId,
                activityKind = activityKind,
                title = title,
                body = body,
                status = status,
                attachment = attachment,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isFinal = isFinal,
            )
            statement.executeUpdate()
        }
    }

    private fun insertOrUpdateRecord(
        connection: Connection,
        id: String,
        sessionId: String,
        turnId: String,
        recordType: PersistedTimelineRecordType,
        parentId: String,
        role: MessageRole?,
        sourceId: String,
        activityKind: PersistedActivityKind?,
        title: String,
        body: String,
        status: ItemStatus,
        attachment: PersistedMessageAttachment?,
        createdAt: Long,
        updatedAt: Long,
        isFinal: Boolean,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO messages (
                id, session_id, turn_id, record_type, parent_id, role, source_id,
                activity_kind, title, body, status, attachment_kind, display_name,
                asset_path, original_path, mime_type, size_bytes, sha256,
                is_final, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id, turn_id, parent_id, source_id, record_type) DO UPDATE SET
                role = excluded.role,
                activity_kind = excluded.activity_kind,
                title = excluded.title,
                body = excluded.body,
                status = excluded.status,
                attachment_kind = excluded.attachment_kind,
                display_name = excluded.display_name,
                asset_path = excluded.asset_path,
                original_path = excluded.original_path,
                mime_type = excluded.mime_type,
                size_bytes = excluded.size_bytes,
                sha256 = excluded.sha256,
                is_final = excluded.is_final,
                updated_at = excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            bindRecord(
                statement = statement,
                id = id,
                sessionId = sessionId,
                turnId = turnId,
                recordType = recordType,
                parentId = parentId,
                role = role,
                sourceId = sourceId,
                activityKind = activityKind,
                title = title,
                body = body,
                status = status,
                attachment = attachment,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isFinal = isFinal,
            )
            statement.executeUpdate()
        }
    }

    private fun bindRecord(
        statement: java.sql.PreparedStatement,
        id: String,
        sessionId: String,
        turnId: String,
        recordType: PersistedTimelineRecordType,
        parentId: String,
        role: MessageRole?,
        sourceId: String,
        activityKind: PersistedActivityKind?,
        title: String,
        body: String,
        status: ItemStatus,
        attachment: PersistedMessageAttachment?,
        createdAt: Long,
        updatedAt: Long,
        isFinal: Boolean,
    ) {
        statement.setString(1, id)
        statement.setString(2, sessionId)
        statement.setString(3, turnId)
        statement.setString(4, recordType.name)
        statement.setString(5, parentId)
        statement.setString(6, role?.name.orEmpty())
        statement.setString(7, sourceId)
        statement.setString(8, activityKind?.name.orEmpty())
        statement.setString(9, title)
        statement.setString(10, body)
        statement.setString(11, status.name)
        statement.setString(12, attachment?.kind?.name.orEmpty())
        statement.setString(13, attachment?.displayName.orEmpty())
        statement.setString(14, attachment?.assetPath.orEmpty())
        statement.setString(15, attachment?.originalPath.orEmpty())
        statement.setString(16, attachment?.mimeType.orEmpty())
        statement.setLong(17, attachment?.sizeBytes ?: 0L)
        statement.setString(18, attachment?.sha256.orEmpty())
        statement.setInt(19, if (isFinal) 1 else 0)
        statement.setLong(20, createdAt)
        statement.setLong(21, updatedAt)
    }

    private fun ensureSessionsTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    message_count INTEGER NOT NULL DEFAULT 0,
                    remote_thread_id TEXT NOT NULL DEFAULT '',
                    usage_model TEXT NOT NULL DEFAULT '',
                    usage_context_window INTEGER NOT NULL DEFAULT 0,
                    usage_input_tokens INTEGER NOT NULL DEFAULT 0,
                    usage_cached_input_tokens INTEGER NOT NULL DEFAULT 0,
                    usage_output_tokens INTEGER NOT NULL DEFAULT 0,
                    usage_captured_at INTEGER NOT NULL DEFAULT 0,
                    is_active INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
    }

    private fun ensureMessagesTable(connection: Connection) {
        if (shouldRecreateMessagesTable(connection)) {
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS messages")
            }
        }
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                    id TEXT NOT NULL UNIQUE,
                    session_id TEXT NOT NULL,
                    turn_id TEXT NOT NULL DEFAULT '',
                    record_type TEXT NOT NULL,
                    parent_id TEXT NOT NULL DEFAULT '',
                    role TEXT NOT NULL DEFAULT '',
                    source_id TEXT NOT NULL DEFAULT '',
                    activity_kind TEXT NOT NULL DEFAULT '',
                    title TEXT NOT NULL DEFAULT '',
                    body TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT '',
                    attachment_kind TEXT NOT NULL DEFAULT '',
                    display_name TEXT NOT NULL DEFAULT '',
                    asset_path TEXT NOT NULL DEFAULT '',
                    original_path TEXT NOT NULL DEFAULT '',
                    mime_type TEXT NOT NULL DEFAULT '',
                    size_bytes INTEGER NOT NULL DEFAULT 0,
                    sha256 TEXT NOT NULL DEFAULT '',
                    is_final INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    private fun shouldRecreateMessagesTable(connection: Connection): Boolean {
        val existingColumns = connection.prepareStatement("PRAGMA table_info(messages)").use { statement ->
            statement.executeQuery().use { rs ->
                buildSet {
                    while (rs.next()) {
                        add(rs.getString("name"))
                    }
                }
            }
        }
        if (existingColumns.isEmpty()) return false
        return "record_type" !in existingColumns || "body" !in existingColumns || "parent_id" !in existingColumns
    }

    private fun ensureIndexes(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_session_sequence
                ON messages(session_id, sequence DESC)
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_messages_session_parent_sequence
                ON messages(session_id, parent_id, sequence ASC)
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_messages_session_source
                ON messages(session_id, turn_id, parent_id, source_id, record_type)
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_sessions_updated_at
                ON sessions(updated_at DESC)
                """.trimIndent(),
            )
        }
    }

    private fun toAttachment(row: MessageRow): PersistedMessageAttachment {
        return PersistedMessageAttachment(
            id = row.id,
            kind = PersistedAttachmentKind.valueOf(row.attachmentKind.ifBlank { PersistedAttachmentKind.FILE.name }),
            displayName = row.displayName,
            assetPath = row.assetPath,
            originalPath = row.originalPath,
            mimeType = row.mimeType,
            sizeBytes = row.sizeBytes,
            sha256 = row.sha256,
            status = row.status,
        )
    }

    private fun MessageRow.toTimelineEntry(attachments: List<PersistedMessageAttachment>): PersistedTimelineEntry {
        return PersistedTimelineEntry(
            id = id,
            cursor = cursor,
            recordType = recordType,
            turnId = turnId,
            sourceId = sourceId,
            role = role,
            activityKind = activityKind,
            title = title,
            body = body,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFinal = isFinal,
            attachments = attachments,
        )
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        return DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
            }
            block(connection)
        }
    }

    private fun java.sql.ResultSet.toSession(): PersistedChatSession {
        val usageModel = getString("usage_model").orEmpty()
        val usageContextWindow = getInt("usage_context_window")
        val usageInputTokens = getInt("usage_input_tokens")
        val usageCachedInputTokens = getInt("usage_cached_input_tokens")
        val usageOutputTokens = getInt("usage_output_tokens")
        val usageCapturedAt = getLong("usage_captured_at")
        val usageSnapshot = if (
            usageCapturedAt == 0L &&
            usageModel.isBlank() &&
            usageContextWindow == 0 &&
            usageInputTokens == 0 &&
            usageCachedInputTokens == 0 &&
            usageOutputTokens == 0
        ) {
            null
        } else {
            TurnUsageSnapshot(
                model = usageModel,
                contextWindow = usageContextWindow,
                inputTokens = usageInputTokens,
                cachedInputTokens = usageCachedInputTokens,
                outputTokens = usageOutputTokens,
                capturedAt = usageCapturedAt,
            )
        }
        return PersistedChatSession(
            id = getString("id"),
            title = getString("title"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
            messageCount = getInt("message_count"),
            remoteThreadId = getString("remote_thread_id").orEmpty(),
            usageSnapshot = usageSnapshot,
            isActive = getInt("is_active") == 1,
        )
    }

    private fun java.sql.ResultSet.toMessageRow(): MessageRow {
        return MessageRow(
            cursor = getLong("sequence"),
            id = getString("id"),
            sessionId = getString("session_id"),
            turnId = getString("turn_id").orEmpty(),
            recordType = PersistedTimelineRecordType.valueOf(getString("record_type")),
            parentId = getString("parent_id").orEmpty(),
            role = getString("role").orEmpty().takeIf { it.isNotBlank() }?.let(MessageRole::valueOf),
            sourceId = getString("source_id").orEmpty(),
            activityKind = getString("activity_kind").orEmpty().takeIf { it.isNotBlank() }?.let(PersistedActivityKind::valueOf),
            title = getString("title").orEmpty(),
            body = getString("body").orEmpty(),
            status = getString("status").orEmpty().takeIf { it.isNotBlank() }?.let(ItemStatus::valueOf) ?: ItemStatus.SUCCESS,
            attachmentKind = getString("attachment_kind").orEmpty(),
            displayName = getString("display_name").orEmpty(),
            assetPath = getString("asset_path").orEmpty(),
            originalPath = getString("original_path").orEmpty(),
            mimeType = getString("mime_type").orEmpty(),
            sizeBytes = getLong("size_bytes"),
            sha256 = getString("sha256").orEmpty(),
            isFinal = getInt("is_final") == 1,
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
        )
    }

    private data class ExistingRecordMetadata(
        val id: String,
        val createdAt: Long,
    )

    private data class MessageRow(
        val cursor: Long,
        val id: String,
        val sessionId: String,
        val turnId: String,
        val recordType: PersistedTimelineRecordType,
        val parentId: String,
        val role: MessageRole?,
        val sourceId: String,
        val activityKind: PersistedActivityKind?,
        val title: String,
        val body: String,
        val status: ItemStatus,
        val attachmentKind: String,
        val displayName: String,
        val assetPath: String,
        val originalPath: String,
        val mimeType: String,
        val sizeBytes: Long,
        val sha256: String,
        val isFinal: Boolean,
        val createdAt: Long,
        val updatedAt: Long,
    )
}
