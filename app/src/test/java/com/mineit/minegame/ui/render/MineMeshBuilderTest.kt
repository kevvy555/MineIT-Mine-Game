package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.ChunkExtent
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MineMeshBuilderTest {
    @Test
    fun zSliceProducesSolidCapNearSurfaceAndAtDepth() {
        val state = MineWorldState()

        val shallow = MineMeshBuilder.buildCutCap(
            state = state,
            axis = ClipAxis.Z,
            fraction = 0.08f,
            flipped = false,
            tunnelSegments = emptyList(),
        )
        val deep = MineMeshBuilder.buildCutCap(
            state = state,
            axis = ClipAxis.Z,
            fraction = 0.80f,
            flipped = false,
            tunnelSegments = emptyList(),
        )

        assertTrue(shallow.vertexCount > 0)
        assertTrue(deep.vertexCount > 0)
    }

    @Test
    fun slicePlaneTracksRequestedPositionWithoutAreaSizedGrid() {
        val state = MineWorldState().copy(
            extent = ChunkExtent(
                minChunkX = -8,
                maxChunkX = 8,
                minChunkY = -8,
                maxChunkY = 8,
                minChunkZ = 0,
                maxChunkZ = 8,
            ),
        )
        val fraction = 0.37f
        val expectedPlane = MineMeshBuilder.clipValue(state, ClipAxis.X, fraction)
        val cap = MineMeshBuilder.buildCutCap(
            state = state,
            axis = ClipAxis.X,
            fraction = fraction,
            flipped = false,
            tunnelSegments = emptyList(),
        )

        assertTrue(cap.vertexCount > 0)
        assertTrue(cap.vertexCount < 500)
        val firstX = cap.vertices[0]
        assertTrue(abs(firstX - expectedPlane) < 0.10f)
    }

    @Test
    fun globalShellCostDoesNotGrowWithChunkExtent() {
        val small = MineMeshBuilder.buildWorldShell(MineWorldState())
        val wide = MineMeshBuilder.buildWorldShell(
            MineWorldState().copy(
                extent = ChunkExtent(-10, 10, -9, 9, 0, 12),
            ),
        )

        assertEquals(30, small.vertexCount)
        assertEquals(small.vertexCount, wide.vertexCount)
    }

    @Test
    fun untouchedChunkProducesNoDetailedMesh() {
        val mesh = MineMeshBuilder.buildChunk(
            state = MineWorldState(),
            key = ChunkKey(-1, -1, 1),
            tunnelSegments = emptyList(),
            gridStepMetres = MineMeshBuilder.ACTIVE_GRID_STEP_METRES,
        )

        assertEquals(0, mesh.vertexCount)
    }

    @Test
    fun tunnelOnlyViewBuildsDirectTunnelAndGrassMeshes() {
        var state = MineWorldController.startDigging(MineWorldState())
        repeat(4) {
            state = MineWorldController.tick(state, 0.25f)
        }

        val tunnel = MineMeshBuilder.buildTunnelOverview(state)
        val grass = MineMeshBuilder.buildGrassSurface(state)

        assertTrue(tunnel.vertexCount > 0)
        assertTrue(grass.vertexCount > 0)
    }
}
