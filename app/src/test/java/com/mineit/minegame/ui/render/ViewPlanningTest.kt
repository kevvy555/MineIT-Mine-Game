package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewPlanningTest {
    @Test
    fun followFractionsTrackDiggerAcrossAllThreeAxes() {
        val state = MineWorldState()
        val point = MinePoint3D(
            x = state.bounds.minX + (state.bounds.width * 0.75f),
            y = state.bounds.minY + (state.bounds.height * 0.25f),
            z = state.bounds.minZ + (state.bounds.depth * 0.50f),
        )

        val fractions = FollowSlicePlanner.forPoint(state, point)

        assertEquals(0.75f, fractions.x, 0.001f)
        assertEquals(0.25f, fractions.y, 0.001f)
        assertEquals(0.50f, fractions.z, 0.001f)
    }

    @Test
    fun followFractionsStayInsideUsableSliderRange() {
        val state = MineWorldState()

        val low = FollowSlicePlanner.forPoint(state, MinePoint3D(-10_000f, -10_000f, -10_000f))
        val high = FollowSlicePlanner.forPoint(state, MinePoint3D(10_000f, 10_000f, 10_000f))

        listOf(low.x, low.y, low.z).forEach { assertTrue(it >= 0.02f) }
        listOf(high.x, high.y, high.z).forEach { assertTrue(it <= 0.98f) }
    }
}
