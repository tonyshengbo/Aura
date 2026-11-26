package com.auracode.assistant.toolwindow.eventing

import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.provider.input.LiveInputResult
import com.auracode.assistant.session.kernel.SessionActivityStatus
import com.auracode.assistant.session.kernel.SessionDomainEvent
import com.auracode.assistant.session.kernel.SessionInputDelivery
import com.auracode.assistant.toolwindow.execution.toSubmissionAnswers
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/** Owns async question answers; synchronous RPC input remains in PlanFlowHandler. */
internal class AsyncQuestionFlowHandler(
    private val context: ToolWindowCoordinatorContext,
    private val conversationHandler: ConversationFlowHandler,
) {
    private val submitting = ConcurrentHashMap.newKeySet<String>()

    fun submitIfAsync(skip: Boolean): Boolean {
        val state = context.toolUserInputPromptStore.state.value
        val question = state.current ?: return false
        if (question.delivery != SessionInputDelivery.ASYNC) return false
        if (state.submitting) return true
        val answers = state.toSubmissionAnswers()
        if (!skip && !state.canSubmit) return true
        val sessionId = context.activeSessionId()
        val settings = context.submissionStore.state.value
        val key = "$sessionId:${question.requestId}"
        if (!submitting.add(key)) return true
        val summary = if (skip) {
            question.questions.joinToString("\n\n") { it.question } + "\n\n" + AuraCodeBundle.message("toolInput.async.skipped")
        } else {
            question.questions.joinToString("\n\n") { item ->
                "${item.question}\n${answers[item.id]?.answers.orEmpty().joinToString(", ")}"
            }
        }
        context.dispatchSessionEvent(sessionId, AppEvent.ToolUserInputSubmissionUpdated(question.threadId, question.requestId, true))
        context.coroutineLauncher.launch("asyncQuestion:$key") {
            var errorMessage: String? = null
            try {
                check(context.chatService.ownsAsyncQuestion(sessionId, question.threadId)) {
                    AuraCodeBundle.message("toolInput.async.sessionChanged")
                }
                if (!skip) {
                    val answerText = "Answer to question ${question.itemId}:\n\n$summary"
                    when (val result = conversationHandler.sendAsyncAnswer(sessionId, question.threadId, answerText, settings)) {
                        is LiveInputResult.Accepted -> Unit
                        is LiveInputResult.Rejected -> error(result.message)
                        LiveInputResult.Inactive -> error(AuraCodeBundle.message("toolInput.async.sessionChanged"))
                    }
                }
                val status = if (skip) SessionActivityStatus.SKIPPED else SessionActivityStatus.SUCCESS
                context.chatService.resolveAsyncQuestion(sessionId, question.threadId, question.requestId, status, summary)
                context.applySessionDomainEvents(sessionId, listOf(
                    SessionDomainEvent.ToolUserInputResolved(question.requestId, status, summary),
                ))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = AuraCodeBundle.message("toolInput.async.failed", error.message.orEmpty())
            } finally {
                submitting.remove(key)
                context.dispatchSessionEvent(sessionId, AppEvent.ToolUserInputSubmissionUpdated(
                    question.threadId, question.requestId, false, errorMessage,
                ))
            }
        }
        return true
    }
}
