package com.codex.assistant.toolwindow.approval

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.assistant.protocol.UnifiedApprovalRequestKind
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.assistantMonospaceStyle
import com.codex.assistant.toolwindow.shared.assistantUiTokens

@Composable
internal fun ApprovalOverlay(
    palette: DesignPalette,
    state: ApprovalAreaState,
    modifier: Modifier = Modifier,
    onIntent: (UiIntent) -> Unit,
) {
    val current = state.current ?: return
    val tokens = assistantUiTokens()
    val focusRequester = FocusRequester()
    LaunchedEffect(current.requestId) {
        focusRequester.requestFocus()
    }
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .background(palette.timelineCardBg, RoundedCornerShape(16.dp))
            .border(1.dp, palette.markdownDivider, RoundedCornerShape(16.dp))
            .padding(tokens.spacing.lg)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight,
                    Key.DirectionDown,
                    -> {
                        onIntent(UiIntent.MoveApprovalActionNext)
                        true
                    }

                    Key.DirectionLeft,
                    Key.DirectionUp,
                    -> {
                        onIntent(UiIntent.MoveApprovalActionPrevious)
                        true
                    }

                    Key.Enter,
                    Key.NumPadEnter,
                    -> {
                        onIntent(UiIntent.SubmitApprovalAction())
                        true
                    }

                    Key.Escape -> {
                        onIntent(UiIntent.SubmitApprovalAction(ApprovalAction.REJECT))
                        true
                    }

                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(
            text = approvalBadgeLabel(current.kind) +
                if (current.queueSize > 1) " ${current.queuePosition}/${current.queueSize}" else "",
            color = palette.textSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            Text(
                text = current.title,
                color = palette.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = current.body,
                color = palette.textSecondary,
            )
        }
        if (!current.command.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.markdownCodeBg, RoundedCornerShape(12.dp))
                    .padding(tokens.spacing.sm),
            ) {
                Text(
                    text = current.command,
                    color = palette.markdownCodeText,
                    style = assistantMonospaceStyle(),
                )
            }
        }
        if (!current.cwd.isNullOrBlank()) {
            Text(
                text = "CWD: ${current.cwd}",
                color = palette.textMuted,
                style = assistantMonospaceStyle(),
            )
        }
        if (current.permissions.isNotEmpty()) {
            Text(
                text = current.permissions.joinToString("\n"),
                color = palette.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            ApprovalAction.entries
                .filter { it != ApprovalAction.ALLOW_FOR_SESSION || current.allowForSession }
                .forEach { action ->
                    val selected = state.selectedAction == action
                    Text(
                        text = action.label(),
                        color = if (selected) palette.timelineCardBg else palette.textPrimary,
                        modifier = Modifier
                            .background(
                                color = when {
                                    selected && action == ApprovalAction.REJECT -> palette.danger
                                    selected -> palette.accent
                                    else -> palette.appBg
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) {
                                    if (action == ApprovalAction.REJECT) palette.danger else palette.accent
                                } else {
                                    palette.markdownDivider
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                onIntent(UiIntent.SelectApprovalAction(action))
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
        }
        Text(
            text = "Enter confirm · Esc reject",
            color = palette.textMuted,
        )
    }
}

private fun approvalBadgeLabel(kind: UnifiedApprovalRequestKind): String {
    return when (kind) {
        UnifiedApprovalRequestKind.COMMAND -> "Command Approval"
        UnifiedApprovalRequestKind.FILE_CHANGE -> "File Change Approval"
        UnifiedApprovalRequestKind.PERMISSIONS -> "Permission Request"
    }
}

private fun ApprovalAction.label(): String {
    return when (this) {
        ApprovalAction.ALLOW -> "Allow"
        ApprovalAction.REJECT -> "Reject"
        ApprovalAction.ALLOW_FOR_SESSION -> "Remember"
    }
}
