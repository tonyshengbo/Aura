package com.auracode.assistant.integration.ide

import com.auracode.assistant.model.ContextFile

/**
 * Represents a normalized request coming from an IDE entrypoint outside the main tool window.
 */
data class IdeExternalRequest(
    val source: IdeRequestSource,
    val title: String,
    val prompt: String,
    val contextFiles: List<ContextFile> = emptyList(),
)

enum class IdeRequestSource {
    BUILD_PROBLEM,
    EDITOR_SELECTION,
    CURRENT_FILE,
}
