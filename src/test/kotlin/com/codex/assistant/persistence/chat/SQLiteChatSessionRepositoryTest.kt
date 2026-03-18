package com.codex.assistant.persistence.chat

import com.codex.assistant.model.ChatMessage
import com.codex.assistant.model.MessageRole
import com.codex.assistant.protocol.ItemStatus
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SQLiteChatSessionRepositoryTest {
    @Test
    fun `stores timeline records with attachment children and paginates by top level entries`() {
        val repository = SQLiteChatSessionRepository(createTempDirectory("chat-repository-test").resolve("chat.db"))
        repository.upsertSession(
            PersistedChatSession(
                id = "session-1",
                title = "First session",
                createdAt = 10L,
                updatedAt = 20L,
                messageCount = 0,
                remoteThreadId = "",
                usageSnapshot = null,
                isActive = true,
            ),
        )

        repository.insertMessageRecord(
            sessionId = "session-1",
            message = ChatMessage(
                id = "user-1",
                role = MessageRole.USER,
                content = "show me this image",
                timestamp = 1L,
            ),
            turnId = "local-turn-1",
            sourceId = "local-user-1",
            attachments = listOf(
                PersistedMessageAttachment(
                    id = "attachment-1",
                    kind = PersistedAttachmentKind.IMAGE,
                    displayName = "preview.png",
                    assetPath = "/assets/preview.png",
                    originalPath = "/tmp/preview.png",
                    mimeType = "image/png",
                    sizeBytes = 128L,
                    status = ItemStatus.SUCCESS,
                ),
                PersistedMessageAttachment(
                    id = "attachment-2",
                    kind = PersistedAttachmentKind.FILE,
                    displayName = "notes.pdf",
                    assetPath = "/assets/notes.pdf",
                    originalPath = "/tmp/notes.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 512L,
                    status = ItemStatus.SUCCESS,
                ),
            ),
        )
        repository.upsertActivityRecord(
            sessionId = "session-1",
            turnId = "turn-1",
            sourceId = "tool-1",
            kind = PersistedActivityKind.TOOL,
            title = "shell",
            body = "ls -la",
            status = ItemStatus.SUCCESS,
            timestamp = 2L,
        )
        repository.upsertMessageRecord(
            sessionId = "session-1",
            turnId = "turn-1",
            sourceId = "assistant-1",
            role = MessageRole.ASSISTANT,
            body = "done",
            status = ItemStatus.SUCCESS,
            timestamp = 3L,
        )

        val latestPage = repository.loadRecentTimeline(sessionId = "session-1", limit = 2)
        assertTrue(latestPage.hasOlder)
        assertEquals(
            listOf(
                PersistedTimelineRecordType.ACTIVITY,
                PersistedTimelineRecordType.MESSAGE,
            ),
            latestPage.entries.map { it.recordType },
        )
        assertTrue(latestPage.entries.all { it.attachments.isEmpty() })

        val olderPage = repository.loadTimelineBefore(
            sessionId = "session-1",
            beforeCursorExclusive = latestPage.entries.first().cursor,
            limit = 2,
        )
        assertFalse(olderPage.hasOlder)
        assertEquals(1, olderPage.entries.size)
        assertEquals("show me this image", olderPage.entries.single().body)
        assertEquals(2, olderPage.entries.single().attachments.size)
        assertEquals(
            listOf(PersistedAttachmentKind.IMAGE, PersistedAttachmentKind.FILE),
            olderPage.entries.single().attachments.map { it.kind },
        )

        val activeSession = assertNotNull(repository.loadActiveSession())
        assertEquals("session-1", activeSession.id)
    }
}
