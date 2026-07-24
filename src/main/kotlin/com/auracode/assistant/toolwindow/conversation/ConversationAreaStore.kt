package com.auracode.assistant.toolwindow.conversation

import com.auracode.assistant.toolwindow.eventing.AppEvent
import com.auracode.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ConversationRenderCause {
    IDLE,
    HISTORY_RESET,
    HISTORY_PREPEND,
    LIVE_UPDATE,
}

internal data class ConversationAreaState(
    val nodes: List<ConversationActivityItem> = emptyList(),
    val oldestCursor: String? = null,
    val hasOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isRunning: Boolean = false,
    val activeTurnId: String? = null,
    val expandedNodeIds: Set<String> = emptySet(),
    val scrollSnapshot: ConversationScrollSnapshot? = null,
    val renderVersion: Long = 0L,
    val timelineContentVersion: Long = 0L,
    val renderCause: ConversationRenderCause = ConversationRenderCause.IDLE,
    val prependedCount: Int = 0,
    val latestError: String? = null,
    val promptScrollRequestVersion: Long = 0L,
    val pendingScrollRestoreSnapshot: ConversationScrollSnapshot? = null,
    val scrollRestoreRequestVersion: Long = 0L,
)

internal class ConversationAreaStore {
    private val _state = MutableStateFlow(ConversationAreaState())
    val state: StateFlow<ConversationAreaState> = _state.asStateFlow()
    private val nodeIndexById = mutableMapOf<String, Int>()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.UiIntentPublished -> {
                when (val intent = event.intent) {
                    is UiIntent.ToggleNodeExpanded -> {
                        val current = _state.value.expandedNodeIds
                        val next = if (current.contains(intent.nodeId)) current - intent.nodeId else current + intent.nodeId
                        _state.value = _state.value.copy(expandedNodeIds = next)
                    }

                    else -> Unit
                }
            }

            is AppEvent.ConversationOlderLoadingChanged -> {
                val previous = _state.value
                _state.value = previous.copy(
                    nodes = decorateHistoryNodes(
                        nodes = previous.nodes.filterNot { it is ConversationActivityItem.LoadMoreNode },
                        hasOlder = previous.hasOlder,
                        isLoadingOlder = event.loading,
                    ),
                    isLoadingOlder = event.loading,
                    renderVersion = previous.renderVersion + 1,
                    timelineContentVersion = previous.timelineContentVersion + 1,
                )
                rebuildNodeIndex(_state.value.nodes)
            }

            is AppEvent.ConversationUiProjectionUpdated -> {
                val appliedIncrementally = event.entryPatches?.let { patches ->
                    syncProjectedStateIncrementally(
                        projectedNodes = event.nodes,
                        patches = patches,
                        oldestCursor = event.oldestCursor,
                        hasOlder = event.hasOlder,
                        isRunning = event.isRunning,
                        activeTurnId = event.activeTurnId,
                        latestError = event.latestError,
                    )
                } == true
                if (!appliedIncrementally) syncProjectedState(
                    nodes = event.nodes,
                    oldestCursor = event.oldestCursor,
                    hasOlder = event.hasOlder,
                    isRunning = event.isRunning,
                    activeTurnId = event.activeTurnId,
                    latestError = event.latestError,
                )
            }

            is AppEvent.PromptAccepted -> {
                val previous = _state.value
                _state.value = previous.copy(
                    isRunning = true,
                    latestError = null,
                    renderVersion = previous.renderVersion + 1,
                    renderCause = ConversationRenderCause.LIVE_UPDATE,
                    prependedCount = 0,
                    promptScrollRequestVersion = previous.promptScrollRequestVersion + 1,
                )
            }

            AppEvent.ConversationReset -> {
                _state.value = ConversationAreaState()
                nodeIndexById.clear()
            }

            else -> Unit
        }
    }

    fun restoreState(state: ConversationAreaState) {
        _state.value = state
        rebuildNodeIndex(state.nodes)
    }

    /** Persists the currently visible viewport snapshot so session switches can capture it later. */
    fun updateScrollSnapshot(snapshot: ConversationScrollSnapshot) {
        val previous = _state.value
        if (previous.scrollSnapshot == snapshot) return
        _state.value = previous.copy(scrollSnapshot = snapshot)
    }

    /** Queues a one-shot viewport restore request after the session projection becomes visible again. */
    fun requestScrollRestore(snapshot: ConversationScrollSnapshot) {
        val previous = _state.value
        _state.value = previous.copy(
            pendingScrollRestoreSnapshot = snapshot,
            scrollRestoreRequestVersion = previous.scrollRestoreRequestVersion + 1,
        )
    }

    /** Restores the session-local expanded node state after projection/history rebuilt the visible nodes. */
    fun restoreExpandedNodeIds(expandedNodeIds: Set<String>) {
        val availableNodeIds = _state.value.nodes.mapTo(linkedSetOf(), ConversationActivityItem::id)
        _state.value = _state.value.copy(
            expandedNodeIds = expandedNodeIds.intersect(availableNodeIds),
        )
    }

    /** Applies one full conversation projection while preserving local UI-only expansion state. */
    private fun syncProjectedState(
        nodes: List<ConversationActivityItem>,
        oldestCursor: String?,
        hasOlder: Boolean,
        isRunning: Boolean,
        activeTurnId: String?,
        latestError: String?,
    ) {
        val previous = _state.value
        val previousContentNodes = previous.nodes.filterNot { it is ConversationActivityItem.LoadMoreNode }
        val nextContentNodes = nodes.filterNot { it is ConversationActivityItem.LoadMoreNode }
        val prependedCount = when {
            previous.isLoadingOlder && nextContentNodes.size >= previousContentNodes.size ->
                nextContentNodes.size - previousContentNodes.size

            else -> 0
        }
        val renderCause = when {
            prependedCount > 0 -> ConversationRenderCause.HISTORY_PREPEND
            previous.nodes.isEmpty() && nextContentNodes.isNotEmpty() -> ConversationRenderCause.HISTORY_RESET
            else -> ConversationRenderCause.LIVE_UPDATE
        }
        val nextState = previous.copy(
            nodes = decorateHistoryNodes(
                nodes = nodes,
                hasOlder = hasOlder,
                isLoadingOlder = previous.isLoadingOlder,
            ),
            oldestCursor = oldestCursor,
            hasOlder = hasOlder,
            isLoadingOlder = false,
            isRunning = isRunning,
            activeTurnId = activeTurnId,
            latestError = latestError,
            renderVersion = previous.renderVersion + 1,
            timelineContentVersion = previous.timelineContentVersion + 1,
            renderCause = renderCause,
            prependedCount = prependedCount,
        )
        _state.value = nextState.copy(
            expandedNodeIds = projectedExpandedNodeIds(previous = previous, nextState = nextState),
        )
        rebuildNodeIndex(_state.value.nodes)
    }

    /** Applies safe same-id replacements and tail appends without rescanning the full timeline. */
    private fun syncProjectedStateIncrementally(
        projectedNodes: List<ConversationActivityItem>,
        patches: List<com.auracode.assistant.toolwindow.eventing.ConversationEntryNodePatch>,
        oldestCursor: String?,
        hasOlder: Boolean,
        isRunning: Boolean,
        activeTurnId: String?,
        latestError: String?,
    ): Boolean {
        val previous = _state.value
        if (previous.isLoadingOlder || previous.hasOlder != hasOlder) return false

        var appendedNodeCount = 0
        patches.forEach { patch ->
            if (patch.previousNodeIds.isEmpty()) {
                if (patch.nodes.any { it.id in nodeIndexById }) return false
                appendedNodeCount += patch.nodes.size
                return@forEach
            }
            if (patch.previousNodeIds.size != patch.nodes.size) return false
            patch.previousNodeIds.zip(patch.nodes).forEach { (previousId, nextNode) ->
                if (previousId != nextNode.id) return false
                if (nodeIndexById[previousId] == null) return false
            }
        }

        val expectedHeaderCount = if (hasOlder || previous.isLoadingOlder) 1 else 0
        if (previous.nodes.size + appendedNodeCount != projectedNodes.size + expectedHeaderCount) return false

        val nextNodes = previous.nodes.toMutableList()
        patches.forEach { patch ->
            if (patch.previousNodeIds.isEmpty()) {
                patch.nodes.forEach { node ->
                    nodeIndexById[node.id] = nextNodes.size
                    nextNodes += node
                }
            } else {
                patch.nodes.forEach { node ->
                    nextNodes[nodeIndexById.getValue(node.id)] = node
                }
            }
        }

        val nextNodeIds = nextNodes.mapTo(linkedSetOf(), ConversationActivityItem::id)
        val appendedDefaultExpandedIds = patches
            .filter { it.previousNodeIds.isEmpty() }
            .flatMap { it.nodes }
            .filterTo(linkedSetOf(), ConversationActivityItem::isExpandedByDefault)
            .mapTo(linkedSetOf(), ConversationActivityItem::id)
        val expanded = previous.expandedNodeIds.intersect(nextNodeIds) + appendedDefaultExpandedIds

        _state.value = previous.copy(
            nodes = nextNodes,
            oldestCursor = oldestCursor,
            hasOlder = hasOlder,
            isLoadingOlder = false,
            isRunning = isRunning,
            activeTurnId = activeTurnId,
            expandedNodeIds = expanded,
            latestError = latestError,
            renderVersion = previous.renderVersion + 1,
            timelineContentVersion = previous.timelineContentVersion + if (patches.any { it.previousNodeIds.isNotEmpty() || it.nodes.isNotEmpty() }) 1 else 0,
            renderCause = ConversationRenderCause.LIVE_UPDATE,
            prependedCount = 0,
        )
        return true
    }

    private fun rebuildNodeIndex(nodes: List<ConversationActivityItem>) {
        nodeIndexById.clear()
        nodes.forEachIndexed { index, node -> nodeIndexById[node.id] = index }
    }

    /** Recomputes expansion state for either reducer-driven or projection-driven node updates. */
    private fun projectedExpandedNodeIds(
        previous: ConversationAreaState,
        nextState: ConversationAreaState,
    ): Set<String> {
        val nextNodeIds = nextState.nodes.mapTo(linkedSetOf(), ConversationActivityItem::id)
        val previousNodeIds = previous.nodes.mapTo(hashSetOf(), ConversationActivityItem::id)
        val newDefaultExpandedIds = nextState.nodes
            .filter { it.id !in previousNodeIds && it.isExpandedByDefault() }
            .mapTo(linkedSetOf(), ConversationActivityItem::id)
        return previous.expandedNodeIds.intersect(nextNodeIds) + newDefaultExpandedIds
    }

    /** Applies the load-more decoration used by both reducer history replay and projected state replacement. */
    private fun decorateHistoryNodes(
        nodes: List<ConversationActivityItem>,
        hasOlder: Boolean,
        isLoadingOlder: Boolean,
    ): List<ConversationActivityItem> {
        val contentNodes = nodes.filterNot { it is ConversationActivityItem.LoadMoreNode }
        return if (hasOlder || isLoadingOlder) {
            listOf(ConversationActivityItem.LoadMoreNode(isLoading = isLoadingOlder)) + contentNodes
        } else {
            contentNodes
        }
    }

}

private fun ConversationActivityItem.isExpandedByDefault(): Boolean {
    return this is ConversationActivityItem.PlanNode || this is ConversationActivityItem.EngineSwitchedNode
}
