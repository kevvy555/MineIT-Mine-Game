package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MineWorldControllerTest {
    @Test
    fun diggingAddsAThreeDimensionalTunnelSegmentAndWasteRock() {
        val initial = MineWorldState()
        val dug = MineWorldController.dig(initial)

        assertEquals(initial.tunnel.points.size + 1, dug.tunnel.points.size)
        assertTrue(dug.excavatedVolumeCubicMetres > initial.excavatedVolumeCubicMetres)
        assertTrue(dug.wasteRockTonnes > initial.wasteRockTonnes)
    }

    @Test
    fun azimuthAndDipControlAllThreeAxes() {
        val initial = MineWorldController.setDip(
            MineWorldController.setAzimuth(MineWorldState(), 90f),
            30f,
        )
        val start = initial.tunnel.end
        val dug = MineWorldController.dig(initial)
        val end = dug.tunnel.end

        assertTrue(kotlin.math.abs(end.x - start.x) < 0.05f)
        assertTrue(end.y > start.y)
        assertTrue(end.z > start.z)
    }

    @Test
    fun worldExtentExpandsWhenExcavationApproachesAnEdge() {
        var state = MineWorldController.setDip(
            MineWorldController.setAzimuth(MineWorldState(), 0f),
            0f,
        )
        val initialMaxX = state.extent.maxChunkX

        repeat(3) {
            state = MineWorldController.dig(state)
        }

        assertTrue(state.extent.maxChunkX > initialMaxX)
        assertTrue(state.bounds.maxX > 24f)
    }

    @Test
    fun upwardDiggingCannotMoveTunnelCentreAboveTheSurface() {
        var state = MineWorldController.setDip(MineWorldState(), -70f)

        repeat(8) {
            state = MineWorldController.dig(state)
        }

        val minimumAllowedDepth = state.tunnel.radiusMetres * 0.65f
        assertTrue(state.tunnel.points.all { it.z >= 0f })
        assertTrue(state.tunnel.end.z >= minimumAllowedDepth - 0.01f)
    }

    @Test
    fun initialShaftDiscoversOnlyOreItActuallyExposes() {
        val state = MineWorldState()

        assertTrue(state.exposedOreSegments.isNotEmpty())

        val remoteTunnel = TunnelGeometry(
            points = listOf(
                MinePoint3D(-20f, -20f, 0f),
                MinePoint3D(-20f, -20f, 20f),
            ),
            radiusMetres = 2f,
        )
        val exposures = MineWorldGeometry.exposedOreSegments(remoteTunnel, state.oreBody)

        assertTrue(exposures.isEmpty())
    }

    @Test
    fun solidFieldTreatsTunnelAsAirInsideRockVolume() {
        val state = MineWorldState()
        val insideRock = MinePoint3D(12f, -12f, 20f)
        val insideShaft = MinePoint3D(0f, 0f, 12f)

        assertTrue(MineWorldGeometry.solidMargin(insideRock, state.bounds, state.tunnel) > 0f)
        assertTrue(MineWorldGeometry.solidMargin(insideShaft, state.bounds, state.tunnel) < 0f)
    }
}
