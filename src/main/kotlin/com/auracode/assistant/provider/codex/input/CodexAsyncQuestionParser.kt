package com.auracode.assistant.provider.codex.input

import com.auracode.assistant.protocol.ProviderToolUserInputOption
import com.auracode.assistant.protocol.ProviderToolUserInputPrompt
import com.auracode.assistant.protocol.ProviderToolUserInputQuestion
import com.auracode.assistant.protocol.UserInputDelivery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reads native async question metadata. Ordinary message text is never guessed to be a form. */
internal object CodexAsyncQuestionParser {
    fun isAsyncQuestion(item: JsonObject): Boolean =
        item.string("type").equals("agentMessage", ignoreCase = true) &&
            item.string("delivery") == "async" && item["questions"] is JsonArray

    fun parse(item: JsonObject, threadId: String?, turnId: String?): ProviderToolUserInputPrompt? {
        if (!isAsyncQuestion(item)) return null
        val itemId = item.string("id")?.takeIf(String::isNotBlank) ?: return null
        val questions = (item["questions"] as JsonArray).mapIndexedNotNull { index, element ->
            val question = element as? JsonObject ?: return@mapIndexedNotNull null
            val title = question.string("title")?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            ProviderToolUserInputQuestion(
                id = "$itemId:question:$index",
                header = "",
                question = title,
                options = (question["options"] as? JsonArray).orEmpty().mapNotNull { option ->
                    (option as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                        ?.let { ProviderToolUserInputOption(it, "") }
                },
                isOther = true,
            )
        }
        if (questions.isEmpty()) return null
        return ProviderToolUserInputPrompt(
            requestId = itemId,
            threadId = threadId.orEmpty(),
            turnId = turnId,
            itemId = itemId,
            questions = questions,
            delivery = UserInputDelivery.ASYNC,
        )
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}
