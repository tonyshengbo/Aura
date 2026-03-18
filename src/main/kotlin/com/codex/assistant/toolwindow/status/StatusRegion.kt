package com.codex.assistant.toolwindow.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.assistantUiTokens
import com.codex.assistant.toolwindow.shared.resolve

@Composable
internal fun StatusRegion(
    p: DesignPalette,
    state: StatusAreaState,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.fillMaxWidth().background(p.topStripBg).padding(horizontal = t.spacing.md, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.text.resolve(),
            color = p.textMuted,
            style = MaterialTheme.typography.caption,
        )
    }
}
