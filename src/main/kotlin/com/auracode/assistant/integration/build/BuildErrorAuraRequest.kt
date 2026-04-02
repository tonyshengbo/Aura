package com.auracode.assistant.integration.build

import com.auracode.assistant.integration.ide.IdeExternalRequest
import com.auracode.assistant.integration.ide.IdeRequestSource

/**
 * Carries a build error snapshot together with the generated prompt that Aura will submit.
 */
data class BuildErrorAuraRequest(
    val snapshot: BuildErrorSnapshot,
    val prompt: String,
) {
    fun toIdeExternalRequest(): IdeExternalRequest {
        return IdeExternalRequest(
            source = IdeRequestSource.BUILD_PROBLEM,
            title = snapshot.title,
            prompt = prompt,
        )
    }
}
