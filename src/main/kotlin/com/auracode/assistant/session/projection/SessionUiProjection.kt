package com.auracode.assistant.session.projection

import com.auracode.assistant.session.kernel.SessionState
import com.auracode.assistant.session.projection.conversation.ConversationUiProjection
import com.auracode.assistant.session.projection.conversation.ConversationUiProjectionBuilder
import com.auracode.assistant.session.projection.execution.ExecutionUiProjection
import com.auracode.assistant.session.projection.execution.ExecutionUiProjectionBuilder
import com.auracode.assistant.session.projection.sessions.SessionNavigationUiProjection
import com.auracode.assistant.session.projection.sessions.SessionNavigationUiProjectionBuilder
import com.auracode.assistant.session.projection.submission.SubmissionUiProjection
import com.auracode.assistant.session.projection.submission.SubmissionUiProjectionBuilder

/**
 * Stores the aggregated UI projection derived from one immutable session state snapshot.
 */
internal data class SessionUiProjection(
    val conversation: ConversationUiProjection,
    val execution: ExecutionUiProjection,
    val submission: SubmissionUiProjection,
    val navigation: SessionNavigationUiProjection,
)

/**
 * Aggregates feature projections so toolwindow code consumes one read-only projection graph.
 */
internal class SessionUiProjectionBuilder(
    private val conversationProjectionBuilder: ConversationUiProjectionBuilder = ConversationUiProjectionBuilder(),
    private val executionProjectionBuilder: ExecutionUiProjectionBuilder = ExecutionUiProjectionBuilder(),
    private val submissionProjectionBuilder: SubmissionUiProjectionBuilder = SubmissionUiProjectionBuilder(),
    private val navigationProjectionBuilder: SessionNavigationUiProjectionBuilder = SessionNavigationUiProjectionBuilder(),
) {
    fun projectConversation(
        state: SessionState,
        changedEntryIds: Set<String>? = null,
    ): ConversationUiProjection = conversationProjectionBuilder.project(state, changedEntryIds)

    fun dropConversation(sessionId: String) = conversationProjectionBuilder.drop(sessionId)

    fun retainConversations(sessionIds: Set<String>) = conversationProjectionBuilder.retain(sessionIds)

    fun projectExecution(state: SessionState): ExecutionUiProjection = executionProjectionBuilder.project(state)

    fun projectSubmission(state: SessionState): SubmissionUiProjection = submissionProjectionBuilder.project(state)

    fun projectNavigation(state: SessionState): SessionNavigationUiProjection = navigationProjectionBuilder.project(state)

    /** Projects one immutable kernel snapshot into the read-only UI projection graph. */
    fun project(state: SessionState): SessionUiProjection {
        return SessionUiProjection(
            conversation = projectConversation(state),
            execution = projectExecution(state),
            submission = projectSubmission(state),
            navigation = projectNavigation(state),
        )
    }
}
