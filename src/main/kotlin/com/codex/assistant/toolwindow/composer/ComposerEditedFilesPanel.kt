package com.codex.assistant.toolwindow.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.FileTypeIcon
import com.codex.assistant.toolwindow.shared.HoverTooltip
import com.codex.assistant.toolwindow.shared.assistantUiTokens

@Composable
internal fun EditedFilesPanel(
    p: DesignPalette,
    state: ComposerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    AnimatedVisibility(
        visible = state.editedFilesExpanded && state.editedFiles.isNotEmpty(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.topBarBg.copy(alpha = 0.96f), RoundedCornerShape(t.spacing.md))
                .border(1.dp, p.markdownDivider.copy(alpha = 0.48f), RoundedCornerShape(t.spacing.md))
                .padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
        ) {
            EditedFilesHeader(p = p, state = state, onIntent = onIntent)
            EditedFilesFilterRow(p = p, state = state, onIntent = onIntent)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 224.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
            ) {
                state.visibleEditedFiles.forEach { file ->
                    EditedFileRow(
                        aggregate = file,
                        p = p,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditedFilesHeader(
    p: DesignPalette,
    state: ComposerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = CodexBundle.message("composer.editedFiles.title"),
                color = p.textPrimary,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.material.MaterialTheme.typography.body2,
            )
            Spacer(Modifier.size(t.spacing.xs))
            Text(
                text = CodexBundle.message("composer.editedFiles.summary", state.editedFilesSummary.total.toString()),
                color = p.textMuted,
                style = androidx.compose.material.MaterialTheme.typography.caption,
            )
        }
        EditedFilesToolbarAction(
            iconPath = "/icons/check.svg",
            tooltip = CodexBundle.message("composer.editedFiles.acceptAll"),
            p = p,
        ) { onIntent(UiIntent.AcceptAllEditedFiles) }
        Spacer(Modifier.width(t.spacing.xs))
        EditedFilesToolbarAction(
            iconPath = "/icons/undo.svg",
            tooltip = CodexBundle.message("composer.editedFiles.revertAll"),
            p = p,
        ) { onIntent(UiIntent.RevertAllEditedFiles) }
        Spacer(Modifier.width(t.spacing.xs))
        EditedFilesToolbarAction(
            iconPath = "/icons/arrow-down.svg",
            tooltip = CodexBundle.message("timeline.collapse"),
            p = p,
        ) { onIntent(UiIntent.ToggleEditedFilesExpanded) }
    }
}

@Composable
private fun EditedFilesFilterRow(
    p: DesignPalette,
    state: ComposerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditedFilesFilterChip(
            label = CodexBundle.message("composer.editedFiles.filter.all"),
            selected = state.editedFilesFilter == EditedFilesFilter.ALL,
            p = p,
        ) { onIntent(UiIntent.SelectEditedFilesFilter(EditedFilesFilter.ALL)) }
        EditedFilesFilterChip(
            label = CodexBundle.message("composer.editedFiles.filter.created"),
            selected = state.editedFilesFilter == EditedFilesFilter.CREATED,
            p = p,
        ) { onIntent(UiIntent.SelectEditedFilesFilter(EditedFilesFilter.CREATED)) }
        EditedFilesFilterChip(
            label = CodexBundle.message("composer.editedFiles.filter.updated"),
            selected = state.editedFilesFilter == EditedFilesFilter.UPDATED,
            p = p,
        ) { onIntent(UiIntent.SelectEditedFilesFilter(EditedFilesFilter.UPDATED)) }
        EditedFilesFilterChip(
            label = CodexBundle.message("composer.editedFiles.filter.deleted"),
            selected = state.editedFilesFilter == EditedFilesFilter.DELETED,
            p = p,
        ) { onIntent(UiIntent.SelectEditedFilesFilter(EditedFilesFilter.DELETED)) }
    }
}

@Composable
private fun EditedFilesFilterChip(
    label: String,
    selected: Boolean,
    p: DesignPalette,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    Box(
        modifier = Modifier
            .background(
                if (selected) p.accent.copy(alpha = 0.16f) else p.topStripBg,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (selected) p.accent.copy(alpha = 0.6f) else p.markdownDivider.copy(alpha = 0.36f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
    ) {
        Text(
            text = label,
            color = if (selected) p.accent else p.textSecondary,
            style = androidx.compose.material.MaterialTheme.typography.caption,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EditedFileRow(
    aggregate: EditedFileAggregate,
    p: DesignPalette,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (hovered) p.topStripBg.copy(alpha = 0.92f) else p.topStripBg.copy(alpha = 0.72f),
                RoundedCornerShape(t.spacing.sm),
            )
            .hoverable(interactionSource)
            .clickable { onIntent(UiIntent.OpenEditedFileDiff(aggregate.path)) }
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        FileTypeIcon(fileName = aggregate.displayName, modifier = Modifier.size(t.controls.iconMd))
        Column(modifier = Modifier.weight(1f)) {
            HoverTooltip(aggregate.path) {
                Text(
                    text = aggregate.displayName,
                    color = p.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.material.MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.size(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(t.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditedFilesKindBadge(kind = aggregate.aggregateKind, p = p)
                Text(
                    text = CodexBundle.message("composer.editedFiles.edits", aggregate.editCount.toString()),
                    color = p.textMuted,
                    style = androidx.compose.material.MaterialTheme.typography.caption,
                )
                aggregate.latestAddedLines?.takeIf { it > 0 }?.let {
                    Text("+$it", color = p.success, style = androidx.compose.material.MaterialTheme.typography.caption)
                }
                aggregate.latestDeletedLines?.takeIf { it > 0 }?.let {
                    Text("-$it", color = p.danger, style = androidx.compose.material.MaterialTheme.typography.caption)
                }
            }
        }
        EditedFilesToolbarAction(
            iconPath = "/icons/split.svg",
            tooltip = CodexBundle.message("composer.editedFiles.viewDiff"),
            p = p,
        ) { onIntent(UiIntent.OpenEditedFileDiff(aggregate.path)) }
        Spacer(Modifier.width(t.spacing.xs))
        EditedFilesToolbarAction(
            iconPath = "/icons/undo.svg",
            tooltip = CodexBundle.message("composer.editedFiles.revert"),
            p = p,
        ) { onIntent(UiIntent.RevertEditedFile(aggregate.path)) }
    }
}

@Composable
private fun EditedFilesKindBadge(
    kind: EditedFileAggregateKind,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    val text = when (kind) {
        EditedFileAggregateKind.CREATED -> CodexBundle.message("composer.editedFiles.kind.created")
        EditedFileAggregateKind.UPDATED -> CodexBundle.message("composer.editedFiles.kind.updated")
        EditedFileAggregateKind.DELETED -> CodexBundle.message("composer.editedFiles.kind.deleted")
        EditedFileAggregateKind.MIXED -> CodexBundle.message("composer.editedFiles.kind.mixed")
    }
    val color = when (kind) {
        EditedFileAggregateKind.CREATED -> p.success
        EditedFileAggregateKind.UPDATED -> p.accent
        EditedFileAggregateKind.DELETED -> p.danger
        EditedFileAggregateKind.MIXED -> p.textSecondary
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = t.spacing.xs, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = androidx.compose.material.MaterialTheme.typography.caption,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EditedFilesToolbarAction(
    iconPath: String,
    tooltip: String,
    p: DesignPalette,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    HoverTooltip(tooltip) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(p.appBg.copy(alpha = 0.4f), RoundedCornerShape(t.spacing.xs))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconPath),
                contentDescription = tooltip,
                tint = p.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
