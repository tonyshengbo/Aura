package com.auracode.assistant.toolwindow.conversation.title

/** 标题文字和可操作目标的范围，与排版及鼠标事件分离；endExclusive 使用 UTF-16 偏移。 */
internal data class ConversationTitleModel(
    val text: String,
    val targetPath: String? = null,
    val targetStart: Int = 0,
    val targetEndExclusive: Int = 0,
) {
    fun targetAt(offset: Int, visibleEndExclusive: Int): String? =
        targetPath?.takeIf { offset in targetStart until minOf(targetEndExclusive, visibleEndExclusive) }
}

internal fun conversationTitleModel(
    title: String,
    targetLabel: String?,
    targetPath: String?,
): ConversationTitleModel {
    if (targetLabel.isNullOrBlank() || targetPath.isNullOrBlank()) return ConversationTitleModel(title)
    val existingStart = title.indexOf(targetLabel)
    if (existingStart >= 0) {
        return ConversationTitleModel(title, targetPath, existingStart, existingStart + targetLabel.length)
    }
    val prefix = title.trimEnd().let { if (it.isEmpty()) "" else "$it " }
    return ConversationTitleModel(prefix + targetLabel, targetPath, prefix.length, prefix.length + targetLabel.length)
}
