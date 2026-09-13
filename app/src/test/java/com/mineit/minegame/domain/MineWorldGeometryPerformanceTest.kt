package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MineWorldGeometryPerformanceTest {
    @Test
    fun materialClassificationSkipsDistantOreBodiesBeforePerSampleFieldEvaluation() {
        val near = OreBody(
            id = "near",
            type = OreType.COPPER,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 8f), 5f),
                OreBodyNode(MinePoint3D(0f, 0f, 20f), 5f),
            ),
        )
        val far = OreBody(
            id = "far",
            type = OreType.GOLD,
            nodes = listOf(
                OreBodyNode(MinePoint3D(80f, 80f, 8f), 5f),
                OreBodyNode(MinePoint3D(80f, 80f, 20f), 5f),
            ),
        )
        var diagnostics: ExcavationClassificationDiagnostics? = null

        MineWorldGeometry.classifyNewExcavation(
            start = MinePoint3D(0f, 0f, 10f),
            end = MinePoint3D(0f, 0f, 10.3f),
            tunnelRadiusMetres = 3.2f,
            existingTunnelSegments = emptyList(),
            oreBodies = listOf(near, far),
            onDiagnostics = { diagnostics = it },
        )

        val measured = requireNotNull(diagnostics)
        assertEquals(1, measured.nearbyOreBodies)
        assertTrue(measured.candidateSamples > 0)
        assertTrue(measured.oreFieldEvaluations <= measured.candidateSamples)
    }

    @Test
    fun historicalTunnelBoundsCullExactCylinderChecks() {
        val historical = buildList {
            var x = 0f
            repeat(100) {
                val next = x + 0.1f
                add(TunnelSegment(MinePoint3D(x, 0f, 12f), MinePoint3D(next, 0f, 12f)))
                x = next
            }
        }
        var diagnostics: ExcavationClassificationDiagnostics? = null

        MineWorldGeometry.classifyNewExcavation(
            start = MinePoint3D(10f, 0f, 12f),
            end = MinePoint3D(10.1f, 0f, 12f),
            tunnelRadiusMetres = 3.2f,
            existingTunnelSegments = historical,
            oreBodies = emptyList(),
            onDiagnostics = { diagnostics = it },
        )

        val measured = requireNotNull(diagnostics)
        assertTrue(measured.nearbyExistingSegments > 0)
        assertTrue(measured.tunnelBoundsChecks > 0)
        assertTrue(
            "expanded AABB broad phase should reject candidates before finite-cylinder math",
            measured.existingTunnelChecks < measured.tunnelBoundsChecks,
        )
    }
}
