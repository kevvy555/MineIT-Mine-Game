package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreBodyNode
import com.mineit.minegame.domain.OreType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class OreMeshBuilderTest {
    @Test
    fun seeOreBuildsOnlySolidCrossSectionsOnRequestedCtPlane() {
        val state = MineWorldState()
        val gold = state.oreBodies.first { it.type == OreType.GOLD }
        val targetZ = gold.nodes.first().centre.z
        val fraction = ((targetZ - state.bounds.minZ) / state.bounds.depth).coerceIn(0f, 1f)
        val clip = MineMeshBuilder.clipValue(state, ClipAxis.Z, fraction)

        val mesh = OreMeshBuilder.buildSlice(
            state = state,
            axis = ClipAxis.Z,
            fraction = fraction,
            flipped = false,
            showAll = true,
        )

        assertTrue(mesh.vertexCount > 0)
        var vertex = 0
        while (vertex < mesh.vertexCount) {
            val z = mesh.vertices[(vertex * 9) + 2]
            assertTrue(abs(z - clip) < 0.10f)
            vertex += 1
        }
    }

    @Test
    fun normalCtDoesNotRevealUndiscoveredOre() {
        val state = MineWorldState()
        val gold = state.oreBodies.first { it.type == OreType.GOLD }
        val targetZ = gold.nodes.first().centre.z
        val fraction = ((targetZ - state.bounds.minZ) / state.bounds.depth).coerceIn(0f, 1f)

        val hidden = OreMeshBuilder.buildSlice(
            state = state,
            axis = ClipAxis.Z,
            fraction = fraction,
            flipped = false,
            showAll = false,
        )

        assertEquals(0, hidden.vertexCount)
    }

    @Test
    fun discoveredOreAppearsAsItsTypedSolidCtColour() {
        val initial = MineWorldState()
        val gold = initial.oreBodies.first { it.type == OreType.GOLD }
        val state = initial.copy(discoveredOreBodyIds = setOf(gold.id))
        val targetZ = gold.nodes.first().centre.z
        val fraction = ((targetZ - state.bounds.minZ) / state.bounds.depth).coerceIn(0f, 1f)

        val mesh = OreMeshBuilder.buildSlice(
            state = state,
            axis = ClipAxis.Z,
            fraction = fraction,
            flipped = false,
            showAll = false,
        )

        assertTrue(mesh.vertexCount > 0)
        val hasGoldColour = (0 until mesh.vertexCount).any { vertex ->
            val base = vertex * 9
            abs(mesh.vertices[base + 6] - 0.96f) < 0.01f &&
                abs(mesh.vertices[base + 7] - 0.72f) < 0.01f &&
                abs(mesh.vertices[base + 8] - 0.10f) < 0.01f
        }
        assertTrue(hasGoldColour)
    }

    @Test
    fun seeOreCrossSectionIsClippedToCurrentGeneratedRockBounds() {
        val initial = MineWorldState()
        val bounds = initial.bounds
        val body = OreBody(
            id = "boundary-copper",
            type = OreType.COPPER,
            nodes = listOf(
                OreBodyNode(
                    centre = MinePoint3D(-4f, bounds.maxY - 1f, 20f),
                    radiusMetres = 8f,
                ),
                OreBodyNode(
                    centre = MinePoint3D(4f, bounds.maxY - 1f, 20f),
                    radiusMetres = 8f,
                ),
            ),
        )
        val state = initial.copy(oreBodies = listOf(body))
        val fraction = ((0f - bounds.minX) / bounds.width).coerceIn(0f, 1f)

        val mesh = OreMeshBuilder.buildSlice(
            state = state,
            axis = ClipAxis.X,
            fraction = fraction,
            flipped = false,
            showAll = true,
        )

        assertTrue(mesh.vertexCount > 0)
        for (vertex in 0 until mesh.vertexCount) {
            val base = vertex * 9
            val x = mesh.vertices[base]
            val y = mesh.vertices[base + 1]
            val z = mesh.vertices[base + 2]
            assertTrue(x >= bounds.minX - 0.1f && x <= bounds.maxX + 0.1f)
            assertTrue(y >= bounds.minY - 0.001f && y <= bounds.maxY + 0.001f)
            assertTrue(z >= bounds.minZ - 0.001f && z <= bounds.maxZ + 0.001f)
        }
    }
    @Test
    fun discoveredOreMeshContainsARealCutterSizedVoid() {
        val body = OreBody(
            id = "cut-copper",
            type = OreType.COPPER,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 2f), 6f),
                OreBodyNode(MinePoint3D(0f, 0f, 14f), 6f),
            ),
        )
        val tunnel = com.mineit.minegame.domain.TunnelGeometry(
            points = listOf(MinePoint3D(0f, 0f, 1f), MinePoint3D(0f, 0f, 15f)),
            radiusMetres = 3.2f,
        )
        val state = MineWorldState(
            tunnel = tunnel,
            oreBodies = listOf(body),
            discoveredOreBodyIds = setOf(body.id),
        )
        val mesh = OreMeshBuilder.buildDiscovered(state)
        assertTrue(mesh.vertexCount > 0)
        var hasInnerCutterWall = false
        for (vertex in 0 until mesh.vertexCount) {
            val base = vertex * 9
            val x = mesh.vertices[base]
            val y = mesh.vertices[base + 1]
            val z = mesh.vertices[base + 2]
            if (z in 4f..12f) {
                val radius = sqrt((x * x) + (y * y))
                assertTrue("ore surface must not bridge through the excavated channel", radius >= 2.35f)
                if (radius in 2.6f..3.8f) hasInnerCutterWall = true
            }
        }
        assertTrue("remaining ore should expose an inner cutter wall", hasInnerCutterWall)
    }

}
