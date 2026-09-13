package com.mineit.minegame.ui

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.ui.render.RenderPerformanceStats
import org.junit.Assert.assertTrue
import org.junit.Test

class MineDiagnosticsLogTest {
    @Test
    fun `csv contains periodic diagnostic fields`() {
        val log = MineDiagnosticsLog(startedAtMillis = 1_000L)
        log.record("sample", MineWorldState(), RenderPerformanceStats(framesPerSecond = 90, lastOreChunkBuildMs = 42f), null, 2_000L)
        val csv = log.toCsv()
        assertTrue(csv.contains("elapsed_ms,event,digging"))
        assertTrue(csv.contains("1000,sample"))
        assertTrue(csv.contains(",90,"))
        assertTrue(csv.contains(",42.000,"))
    }
}
