package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun tunnelOverviewOnlyRendersWhenRockIsOffAndTunnelIsEnabled() {
        assertFalse(TunnelRenderPlanner.shouldRenderOverview(rockVisible = true, tunnelVisible = true))
        assertFalse(TunnelRenderPlanner.shouldRenderOverview(rockVisible = true, tunnelVisible = false))
        assertFalse(TunnelRenderPlanner.shouldRenderOverview(rockVisible = false, tunnelVisible = false))
        assertTrue(TunnelRenderPlanner.shouldRenderOverview(rockVisible = false, tunnelVisible = true))
    }

    @Test
    fun sliceConfigurationKeepsIndependentAxisPositionsWhenSwitchingCuts() {
        val initial = SliceConfiguration(
            fractions = SliceFractions(x = 0.20f, y = 0.40f, z = 0.60f),
        )

        val withY = initial.toggleAxis(ClipAxis.Y).withFraction(ClipAxis.Y, 0.72f)
        val withZ = withY.toggleAxis(ClipAxis.Z).withFraction(ClipAxis.Z, 0.31f)

        assertEquals(0.20f, withZ.fraction(ClipAxis.X), 0.001f)
        assertEquals(0.72f, withZ.fraction(ClipAxis.Y), 0.001f)
        assertEquals(0.31f, withZ.fraction(ClipAxis.Z), 0.001f)
        assertTrue(withZ.isEnabled(ClipAxis.X))
        assertTrue(withZ.isEnabled(ClipAxis.Y))
        assertTrue(withZ.isEnabled(ClipAxis.Z))
    }

    @Test
    fun sliceConfigurationTracksCutSidePerAxisIndependently() {
        val configuration = SliceConfiguration()
            .toggleFlipped(ClipAxis.Y)
            .toggleFlipped(ClipAxis.Z)

        assertFalse(configuration.isFlipped(ClipAxis.X))
        assertTrue(configuration.isFlipped(ClipAxis.Y))
        assertTrue(configuration.isFlipped(ClipAxis.Z))
    }
}
