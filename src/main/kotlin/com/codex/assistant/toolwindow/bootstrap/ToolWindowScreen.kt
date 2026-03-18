package com.codex.assistant.toolwindow.bootstrap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.codex.assistant.toolwindow.composer.ComposerAreaState
import com.codex.assistant.toolwindow.composer.ComposerRegion
import com.codex.assistant.toolwindow.drawer.RightDrawerAreaState
import com.codex.assistant.toolwindow.drawer.RightDrawerKind
import com.codex.assistant.toolwindow.drawer.RightDrawerRegion
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.header.HeaderAreaState
import com.codex.assistant.settings.UiThemeMode
import com.codex.assistant.toolwindow.header.HeaderRegion
import com.codex.assistant.toolwindow.shared.assistantPalette
import com.codex.assistant.toolwindow.shared.assistantUiTokens
import com.codex.assistant.toolwindow.status.StatusAreaState
import com.codex.assistant.toolwindow.status.StatusRegion
import com.codex.assistant.toolwindow.timeline.TimelineAreaState
import com.codex.assistant.toolwindow.timeline.TimelineRegion

@Composable
internal fun ToolWindowScreen(
    headerState: HeaderAreaState,
    statusState: StatusAreaState,
    timelineState: TimelineAreaState,
    composerState: ComposerAreaState,
    rightDrawerState: RightDrawerAreaState,
    themeMode: UiThemeMode,
    onIntent: (UiIntent) -> Unit,
) {
    val p = assistantPalette(themeMode)
    val t = assistantUiTokens()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.appBg)
            .drawBehind {
                drawLine(
                    color = p.markdownDivider.copy(alpha = 0.95f),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1f,
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            HeaderRegion(p = p, state = headerState, onIntent = onIntent)
            StatusRegion(p = p, state = statusState)
            TimelineRegion(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                p = p,
                state = timelineState,
                onIntent = onIntent,
            )
            ComposerRegion(
                p = p,
                state = composerState,
                conversationState = timelineState,
                onIntent = onIntent,
            )
        }
        if (rightDrawerState.kind != RightDrawerKind.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = { onIntent(UiIntent.CloseRightDrawer) },
                    ),
            )
            Box(modifier = Modifier.fillMaxSize().padding(t.spacing.md)) {
                RightDrawerRegion(p = p, state = rightDrawerState, onIntent = onIntent)
            }
        }
    }
}
