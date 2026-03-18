package com.codex.assistant.toolwindow.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.HoverTooltip
import com.codex.assistant.toolwindow.shared.assistantUiTokens

@Composable
internal fun HeaderRegion(
    p: DesignPalette,
    state: HeaderAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showNewTabConfirmDialog by remember { mutableStateOf(false) }
    val displayTitle = state.title.trim().ifBlank { CodexBundle.message("header.newChat") }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(CodexBundle.message("header.newDialog.title")) },
            text = { Text(CodexBundle.message("header.newDialog.text")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onIntent(UiIntent.NewSession)
                    },
                ) {
                    Text(CodexBundle.message("header.newDialog.confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(CodexBundle.message("common.cancel"))
                }
            },
        )
    }

    if (showNewTabConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNewTabConfirmDialog = false },
            title = { Text(CodexBundle.message("header.newTabDialog.title")) },
            text = { Text(CodexBundle.message("header.newTabDialog.text")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewTabConfirmDialog = false
                        onIntent(UiIntent.NewTab)
                    },
                ) {
                    Text(CodexBundle.message("header.newTabDialog.confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTabConfirmDialog = false }) {
                    Text(CodexBundle.message("common.cancel"))
                }
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(p.topBarBg).padding(horizontal = t.spacing.md, vertical = t.spacing.xs + t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayTitle,
            color = p.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material.MaterialTheme.typography.h5,
        )
        Spacer(modifier = Modifier.width(t.spacing.xs))
        HeaderAction(
            p = p,
            iconPath = "/icons/add.svg",
            description = CodexBundle.message("header.action.newSession"),
            enabled = state.canCreateNewSession,
        ) {
            showConfirmDialog = true
        }
        Spacer(modifier = Modifier.width(t.spacing.xs))
        HeaderAction(p, "/icons/split.svg", CodexBundle.message("header.action.newTab")) { showNewTabConfirmDialog = true }
        Spacer(modifier = Modifier.width(t.spacing.xs))
        HeaderAction(p, "/icons/history.svg", CodexBundle.message("header.action.history")) { onIntent(UiIntent.ToggleHistory) }
        Spacer(modifier = Modifier.width(t.spacing.xs))
        HeaderAction(p, "/icons/settings.svg", CodexBundle.message("header.action.settings")) { onIntent(UiIntent.ToggleSettings) }
    }
}

@Composable
internal fun HeaderAction(
    p: DesignPalette,
    iconPath: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    HoverTooltip(text = description) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(RoundedCornerShape(t.spacing.xs))
                .clickable(enabled = enabled, onClick = onClick)
                .size(t.controls.headerActionTouch)
                .padding(t.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconPath),
                contentDescription = description,
                tint = if (enabled) p.textSecondary else p.textMuted,
                modifier = Modifier.size(t.controls.iconLg),
            )
        }
    }
}
