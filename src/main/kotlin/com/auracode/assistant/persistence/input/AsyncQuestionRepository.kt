package com.auracode.assistant.persistence.input

/** Local interaction state supplements native history, which has no async-answer acknowledgement. */
internal data class PersistedAsyncQuestion(
    val sessionId: String,
    val threadId: String,
    val requestId: String,
    val requestJson: String,
    val status: String? = null,
    val responseSummary: String? = null,
)

internal interface AsyncQuestionRepository {
    fun saveAsyncQuestion(question: PersistedAsyncQuestion)
    fun loadAsyncQuestions(sessionId: String, threadId: String): List<PersistedAsyncQuestion>
    fun deleteAsyncQuestions(sessionId: String)
}
