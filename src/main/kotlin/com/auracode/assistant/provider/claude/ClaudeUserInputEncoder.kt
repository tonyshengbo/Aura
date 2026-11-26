package com.auracode.assistant.provider.claude

import com.auracode.assistant.model.AgentRequest
import java.io.File
import java.util.Base64
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Shared multimodal wire encoder for the first prompt and live follow-ups. */
internal object ClaudeUserInputEncoder {
    fun buildPromptText(request: AgentRequest): String {
        val promptBody = request.prompt.trim()
        val contextSection = request.contextFiles
            .map { context ->
                buildString {
                    append("Path: ").append(context.path)
                    context.content?.trim()?.takeIf { it.isNotBlank() }?.let { content ->
                        append("\n```")
                        append("\n")
                        append(content)
                        append("\n```")
                    }
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n\n", prefix = "Context files:\n", postfix = "\n\n")
            .orEmpty()
        val fileAttachmentLines = request.fileAttachments.map { "File: ${it.name} (${it.path})" }
        val attachmentSection = fileAttachmentLines
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n", prefix = "Local attachments:\n", postfix = "\n\n")
            .orEmpty()
        val explicitPrompt = promptBody.ifBlank { "Follow the provided system instructions and context." }
        return buildString {
            append(contextSection)
            append(attachmentSection)
            append(explicitPrompt)
        }
    }

    fun encode(
        request: AgentRequest,
        messageId: String = request.requestId,
        includeInstructions: Boolean = false,
    ): String {
        val textContent = buildString {
            append(buildPromptText(request))
            if (includeInstructions && request.systemInstructions.isNotEmpty()) {
                append("\n\nAdditional instructions:\n")
                append(request.systemInstructions.joinToString("\n\n"))
            }
        }
        val contentArray = buildJsonArray {
            if (textContent.isNotBlank()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", textContent)
                })
            }
            request.imageAttachments.forEach { attachment ->
                val imageFile = File(attachment.path)
                require(imageFile.isFile) { "Image attachment is unavailable: ${attachment.name}" }
                val base64Data = Base64.getEncoder().encodeToString(imageFile.readBytes())
                add(buildJsonObject {
                    put("type", "image")
                    put("source", buildJsonObject {
                        put("type", "base64")
                        put("media_type", attachment.mimeType)
                        put("data", base64Data)
                    })
                })
            }
        }
        val message = buildJsonObject {
            put("type", "user")
            put("uuid", messageId)
            put("message", buildJsonObject {
                put("role", "user")
                put("content", contentArray)
            })
        }
        return message.toString()
    }
}
