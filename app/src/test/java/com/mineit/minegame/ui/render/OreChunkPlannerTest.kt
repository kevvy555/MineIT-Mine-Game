package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreBodyNode
import com.mineit.minegame.domain.OreType
import com.mineit.minegame.domain.TunnelSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OreChunkPlannerTest {
    @Test
    fun cutterInvalidatesOnlyLocalSubsetOfLongDeposit() {
        val world = MineWorldBounds(
            minX = -48f,
            maxX = 48f,
            minY = -48f,
            maxY = 48f,
            minZ = 0f,
            maxZ = 96f,
        )
        val body = OreBody(
            id = "long-copper",
            type = OreType.COPPER,
            nodes = listOf(
                OreBodyNode(MinePoint3D(-36f, 0f, 36f), 7f),
                OreBodyNode(MinePoint3D(36f, 0f, 36f), 7f),
            ),
        )
        val cut = TunnelSegment(
            start = MinePoint3D(-2f, -8f, 36f),
            end = MinePoint3D(-2f, 8f, 36f),
        )

        val all = OreChunkPlanner.chunksForBody(body, world)
        val affected = OreChunkPlanner.affectedChunks(
            segment = cut,
            tunnelRadiusMetres = 3.2f,
            body = body,
            worldBounds = world,
            extraPaddingMetres = OreMeshBuilder.BODY_GRID_STEP_METRES,
        )

        assertTrue(all.isNotEmpty())
        assertTrue(affected.isNotEmpty())
        assertTrue("a local cutter pass must not invalidate the entire deposit", affected.size < all.size)
        assertTrue(all.containsAll(affected))
    }

    @Test
    fun bodyGridAlignsExactlyWithOreChunkEdges() {
        val cellsPerChunk = OreChunkPlanner.CHUNK_SIZE_METRES / OreMeshBuilder.BODY_GRID_STEP_METRES
        assertEquals(20f, cellsPerChunk, 0.0001f)
    }
}
