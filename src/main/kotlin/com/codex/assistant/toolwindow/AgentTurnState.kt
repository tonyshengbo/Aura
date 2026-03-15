package com.codex.assistant.toolwindow

import com.codex.assistant.model.TimelineAction

internal class AgentTurnState {
    private val timelineActions = mutableListOf<TimelineAction>()

    var statusText: String = ""
        private set
    var startedAtMs: Long = 0L
        private set
    var active: Boolean = false
        private set
    var finalized: Boolean = false
        private set

    fun begin(startedAtMs: Long) {
        this.startedAtMs = startedAtMs
        active = true
        finalized = false
        statusText = ""
        timelineActions.clear()
    }

    fun canHandle(action: TimelineAction): Boolean {
        if (!active && action !is TimelineAction.MarkTurnFailed) {
            return false
        }
        if (finalized && action != TimelineAction.FinishTurn && action !is TimelineAction.MarkTurnFailed) {
            return false
        }
        return true
    }

    fun record(action: TimelineAction) {
        timelineActions.add(action)
    }

    fun markFinalized() {
        finalized = true
    }

    fun snapshotActions(): List<TimelineAction> = timelineActions.toList()

    fun hasActions(): Boolean = timelineActions.isNotEmpty()

    fun setStatus(status: String) {
        statusText = status
    }

    fun clear() {
        active = false
        finalized = false
        statusText = ""
        startedAtMs = 0L
        timelineActions.clear()
    }
}
