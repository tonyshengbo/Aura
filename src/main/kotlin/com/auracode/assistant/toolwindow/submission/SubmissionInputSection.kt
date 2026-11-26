package com.auracode.assistant.toolwindow.submission

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.eventing.UiIntent
import com.auracode.assistant.toolwindow.input.LocalMainInputController
import com.auracode.assistant.toolwindow.input.LocalInputEnabled
import com.auracode.assistant.toolwindow.input.InputDraft
import com.auracode.assistant.toolwindow.input.InputHighlight
import com.auracode.assistant.toolwindow.input.ComposeTextInput
import androidx.compose.material.DropdownMenu
import com.auracode.assistant.toolwindow.shared.AssistantUiText
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.FileTypeIcon
import com.auracode.assistant.toolwindow.shared.assistantPainterResource
import com.auracode.assistant.toolwindow.shared.assistantUiTokens

private val SubmissionOutlinedTextFieldTopPadding = 8.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubmissionInputSection(
    p: DesignPalette,
    state: SubmissionAreaState,
    running: Boolean,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val inputEnabled = LocalInputEnabled.current
    val density = LocalDensity.current
    var popupRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var editorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var caretBounds by remember { mutableStateOf<Rect?>(null) }
    val popupAnchor = remember(popupRootCoordinates, editorCoordinates, caretBounds) {
        resolveSubmissionPopupAnchorLayout(
            rootCoordinates = popupRootCoordinates,
            editorCoordinates = editorCoordinates,
        )?.let { layout ->
            calculateSubmissionPopupAnchor(
                layout = layout,
                cursorRect = caretBounds,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { popupRootCoordinates = it },
        ) {
            ComposeTextInput(
                draft = InputDraft(state.document, state.documentVersion, state.documentOrigin),
                identityKey = state.currentSessionId,
                controller = LocalMainInputController.current,
                palette = p,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = with(density) { SubmissionOutlinedTextFieldTopPadding.toDp() }),
                outlinedLabel = AssistantUiText.COMPOSER_HINT,
                onTextPositioned = { editorCoordinates = it },
                onValueChange = { onIntent(UiIntent.UpdateDocument(it.value, it.origin)) },
                highlights = state.mentionEntries.map { InputHighlight(it.start, it.endExclusive) },
                resolveCommand = { key, document ->
                    resolveSubmissionEditorCommand(state, document, key, onIntent)
                },
                onCaretBoundsChange = { caretBounds = it },
                normalizeValue = { previous, updated ->
                    normalizeMentionSelection(previous, updated, state.mentionEntries)
                },
            )
            val maxPopupHeight = maxHeight * 0.52f
            val popupMode = when {
                state.slashPopupVisible -> SubmissionPopupMode.SLASH
                state.agentPopupVisible -> SubmissionPopupMode.AGENT
                state.mentionPopupVisible -> SubmissionPopupMode.MENTION
                else -> SubmissionPopupMode.NONE
            }
            val popupContent = buildSubmissionPopupContent(
                slashSuggestions = state.slashSuggestions,
                activeSlashIndex = state.activeSlashIndex,
                mentionSuggestions = state.mentionSuggestions,
                activeMentionIndex = state.activeMentionIndex,
                agentSuggestions = state.agentSuggestions,
                activeAgentIndex = state.activeAgentIndex,
                mode = popupMode,
            )
            val popupScrollState = rememberScrollState()
            val popupRowRequesters = popupContent.rows.map { remember { BringIntoViewRequester() } }

            LaunchedEffect(popupMode, popupContent.selectedRowIndex) {
                if (popupMode != SubmissionPopupMode.NONE) {
                    popupContent.selectedRowIndex?.let { selectedRowIndex ->
                        popupRowRequesters.getOrNull(selectedRowIndex)?.bringIntoView()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { (popupAnchor ?: SubmissionPopupAnchor(x = 0, y = 0)).toIntOffset() }
                    .size(1.dp),
            ) {
                DropdownMenu(
                    expanded = inputEnabled && (state.slashPopupVisible || state.mentionPopupVisible || state.agentPopupVisible),
                    onDismissRequest = {
                        if (state.slashPopupVisible) {
                            onIntent(UiIntent.DismissSlashPopup)
                        } else if (state.agentPopupVisible) {
                            onIntent(UiIntent.DismissAgentPopup)
                        } else {
                            onIntent(UiIntent.DismissMentionPopup)
                        }
                    },
                    modifier = Modifier.heightIn(max = maxPopupHeight),
                    properties = PopupProperties(focusable = false),
                    scrollState = popupScrollState,
                ) {
                    Column(
                        modifier = Modifier
                            .width(360.dp),
                    ) {
                        popupContent.rows.forEachIndexed { index, row ->
                            when (row) {
                                is SubmissionPopupRow.Header -> Box(
                                    modifier = Modifier.bringIntoViewRequester(popupRowRequesters[index]),
                                ) {
                                    SlashSectionHeader(
                                        title = row.title,
                                        p = p,
                                    )
                                }
                                is SubmissionPopupRow.SlashItem -> {
                                    DropdownMenuItem(
                                        modifier = Modifier.bringIntoViewRequester(popupRowRequesters[index]),
                                        enabled = row.item.isEnabled(),
                                        onClick = { row.item.dispatch(onIntent) },
                                    ) {
                                        SlashSuggestionRow(
                                            title = row.item.title,
                                            description = row.item.description,
                                            selected = index == popupContent.selectedRowIndex,
                                            enabled = row.item.isEnabled(),
                                            p = p,
                                        )
                                    }
                                }
                                is SubmissionPopupRow.AgentItem -> {
                                    DropdownMenuItem(
                                        modifier = Modifier.bringIntoViewRequester(popupRowRequesters[index]),
                                        onClick = { onIntent(UiIntent.SelectAgent(row.agent)) },
                                    ) {
                                        AgentSuggestionRow(
                                            name = row.agent.name,
                                            selected = index == popupContent.selectedRowIndex,
                                            p = p,
                                        )
                                    }
                                }
                                is SubmissionPopupRow.MentionItem -> {
                                    DropdownMenuItem(
                                        modifier = Modifier.bringIntoViewRequester(popupRowRequesters[index]),
                                        onClick = {
                                            when (row) {
                                                is SubmissionPopupRow.MentionAgentItem -> {
                                                    onIntent(UiIntent.SelectSessionSubagentMention(row.agent.threadId))
                                                }

                                                is SubmissionPopupRow.MentionFileItem -> {
                                                    onIntent(UiIntent.SelectMentionFile(row.entry.path))
                                                }
                                            }
                                        },
                                    ) {
                                        when (row) {
                                            is SubmissionPopupRow.MentionAgentItem -> MentionAgentSuggestionRow(
                                                agent = row.agent,
                                                selected = index == popupContent.selectedRowIndex,
                                                p = p,
                                            )

                                            is SubmissionPopupRow.MentionFileItem -> MentionFileSuggestionRow(
                                                entry = row.entry,
                                                selected = index == popupContent.selectedRowIndex,
                                                p = p,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlashSectionHeader(
    title: String,
    p: DesignPalette,
) {
    Text(
        text = title,
        color = p.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SlashSuggestionRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) p.topStripBg else Color.Transparent, RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (enabled) p.textPrimary else p.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material.MaterialTheme.typography.body2,
            )
            Text(
                text = description,
                color = p.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material.MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun MentionFileSuggestionRow(
    entry: ContextEntry,
    selected: Boolean,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) p.topStripBg else Color.Transparent, RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        FileTypeIcon(
            fileName = entry.path.substringAfterLast('/').substringAfterLast('\\').ifBlank { entry.displayName },
            tint = p.textSecondary,
        )
        Spacer(Modifier.width(t.spacing.sm))
        Text(
            text = entry.displayName,
            color = if (selected) p.textPrimary else p.textSecondary,
            maxLines = 1,
        )
        if (entry.tailPath.isNotBlank()) {
            Spacer(Modifier.width(t.spacing.sm))
            Text(
                text = entry.tailPath,
                color = p.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MentionAgentSuggestionRow(
    agent: SessionSubagentUiModel,
    selected: Boolean,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) p.topStripBg else Color.Transparent, RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = assistantPainterResource("/icons/community.svg"),
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(t.controls.iconMd),
        )
        Spacer(Modifier.width(t.spacing.sm))
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "@${agent.mentionSlug}",
                color = if (selected) p.textPrimary else p.textSecondary,
                maxLines = 1,
            )
            Text(
                text = agent.summary ?: agent.displayName,
                color = p.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material.MaterialTheme.typography.caption,
            )
        }
    }
}

@Composable
private fun AgentSuggestionRow(
    name: String,
    selected: Boolean,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) p.topStripBg else Color.Transparent, RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = assistantPainterResource("/icons/agent-settings.svg"),
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(t.controls.iconMd),
        )
        Spacer(Modifier.width(t.spacing.sm))
        Text(
            text = name,
            color = if (selected) p.textPrimary else p.textSecondary,
            maxLines = 1,
        )
    }
}

internal enum class SubmissionPopupMode {
    NONE,
    SLASH,
    MENTION,
    AGENT,
}

internal data class SubmissionPopupContent(
    val rows: List<SubmissionPopupRow>,
    val selectedRowIndex: Int?,
)

internal sealed interface SubmissionPopupRow {
    data class Header(val title: String) : SubmissionPopupRow
    data class SlashItem(val item: SlashSuggestionItem) : SubmissionPopupRow
    sealed interface MentionItem : SubmissionPopupRow
    data class MentionFileItem(val entry: ContextEntry) : MentionItem
    data class MentionAgentItem(val agent: SessionSubagentUiModel) : MentionItem
    data class AgentItem(val agent: com.auracode.assistant.settings.SavedAgentDefinition) : SubmissionPopupRow
}

/** Builds one popup row model so slash, mention, and agent menus share navigation behavior. */
internal fun buildSubmissionPopupContent(
    slashSuggestions: List<SlashSuggestionItem>,
    activeSlashIndex: Int,
    mentionSuggestions: List<MentionSuggestion>,
    activeMentionIndex: Int,
    agentSuggestions: List<com.auracode.assistant.settings.SavedAgentDefinition>,
    activeAgentIndex: Int,
    mode: SubmissionPopupMode,
): SubmissionPopupContent {
    return when (mode) {
        SubmissionPopupMode.SLASH -> {
            val rows = mutableListOf<SubmissionPopupRow>()
            var selectedRowIndex: Int? = null
            val commandItems = slashSuggestions.filterIsInstance<SlashSuggestionItem.Command>()
            val skillItems = slashSuggestions.filterIsInstance<SlashSuggestionItem.Skill>()
            if (commandItems.isNotEmpty()) {
                rows += SubmissionPopupRow.Header(AuraCodeBundle.message("composer.slash.section.commands"))
                commandItems.forEachIndexed { index, item ->
                    if (index == activeSlashIndex) {
                        selectedRowIndex = rows.size
                    }
                    rows += SubmissionPopupRow.SlashItem(item)
                }
            }
            if (skillItems.isNotEmpty()) {
                rows += SubmissionPopupRow.Header(AuraCodeBundle.message("composer.slash.section.skills"))
                skillItems.forEachIndexed { index, item ->
                    val overallIndex = commandItems.size + index
                    if (overallIndex == activeSlashIndex) {
                        selectedRowIndex = rows.size
                    }
                    rows += SubmissionPopupRow.SlashItem(item)
                }
            }
            SubmissionPopupContent(rows = rows, selectedRowIndex = selectedRowIndex)
        }
        SubmissionPopupMode.MENTION -> {
            val rows = mutableListOf<SubmissionPopupRow>()
            var selectedRowIndex: Int? = null
            val agents = mentionSuggestions.filterIsInstance<MentionSuggestion.Agent>()
            val files = mentionSuggestions.filterIsInstance<MentionSuggestion.File>()
            if (agents.isNotEmpty()) {
                rows += SubmissionPopupRow.Header(AuraCodeBundle.message("composer.mention.section.agents"))
                agents.forEachIndexed { index, item ->
                    if (index == activeMentionIndex) {
                        selectedRowIndex = rows.size
                    }
                    rows += SubmissionPopupRow.MentionAgentItem(item.agent)
                }
            }
            if (files.isNotEmpty()) {
                rows += SubmissionPopupRow.Header(AuraCodeBundle.message("composer.mention.section.files"))
                files.forEachIndexed { index, item ->
                    val overallIndex = agents.size + index
                    if (overallIndex == activeMentionIndex) {
                        selectedRowIndex = rows.size
                    }
                    rows += SubmissionPopupRow.MentionFileItem(item.entry)
                }
            }
            SubmissionPopupContent(rows = rows, selectedRowIndex = selectedRowIndex)
        }
        SubmissionPopupMode.AGENT -> SubmissionPopupContent(
            rows = agentSuggestions.map { SubmissionPopupRow.AgentItem(it) },
            selectedRowIndex = agentSuggestions.indices.firstOrNull { it == activeAgentIndex },
        )
        SubmissionPopupMode.NONE -> SubmissionPopupContent(rows = emptyList(), selectedRowIndex = null)
    }
}

/** Routes popup row clicks back through the existing slash selection intents. */
private fun SlashSuggestionItem.dispatch(onIntent: (UiIntent) -> Unit) {
    when (this) {
        is SlashSuggestionItem.Command -> onIntent(UiIntent.SelectSlashCommand(command))
        is SlashSuggestionItem.Skill -> onIntent(UiIntent.SelectSlashSkill(name))
    }
}

/** Exposes the enabled flag for command rows while keeping skill rows always selectable. */
private fun SlashSuggestionItem.isEnabled(): Boolean {
    return when (this) {
        is SlashSuggestionItem.Command -> enabled
        is SlashSuggestionItem.Skill -> true
    }
}

/** Provides stable keys so the popup list keeps its scroll position while selection changes. */
private fun SubmissionPopupRow.stableKey(index: Int): String {
    return when (this) {
        is SubmissionPopupRow.Header -> "header:$title:$index"
        is SubmissionPopupRow.SlashItem -> "slash:${item.title}"
        is SubmissionPopupRow.MentionAgentItem -> "mention-agent:${agent.threadId}"
        is SubmissionPopupRow.MentionFileItem -> "mention-file:${entry.path}"
        is SubmissionPopupRow.AgentItem -> "agent:${agent.id}"
    }
}
