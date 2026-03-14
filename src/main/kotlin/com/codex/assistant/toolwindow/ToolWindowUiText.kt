package com.codex.assistant.toolwindow

import kotlin.math.floor

object ToolWindowUiText {
    const val PRIMARY_TOOL_WINDOW_ID = "Codex Chat"
    const val COMPOSER_HINT = "@引入文件，#唤起智能体，↑输入提示词，Enter发送"

    fun selectionChipLabel(label: String): String {
        val normalized = label.trim()
        return normalized.substringAfter(':', normalized).trim()
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs < 1_000L) {
            return "${durationMs}ms"
        }

        val totalSeconds = floor(durationMs / 1_000.0).toLong()
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            hours > 0L -> "%dh %02dm".format(hours, minutes)
            minutes > 0L -> "%dm %02ds".format(minutes, seconds)
            else -> "${totalSeconds}s"
        }
    }

    fun runningStatus(elapsedMs: Long): String {
        return "执行中 · ${formatDuration(elapsedMs.coerceAtLeast(0L))}"
    }

    fun finishedStatus(label: String, durationMs: Long?): String {
        val normalized = label.trim().ifBlank { "完成" }
        val duration = durationMs?.takeIf { it >= 0L } ?: return normalized
        return "$normalized · ${formatDuration(duration)}"
    }
}
