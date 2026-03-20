package com.auracode.assistant.toolwindow.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import com.auracode.assistant.protocol.UnifiedApprovalRequestKind
import com.auracode.assistant.toolwindow.approval.ApprovalAction
import com.auracode.assistant.toolwindow.approval.ApprovalAreaState
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantMonospaceStyle
import com.auracode.assistant.toolwindow.shared.assistantUiTokens

@Composable
internal fun ApprovalComposerSection(
    p: DesignPalette,
    state: ApprovalAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val current = state.current ?: return
    val t = assistantUiTokens()
    val focusRequester = FocusRequester()

    LaunchedEffect(current.requestId) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }

    ComposerInteractionCard(
        p = p,
        onRequestFocus = {
            // Approval cards always navigate at the card level, so a card refocus is sufficient.
            focusRequester.requestFocus()
        },
        modifier = Modifier
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
    ) {
        Text(
            text = approvalBadgeLabel(current.kind) +
                if (current.queueSize > 1) " ${current.queuePosition}/${current.queueSize}" else "",
            color = p.textSecondary,
        )
        Text(
            text = current.title,
            color = p.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = current.body,
            color = p.textSecondary,
        )
        if (!current.command.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.markdownCodeBg, RoundedCornerShape(12.dp))
                    .padding(t.spacing.sm),
            ) {
                Text(
                    text = current.command,
                    color = p.markdownCodeText,
                    style = assistantMonospaceStyle(),
                )
            }
        }
        if (!current.cwd.isNullOrBlank()) {
            Text(
                text = "CWD: ${current.cwd}",
                color = p.textMuted,
                style = assistantMonospaceStyle(),
            )
        }
        if (current.permissions.isNotEmpty()) {
            Text(
                text = current.permissions.joinToString("\n"),
                color = p.textSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
        ) {
            ApprovalAction.entries
                .filter { it != ApprovalAction.ALLOW_FOR_SESSION || current.allowForSession }
                .forEach { action ->
                    val selected = state.selectedAction == action
                    ComposerCardAction(
                        label = action.label(),
                        emphasized = selected && action != ApprovalAction.REJECT,
                        danger = selected && action == ApprovalAction.REJECT,
                        p = p,
                        modifier = Modifier.widthIn(min = 72.dp),
                        onClick = {
                            onIntent(UiIntent.SelectApprovalAction(action))
                            onIntent(UiIntent.SubmitApprovalAction(action))
                        },
                    )
                }
        }
        Text(
            text = "Enter confirm · Esc reject",
            color = p.textMuted,
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
