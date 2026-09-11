package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.ChunkExtent
import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.TunnelSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkMeshPlannerTest {
    private val extent = ChunkExtent(
        minChunkX = -1,
        maxChunkX = 1,
        minChunkY = -1,
        maxChunkY = 1,
        minChunkZ = 0,
        maxChunkZ = 2,
    )

    @Test
    fun shortTunnelOnlyInvalidatesNearbyChunks() {
        val affected = ChunkMeshPlanner.affectedChunks(
            segment = TunnelSegment(
                MinePoint3D(1f, 1f, 4f),
                MinePoint3D(5f, 1f, 5f),
            ),
            tunnelRadiusMetres = 3.2f,
            extent = extent,
            extraPaddingMetres = 2f,
        )

        assertTrue(ChunkKey(0, 0, 0) in affected)
        assertFalse(ChunkKey(1, 1, 2) in affected)
        assertTrue(affected.size < extent.chunkCount)
    }

    @Test
    fun segmentOnlyDirtiesCtCapWhenItReachesSlicePlane() {
        val segment = TunnelSegment(
            MinePoint3D(2f, 0f, 10f),
            MinePoint3D(8f, 0f, 10f),
        )

        assertTrue(
            ChunkMeshPlanner.segmentTouchesSlice(
                segment = segment,
                axis = ClipAxis.X,
                clipValue = 7f,
                tunnelRadiusMetres = 3f,
                paddingMetres = 1f,
            ),
        )
        assertFalse(
            ChunkMeshPlanner.segmentTouchesSlice(
                segment = segment,
                axis = ClipAxis.X,
                clipValue = 20f,
                tunnelRadiusMetres = 3f,
                paddingMetres = 1f,
            ),
        )
    }

    @Test
    fun expandingWorldMarksOldOuterFaceForRemesh() {
        val previous = ChunkExtent(-1, 0, -1, 0, 0, 1)
        val current = previous.copy(maxChunkX = 1)

        val changed = ChunkMeshPlanner.chunksWhoseBoundaryChanged(previous, current)

        assertTrue(ChunkKey(0, -1, 0) in changed)
        assertTrue(ChunkKey(0, 0, 1) in changed)
        assertFalse(ChunkKey(-1, 0, 0) in changed)
    }
}
