package com.codex.assistant.toolwindow.eventing

import com.codex.assistant.model.EngineEvent
import com.codex.assistant.protocol.UnifiedEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ToolWindowEventHub {
    private val _eventLog = MutableStateFlow<List<AppEvent>>(emptyList())
    val events: StateFlow<List<AppEvent>> = _eventLog.asStateFlow()

    private val _stream = MutableSharedFlow<AppEvent>(extraBufferCapacity = 128)
    val stream: SharedFlow<AppEvent> = _stream.asSharedFlow()

    fun publishUiIntent(intent: UiIntent) {
        publish(AppEvent.UiIntentPublished(intent))
    }

    fun publishUnifiedEvent(event: UnifiedEvent) {
        publish(AppEvent.UnifiedEventPublished(event))
    }

    fun publishEngineEvent(event: EngineEvent) {
        publish(AppEvent.EngineEventPublished(event))
    }

    fun publish(event: AppEvent) {
        _eventLog.value = _eventLog.value + event
        _stream.tryEmit(event)
    }
}
