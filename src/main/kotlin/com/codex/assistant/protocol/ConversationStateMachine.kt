package com.codex.assistant.protocol

class ConversationStateMachine {
    var state: ConversationUiState = ConversationUiState()
        private set

    private var activeTurnId: String? = null

    fun accept(event: UnifiedEvent) {
        state = reduce(state, event)
    }

    private fun reduce(current: ConversationUiState, event: UnifiedEvent): ConversationUiState {
        return when (event) {
            is UnifiedEvent.ThreadStarted -> {
                current.copy(
                    threadId = event.threadId,
                    isRunning = current.isRunning || activeTurnId != null,
                )
            }

            is UnifiedEvent.TurnStarted -> {
                activeTurnId = event.turnId
                val turns = current.turns.toMutableList()
                if (turns.none { it.id == event.turnId }) {
                    turns += TurnUiState(id = event.turnId, outcome = TurnOutcome.RUNNING)
                }
                current.copy(isRunning = true, turns = turns)
            }

            is UnifiedEvent.ItemUpdated -> {
                val turnId = activeTurnId ?: current.turns.lastOrNull()?.id
                if (turnId == null) return current
                val turns = current.turns.map { turn ->
                    if (turn.id != turnId) return@map turn
                    val items = turn.items.toMutableList()
                    val idx = items.indexOfFirst { it.id == event.item.id }
                    if (idx >= 0) {
                        items[idx] = merge(items[idx], event.item)
                    } else {
                        items += event.item
                    }
                    turn.copy(items = items)
                }
                current.copy(turns = turns)
            }

            is UnifiedEvent.TurnCompleted -> {
                if (activeTurnId == event.turnId || event.turnId.isBlank()) {
                    activeTurnId = null
                }
                val turns = current.turns.map { turn ->
                    if (event.turnId.isNotBlank() && turn.id != event.turnId) return@map turn
                    turn.copy(
                        outcome = event.outcome,
                        usage = event.usage ?: turn.usage,
                    )
                }
                current.copy(isRunning = activeTurnId != null, turns = turns)
            }

            is UnifiedEvent.Error -> current.copy(latestError = event.message, isRunning = false)
        }
    }

    private fun merge(old: UnifiedItem, fresh: UnifiedItem): UnifiedItem {
        return old.copy(
            kind = fresh.kind,
            status = fresh.status,
            name = fresh.name ?: old.name,
            text = fresh.text ?: old.text,
            command = fresh.command ?: old.command,
            cwd = fresh.cwd ?: old.cwd,
            filePath = fresh.filePath ?: old.filePath,
            exitCode = fresh.exitCode ?: old.exitCode,
            approvalDecision = fresh.approvalDecision ?: old.approvalDecision,
        )
    }
}
