package com.codex.assistant.toolwindow.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.assistant.toolwindow.drawer.settings.SettingsOverlay
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.shared.DesignPalette

@Composable
internal fun RightDrawerRegion(
    p: DesignPalette,
    state: RightDrawerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        color = p.topBarBg,
        contentColor = p.textPrimary,
        elevation = 0.dp,
    ) {
        when (state.kind) {
            RightDrawerKind.HISTORY -> HistoryOverlay(p = p, state = state, onIntent = onIntent)
            RightDrawerKind.SETTINGS -> SettingsOverlay(p = p, state = state, onIntent = onIntent)
            RightDrawerKind.NONE -> Unit
        }
    }
}
