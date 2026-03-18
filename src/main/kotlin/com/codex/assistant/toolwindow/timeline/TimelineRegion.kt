package com.codex.assistant.toolwindow.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.assistant.model.MessageRole
import com.codex.assistant.protocol.ItemStatus
import com.codex.assistant.toolwindow.eventing.UiIntent
import com.codex.assistant.toolwindow.shared.DesignPalette
import com.codex.assistant.toolwindow.shared.HoverTooltip
import com.codex.assistant.toolwindow.shared.assistantUiTokens
import com.codex.assistant.i18n.CodexBundle
import java.io.FileInputStream

@Composable
internal fun TimelineRegion(
    modifier: Modifier,
    p: DesignPalette,
    state: TimelineAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    var previewAttachment by remember { mutableStateOf<TimelineMessageAttachment?>(null) }
    val listState = rememberLazyListState()
    val rowCount = state.nodes.size
    val nearBottom = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) {
                true
            } else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= total - 2
            }
        }
    }

    LaunchedEffect(state.renderVersion) {
        when (state.renderCause) {
            TimelineRenderCause.HISTORY_PREPEND -> {
                val delta = state.prependedCount
                if (delta > 0) {
                    listState.scrollToItem(
                        index = (listState.firstVisibleItemIndex + delta).coerceAtLeast(0),
                        scrollOffset = listState.firstVisibleItemScrollOffset,
                    )
                }
            }

            TimelineRenderCause.HISTORY_RESET,
            TimelineRenderCause.LIVE_UPDATE,
            -> {
                val lastIndex = rowCount - 1
                if (lastIndex >= 0 && nearBottom.value) {
                    listState.animateScrollToItem(lastIndex)
                }
            }

            TimelineRenderCause.IDLE -> Unit
        }
    }

    previewAttachment?.let { attachment ->
        val bitmap = rememberTimelineAttachmentBitmap(attachment.assetPath)
        AlertDialog(
            onDismissRequest = { previewAttachment = null },
            title = { Text(attachment.displayName) },
            text = {
                Box(
                    modifier = Modifier
                        .size(420.dp)
                        .background(p.topBarBg, RoundedCornerShape(t.spacing.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (attachment.kind == TimelineAttachmentKind.IMAGE && bitmap != null) {
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
                TextButton(onClick = { previewAttachment = null }) {
                    Text(CodexBundle.message("common.close"))
                }
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = t.spacing.lg, vertical = t.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        items(
            items = state.nodes,
            key = TimelineNode::id,
        ) { node ->
            when (node) {
                is TimelineNode.LoadMoreNode -> LoadOlderHistoryButton(
                    loading = node.isLoading,
                    p = p,
                    onClick = { onIntent(UiIntent.LoadOlderMessages) },
                )

                is TimelineNode.MessageNode -> MessageNodeView(
                    node = node,
                    p = p,
                    onPreviewAttachment = { previewAttachment = it },
                )
                is TimelineNode.ActivityNode -> ActivityNodeView(
                    node = node,
                    p = p,
                    state = state,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun LoadOlderHistoryButton(
    loading: Boolean,
    p: DesignPalette,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.timelineCardBg, RoundedCornerShape(t.spacing.sm))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
    ) {
        Text(
            text = if (loading) CodexBundle.message("timeline.loadingOlder") else CodexBundle.message("timeline.loadOlder"),
            color = p.textSecondary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ActivityNodeView(
    node: TimelineNode.ActivityNode,
    p: DesignPalette,
    state: TimelineAreaState,
    onIntent: (UiIntent) -> Unit,
) {
    val t = assistantUiTokens()
    val expanded = state.expandedNodeIds.contains(node.id)
    val indicatorColor = when (node.status) {
        ItemStatus.FAILED -> p.danger
        ItemStatus.RUNNING -> p.accent
        else -> p.success
    }

    HoverTooltip(text = if (expanded) CodexBundle.message("timeline.collapse") else CodexBundle.message("timeline.expand")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.timelineCardBg, RoundedCornerShape(t.spacing.sm))
                .border(
                    width = 1.dp,
                    color = p.markdownDivider.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(t.spacing.sm),
                )
                .clickable { onIntent(UiIntent.ToggleNodeExpanded(node.id)) }
                .padding(horizontal = t.spacing.md, vertical = t.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (expanded) "/icons/arrow-down.svg" else "/icons/arrow-right.svg"),
                    contentDescription = if (expanded) CodexBundle.message("timeline.collapse") else CodexBundle.message("timeline.expand"),
                    tint = p.textMuted,
                    modifier = Modifier.size(t.controls.iconMd),
                )
                Spacer(Modifier.width(t.spacing.sm))
                Text(
                    text = node.title,
                    color = p.timelineCardText,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material.MaterialTheme.typography.subtitle1,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(t.spacing.sm)
                        .background(indicatorColor, CircleShape),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(t.spacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(p.topBarBg.copy(alpha = 0.55f), RoundedCornerShape(t.spacing.sm))
                        .padding(horizontal = t.spacing.sm, vertical = t.spacing.sm),
                ) {
                TimelineMarkdown(
                    text = node.body,
                    palette = p,
                )
                }
            }
        }
    }
}

@Composable
private fun MessageNodeView(
    node: TimelineNode.MessageNode,
    p: DesignPalette,
    onPreviewAttachment: (TimelineMessageAttachment) -> Unit,
) {
    val t = assistantUiTokens()
    if (isUserMessageNode(node)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(p.userBubbleBg, RoundedCornerShape(t.spacing.sm + t.spacing.xs))
                    .padding(horizontal = t.spacing.md, vertical = t.spacing.xs + t.spacing.xs),
            ) {
                MessageContent(
                    node = node,
                    p = p,
                    onPreviewAttachment = onPreviewAttachment,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        ) {
            MessageContent(
                node = node,
                p = p,
                onPreviewAttachment = onPreviewAttachment,
            )
        }
    }
}

@Composable
private fun MessageContent(
    node: TimelineNode.MessageNode,
    p: DesignPalette,
    onPreviewAttachment: (TimelineMessageAttachment) -> Unit,
) {
    val t = assistantUiTokens()
    Column {
        if (node.attachments.isNotEmpty()) {
            AttachmentRow(
                attachments = node.attachments,
                p = p,
                onPreviewAttachment = onPreviewAttachment,
            )
            if (node.text.isNotBlank()) {
                Spacer(Modifier.height(t.spacing.sm))
            }
        }
        if (node.text.isNotBlank()) {
            TimelineMarkdown(
                text = node.text,
                palette = p,
                modifier = Modifier.fillMaxWidth().padding(end = t.spacing.sm),
            )
        }
    }
}

@Composable
private fun AttachmentRow(
    attachments: List<TimelineMessageAttachment>,
    p: DesignPalette,
    onPreviewAttachment: (TimelineMessageAttachment) -> Unit,
) {
    val t = assistantUiTokens()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        attachments.forEach { attachment ->
            AttachmentCard(
                attachment = attachment,
                p = p,
                onClick = { onPreviewAttachment(attachment) },
            )
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: TimelineMessageAttachment,
    p: DesignPalette,
    onClick: () -> Unit,
) {
    val t = assistantUiTokens()
    val bitmap = if (attachment.kind == TimelineAttachmentKind.IMAGE) {
        rememberTimelineAttachmentBitmap(attachment.assetPath)
    } else {
        null
    }
    Column(
        modifier = Modifier
            .width(140.dp)
            .background(p.timelineCardBg, RoundedCornerShape(t.spacing.sm))
            .clickable(onClick = onClick)
            .padding(t.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(t.spacing.xs),
    ) {
        if (attachment.kind == TimelineAttachmentKind.IMAGE && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(p.topBarBg, RoundedCornerShape(t.spacing.sm)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(p.topBarBg, RoundedCornerShape(t.spacing.sm)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource("/icons/attach-file.svg"),
                    contentDescription = null,
                    tint = p.textMuted,
                    modifier = Modifier.size(t.controls.iconLg),
                )
            }
        }
        Text(
            text = attachment.displayName,
            color = p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material.MaterialTheme.typography.body2,
        )
    }
}

internal fun isUserMessageNode(node: TimelineNode): Boolean {
    return node is TimelineNode.MessageNode && node.role == MessageRole.USER
}

@Composable
private fun rememberTimelineAttachmentBitmap(path: String): ImageBitmap? {
    return remember(path) {
        runCatching {
            FileInputStream(path).use { loadImageBitmap(it) }
        }.getOrNull()
    }
}
