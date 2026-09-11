package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.TunnelSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSegmentReducerTest {
    @Test
    fun collinearShortSegmentsAreMergedWithoutMovingEndpoints() {
        val segments = listOf(
            TunnelSegment(MinePoint3D(0f, 0f, 0f), MinePoint3D(0.3f, 0f, 0f)),
            TunnelSegment(MinePoint3D(0.3f, 0f, 0f), MinePoint3D(0.6f, 0f, 0f)),
            TunnelSegment(MinePoint3D(0.6f, 0f, 0f), MinePoint3D(0.9f, 0f, 0f)),
            TunnelSegment(MinePoint3D(0.9f, 0f, 0f), MinePoint3D(1.2f, 0f, 0f)),
        )

        val reduced = TunnelSegmentReducer.reduce(segments, 1.0f)

        assertTrue(reduced.size < segments.size)
        assertEquals(segments.first().start, reduced.first().start)
        assertEquals(segments.last().end, reduced.last().end)
    }

    @Test
    fun sharpTurnIsNotMergedAway() {
        val segments = listOf(
            TunnelSegment(MinePoint3D(0f, 0f, 0f), MinePoint3D(0.5f, 0f, 0f)),
            TunnelSegment(MinePoint3D(0.5f, 0f, 0f), MinePoint3D(0.5f, 0.5f, 0f)),
        )

        val reduced = TunnelSegmentReducer.reduce(segments, 2f)

        assertEquals(2, reduced.size)
    }

    @Test
    fun disconnectedSegmentsRemainSeparate() {
        val segments = listOf(
            TunnelSegment(MinePoint3D(0f, 0f, 0f), MinePoint3D(0.4f, 0f, 0f)),
            TunnelSegment(MinePoint3D(5f, 5f, 5f), MinePoint3D(5.4f, 5f, 5f)),
        )

        assertEquals(2, TunnelSegmentReducer.reduce(segments, 2f).size)
    }
}
