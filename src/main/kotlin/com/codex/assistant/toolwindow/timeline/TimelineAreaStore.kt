package com.codex.assistant.toolwindow.timeline

import com.codex.assistant.toolwindow.eventing.AppEvent
import com.codex.assistant.toolwindow.eventing.UiIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class TimelineRenderCause {
    IDLE,
    HISTORY_RESET,
    HISTORY_PREPEND,
    LIVE_UPDATE,
}

internal data class TimelineAreaState(
    val nodes: List<TimelineNode> = emptyList(),
    val oldestCursor: String? = null,
    val hasOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val isRunning: Boolean = false,
    val expandedNodeIds: Set<String> = emptySet(),
    val renderVersion: Long = 0L,
    val renderCause: TimelineRenderCause = TimelineRenderCause.IDLE,
    val prependedCount: Int = 0,
    val latestError: String? = null,
)

internal class TimelineAreaStore {
    private val reducer = TimelineNodeReducer()

    private val _state = MutableStateFlow(TimelineAreaState())
    val state: StateFlow<TimelineAreaState> = _state.asStateFlow()

    fun onEvent(event: AppEvent) {
        when (event) {
            is AppEvent.TimelineMutationApplied -> {
                reducer.accept(event.mutation)
                syncReducerState()
            }

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

            is AppEvent.TimelineOlderLoadingChanged -> {
                reducer.setLoadingOlder(event.loading)
                syncReducerState()
            }

            is AppEvent.TimelineHistoryLoaded -> {
                if (event.prepend) {
                    reducer.prependHistory(
                        nodes = event.nodes,
                        oldestCursor = event.oldestCursor,
                        hasOlder = event.hasOlder,
                    )
                } else {
                    reducer.replaceHistory(
                        nodes = event.nodes,
                        oldestCursor = event.oldestCursor,
                        hasOlder = event.hasOlder,
                    )
                }
                syncReducerState()
            }

            AppEvent.ConversationReset -> {
                reducer.reset()
                _state.value = reducer.state
            }

            else -> Unit
        }
    }

    private fun syncReducerState() {
        _state.value = reducer.state.copy(
            expandedNodeIds = _state.value.expandedNodeIds,
        )
    }
}
