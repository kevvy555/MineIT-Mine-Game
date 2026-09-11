package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineWorldControllerTest {
    @Test
    fun mineStartsAtSurfaceWithSolidRockAndHiddenOre() {
        val state = MineWorldState()

        assertEquals(1, state.tunnel.points.size)
        assertEquals(0f, state.excavatedVolumeCubicMetres, 0.001f)
        assertFalse(state.oreBodyDiscovered)
        assertTrue(MineWorldGeometry.solidMargin(MinePoint3D(0f, 0f, 4f), state.bounds, state.tunnel) > 0f)
    }

    @Test
    fun startAndTickCreateThreeDimensionalExcavationAndWaste() {
        val started = MineWorldController.startDigging(MineWorldState())
        val dug = MineWorldController.tick(started, 0.25f)

        assertTrue(started.isDigging)
        assertEquals(2, dug.tunnel.points.size)
        assertTrue(dug.tunnel.end.z > 0f)
        assertTrue(dug.excavatedVolumeCubicMetres > 0f)
        assertTrue(dug.wasteRockTonnes > 0f)
    }

    @Test
    fun steeringCurvesHeadingWhileMachineMoves() {
        var state = MineWorldController.setSteering(MineWorldState(), 1f)
        state = MineWorldController.startDigging(state)
        val startHeading = state.headingDegrees

        repeat(4) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.headingDegrees > startHeading)
        assertTrue(state.tunnel.end.x > 0f)
        assertTrue(state.tunnel.end.y > 0f)
    }

    @Test
    fun verticalAngleCanDriveDownAndBackTowardSurface() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(12) {
            state = MineWorldController.tick(state, 0.25f)
        }
        val deepPoint = state.tunnel.end

        state = MineWorldController.setVerticalAngle(state, -60f)
        repeat(30) {
            state = MineWorldController.tick(state, 0.25f)
            if (!state.isDigging) return@repeat
        }

        assertTrue(deepPoint.z > 0f)
        assertTrue(state.tunnel.end.z >= 0f)
        assertFalse(state.isDigging)
    }

    @Test
    fun surfaceMachineCannotStartLevelOrUpward() {
        val level = MineWorldController.setVerticalAngle(MineWorldState(), 0f)
        val upward = MineWorldController.setVerticalAngle(MineWorldState(), -20f)

        assertFalse(MineWorldController.startDigging(level).isDigging)
        assertFalse(MineWorldController.startDigging(upward).isDigging)
    }

    @Test
    fun worldExtentExpandsWhenContinuousExcavationApproachesEdge() {
        var state = MineWorldController.setVerticalAngle(MineWorldState(), 10f)
        state = MineWorldController.startDigging(state)
        val initialMaxX = state.extent.maxChunkX

        repeat(35) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.extent.maxChunkX > initialMaxX)
    }

    @Test
    fun touchingOreDiscoversTheConnectedBody() {
        var state = MineWorldController.startDigging(MineWorldState())

        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.exposedOreSegments.isNotEmpty())
        assertTrue(state.oreBodyDiscovered)
    }

    @Test
    fun discoveredOreRemainsKnownAfterFurtherDigging() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }
        assertTrue(state.oreBodyDiscovered)

        state = MineWorldController.setSteering(state, -1f)
        repeat(12) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.oreBodyDiscovered)
    }

    @Test
    fun excavatedTunnelBecomesAirInsideRockVolume() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(6) {
            state = MineWorldController.tick(state, 0.25f)
        }
        val tunnelPoint = MineWorldGeometry.interpolate(
            state.tunnel.points[1],
            state.tunnel.points[2],
            0.5f,
        )

        assertTrue(MineWorldGeometry.solidMargin(tunnelPoint, state.bounds, state.tunnel) < 0f)
        assertTrue(MineWorldGeometry.solidMargin(MinePoint3D(-12f, -12f, 12f), state.bounds, state.tunnel) > 0f)
    }
}
