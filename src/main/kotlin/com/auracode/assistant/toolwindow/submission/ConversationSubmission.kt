package com.auracode.assistant.toolwindow.submission

import androidx.compose.ui.text.input.TextFieldValue
import com.auracode.assistant.model.ContextFile
import com.auracode.assistant.model.FileAttachment
import com.auracode.assistant.model.ImageAttachment
import com.auracode.assistant.persistence.chat.PersistedMessageAttachment
import com.auracode.assistant.toolwindow.eventing.SubmissionMode
import com.auracode.assistant.toolwindow.eventing.SubmissionReasoning

/** Immutable content/settings captured by one send action, never retained as a pending message. */
internal data class ConversationSubmission(
    val prompt: String,
    val systemInstructions: List<String>,
    val contextFiles: List<ContextFile>,
    val imageAttachments: List<ImageAttachment>,
    val fileAttachments: List<FileAttachment>,
    val stagedAttachments: List<PersistedMessageAttachment>,
    val selectedModel: String,
    val selectedReasoning: SubmissionReasoning,
    val executionMode: SubmissionMode,
    val planEnabled: Boolean,
    val engineId: String = "codex",
    val clearSubmissionDraftOnAccept: Boolean = true,
)

/** Identifies the exact draft acknowledged by an asynchronous live-input write. */
internal data class SubmissionDraftSnapshot(
    val document: TextFieldValue,
    val version: Long,
    val attachments: List<AttachmentEntry>,
    val context: List<ContextEntry>,
    val agents: List<AgentContextEntry>,
    val mentions: List<MentionEntry>,
)

internal fun SubmissionAreaState.draftSnapshot() = SubmissionDraftSnapshot(
    document = document,
    version = documentVersion,
    attachments = attachments,
    context = manualContextEntries,
    agents = agentEntries,
    mentions = mentionEntries,
)
