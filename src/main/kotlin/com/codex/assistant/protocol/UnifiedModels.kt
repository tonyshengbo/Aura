package com.codex.assistant.protocol

enum class TurnOutcome {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

enum class ItemKind {
    NARRATIVE,
    TOOL_CALL,
    COMMAND_EXEC,
    DIFF_APPLY,
    APPROVAL_REQUEST,
    PLAN_UPDATE,
    UNKNOWN,
}

enum class ItemStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
}

enum class ApprovalDecision {
    PENDING,
    APPROVED,
    REJECTED,
}

enum class UnifiedApprovalRequestKind {
    COMMAND,
    FILE_CHANGE,
    PERMISSIONS,
}

data class UnifiedApprovalRequest(
    val requestId: String,
    val turnId: String?,
    val itemId: String,
    val kind: UnifiedApprovalRequestKind,
    val title: String,
    val body: String,
    val command: String? = null,
    val cwd: String? = null,
    val fileChanges: List<UnifiedFileChange> = emptyList(),
    val permissions: List<String> = emptyList(),
    val allowForSession: Boolean = true,
)

data class TurnUsage(
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
)

data class UnifiedFileChange(
    val path: String,
    val kind: String,
    val oldContent: String? = null,
    val newContent: String? = null,
)

data class UnifiedMessageAttachment(
    val id: String,
    val kind: String,
    val displayName: String,
    val assetPath: String,
    val originalPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: ItemStatus,
)

data class UnifiedItem(
    val id: String,
    val kind: ItemKind,
    val status: ItemStatus,
    val name: String? = null,
    val text: String? = null,
    val command: String? = null,
    val cwd: String? = null,
    val filePath: String? = null,
    val fileChanges: List<UnifiedFileChange> = emptyList(),
    val attachments: List<UnifiedMessageAttachment> = emptyList(),
    val exitCode: Int? = null,
    val approvalDecision: ApprovalDecision? = null,
)

sealed class UnifiedEvent {
    data class ApprovalRequested(
        val request: UnifiedApprovalRequest,
    ) : UnifiedEvent()

    data class ThreadStarted(
        val threadId: String,
        val resumedFromTurnId: String? = null,
    ) : UnifiedEvent()

    data class TurnStarted(
        val turnId: String,
        val threadId: String? = null,
    ) : UnifiedEvent()

    data class TurnCompleted(
        val turnId: String,
        val outcome: TurnOutcome,
        val usage: TurnUsage? = null,
    ) : UnifiedEvent()

    data class ItemUpdated(
        val item: UnifiedItem,
    ) : UnifiedEvent()

    data class Error(
        val message: String,
    ) : UnifiedEvent()
}
