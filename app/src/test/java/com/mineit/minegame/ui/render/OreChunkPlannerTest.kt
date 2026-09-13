package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreBodyNode
import com.mineit.minegame.domain.OreType
import com.mineit.minegame.domain.TunnelSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun bentDepositDoesNotQueueEmptyCornersOfItsWholeBodyBox() {
        val world = MineWorldBounds(-60f, 60f, -60f, 60f, 0f, 72f)
        val body = OreBody(
            id = "dogleg",
            type = OreType.GOLD,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 12f), 2f),
                OreBodyNode(MinePoint3D(0f, 36f, 36f), 2f),
                OreBodyNode(MinePoint3D(36f, 36f, 36f), 2f),
            ),
        )

        val chunks = OreChunkPlanner.chunksForBody(body, world)

        assertTrue(chunks.isNotEmpty())
        assertFalse(
            "whole-body AABB would queue this corner even though neither local segment reaches it",
            OreChunkKey(body.id, x = 2, y = 0, z = 2) in chunks,
        )
    }

    @Test
    fun cutterNearWholeBodyBoxButAwayFromLocalDepositSegmentsQueuesNothing() {
        val world = MineWorldBounds(-60f, 60f, -60f, 60f, 0f, 72f)
        val body = OreBody(
            id = "dogleg",
            type = OreType.SILVER,
            nodes = listOf(
                OreBodyNode(MinePoint3D(0f, 0f, 12f), 2f),
                OreBodyNode(MinePoint3D(0f, 36f, 36f), 2f),
                OreBodyNode(MinePoint3D(36f, 36f, 36f), 2f),
            ),
        )
        val cut = TunnelSegment(
            start = MinePoint3D(30f, 0f, 30f),
            end = MinePoint3D(30f, 0f, 34f),
        )

        val affected = OreChunkPlanner.affectedChunks(
            segment = cut,
            tunnelRadiusMetres = 3.2f,
            body = body,
            worldBounds = world,
            extraPaddingMetres = OreMeshBuilder.BODY_GRID_STEP_METRES,
        )

        assertTrue(affected.isEmpty())
    }

    @Test
    fun bodyGridAlignsExactlyWithOreChunkEdges() {
        val cellsPerChunk = OreChunkPlanner.CHUNK_SIZE_METRES / OreMeshBuilder.BODY_GRID_STEP_METRES
        assertEquals(20f, cellsPerChunk, 0.0001f)
    }
}
