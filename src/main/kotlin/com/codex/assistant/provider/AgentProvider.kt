package com.codex.assistant.provider

import com.codex.assistant.conversation.ConversationCapabilities
import com.codex.assistant.conversation.ConversationHistoryPage
import com.codex.assistant.conversation.ConversationRef
import com.codex.assistant.conversation.ConversationSummaryPage
import com.codex.assistant.model.AgentRequest
import com.codex.assistant.protocol.UnifiedEvent
import com.codex.assistant.toolwindow.approval.ApprovalAction
import kotlinx.coroutines.flow.Flow

interface AgentProvider {
    val providerId: String
        get() = CodexProviderFactory.ENGINE_ID
    fun stream(request: AgentRequest): Flow<UnifiedEvent>
    suspend fun loadInitialHistory(ref: ConversationRef, pageSize: Int): ConversationHistoryPage =
        ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)

    suspend fun loadOlderHistory(ref: ConversationRef, cursor: String, pageSize: Int): ConversationHistoryPage =
        ConversationHistoryPage(events = emptyList(), hasOlder = false, olderCursor = null)

    suspend fun listRemoteConversations(
        pageSize: Int,
        cursor: String? = null,
        cwd: String? = null,
        searchTerm: String? = null,
    ): ConversationSummaryPage = ConversationSummaryPage(conversations = emptyList(), nextCursor = null)

    fun capabilities(): ConversationCapabilities = ConversationCapabilities(
        supportsStructuredHistory = false,
        supportsHistoryPagination = false,
        supportsPlanMode = false,
        supportsApprovalRequests = false,
        supportsToolUserInput = false,
        supportsResume = true,
        supportsAttachments = true,
        supportsImageInputs = true,
    )
    fun submitApprovalDecision(requestId: String, decision: ApprovalAction): Boolean = false
    fun cancel(requestId: String)
}
