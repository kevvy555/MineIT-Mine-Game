package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreType
import com.mineit.minegame.domain.TabularVeinGeometry
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
            geometry = horizontalVein(
                centre = MinePoint3D(0f, 0f, 36f),
                strikeDegrees = 0f,
                lengthMetres = 72f,
                widthMetres = 10f,
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
    fun diagonalVeinDoesNotQueueEmptyCornersOfItsWholeBodyBox() {
        val world = MineWorldBounds(-60f, 60f, -60f, 60f, 0f, 72f)
        val body = OreBody(
            id = "diagonal",
            type = OreType.GOLD,
            geometry = horizontalVein(
                centre = MinePoint3D(0f, 0f, 36f),
                strikeDegrees = 45f,
                lengthMetres = 72f,
                widthMetres = 6f,
            ),
        )

        val chunks = OreChunkPlanner.chunksForBody(body, world)

        assertTrue(chunks.isNotEmpty())
        assertFalse(
            "whole-body AABB would queue this corner even though the local vein does not reach it",
            OreChunkKey(body.id, x = 2, y = -2, z = 3) in chunks,
        )
    }

    @Test
    fun cutterNearWholeBodyBoxButAwayFromLocalPlanningBoundsQueuesNothing() {
        val world = MineWorldBounds(-60f, 60f, -60f, 60f, 0f, 72f)
        val body = OreBody(
            id = "diagonal",
            type = OreType.SILVER,
            geometry = horizontalVein(
                centre = MinePoint3D(0f, 0f, 36f),
                strikeDegrees = 45f,
                lengthMetres = 72f,
                widthMetres = 6f,
            ),
        )
        val cut = TunnelSegment(
            start = MinePoint3D(30f, -20f, 34f),
            end = MinePoint3D(30f, -20f, 38f),
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

    private fun horizontalVein(
        centre: MinePoint3D,
        strikeDegrees: Float,
        lengthMetres: Float,
        widthMetres: Float,
    ) = TabularVeinGeometry(
        centre = centre,
        strikeDegrees = strikeDegrees,
        dipDegrees = 0f,
        lengthMetres = lengthMetres,
        widthMetres = widthMetres,
        thicknessMetres = 4f,
        pinchAmplitude = 0f,
        waveAmplitudeMetres = 0f,
        waveCycles = 1f,
        phaseRadians = 0f,
    )
}
