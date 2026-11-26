package com.auracode.assistant.toolwindow.popup

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Compose 使用物理像素，AWT 使用屏幕逻辑坐标；两次转换分别采用所属窗口的缩放比例。 */
internal fun popupScreenAnchor(bounds: Rect, panelOrigin: Point, scale: Float): Rectangle = Rectangle(
    panelOrigin.x + (bounds.left / scale).roundToInt(),
    panelOrigin.y + (bounds.top / scale).roundToInt(),
    (bounds.width / scale).roundToInt(),
    (bounds.height / scale).roundToInt(),
)

internal fun popupScreenBounds(
    anchor: Rectangle,
    workArea: Rectangle,
    contentSize: IntSize,
    scale: Float,
    layoutDirection: LayoutDirection,
    provider: PopupPositionProvider,
    providerScale: Float = scale,
): Rectangle {
    val width = ceil(contentSize.width / scale).toInt().coerceIn(1, workArea.width)
    val height = ceil(contentSize.height / scale).toInt().coerceIn(1, workArea.height)
    val position = provider.calculatePosition(
        anchorBounds = IntRect(
            ((anchor.x - workArea.x) * providerScale).roundToInt(),
            ((anchor.y - workArea.y) * providerScale).roundToInt(),
            ((anchor.x + anchor.width - workArea.x) * providerScale).roundToInt(),
            ((anchor.y + anchor.height - workArea.y) * providerScale).roundToInt(),
        ),
        windowSize = IntSize((workArea.width * providerScale).roundToInt(), (workArea.height * providerScale).roundToInt()),
        layoutDirection = layoutDirection,
        popupContentSize = IntSize(
            (contentSize.width / scale * providerScale).roundToInt(),
            (contentSize.height / scale * providerScale).roundToInt(),
        ),
    )
    return Rectangle(
        (workArea.x + (position.x / providerScale).roundToInt()).coerceIn(workArea.x, workArea.x + workArea.width - width),
        (workArea.y + (position.y / providerScale).roundToInt()).coerceIn(workArea.y, workArea.y + workArea.height - height),
        width,
        height,
    )
}

/** 给阴影预留透明边距，定位仍以可见内容边缘为准。 */
internal class PaddedPopupPositionProvider(
    private val delegate: PopupPositionProvider,
    private val inset: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = delegate.calculatePosition(
        anchorBounds,
        windowSize,
        layoutDirection,
        IntSize((popupContentSize.width - inset * 2).coerceAtLeast(0), (popupContentSize.height - inset * 2).coerceAtLeast(0)),
    ) - IntOffset(inset, inset)
}

/** 优先沿锚点下沿展开，空间不足时翻到上方；最终屏幕避让由宿主统一执行。 */
internal class DropdownPopupPositionProvider(private val offset: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left + offset.x
        } else {
            anchorBounds.right - popupContentSize.width - offset.x
        }
        val below = anchorBounds.bottom + offset.y
        val above = anchorBounds.top - offset.y - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> (windowSize.height - popupContentSize.height) / 2
        }
        return IntOffset(x, y)
    }
}
