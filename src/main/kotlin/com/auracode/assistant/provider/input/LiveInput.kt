package com.auracode.assistant.provider.input

import com.auracode.assistant.model.ContextFile
import com.auracode.assistant.model.FileAttachment
import com.auracode.assistant.model.ImageAttachment

/** 追加输入只携带内容；当前任务的模型、权限和计划模式保持原有配置。 */
internal data class LiveInput(
    val prompt: String,
    val systemInstructions: List<String> = emptyList(),
    val contextFiles: List<ContextFile> = emptyList(),
    val imageAttachments: List<ImageAttachment> = emptyList(),
    val fileAttachments: List<FileAttachment> = emptyList(),
)

/** Only an explicit inactive result permits starting a fresh turn; failures must never auto-resend. */
internal sealed interface LiveInputResult {
    data class Accepted(val turnId: String) : LiveInputResult
    data object Inactive : LiveInputResult
    data class Rejected(val message: String) : LiveInputResult
}
