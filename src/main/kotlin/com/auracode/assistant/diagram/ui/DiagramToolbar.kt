package com.auracode.assistant.diagram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.shared.DesignPalette
import com.auracode.assistant.toolwindow.shared.HoverTooltip
import com.auracode.assistant.toolwindow.shared.assistantPainterResource
import kotlin.math.roundToInt

/** Compact, shared controls: zoom out, scale, zoom in, fullscreen toggle. */
@Composable
internal fun DiagramToolbar(
    state: DiagramViewState,
    palette: DesignPalette,
    fullscreen: Boolean,
    onToggleFullscreen: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DiagramIconButton("zoom-out", "diagram.zoomOut", palette, enabled = state.zoom > 1f) {
            state.zoomBy(1f / 1.25f)
        }
        Box(Modifier.width(44.dp).height(28.dp), contentAlignment = Alignment.Center) {
            // Body typography has a taller line box; keep the scale label centered with the icons.
            Text(
                text = "${(state.zoom * 100).roundToInt()}%",
                color = palette.textMuted,
                // Digits have no descenders; lift their visible center to match the 18dp icons.
                modifier = Modifier.offset(y = (-1).dp),
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                maxLines = 1,
            )
        }
        DiagramIconButton("zoom-in", "diagram.zoomIn", palette, enabled = state.zoom < 8f) {
            state.zoomBy(1.25f)
        }
        if (onToggleFullscreen != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(14.dp).background(palette.markdownDivider.copy(alpha = 0.65f)))
            Spacer(Modifier.width(8.dp))
            DiagramIconButton(
                icon = if (fullscreen) "fullscreen-exit" else "fullscreen",
                labelKey = if (fullscreen) "diagram.exitFullscreen" else "diagram.fullscreen",
                palette = palette,
                onClick = onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun DiagramIconButton(
    icon: String,
    labelKey: String,
    palette: DesignPalette,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val label = AuraCodeBundle.message(labelKey)
    HoverTooltip(label) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(5.dp))
                .background(if (hovered && enabled) palette.textPrimary.copy(alpha = 0.08f) else Color.Transparent)
                .hoverable(interactions, enabled)
                .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = assistantPainterResource("/icons/diagram/$icon.svg"),
                contentDescription = label,
                tint = when {
                    !enabled -> palette.textMuted.copy(alpha = 0.35f)
                    hovered -> palette.textPrimary
                    else -> palette.textSecondary
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
