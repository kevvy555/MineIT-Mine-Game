package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun untouchedBoundaryChunkUsesCheapPlanarShell() {
        val state = MineWorldState()
        val mesh = MineMeshBuilder.buildChunk(
            state = state,
            key = ChunkKey(-1, -1, 1),
            tunnelSegments = emptyList(),
            gridStepMetres = MineMeshBuilder.ACTIVE_GRID_STEP_METRES,
        )

        assertTrue(mesh.vertexCount > 0)
        assertTrue(mesh.vertexCount <= 36)
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
