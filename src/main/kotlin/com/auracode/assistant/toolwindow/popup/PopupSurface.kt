package com.auracode.assistant.toolwindow.popup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 菜单的统一外壳：阴影与描边分开绘制，避免深色主题的 elevation overlay 再次提亮底色。 */
@Composable
internal fun PopupSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.surface,
    borderColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.14f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.shadow(
            elevation = 4.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.16f),
        ),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
        elevation = 0.dp,
        content = content,
    )
}
