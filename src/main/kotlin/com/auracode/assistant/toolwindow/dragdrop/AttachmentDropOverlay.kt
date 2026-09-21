package com.auracode.assistant.toolwindow.dragdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracode.assistant.i18n.AuraCodeBundle
import com.auracode.assistant.toolwindow.shared.DesignPalette

private val OVERLAY_INSET = 8.dp
private val OVERLAY_RADIUS = 12.dp
private val OVERLAY_STROKE = 1.5.dp
private val HINT_RADIUS = 8.dp

/**
 * 拖拽悬停时的整窗提示层：轻微压暗背景 + 虚线边框 + 居中文案。
 */
@Composable
internal fun AttachmentDropOverlay(
    palette: DesignPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.appBg.copy(alpha = 0.35f))
            .drawBehind {
                val inset = OVERLAY_INSET.toPx()
                val stroke = OVERLAY_STROKE.toPx()
                drawRoundRect(
                    color = palette.accent,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(OVERLAY_RADIUS.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 6f, stroke * 4f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = AuraCodeBundle.message("composer.dropAttachment"),
            color = palette.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier
                .background(color = palette.submissionBg, shape = RoundedCornerShape(HINT_RADIUS))
                .border(
                    width = 1.dp,
                    color = palette.accent.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(HINT_RADIUS),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
