package com.auracode.assistant.toolwindow.drawer.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.settings.skills.SkillSource
import com.auracode.assistant.settings.skills.SkillSummary
import com.auracode.assistant.toolwindow.drawer.RightDrawerAreaState
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.assistantUiTokens

@Composable
internal fun SkillsSettingsPage(
    p: DesignPalette,
    state: RightDrawerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(t.spacing.md),
    ) {
        item {
            Text(
                text = AuraCodeBundle.message("settings.skills.list.title"),
                color = p.textPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.h6,
            )
        }
        item {
            Text(
                text = AuraCodeBundle.message("settings.skills.list.subtitle"),
                color = p.textSecondary,
                style = MaterialTheme.typography.body2,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(t.spacing.sm)) {
                SettingsActionButton(
                    p = p,
                    text = AuraCodeBundle.message("settings.skills.import"),
                    enabled = !state.skillsLoading,
                    onClick = { onIntent(UiIntent.ImportLocalSkill) },
                )
                SettingsActionButton(
                    p = p,
                    text = AuraCodeBundle.message("settings.skills.refresh"),
                    emphasized = false,
                    enabled = !state.skillsLoading,
                    onClick = { onIntent(UiIntent.LoadSkills) },
                )
            }
        }
        if (state.skills.isEmpty()) {
            item {
                Text(
                    text = AuraCodeBundle.message("settings.skills.empty"),
                    color = p.textMuted,
                    style = MaterialTheme.typography.body2,
                )
            }
        } else {
            items(state.skills, key = { it.name }) { skill ->
                SkillRow(
                    p = p,
                    skill = skill,
                    busy = state.skillsLoading,
                    onToggle = { enabled -> onIntent(UiIntent.ToggleSkillEnabled(skill.name, enabled)) },
                    onOpen = { onIntent(UiIntent.OpenSkillPath(skill.effectivePath)) },
                    onReveal = { onIntent(UiIntent.RevealSkillPath(skill.effectivePath)) },
                    onDelete = { onIntent(UiIntent.DeleteSkill(skill.name)) },
                )
            }
        }
    }
}

@Composable
private fun SkillRow(
    p: DesignPalette,
    skill: SkillSummary,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.topBarBg.copy(alpha = 0.72f), RoundedCornerShape(t.spacing.md))
            .border(1.dp, p.markdownDivider.copy(alpha = 0.42f), RoundedCornerShape(t.spacing.md))
            .padding(horizontal = t.spacing.md, vertical = t.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(t.spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = skill.name,
                    color = p.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.body1,
                )
                SkillStateBadge(p = p, text = if (skill.enabled) AuraCodeBundle.message("settings.skills.status.enabled") else AuraCodeBundle.message("settings.skills.status.disabled"), accent = skill.enabled)
                SkillStateBadge(p = p, text = sourceLabel(skill.effectiveSource), accent = false)
                if (skill.sourceCount > 1) {
                    SkillStateBadge(
                        p = p,
                        text = AuraCodeBundle.message("settings.skills.sources.count", skill.sourceCount.toString()),
                        accent = false,
                    )
                }
            }
            Text(
                text = skill.description,
                color = p.textSecondary,
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkillMoreMenu(
                p = p,
                deletable = skill.deletable,
                enabled = !busy,
                onOpen = onOpen,
                onReveal = onReveal,
                onDelete = onDelete,
            )
            SettingsToggle(
                p = p,
                checked = skill.enabled,
                enabled = !busy,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun SkillMoreMenu(
    p: DesignPalette,
    deletable: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = assistantUiTokens()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .background(p.topStripBg, RoundedCornerShape(t.spacing.sm))
                .border(1.dp, p.markdownDivider.copy(alpha = 0.55f), RoundedCornerShape(t.spacing.sm))
                .clickable(enabled = enabled, onClick = { expanded = true })
                .padding(horizontal = t.spacing.sm, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource("/icons/more-vert.svg"),
                contentDescription = AuraCodeBundle.message("common.more"),
                tint = if (enabled) p.textSecondary else p.textMuted,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(onClick = {
                expanded = false
                onOpen()
            }) {
                Text(AuraCodeBundle.message("settings.skills.open"))
            }
            DropdownMenuItem(onClick = {
                expanded = false
                onReveal()
            }) {
                Text(AuraCodeBundle.message("settings.skills.reveal"))
            }
            if (deletable) {
                DropdownMenuItem(onClick = {
                    expanded = false
                    onDelete()
                }) {
                    Text(AuraCodeBundle.message("common.delete"))
                }
            }
        }
    }
}

@Composable
private fun SkillStateBadge(
    p: DesignPalette,
    text: String,
    accent: Boolean,
) {
    val t = assistantUiTokens()
    Box(
        modifier = Modifier
            .background(
                if (accent) p.accent.copy(alpha = 0.12f) else p.topStripBg,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (accent) p.accent.copy(alpha = 0.28f) else p.markdownDivider.copy(alpha = 0.45f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = t.spacing.sm, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (accent) p.accent else p.textMuted,
            style = MaterialTheme.typography.caption,
        )
    }
}

private fun sourceLabel(source: SkillSource): String = when (source) {
    SkillSource.LOCAL -> AuraCodeBundle.message("settings.skills.source.local")
    SkillSource.SUPERPOWERS -> AuraCodeBundle.message("settings.skills.source.superpowers")
    SkillSource.AGENTS -> AuraCodeBundle.message("settings.skills.source.agents")
}
