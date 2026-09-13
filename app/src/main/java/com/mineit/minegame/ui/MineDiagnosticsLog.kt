package com.mineit.minegame.ui

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.ui.render.RenderPerformanceStats
import java.util.Locale

internal class MineDiagnosticsLog(private val startedAtMillis: Long = System.currentTimeMillis()) {
    private val rows = mutableListOf<String>()
    val recordCount: Int get() = rows.size

    @Synchronized
    fun record(
        event: String,
        state: MineWorldState,
        render: RenderPerformanceStats,
        tick: SequencedTickDiagnostics?,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val domainTick = tick?.diagnostics
        rows += listOf(
            nowMillis - startedAtMillis,
            event,
            state.isDigging,
            f(state.depthMetres),
            f(state.excavatedVolumeCubicMetres),
            f(state.totalMinedOreVolumeCubicMetres),
            render.framesPerSecond,
            f(render.frameTimeMs),
            f(render.lastChunkBuildMs),
            f(render.lastOreChunkBuildMs),
            f(render.lastOreGridStepMetres),
            f(render.lastCapBuildMs),
            f(render.lastUploadMs),
            render.triangleCount,
            render.oreTriangleCount,
            render.cachedChunks,
            render.cachedOreChunks,
            render.queuedChunks,
            render.queuedOreChunks,
            render.queuedOreBackgroundChunks,
            render.meshWorkerBusy,
            render.oreWorkerBusy,
            f(domainTick?.totalMs),
            f(domainTick?.materialMs),
            f(domainTick?.discoveryMs),
            f(tick?.schedulerLateMs),
            f(tick?.tickIntervalMs),
            domainTick?.classification?.candidateSamples ?: 0,
            domainTick?.classification?.newMaterialSamples ?: 0,
            domainTick?.classification?.nearbyExistingSegments ?: 0,
            domainTick?.classification?.nearbyOreBodies ?: 0,
            domainTick?.classification?.existingTunnelChecks ?: 0,
            domainTick?.classification?.oreFieldEvaluations ?: 0,
            f(domainTick?.segmentLengthMetres),
            f(domainTick?.newExcavatedVolumeCubicMetres),
            f(domainTick?.oreVolumeCubicMetres),
            f(domainTick?.wasteRockVolumeCubicMetres),
        ).joinToString(",")
    }

    @Synchronized
    fun clear() = rows.clear()

    @Synchronized
    fun toCsv(): String = buildString {
        appendLine(
            "elapsed_ms,event,digging,depth_m,removed_m3,ore_m3,fps,frame_ms," +
                "rock_mesh_ms,ore_mesh_ms,ore_grid_m,cap_ms,upload_ms,triangles,ore_triangles," +
                "rock_chunks,ore_chunks,rock_queue,ore_queue,ore_background_queue,rock_busy,ore_busy," +
                "tick_ms,material_ms,discovery_ms,scheduler_late_ms,tick_interval_ms,candidate_samples," +
                "new_samples,nearby_tunnel_segments,nearby_ore_bodies,tunnel_checks,ore_field_evals," +
                "segment_m,new_volume_m3,tick_ore_m3,tick_waste_m3",
        )
        rows.forEach(::appendLine)
    }

    private fun f(value: Float?): String =
        if (value == null) "" else String.format(Locale.US, "%.3f", value)
}
