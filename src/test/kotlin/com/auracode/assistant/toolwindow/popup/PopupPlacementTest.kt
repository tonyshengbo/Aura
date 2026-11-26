package com.auracode.assistant.toolwindow.popup

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class PopupPlacementTest {
    @Test
    fun `retina anchor converts pixels to logical coordinates on a left hand display`() {
        assertEquals(
            Rectangle(-1420, 90, 120, 24),
            popupScreenAnchor(Rect(40f, 60f, 280f, 108f), Point(-1440, 60), 2f),
        )
    }

    @Test
    fun `menu flips above anchor near screen bottom`() {
        assertEquals(Rectangle(180, 560, 200, 160), place(Rectangle(180, 720, 80, 30)))
    }

    @Test
    fun `retina popup uses target window scale independently of anchor scale`() {
        assertEquals(
            Rectangle(1900, 170, 200, 160),
            popupScreenBounds(
                anchor = Rectangle(1900, 140, 80, 30),
                workArea = Rectangle(1440, 0, 1440, 900),
                contentSize = IntSize(400, 320),
                scale = 2f,
                layoutDirection = LayoutDirection.Ltr,
                provider = DropdownPopupPositionProvider(IntOffset.Zero),
            ),
        )
    }

    @Test
    fun `source scaled offsets survive a different target display scale`() {
        assertEquals(
            Rectangle(1905, 177, 200, 160),
            popupScreenBounds(
                Rectangle(1900, 140, 80, 30), Rectangle(1440, 0, 1440, 900), IntSize(400, 320), 2f,
                LayoutDirection.Ltr, DropdownPopupPositionProvider(IntOffset(5, 7)), providerScale = 1f,
            ),
        )
    }

    @Test
    fun `transparent shadow padding preserves visible content alignment`() {
        assertEquals(
            Rectangle(172, 162, 216, 176),
            popupScreenBounds(
                Rectangle(180, 140, 80, 30), Rectangle(0, 0, 1024, 768), IntSize(216, 176), 1f,
                LayoutDirection.Ltr, PaddedPopupPositionProvider(DropdownPopupPositionProvider(IntOffset.Zero), 8),
            ),
        )
    }

    @Test
    fun `rtl menu aligns trailing edge and clamps within usable screen`() {
        assertEquals(Rectangle(80, 170, 200, 160), place(Rectangle(200, 140, 80, 30), LayoutDirection.Rtl))
        assertEquals(Rectangle(824, 170, 200, 160), place(Rectangle(990, 140, 30, 30)))
    }

    @Test
    fun `context menu provider respects negative screen origin and task bar insets`() {
        val bounds = popupScreenBounds(
            Rectangle(-1100, 760, 0, 0), Rectangle(-1280, 40, 1280, 760), IntSize(200, 160), 1f,
            LayoutDirection.Ltr, object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ) = IntOffset(anchorBounds.left, anchorBounds.bottom)
            },
        )
        assertEquals(Rectangle(-1100, 640, 200, 160), bounds)
    }

    private fun place(anchor: Rectangle, direction: LayoutDirection = LayoutDirection.Ltr): Rectangle =
        popupScreenBounds(
            anchor, Rectangle(0, 0, 1024, 768), IntSize(200, 160), 1f, direction,
            DropdownPopupPositionProvider(IntOffset.Zero),
        )
}
