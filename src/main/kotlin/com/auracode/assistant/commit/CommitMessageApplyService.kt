package com.auracode.assistant.commit

import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.vcs.commit.CommitMessageUi

/**
 * Applies the generated commit message to the active commit UI controls.
 */
internal class CommitMessageApplyService {
    fun apply(
        message: String,
        commitMessageControl: CommitMessageI,
        commitMessageUi: CommitMessageUi,
    ) {
        commitMessageControl.setCommitMessage(message)
        commitMessageUi.setText(message)
        commitMessageUi.focus()
    }
}
