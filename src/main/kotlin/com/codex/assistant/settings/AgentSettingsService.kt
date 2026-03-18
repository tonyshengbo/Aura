package com.codex.assistant.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UiLanguageMode {
    FOLLOW_IDE,
    ZH,
    EN,
}

enum class UiThemeMode {
    FOLLOW_IDE,
    LIGHT,
    DARK,
}

@Service(Service.Level.APP)
@State(name = "CodexAssistantSettings", storages = [Storage("codex-assistant.xml")])
class AgentSettingsService : PersistentStateComponent<AgentSettingsService.State> {
    data class State(
        var codexCliPath: String = "codex",
        var engineExecutablePaths: MutableMap<String, String> = mutableMapOf("codex" to "codex"),
        var uiLanguage: String = UiLanguageMode.FOLLOW_IDE.name,
        var uiTheme: String = UiThemeMode.FOLLOW_IDE.name,
        var savedAgents: MutableList<SavedAgentDefinition> = mutableListOf(),
    ) {
        fun executablePathFor(engineId: String): String {
            val fromMap = engineExecutablePaths[engineId]?.trim().orEmpty()
            if (fromMap.isNotBlank()) {
                return fromMap
            }
            return if (engineId == "codex") codexCliPath.trim() else engineId
        }

        fun setExecutablePathFor(engineId: String, path: String) {
            val normalized = path.trim()
            if (engineId == "codex") {
                codexCliPath = normalized
            }
            engineExecutablePaths[engineId] = normalized
        }
    }

    private var state = State()
    private val _languageVersion = MutableStateFlow(0L)
    val languageVersion: StateFlow<Long> = _languageVersion.asStateFlow()
    private val _appearanceVersion = MutableStateFlow(0L)
    val appearanceVersion: StateFlow<Long> = _appearanceVersion.asStateFlow()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        notifyLanguageChanged()
    }

    fun uiLanguageMode(): UiLanguageMode {
        return runCatching { UiLanguageMode.valueOf(state.uiLanguage) }.getOrDefault(UiLanguageMode.FOLLOW_IDE)
    }

    fun setUiLanguageMode(mode: UiLanguageMode) {
        state.uiLanguage = mode.name
    }

    fun uiThemeMode(): UiThemeMode {
        return runCatching { UiThemeMode.valueOf(state.uiTheme) }.getOrDefault(UiThemeMode.FOLLOW_IDE)
    }

    fun setUiThemeMode(mode: UiThemeMode) {
        state.uiTheme = mode.name
    }

    fun notifyLanguageChanged() {
        _languageVersion.value = _languageVersion.value + 1
    }

    fun notifyAppearanceChanged() {
        _appearanceVersion.value = _appearanceVersion.value + 1
    }

    fun savedAgents(): List<SavedAgentDefinition> = state.savedAgents.toList()

    companion object {
        fun getInstance(): AgentSettingsService {
            return ApplicationManager.getApplication().getService(AgentSettingsService::class.java)
        }
    }
}
