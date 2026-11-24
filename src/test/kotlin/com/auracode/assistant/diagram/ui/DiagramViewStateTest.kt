package com.auracode.assistant.diagram.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramViewStateTest {
    @Test
    fun `zoom remains bounded through repeated operations and ignores non-finite input`() {
        val state = DiagramViewState()
        repeat(50) { state.zoomBy(1.25f) }
        assertEquals(8f, state.zoom)
        state.zoomBy(Float.NaN)
        state.zoomBy(Float.POSITIVE_INFINITY)
        assertEquals(8f, state.zoom)
        repeat(50) { state.zoomBy(0.8f) }
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.panFraction)
    }

    @Test
    fun `panning is limited to actual image overflow`() {
        val state = DiagramViewState()
        state.zoomBy(2f)
        val geometry = diagramViewGeometry(Size(200f, 100f), Size(300f, 150f), state.zoom)
        assertEquals(Offset(50f, 25f), geometry.panLimits)
        state.panBy(Offset(1000f, -1000f), geometry)
        assertEquals(Offset(1f, -1f), state.panFraction)
        state.panBy(Offset(Float.NaN, 0f), geometry)
        assertEquals(Offset(1f, -1f), state.panFraction)
    }

    @Test
    fun `resizing preserves relative position and cannot create negative pan limits`() {
        val state = DiagramViewState()
        state.zoomBy(2f)
        val small = diagramViewGeometry(Size(800f, 600f), Size(400f, 300f), state.zoom)
        state.panBy(Offset(100f, 75f), small)
        assertEquals(Offset(0.5f, 0.5f), state.panFraction)
        val large = diagramViewGeometry(Size(800f, 600f), Size(1200f, 900f), state.zoom)
        assertTrue(large.panLimits.x >= 0 && large.panLimits.y >= 0)
        assertEquals(Offset(0.5f, 0.5f), state.panFraction)
        assertEquals(Offset.Zero, diagramViewGeometry(Size(800f, 600f), Size(2000f, 1600f), state.zoom).panLimits)
    }

    @Test
    fun `zero and invalid viewport sizes produce empty geometry`() {
        for (viewport in listOf(Size.Zero, Size(Float.POSITIVE_INFINITY, 100f), Size(100f, Float.NaN))) {
            assertEquals(DiagramViewGeometry(Size.Zero, Offset.Zero), diagramViewGeometry(Size(200f, 100f), viewport, 2f))
        }
    }
}
