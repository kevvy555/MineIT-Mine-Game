package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinePrismControllerTest {
    @Test
    fun cutDepthIsClampedToMineBounds() {
        val state = MinePrismState()

        assertEquals(
            MinePrismController.MIN_DEPTH_METRES,
            MinePrismController.setCutDepth(state, -50f).cutDepthMetres,
            0.01f,
        )
        assertEquals(
            MinePrismController.MAX_DEPTH_METRES,
            MinePrismController.setCutDepth(state, 999f).cutDepthMetres,
            0.01f,
        )
    }

    @Test
    fun explodeModeTogglesWithoutChangingMineGeometry() {
        val state = MinePrismState()
        val toggled = MinePrismController.toggleExploded(state)

        assertTrue(toggled.exploded)
        assertEquals(state.levels, toggled.levels)
        assertEquals(state.oreBody, toggled.oreBody)
        assertFalse(MinePrismController.toggleExploded(toggled).exploded)
    }

    @Test
    fun revealedLevelCanBeSelectedAndClosed() {
        val state = MinePrismState(cutDepthMetres = 200f)

        val selected = MinePrismController.selectLevel(state, "level-180")

        assertEquals("level-180", selected.selectedLevelId)
        assertNull(MinePrismController.closeLevel(selected).selectedLevelId)
    }

    @Test
    fun hiddenLevelCannotBeSelected() {
        val state = MinePrismState(cutDepthMetres = 100f)

        val selected = MinePrismController.selectLevel(state, "level-285")

        assertNull(selected.selectedLevelId)
    }

    @Test
    fun reducingCutDepthClosesLevelThatIsNoLongerRevealed() {
        val selected = MinePrismController.selectLevel(
            MinePrismState(cutDepthMetres = 300f),
            "level-285",
        )

        val shallower = MinePrismController.setCutDepth(selected, 120f)

        assertNull(shallower.selectedLevelId)
        assertEquals(120f, shallower.cutDepthMetres, 0.01f)
    }
}
