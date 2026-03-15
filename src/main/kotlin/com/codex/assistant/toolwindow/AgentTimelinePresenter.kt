package com.codex.assistant.toolwindow

import com.codex.assistant.model.TimelineAction
import com.codex.assistant.toolwindow.timeline.LiveCommandTrace
import com.codex.assistant.toolwindow.timeline.LiveNarrativeTrace
import com.codex.assistant.toolwindow.timeline.LiveToolTrace
import com.codex.assistant.toolwindow.timeline.LiveTurnSnapshot
import com.codex.assistant.toolwindow.timeline.TimelineNodeKind
import com.codex.assistant.toolwindow.timeline.TimelineNodeOrigin
import com.codex.assistant.toolwindow.timeline.TimelineNodeStatus
import java.util.Base64

internal class AgentTimelinePresenter {
    data class NarrativeTraceView(
        val id: String,
        val sequence: Int,
        val body: String,
        val origin: TimelineNodeOrigin,
        val startedAtMs: Long,
        val source: String,
    )

    data class ToolTraceView(
        val id: String,
        val name: String,
        val sequence: Int,
        val input: String,
        val output: String,
        val done: Boolean,
        val failed: Boolean,
        val startedAtMs: Long,
        val finishedAtMs: Long,
    )

    data class CommandTraceView(
        val id: String,
        val sequence: Int,
        val status: String,
        val command: String,
        val cwd: String,
        val startedAtMs: Long,
        val finishedAtMs: Long,
        val exitCode: Int?,
        val output: String,
    )

    fun buildAssistantContentFromTimelineActions(actions: List<TimelineAction>): String {
        val narratives = actions
            .filterIsInstance<TimelineAction.AppendNarrative>()
            .sortedBy { it.sequence }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
        if (narratives.isNotEmpty()) {
            return narratives.joinToString("\n\n")
        }

        val failures = actions
            .filterIsInstance<TimelineAction.MarkTurnFailed>()
            .map { it.message.trim() }
            .filter { it.isNotBlank() }
        if (failures.isNotEmpty()) {
            return failures.joinToString("\n\n")
        }

        return when {
            actions.any { it is TimelineAction.UpsertCommand } -> "已记录命令执行。"
            actions.any { it is TimelineAction.UpsertTool } -> "已记录工具执行。"
            else -> ""
        }
    }

    fun buildLiveTurnSnapshot(
        turnState: AgentTurnState,
        isRunning: Boolean,
        assistantContent: String,
        thinkingContent: String,
        narratives: List<NarrativeTraceView>,
        tools: List<ToolTraceView>,
        commands: List<CommandTraceView>,
    ): LiveTurnSnapshot? {
        if (turnState.finalized) return null
        if (turnState.hasActions()) {
            return LiveTurnSnapshot(
                statusText = turnState.statusText,
                actions = turnState.snapshotActions(),
                isRunning = isRunning,
                startedAtMs = turnState.startedAtMs.takeIf { it > 0L },
            )
        }

        val content = assistantContent.trim()
        val thinking = thinkingContent.trim()
        val hasLiveContent = turnState.active ||
            turnState.statusText.isNotBlank() ||
            content.isNotBlank() ||
            thinking.isNotBlank() ||
            tools.isNotEmpty() ||
            commands.isNotEmpty()
        if (!hasLiveContent) return null

        return LiveTurnSnapshot(
            statusText = turnState.statusText,
            assistantContent = content,
            thinking = thinking,
            notes = narratives
                .filter { it.body.isNotBlank() }
                .map { trace ->
                    LiveNarrativeTrace(
                        id = trace.id,
                        body = trace.body.trim(),
                        sequence = trace.sequence,
                        origin = trace.origin,
                        kind = TimelineNodeKind.ASSISTANT_NOTE,
                        timestamp = trace.startedAtMs.takeIf { it > 0L },
                    )
                },
            tools = tools.map { trace ->
                LiveToolTrace(
                    id = trace.id,
                    name = trace.name,
                    input = trace.input,
                    output = trace.output,
                    status = when {
                        trace.failed -> TimelineNodeStatus.FAILED
                        trace.done -> TimelineNodeStatus.SUCCESS
                        else -> TimelineNodeStatus.RUNNING
                    },
                    sequence = trace.sequence,
                    startedAtMs = trace.startedAtMs.takeIf { it > 0L },
                )
            },
            commands = commands.map { trace ->
                LiveCommandTrace(
                    id = trace.id,
                    command = trace.command,
                    cwd = trace.cwd,
                    output = trace.output,
                    status = when (trace.status) {
                        "DONE" -> TimelineNodeStatus.SUCCESS
                        "FAILED" -> TimelineNodeStatus.FAILED
                        "SKIPPED" -> TimelineNodeStatus.SKIPPED
                        else -> TimelineNodeStatus.RUNNING
                    },
                    sequence = trace.sequence,
                    exitCode = trace.exitCode,
                    startedAtMs = trace.startedAtMs.takeIf { it > 0L },
                )
            },
            isRunning = isRunning,
            startedAtMs = turnState.startedAtMs.takeIf { it > 0L },
        )
    }

    fun buildAssistantStructuredMessage(
        content: String,
        thinking: String,
        tools: List<ToolTraceView>,
        commands: List<CommandTraceView>,
        narratives: List<NarrativeTraceView>,
        includeThinking: Boolean,
    ): String {
        val sections = mutableListOf<String>()
        if (includeThinking && thinking.isNotBlank()) {
            sections.add("### Thinking\n$thinking")
        }
        if (content.isNotBlank()) {
            sections.add("### Response\n$content")
        }
        val narrativeLines = serializeNarratives(narratives)
        if (narrativeLines.isNotEmpty()) {
            sections.add("### Narrative\n${narrativeLines.joinToString("\n") { "- $it" }}")
        }
        if (tools.isNotEmpty()) {
            val toolLines = tools.joinToString("\n") { "- ${serializeToolTrace(it)}" }
            sections.add("### Tools\n$toolLines")
        }
        if (commands.isNotEmpty()) {
            val commandLines = commands.joinToString("\n") { "- ${serializeCommandTrace(it)}" }
            sections.add("### Commands\n$commandLines")
        }
        return sections.joinToString("\n\n").trim()
    }

    private fun serializeNarratives(narratives: List<NarrativeTraceView>): List<String> {
        val cleaned = narratives
            .map { it.copy(body = it.body.trim()) }
            .filter { it.body.isNotBlank() }
        if (cleaned.isEmpty()) return emptyList()
        val lastContentIndex = cleaned.indexOfLast { it.source == "CONTENT" }
        return cleaned.mapIndexed { index, trace ->
            val kind = if (index == lastContentIndex) "result" else "note"
            val body64 = Base64.getEncoder().encodeToString(trace.body.toByteArray(Charsets.UTF_8))
            buildString {
                append(kind)
                append(" | id:").append(trace.id)
                append(" | seq:").append(trace.sequence)
                append(" | origin:").append(
                    when (trace.origin) {
                        TimelineNodeOrigin.EVENT -> "event"
                        TimelineNodeOrigin.INFERRED_RESPONSE -> "inferred_response"
                    },
                )
                if (trace.startedAtMs > 0L) {
                    append(" | ts:").append(trace.startedAtMs)
                }
                append(" | body64:").append(body64)
            }
        }
    }

    private fun serializeToolTrace(trace: ToolTraceView): String {
        val status = when {
            trace.failed -> "failed"
            trace.done -> "done"
            else -> "running"
        }
        val parts = mutableListOf(status, "id:${trace.id}", trace.name)
        parts.add("seq:${trace.sequence}")
        if (trace.startedAtMs > 0L) {
            parts.add("ts:${trace.startedAtMs}")
        }
        if (trace.finishedAtMs > 0L && trace.startedAtMs > 0L) {
            parts.add("dur:${(trace.finishedAtMs - trace.startedAtMs).coerceAtLeast(0L)}")
        }
        if (trace.input.isNotBlank()) {
            parts.add("in: ${trace.input}")
        }
        if (trace.output.isNotBlank()) {
            parts.add("out: ${trace.output}")
        }
        return parts.joinToString(" | ")
    }

    private fun serializeCommandTrace(trace: CommandTraceView): String {
        val parts = mutableListOf("command", "id:${trace.id}", "status:${trace.status}")
        parts.add("seq:${trace.sequence}")
        parts.add("cmd: ${trace.command}")
        if (trace.cwd.isNotBlank()) {
            parts.add("cwd: ${trace.cwd}")
        }
        if (trace.startedAtMs > 0L) {
            parts.add("ts:${trace.startedAtMs}")
        }
        if (trace.finishedAtMs > 0L && trace.startedAtMs > 0L) {
            parts.add("dur:${(trace.finishedAtMs - trace.startedAtMs).coerceAtLeast(0L)}")
        }
        trace.exitCode?.let { parts.add("exit:$it") }
        if (trace.output.isNotBlank()) {
            parts.add("out: ${trace.output}")
        }
        return parts.joinToString(" | ")
    }
}
