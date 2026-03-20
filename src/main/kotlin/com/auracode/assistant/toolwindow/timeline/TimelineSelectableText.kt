package com.auracode.assistant.toolwindow.timeline

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocalization

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineTextInteractionHost(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalTextContextMenu provides rememberTimelineCopyOnlyTextContextMenu(),
    ) {
        content()
    }
}

@Composable
internal fun TimelineSelectableText(
    content: @Composable () -> Unit,
) {
    SelectionContainer {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberTimelineCopyOnlyTextContextMenu(): TextContextMenu {
    val copyLabel = LocalLocalization.current.copy
    return remember(copyLabel) {
        object : TextContextMenu {
            @Composable
            override fun Area(
                textManager: TextContextMenu.TextManager,
                state: ContextMenuState,
                content: @Composable () -> Unit,
            ) {
                val items = timelineTextContextMenuItems(
                    copyLabel = copyLabel,
                    onCopy = textManager.copy,
                )
                ContextMenuArea(
                    items = { items },
                    state = state,
                    enabled = items.isNotEmpty(),
                    content = content,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun timelineTextContextMenuItems(
    copyLabel: String,
    onCopy: (() -> Unit)?,
): List<ContextMenuItem> {
    return listOfNotNull(
        onCopy?.let { copyAction ->
            ContextMenuItem(copyLabel, copyAction)
        },
    )
}
