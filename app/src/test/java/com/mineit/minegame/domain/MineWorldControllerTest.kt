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
    fun stoppedSteeringPreviewsDirectionAndStartCommitsIt() {
        val preview = MineWorldController.setSteering(MineWorldState(), 0.5f)

        assertEquals(70f, preview.machineHeadingDegrees, 0.001f)

        val started = MineWorldController.startDigging(preview)
        assertEquals(70f, started.headingDegrees, 0.001f)
        assertEquals(70f, started.machineHeadingDegrees, 0.001f)
        assertEquals(0f, started.steering, 0.001f)
    }

    @Test
    fun fixedHorizontalTurnsUseVisibleMachineDirectionAndRecentreSteering() {
        val preview = MineWorldController.setSteering(MineWorldState(), 0.5f)
        val left = MineWorldController.turnHeadingBy(preview, -90f)
        val right = MineWorldController.turnHeadingBy(left, 45f)

        assertEquals(340f, left.headingDegrees, 0.001f)
        assertEquals(0f, left.steering, 0.001f)
        assertEquals(25f, right.headingDegrees, 0.001f)
        assertEquals(25f, right.machineHeadingDegrees, 0.001f)
    }

    @Test
    fun steeringCurvesHeadingWhileMachineMoves() {
        var state = MineWorldController.startDigging(MineWorldState())
        state = MineWorldController.setSteering(state, 1f)
        val startHeading = state.headingDegrees

        repeat(4) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.headingDegrees > startHeading)
        assertTrue(state.tunnel.end.x > 0f)
        assertTrue(state.tunnel.end.y > 0f)
    }

    @Test
    fun absoluteNinetyDownCreatesVerticalShaftThenLevelCreatesHorizontalDrive() {
        var state = MineWorldController.setVerticalAngle(MineWorldState(), 90f)
        state = MineWorldController.startDigging(state)
        repeat(8) {
            state = MineWorldController.tick(state, 0.25f)
        }

        val shaftBottom = state.tunnel.end
        assertTrue(shaftBottom.z > 0f)
        assertEquals(0f, shaftBottom.x, 0.01f)
        assertEquals(0f, shaftBottom.y, 0.01f)

        state = MineWorldController.setVerticalAngle(state, 0f)
        repeat(4) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertEquals(shaftBottom.z, state.tunnel.end.z, 0.02f)
        assertTrue(MineWorldGeometry.distance(shaftBottom, state.tunnel.end) > 1f)
    }

    @Test
    fun verticalAngleSupportsFullAbsoluteRange() {
        val down = MineWorldController.setVerticalAngle(MineWorldState(), 90f)
        val up = MineWorldController.setVerticalAngle(MineWorldState(), -90f)

        assertEquals(90f, down.verticalAngleDegrees, 0.001f)
        assertEquals(-90f, up.verticalAngleDegrees, 0.001f)
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
            if (state.isDigging) {
                state = MineWorldController.tick(state, 0.25f)
            }
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
    fun incrementalOreExposureMatchesFullTunnelScan() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }

        val fullScan = MineWorldGeometry.exposedOreSegments(state.tunnel, state.oreBody)
        assertEquals(fullScan, state.exposedOreSegments)
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
