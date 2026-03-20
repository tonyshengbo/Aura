package com.auracode.assistant.persistence.chat

import com.auracode.assistant.model.MessageRole
import com.auracode.assistant.protocol.ItemStatus
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SQLiteChatSessionRepositoryTest {
    @Test
    fun `stores session assets and updates local turn ids after remote turn starts`() {
        val repository = SQLiteChatSessionRepository(createTempDirectory("chat-repository-test").resolve("chat.db"))
        repository.upsertSession(
            PersistedChatSession(
                id = "session-1",
                title = "First session",
                createdAt = 10L,
                updatedAt = 20L,
                messageCount = 1,
                providerId = "codex",
                remoteConversationId = "thread-1",
                usageSnapshot = null,
                isActive = true,
            ),
        )

        repository.saveSessionAssets(
            sessionId = "session-1",
            turnId = "local-turn-1",
            messageRole = MessageRole.USER,
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
            createdAt = 30L,
        )

        repository.replaceSessionAssetTurnId(
            sessionId = "session-1",
            fromTurnId = "local-turn-1",
            toTurnId = "turn-1",
        )

        val assets = repository.loadSessionAssets("session-1")
        assertEquals(2, assets.size)
        assertTrue(assets.all { it.turnId == "turn-1" })
        assertEquals(
            listOf(PersistedAttachmentKind.IMAGE, PersistedAttachmentKind.FILE),
            assets.map { it.attachment.kind },
        )

        val activeSession = assertNotNull(repository.loadActiveSession())
        assertEquals("session-1", activeSession.id)
    }

    @Test
    fun `deleting a session cascades stored assets`() {
        val repository = SQLiteChatSessionRepository(createTempDirectory("chat-repository-delete").resolve("chat.db"))
        repository.upsertSession(
            PersistedChatSession(
                id = "session-1",
                title = "",
                createdAt = 1L,
                updatedAt = 1L,
                messageCount = 0,
                providerId = "codex",
                remoteConversationId = "",
                usageSnapshot = null,
                isActive = true,
            ),
        )
        repository.saveSessionAssets(
            sessionId = "session-1",
            turnId = "turn-1",
            messageRole = MessageRole.USER,
            attachments = listOf(
                PersistedMessageAttachment(
                    id = "attachment-1",
                    kind = PersistedAttachmentKind.TEXT,
                    displayName = "hello.txt",
                    assetPath = "/assets/hello.txt",
                    originalPath = "/tmp/hello.txt",
                    mimeType = "text/plain",
                    sizeBytes = 5L,
                ),
            ),
            createdAt = 2L,
        )

        repository.deleteSession("session-1")

        assertEquals(null, repository.loadSession("session-1"))
        assertTrue(repository.loadSessionAssets("session-1").isEmpty())
    }
}
