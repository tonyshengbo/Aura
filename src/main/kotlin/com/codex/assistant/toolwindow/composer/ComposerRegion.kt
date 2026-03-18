package com.codex.assistant.toolwindow.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.AlertDialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.codex.assistant.provider.CodexModelCatalog
import com.codex.assistant.toolwindow.eventing.ComposerMode
import com.codex.assistant.toolwindow.eventing.ComposerReasoning
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.eventing.localizedLabel
import com.codex.assistant.i18n.CodexBundle
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.HoverTooltip
import com.codex.assistant.toolwindow.shared.ToolWindowUiText
import com.codex.assistant.toolwindow.shared.assistantUiTokens
import com.codex.assistant.toolwindow.timeline.TimelineAreaState
import com.intellij.openapi.fileTypes.FileTypeManager
import java.io.FileInputStream
import java.awt.image.BufferedImage

@Composable
internal fun ComposerRegion(
    p: DesignPalette,
    state: ComposerAreaState,
    conversationState: TimelineAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val running = conversationState.isRunning
    val selectedMention = state.mentionSuggestions.getOrNull(state.activeMentionIndex)
    val selectedAgent = state.agentSuggestions.getOrNull(state.activeAgentIndex)
    val composing = state.document.composition != null

    val previewAttachment = state.attachments.firstOrNull { it.id == state.previewAttachmentId }
    if (previewAttachment != null) {
        val bitmap = rememberAttachmentBitmap(previewAttachment.path)
        AlertDialog(
            onDismissRequest = { onIntent(UiIntent.CloseAttachmentPreview) },
            title = { Text(previewAttachment.displayName) },
            text = {
                Box(
                    modifier = Modifier
                        .size(420.dp)
                        .background(p.topBarBg, RoundedCornerShape(t.spacing.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewAttachment.kind == AttachmentKind.IMAGE && bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(400.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            painter = painterResource("/icons/attach-file.svg"),
                            contentDescription = null,
                            tint = p.textMuted,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(UiIntent.CloseAttachmentPreview) }) {
                    Text(CodexBundle.message("common.close"))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(UiIntent.RemoveAttachment(previewAttachment.id)) }) {
                    Text(CodexBundle.message("common.delete"))
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(p.composerBg).padding(horizontal = t.spacing.md, vertical = t.spacing.xs + t.spacing.xs),
    ) {
        AttachmentStrip(p = p, state = state, onIntent = onIntent)
        if (state.attachments.isNotEmpty()) {
            Spacer(Modifier.height(t.spacing.sm))
        }
        ContextEntryStrip(p = p, state = state, onIntent = onIntent)
        Spacer(Modifier.height(t.spacing.xs + t.spacing.xs))

        Box(modifier = Modifier.fillMaxWidth()) {
            val mentionVisualTransformation = remember(state.mentionEntries, p) {
                MentionVisualTransformation(state.mentionEntries, p)
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        if (!composing && state.mentionPopupVisible) {
                            when (it.key) {
                                Key.DirectionDown -> {
                                    onIntent(UiIntent.MoveMentionSelectionNext)
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionUp -> {
                                    onIntent(UiIntent.MoveMentionSelectionPrevious)
                                    return@onPreviewKeyEvent true
                                }
                                Key.Escape -> {
                                    onIntent(UiIntent.DismissMentionPopup)
                                    return@onPreviewKeyEvent true
                                }
                                Key.Enter -> {
                                    if (!it.isShiftPressed && selectedMention != null) {
                                        onIntent(UiIntent.SelectMentionFile(selectedMention.path))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                else -> Unit
                            }
                        }
                        if (!composing && state.agentPopupVisible) {
                            when (it.key) {
                                Key.DirectionDown -> {
                                    onIntent(UiIntent.MoveAgentSelectionNext)
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionUp -> {
                                    onIntent(UiIntent.MoveAgentSelectionPrevious)
                                    return@onPreviewKeyEvent true
                                }
                                Key.Escape -> {
                                    onIntent(UiIntent.DismissAgentPopup)
                                    return@onPreviewKeyEvent true
                                }
                                Key.Enter -> {
                                    if (!it.isShiftPressed && selectedAgent != null) {
                                        onIntent(UiIntent.SelectAgent(selectedAgent))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                else -> Unit
                            }
                        }
                        if (!composing && !it.isShiftPressed && !it.isMetaPressed && !it.isCtrlPressed) {
                            when (it.key) {
                                Key.DirectionLeft -> {
                                    moveCursorLeftAcrossMention(state.document, state.mentionEntries)?.let { next ->
                                        onIntent(UiIntent.UpdateDocument(next))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                Key.DirectionRight -> {
                                    moveCursorRightAcrossMention(state.document, state.mentionEntries)?.let { next ->
                                        onIntent(UiIntent.UpdateDocument(next))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                Key.Backspace -> {
                                    val next = if (state.document.selection.collapsed) {
                                        removeMentionByBackspace(state.document, state.mentionEntries)
                                    } else {
                                        removeMentionSelection(state.document, state.mentionEntries)
                                    }
                                    next?.let { removed ->
                                        onIntent(UiIntent.UpdateDocument(removed.first))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                Key.Delete -> {
                                    val next = if (state.document.selection.collapsed) {
                                        removeMentionByDelete(state.document, state.mentionEntries)
                                    } else {
                                        removeMentionSelection(state.document, state.mentionEntries)
                                    }
                                    next?.let { removed ->
                                        onIntent(UiIntent.UpdateDocument(removed.first))
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                else -> Unit
                            }
                        }
                        if (it.key == Key.V && (it.isMetaPressed || it.isCtrlPressed)) {
                            onIntent(UiIntent.PasteImageFromClipboard)
                            return@onPreviewKeyEvent false
                        }
                        if (!composing && !running && it.key == Key.Enter && !it.isShiftPressed) {
                            onIntent(UiIntent.SendPrompt)
                            true
                        } else {
                            false
                        }
                    },
                value = state.document,
                onValueChange = { onIntent(UiIntent.UpdateDocument(it)) },
                textStyle = TextStyle(color = p.textPrimary, fontSize = t.type.body, lineHeight = 19.sp),
                label = {
                    Text(
                        text = ToolWindowUiText.COMPOSER_HINT,
                        color = p.textMuted,
                        style = androidx.compose.material.MaterialTheme.typography.body2,
                    )
                },
                visualTransformation = mentionVisualTransformation,
                singleLine = false,
                maxLines = 6,
                shape = RoundedCornerShape(t.spacing.sm),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = p.textPrimary,
                    backgroundColor = p.timelineCardBg,
                    cursorColor = p.textPrimary,
                    focusedBorderColor = p.accent,
                    unfocusedBorderColor = p.markdownDivider.copy(alpha = 0.55f),
                    focusedLabelColor = p.textSecondary,
                    unfocusedLabelColor = p.textMuted,
                ),
            )
            DropdownMenu(
                expanded = state.mentionPopupVisible || state.agentPopupVisible,
                onDismissRequest = {
                    if (state.agentPopupVisible) {
                        onIntent(UiIntent.DismissAgentPopup)
                    } else {
                        onIntent(UiIntent.DismissMentionPopup)
                    }
                },
                properties = PopupProperties(focusable = false),
            ) {
                if (state.agentPopupVisible) {
                    state.agentSuggestions.forEachIndexed { index, agent ->
                        DropdownMenuItem(onClick = { onIntent(UiIntent.SelectAgent(agent)) }) {
                            AgentSuggestionRow(
                                name = agent.name,
                                selected = index == state.activeAgentIndex,
                                p = p,
                            )
                        }
                    }
                } else {
                    state.mentionSuggestions.forEachIndexed { index, entry ->
                        DropdownMenuItem(onClick = { onIntent(UiIntent.SelectMentionFile(entry.path)) }) {
                            MentionSuggestionRow(
                                entry = entry,
                                selected = index == state.activeMentionIndex,
                                p = p,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(t.spacing.xs + t.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth().background(p.topBarBg.copy(alpha = 0.62f), RoundedCornerShape(t.spacing.sm)).padding(horizontal = t.spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropdownChip(
                iconPath = modeOption(state.selectedMode).iconPath,
                label = state.selectedMode.localizedLabel(),
                expanded = state.modeMenuExpanded,
                onToggle = { onIntent(UiIntent.ToggleModeMenu) },
                onDismiss = { onIntent(UiIntent.ToggleModeMenu) },
                p = p,
            ) {
                ComposerMode.entries.forEach { mode ->
                    DropdownMenuItem(onClick = { onIntent(UiIntent.SelectMode(mode)) }) {
                        DropdownOptionRow(
                            option = modeOption(mode),
                            selected = state.selectedMode == mode,
                            p = p,
                        )
                    }
                }
            }
            Spacer(Modifier.width(t.spacing.sm))
            DropdownChip(
                iconPath = if (state.selectedModel.contains("gpt")) "/icons/gpt.svg" else "/icons/codex.svg",
                label = state.selectedModel,
                expanded = state.modelMenuExpanded,
                onToggle = { onIntent(UiIntent.ToggleModelMenu) },
                onDismiss = { onIntent(UiIntent.ToggleModelMenu) },
                p = p,
            ) {
                CodexModelCatalog.ids().forEach { model ->
                    DropdownMenuItem(onClick = { onIntent(UiIntent.SelectModel(model)) }) {
                        DropdownOptionRow(
                            option = modelOption(model),
                            selected = state.selectedModel == model,
                            p = p,
                        )
                    }
                }
            }
            Spacer(Modifier.width(t.spacing.sm))
            DropdownChip(
                iconPath = reasoningIcon(state.selectedReasoning),
                label = state.selectedReasoning.localizedLabel(),
                expanded = state.reasoningMenuExpanded,
                onToggle = { onIntent(UiIntent.ToggleReasoningMenu) },
                onDismiss = { onIntent(UiIntent.ToggleReasoningMenu) },
                p = p,
            ) {
                ComposerReasoning.entries.forEach { level ->
                    DropdownMenuItem(onClick = { onIntent(UiIntent.SelectReasoning(level)) }) {
                        DropdownOptionRow(
                            option = reasoningOption(level),
                            selected = state.selectedReasoning == level,
                            p = p,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val canSend = !running && state.hasPromptContent()
            HoverTooltip(text = if (running) CodexBundle.message("composer.stop") else CodexBundle.message("composer.send")) {
                IconButton(
                    modifier = Modifier.size(t.controls.sendButton).background(
                        color = if (running || canSend) p.timelineCardBg else p.topStripBg,
                        shape = RoundedCornerShape(t.spacing.sm),
                    ),
                    onClick = {
                        if (running) {
                            onIntent(UiIntent.CancelRun)
                        } else {
                            onIntent(UiIntent.SendPrompt)
                        }
                    },
                    enabled = running || canSend,
                ) {
                    Icon(
                        painter = painterResource(if (running) "/icons/stop.svg" else "/icons/send.svg"),
                        contentDescription = if (running) CodexBundle.message("composer.stop") else CodexBundle.message("composer.send"),
                        tint = if (running || canSend) p.textPrimary else p.textMuted,
                        modifier = Modifier.size(t.controls.iconLg),
                    )

                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    p: DesignPalette,
    state: ComposerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    if (state.attachments.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.topBarBg.copy(alpha = 0.72f), RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.attachments.forEach { attachment ->
            AttachmentCard(attachment = attachment, p = p, onIntent = onIntent)
            Spacer(Modifier.width(t.spacing.xs))
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: AttachmentEntry,
    p: DesignPalette,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val bitmap = rememberAttachmentBitmap(attachment.path)
    Box(
        modifier = Modifier
            .size(width = t.controls.attachmentCard, height = t.controls.attachmentCard)
            .background(p.topStripBg, RoundedCornerShape(t.spacing.sm))
            .clickable { onIntent(UiIntent.OpenAttachmentPreview(attachment.id)) }
            .padding(t.spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(p.timelineCardBg, RoundedCornerShape(t.spacing.sm)),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.kind == AttachmentKind.IMAGE && bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource("/icons/attach-file.svg"),
                    contentDescription = null,
                    tint = p.textMuted,
                    modifier = Modifier.size(t.controls.iconMd),
                )
            }
        }
        HoverTooltip(text = CodexBundle.message("common.delete")) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
                    .background(p.topBarBg, RoundedCornerShape(999.dp))
                    .clickable { onIntent(UiIntent.RemoveAttachment(attachment.id)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource("/icons/delete.svg"),
                    contentDescription = CodexBundle.message("common.delete"),
                    tint = p.textMuted,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberAttachmentBitmap(path: String): ImageBitmap? {
    return remember(path) {
        runCatching {
            FileInputStream(path).use(::loadImageBitmap)
        }.getOrNull()
    }
}

@Composable
private fun FileAttachmentChip(
    attachment: AttachmentEntry,
    p: DesignPalette,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.background(p.topStripBg, RoundedCornerShape(999.dp)).padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(fileName = attachment.displayName, tint = p.textSecondary)
        Spacer(Modifier.width(t.spacing.xs))
        Text(
            text = attachment.displayName,
            color = p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(t.spacing.xs))
        HoverTooltip(text = CodexBundle.message("common.delete")) {
            Icon(
                painter = painterResource("/icons/delete.svg"),
                contentDescription = CodexBundle.message("common.delete"),
                tint = p.textMuted,
                modifier = Modifier.size(t.controls.iconSm).clickable(onClick = { onIntent(UiIntent.RemoveAttachment(attachment.id)) }),
            )
        }
    }
}

@Composable
private fun ContextEntryStrip(
    p: DesignPalette,
    state: ComposerAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.topBarBg.copy(alpha = 0.72f), RoundedCornerShape(t.spacing.sm))
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoverTooltip(text = CodexBundle.message("composer.addAttachment")) {
            IconButton(
                modifier = Modifier.size(t.controls.headerActionTouch),
                onClick = { onIntent(UiIntent.OpenAttachmentPicker) },
            ) {
                Icon(
                    painter = painterResource("/icons/attach-file.svg"),
                    contentDescription = CodexBundle.message("composer.addAttachment"),
                    tint = p.textSecondary,
                    modifier = Modifier.size(t.controls.iconMd),
                )
            }
        }
        Spacer(Modifier.width(t.spacing.xs))
        state.contextEntries.forEach { entry ->
            ContextEntryChip(
                entry = entry,
                p = p,
                onRemove = { onIntent(UiIntent.RemoveContextFile(entry.path)) },
            )
            Spacer(Modifier.width(t.spacing.xs))
        }
        state.agentEntries.forEach { entry ->
            AgentEntryChip(
                entry = entry,
                p = p,
                onRemove = { onIntent(UiIntent.RemoveSelectedAgent(entry.id)) },
            )
            Spacer(Modifier.width(t.spacing.xs))
        }
    }
}

@Composable
private fun ContextEntryChip(
    entry: ContextEntry,
    p: DesignPalette,
    onRemove: () -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.background(p.topStripBg, RoundedCornerShape(999.dp)).padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(fileName = entry.displayName, tint = p.textSecondary)
        Spacer(Modifier.width(t.spacing.xs))
        Text(
            text = entry.displayName,
            color = p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(t.spacing.xs))
        HoverTooltip(text = CodexBundle.message("composer.removeFile")) {
            Icon(
                painter = painterResource("/icons/close-small.svg"),
                contentDescription = CodexBundle.message("composer.removeFile"),
                tint = p.textMuted,
                modifier = Modifier.size(t.controls.iconSm).clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun AgentEntryChip(
    entry: AgentContextEntry,
    p: DesignPalette,
    onRemove: () -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.background(p.topStripBg, RoundedCornerShape(999.dp)).padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource("/icons/agent-settings.svg"),
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(t.controls.iconMd),
        )
        Spacer(Modifier.width(t.spacing.xs))
        Text(
            text = entry.name,
            color = p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(t.spacing.xs))
        HoverTooltip(text = CodexBundle.message("common.delete")) {
            Icon(
                painter = painterResource("/icons/close-small.svg"),
                contentDescription = CodexBundle.message("common.delete"),
                tint = p.textMuted,
                modifier = Modifier.size(t.controls.iconSm).clickable(onClick = onRemove),
            )
        }
    }
}

private class MentionVisualTransformation(
    mentions: List<MentionEntry>,
    private val palette: DesignPalette,
) : VisualTransformation {
    private val spans = mentions.map { MentionTransformSpan(start = it.start, endExclusive = it.endExclusive) }

    override fun filter(text: AnnotatedString): TransformedText {
        return buildMentionTransformedText(
            text = text.text,
            spans = spans,
        ) { builder, start, endExclusive ->
            builder.addStyle(
                    SpanStyle(
                        color = palette.textPrimary,
                        background = palette.topStripBg,
                    ),
                    start,
                    endExclusive,
                )
        }
    }
}

@Composable
private fun MentionSuggestionRow(
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
        FileTypeIcon(fileName = entry.displayName, tint = p.textSecondary)
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
            painter = painterResource("/icons/agent-settings.svg"),
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

@Composable
private fun FileTypeIcon(fileName: String, tint: Color) {
    val t = assistantUiTokens()
    val bitmap = remember(fileName) {
        val icon = FileTypeManager.getInstance().getFileTypeByFileName(fileName).icon
        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }
        image.toComposeImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.size(t.controls.iconMd),
        alpha = tint.alpha,
    )
}

@Composable
private fun DropdownChip(
    iconPath: String,
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    p: DesignPalette,
    content: @Composable () -> Unit,
) {
    val t = assistantUiTokens()
    Box {
        Row(
            modifier = Modifier
                .background(Color.Transparent, RoundedCornerShape(t.spacing.sm))
                .clickable(onClick = onToggle)
                .padding(horizontal = t.spacing.xs + t.spacing.xs, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconPath),
                contentDescription = null,
                tint = p.textSecondary,
                modifier = Modifier.size(t.controls.iconMd),
            )
            Spacer(Modifier.width(t.spacing.xs))
            Text(label, color = p.textSecondary)
            Spacer(Modifier.width(t.spacing.xs))
            Icon(
                painter = painterResource("/icons/arrow-down.svg"),
                contentDescription = null,
                tint = p.textMuted,
                modifier = Modifier.size(t.controls.iconLg),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            content()
        }
    }
}

private fun reasoningIcon(reasoning: ComposerReasoning): String = when (reasoning) {
    ComposerReasoning.LOW -> "/icons/stat_0.svg"
    ComposerReasoning.MEDIUM -> "/icons/stat_1.svg"
    ComposerReasoning.HIGH -> "/icons/stat_2.svg"
    ComposerReasoning.MAX -> "/icons/stat_3.svg"
}

private data class DropdownOption(
    val iconPath: String,
    val label: String,
)

private fun modeOption(mode: ComposerMode): DropdownOption = when (mode) {
    ComposerMode.AUTO -> DropdownOption(iconPath = "/icons/auto-mode.svg", label = mode.localizedLabel())
    ComposerMode.APPROVAL -> DropdownOption(iconPath = "/icons/auto-mode-off.svg", label = mode.localizedLabel())
}

private fun modelOption(model: String): DropdownOption = DropdownOption(
    iconPath = if (model.contains("gpt", ignoreCase = true)) "/icons/gpt.svg" else "/icons/codex.svg",
    label = model,
)

private fun reasoningOption(reasoning: ComposerReasoning): DropdownOption = DropdownOption(
    iconPath = reasoningIcon(reasoning),
    label = reasoning.localizedLabel(),
)

@Composable
private fun DropdownOptionRow(
    option: DropdownOption,
    selected: Boolean,
    p: DesignPalette,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) p.topStripBg else Color.Transparent,
                shape = RoundedCornerShape(t.spacing.sm),
            )
            .padding(horizontal = t.spacing.sm, vertical = t.spacing.xs),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(option.iconPath),
            contentDescription = null,
            tint = p.textSecondary,
            modifier = Modifier.size(t.controls.iconMd),
        )
        Spacer(Modifier.width(t.spacing.sm))
        Text(
            text = option.label,
            color = if (selected) p.textPrimary else p.textSecondary,
            style = androidx.compose.material.MaterialTheme.typography.body2.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
        )
    }
}
