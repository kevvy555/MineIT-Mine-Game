package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
}
