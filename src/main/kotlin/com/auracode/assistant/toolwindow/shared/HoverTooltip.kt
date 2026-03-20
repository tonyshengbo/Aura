package com.auracode.assistant.toolwindow.shared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HoverTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    if (text.isBlank()) {
        content()
        return
    }

    TooltipArea(
        delayMillis = 500,
        tooltip = {
            Surface(
                shape = MaterialTheme.shapes.small,
                elevation = 4.dp,
                color = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        },
    ) {
        content()
    }
}
