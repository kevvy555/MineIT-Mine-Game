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
        assertEquals(0f, state.wasteRockVolumeCubicMetres, 0.001f)
        assertEquals(0f, state.totalMinedOreVolumeCubicMetres, 0.001f)
        assertFalse(state.oreBodyDiscovered)
        assertTrue(state.discoveredOreBodyIds.isEmpty())
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
        assertEquals(
            dug.excavatedVolumeCubicMetres,
            dug.wasteRockVolumeCubicMetres + dug.totalMinedOreVolumeCubicMetres,
            0.001f,
        )
    }

    @Test
    fun excavationPartitionsOreAndWasteWithoutDoubleCountingMaterial() {
        val ore = OreBody(
            id = "test-gold",
            type = OreType.GOLD,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 2f), 8f),
                OreBodyNode(MinePoint3D(0f, 0f, 20f), 8f),
            ),
        )
        var state = MineWorldState(
            oreBodies = listOf(ore),
            verticalAngleDegrees = 90f,
        )
        state = MineWorldController.startDigging(state)
        repeat(8) {
            state = MineWorldController.tick(state, 0.25f)
        }

        val minedGold = state.minedOreVolumeCubicMetres(OreType.GOLD)
        assertTrue(minedGold > 0f)
        assertEquals(
            state.excavatedVolumeCubicMetres,
            state.wasteRockVolumeCubicMetres + minedGold,
            0.001f,
        )
        assertEquals(
            state.wasteRockVolumeCubicMetres *
                MineWorldController.ROCK_DENSITY_TONNES_PER_CUBIC_METRE,
            state.wasteRockTonnes,
            0.001f,
        )
        assertTrue(
            "ore must not also be counted as waste",
            state.wasteRockVolumeCubicMetres < state.excavatedVolumeCubicMetres,
        )
    }

    @Test
    fun crossingAnExistingWorkingDoesNotCreateMaterialTwice() {
        val segment = TunnelSegment(
            start = MinePoint3D(0f, 0f, 10f),
            end = MinePoint3D(0f, 0f, 12f),
        )

        val firstPass = MineWorldGeometry.classifyNewExcavation(
            start = segment.start,
            end = segment.end,
            tunnelRadiusMetres = 3.2f,
            existingTunnelSegments = emptyList(),
            oreBodies = emptyList(),
        )
        val repeatedPass = MineWorldGeometry.classifyNewExcavation(
            start = segment.start,
            end = segment.end,
            tunnelRadiusMetres = 3.2f,
            existingTunnelSegments = listOf(segment),
            oreBodies = emptyList(),
        )

        assertTrue(firstPass.newExcavatedVolumeCubicMetres > 0f)
        assertEquals(0f, repeatedPass.newExcavatedVolumeCubicMetres, 0.001f)
        assertEquals(0f, repeatedPass.wasteRockVolumeCubicMetres, 0.001f)
        assertEquals(0f, repeatedPass.oreVolumeCubicMetres, 0.001f)
    }

    @Test
    fun excavationLeavesOriginalDepositImmutableAndCreatesTrueRemainingMaterialHole() {
        val ore = OreBody(
            id = "test-copper",
            type = OreType.COPPER,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 2f), 6f),
                OreBodyNode(MinePoint3D(0f, 0f, 14f), 6f),
            ),
        )
        var state = MineWorldState(oreBodies = listOf(ore), verticalAngleDegrees = 90f)
        state = MineWorldController.startDigging(state)
        repeat(12) { state = MineWorldController.tick(state, 0.25f) }

        val cutCentre = MinePoint3D(0f, 0f, 6f)
        val untouchedSide = MinePoint3D(5f, 0f, 6f)
        assertTrue(state.minedOreVolumeCubicMetres(OreType.COPPER) > 0f)
        assertEquals("original deposit definition must not be mutated", ore, state.oreBodies.single())
        assertTrue(MineWorldGeometry.oreMargin(cutCentre, ore.nodes) > 0f)
        assertTrue(MineWorldGeometry.remainingOreMargin(cutCentre, ore, state.tunnel) < 0f)
        assertTrue(MineWorldGeometry.remainingOreMargin(untouchedSide, ore, state.tunnel) > 0f)
    }

    @Test
    fun remainingOreFieldUsesTheActualTunnelRadiusInsteadOfEquivalentRadiusShrink() {
        val ore = OreBody(
            id = "test-gold",
            type = OreType.GOLD,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 2f), 7f),
                OreBodyNode(MinePoint3D(0f, 0f, 14f), 7f),
            ),
        )
        val tunnel = TunnelGeometry(
            points = listOf(MinePoint3D(0f, 0f, 1f), MinePoint3D(0f, 0f, 15f)),
            radiusMetres = 3.2f,
        )

        assertTrue(MineWorldGeometry.remainingOreMargin(MinePoint3D(0f, 0f, 8f), ore, tunnel) < 0f)
        assertTrue(MineWorldGeometry.remainingOreMargin(MinePoint3D(3.1f, 0f, 8f), ore, tunnel) < 0f)
        assertTrue(MineWorldGeometry.remainingOreMargin(MinePoint3D(3.4f, 0f, 8f), ore, tunnel) > 0f)
        assertTrue(MineWorldGeometry.remainingOreMargin(MinePoint3D(6.5f, 0f, 8f), ore, tunnel) > 0f)
    }

    @Test
    fun miningSpeedMultiplierChangesTravelDistanceAndIsClamped() {
        val origin = MineWorldState().tunnel.end
        val normal = MineWorldController.tick(
            MineWorldController.startDigging(MineWorldState()),
            0.25f,
        )
        val fastState = MineWorldController.setDigSpeedMultiplier(MineWorldState(), 2f)
        val fast = MineWorldController.tick(
            MineWorldController.startDigging(fastState),
            0.25f,
        )

        val normalTravel = MineWorldGeometry.distance(origin, normal.tunnel.end)
        val fastTravel = MineWorldGeometry.distance(origin, fast.tunnel.end)

        assertEquals(normalTravel * 2f, fastTravel, 0.01f)
        assertEquals(
            MineWorldController.MIN_DIG_SPEED_MULTIPLIER,
            MineWorldController.setDigSpeedMultiplier(MineWorldState(), -5f).digSpeedMultiplier,
            0.001f,
        )
        assertEquals(
            MineWorldController.MAX_DIG_SPEED_MULTIPLIER,
            MineWorldController.setDigSpeedMultiplier(MineWorldState(), 9f).digSpeedMultiplier,
            0.001f,
        )
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
    fun touchingOreDiscoversTheConnectedTypedBody() {
        var state = MineWorldController.startDigging(MineWorldState())

        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.oreBodyDiscovered)
        assertTrue("gold-1" in state.discoveredOreBodyIds)
        assertTrue(state.discoveredOreBodies.any { it.type == OreType.GOLD })
        assertTrue(state.minedOreVolumeCubicMetres(OreType.GOLD) > 0f)
    }

    @Test
    fun discoveredOreRemainsKnownAfterFurtherDigging() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }
        val discovered = state.discoveredOreBodyIds
        assertTrue(discovered.isNotEmpty())

        state = MineWorldController.setSteering(state, -1f)
        repeat(12) {
            state = MineWorldController.tick(state, 0.25f)
        }

        assertTrue(state.discoveredOreBodyIds.containsAll(discovered))
    }

    @Test
    fun incrementalTypedDiscoveryMatchesOriginalGeologyTunnelScan() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(45) {
            state = MineWorldController.tick(state, 0.25f)
        }

        val fullScan = MineWorldContent.oreBodies
            .filter { MineWorldGeometry.exposedOreSegments(state.tunnel, it.nodes).isNotEmpty() }
            .map { it.id }
            .toSet()
        assertEquals(fullScan, state.discoveredOreBodyIds)
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
