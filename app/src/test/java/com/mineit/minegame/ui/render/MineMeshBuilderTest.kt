package com.mineit.minegame.ui.render

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
}
