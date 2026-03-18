package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.protocol.TurnOutcome

internal class TimelineNodeReducer {
    var state: TimelineAreaState = TimelineAreaState()
        private set

    private var activeThreadId: String? = null
    private var activeTurnId: String? = null
    private var syntheticTurnCount: Int = 0

    fun accept(mutation: TimelineMutation) {
        state = when (mutation) {
            is TimelineMutation.ThreadStarted -> {
                activeThreadId = mutation.threadId
                state
            }

            is TimelineMutation.TurnStarted -> acceptTurnStarted(mutation)
            is TimelineMutation.UpsertMessage -> acceptMessage(mutation)
            is TimelineMutation.UpsertActivity -> acceptActivity(mutation)
            is TimelineMutation.TurnCompleted -> acceptTurnCompleted(mutation)
            is TimelineMutation.Error -> acceptError(mutation)
        }.withLiveRender()
    }

    fun replaceHistory(
        nodes: List<TimelineNode>,
        oldestCursor: Long?,
        hasOlder: Boolean,
    ) {
        state = state.copy(
            nodes = decorateHistoryNodes(nodes = nodes, hasOlder = hasOlder, isLoadingOlder = false),
            oldestCursor = oldestCursor,
            hasOlder = hasOlder,
            isLoadingOlder = false,
            renderVersion = state.renderVersion + 1,
            renderCause = TimelineRenderCause.HISTORY_RESET,
            prependedCount = 0,
        )
    }

    fun prependHistory(
        nodes: List<TimelineNode>,
        oldestCursor: Long?,
        hasOlder: Boolean,
    ) {
        val existing = state.nodes.filterNot { it is TimelineNode.LoadMoreNode }
        state = state.copy(
            nodes = decorateHistoryNodes(
                nodes = nodes.filterNot { it is TimelineNode.LoadMoreNode } + existing,
                hasOlder = hasOlder,
                isLoadingOlder = false,
            ),
            oldestCursor = oldestCursor,
            hasOlder = hasOlder,
            isLoadingOlder = false,
            renderVersion = state.renderVersion + 1,
            renderCause = TimelineRenderCause.HISTORY_PREPEND,
            prependedCount = nodes.count { it !is TimelineNode.LoadMoreNode },
        )
    }

    fun setLoadingOlder(loading: Boolean) {
        state = state.copy(
            nodes = decorateHistoryNodes(
                nodes = state.nodes.filterNot { it is TimelineNode.LoadMoreNode },
                hasOlder = state.hasOlder,
                isLoadingOlder = loading,
            ),
            isLoadingOlder = loading,
        )
    }

    fun reset() {
        activeThreadId = null
        activeTurnId = null
        syntheticTurnCount = 0
        state = TimelineAreaState()
    }

    private fun acceptTurnStarted(mutation: TimelineMutation.TurnStarted): TimelineAreaState {
        if (!mutation.threadId.isNullOrBlank()) {
            activeThreadId = mutation.threadId
        }
        val promotedTurnId = activeTurnId?.takeIf(::isSyntheticTurnId)
        val nextNodes = if (promotedTurnId != null) {
            state.nodes.map { node -> node.replaceTurnId(promotedTurnId, mutation.turnId) }
        } else {
            state.nodes
        }
        activeTurnId = mutation.turnId
        return state.copy(
            nodes = nextNodes,
            isRunning = true,
            latestError = null,
        )
    }

    private fun acceptMessage(mutation: TimelineMutation.UpsertMessage): TimelineAreaState {
        val turnId = resolveTurnId(mutation.turnId)
        val existing = state.nodes.filterIsInstance<TimelineNode.MessageNode>().firstOrNull {
            it.sourceId == mutation.sourceId && it.turnId == turnId
        }
        val node = TimelineNode.MessageNode(
            id = messageNodeId(turnId, mutation.sourceId),
            sourceId = mutation.sourceId,
            role = mutation.role,
            text = mutation.text,
            status = mutation.status,
            timestamp = mutation.timestamp,
            turnId = turnId,
            cursor = mutation.cursor,
            attachments = if (mutation.attachments.isNotEmpty()) mutation.attachments else existing?.attachments.orEmpty(),
        )
        val nextNodes = upsertNode(state.nodes, node, turnId)
        return state.copy(
            nodes = nextNodes,
            isRunning = shouldKeepRunning(mutation.status, turnId),
            latestError = null,
        )
    }

    private fun acceptActivity(mutation: TimelineMutation.UpsertActivity): TimelineAreaState {
        val turnId = resolveTurnId(mutation.turnId)
        val node = TimelineNode.ActivityNode(
            id = activityNodeId(turnId, mutation.sourceId),
            sourceId = mutation.sourceId,
            kind = mutation.kind,
            title = mutation.title,
            body = mutation.body,
            status = mutation.status,
            turnId = turnId,
        )
        val nextNodes = upsertNode(state.nodes, node, turnId)
        return state.copy(
            nodes = nextNodes,
            isRunning = shouldKeepRunning(mutation.status, turnId),
            latestError = null,
        )
    }

    private fun acceptTurnCompleted(mutation: TimelineMutation.TurnCompleted): TimelineAreaState {
        val targetTurnId = resolveCompletionTurnId(mutation.turnId)
        if (targetTurnId != null && activeTurnId == targetTurnId) {
            activeTurnId = null
        }
        val nextNodes = finalizeRunningNodes(
            nodes = state.nodes,
            turnId = targetTurnId,
            status = mutation.outcome.toItemStatus(),
        )
        return state.copy(
            nodes = nextNodes,
            isRunning = false,
        )
    }

    private fun acceptError(mutation: TimelineMutation.Error): TimelineAreaState {
        val nextNodes = finalizeRunningNodes(
            nodes = state.nodes,
            turnId = activeTurnId,
            status = ItemStatus.FAILED,
        )
        activeTurnId = null
        return state.copy(
            nodes = nextNodes,
            isRunning = false,
            latestError = mutation.message,
        )
    }

    private fun resolveTurnId(explicitTurnId: String?): String? {
        val normalized = explicitTurnId?.trim().orEmpty()
        if (normalized.isNotBlank()) {
            activeTurnId = normalized
            return normalized
        }
        if (!activeTurnId.isNullOrBlank()) {
            return activeTurnId
        }
        return nextSyntheticTurnId().also { generated ->
            activeTurnId = generated
        }
    }

    private fun resolveCompletionTurnId(turnId: String): String? {
        val normalized = turnId.trim()
        if (normalized.isNotBlank()) return normalized
        if (!activeTurnId.isNullOrBlank()) return activeTurnId
        return state.nodes.asReversed().firstNotNullOfOrNull { node ->
            when (node) {
                is TimelineNode.MessageNode -> node.turnId?.takeIf { node.status == ItemStatus.RUNNING }
                is TimelineNode.ActivityNode -> node.turnId?.takeIf { node.status == ItemStatus.RUNNING }
                is TimelineNode.LoadMoreNode -> null
            }
        }
    }

    private fun finalizeRunningNodes(
        nodes: List<TimelineNode>,
        turnId: String?,
        status: ItemStatus,
    ): List<TimelineNode> {
        return nodes.map { node ->
            when (node) {
                is TimelineNode.MessageNode -> {
                    if (node.status == ItemStatus.RUNNING && (turnId == null || node.turnId == turnId)) {
                        node.copy(status = status)
                    } else {
                        node
                    }
                }

                is TimelineNode.ActivityNode -> {
                    if (node.status == ItemStatus.RUNNING && (turnId == null || node.turnId == turnId)) {
                        node.copy(status = status)
                    } else {
                        node
                    }
                }

                is TimelineNode.LoadMoreNode -> node
            }
        }
    }

    private fun upsertNode(
        nodes: List<TimelineNode>,
        node: TimelineNode,
        turnId: String?,
    ): List<TimelineNode> {
        val next = nodes.toMutableList()
        val existingIndex = next.indexOfFirst { candidate ->
            when {
                candidate is TimelineNode.MessageNode && node is TimelineNode.MessageNode ->
                    candidate.sourceId == node.sourceId && candidate.turnId == turnId

                candidate is TimelineNode.ActivityNode && node is TimelineNode.ActivityNode ->
                    candidate.sourceId == node.sourceId && candidate.turnId == turnId

                else -> false
            }
        }
        if (existingIndex >= 0) {
            next[existingIndex] = node
        } else {
            next += node
        }
        return next
    }

    private fun decorateHistoryNodes(
        nodes: List<TimelineNode>,
        hasOlder: Boolean,
        isLoadingOlder: Boolean,
    ): List<TimelineNode> {
        val contentNodes = nodes.filterNot { it is TimelineNode.LoadMoreNode }
        return if (hasOlder || isLoadingOlder) {
            listOf(TimelineNode.LoadMoreNode(isLoading = isLoadingOlder)) + contentNodes
        } else {
            contentNodes
        }
    }

    private fun shouldKeepRunning(status: ItemStatus, turnId: String?): Boolean {
        return status == ItemStatus.RUNNING || !turnId.isNullOrBlank()
    }

    private fun TimelineAreaState.withLiveRender(): TimelineAreaState {
        return copy(
            renderVersion = renderVersion + 1,
            renderCause = TimelineRenderCause.LIVE_UPDATE,
            prependedCount = 0,
        )
    }

    private fun TimelineNode.replaceTurnId(
        fromTurnId: String,
        toTurnId: String,
    ): TimelineNode {
        return when (this) {
            is TimelineNode.MessageNode -> {
                if (turnId != fromTurnId) this else copy(
                    id = messageNodeId(toTurnId, sourceId),
                    turnId = toTurnId,
                )
            }

            is TimelineNode.ActivityNode -> {
                if (turnId != fromTurnId) this else copy(
                    id = activityNodeId(toTurnId, sourceId),
                    turnId = toTurnId,
                )
            }

            is TimelineNode.LoadMoreNode -> this
        }
    }

    private fun TurnOutcome.toItemStatus(): ItemStatus {
        return when (this) {
            TurnOutcome.SUCCESS -> ItemStatus.SUCCESS
            TurnOutcome.CANCELLED,
            TurnOutcome.FAILED,
            -> ItemStatus.FAILED

            TurnOutcome.RUNNING -> ItemStatus.RUNNING
        }
    }

    private fun nextSyntheticTurnId(): String {
        syntheticTurnCount += 1
        return "local-turn-$syntheticTurnCount"
    }

    private fun isSyntheticTurnId(turnId: String): Boolean = turnId.startsWith("local-turn-")

    private fun messageNodeId(turnId: String?, sourceId: String): String =
        listOfNotNull("message", turnId?.takeIf { it.isNotBlank() }, sourceId).joinToString(":")

    private fun activityNodeId(turnId: String?, sourceId: String): String =
        listOfNotNull("activity", turnId?.takeIf { it.isNotBlank() }, sourceId).joinToString(":")
}
