from pathlib import Path


def replace(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


replace('app/build.gradle.kts', 'versionCode = 19\n        versionName = "0.13.1"', 'versionCode = 20\n        versionName = "0.13.2"')
replace('app/src/main/java/com/mineit/minegame/ui/MineWorldScreen.kt', 'text = "CHUNKED SUBTRACTIVE ORE MESHING"', 'text = "LIVE ORE + DIAGNOSTIC CAPTURE"')

geom = Path('app/src/main/java/com/mineit/minegame/domain/MineWorldGeometry.kt')
s = geom.read_text()
marker = '''data class ExcavationMaterialBreakdown(\n    val newExcavatedVolumeCubicMetres: Float,\n    val wasteRockVolumeCubicMetres: Float,\n    val oreVolumeCubicMetresByBodyId: Map<String, Float>,\n) {\n    val oreVolumeCubicMetres: Float\n        get() = oreVolumeCubicMetresByBodyId.values.sum()\n}\n'''
insert = '''\n\ndata class ExcavationClassificationDiagnostics(\n    val elapsedMs: Float,\n    val candidateSamples: Int,\n    val newMaterialSamples: Int,\n    val nearbyExistingSegments: Int,\n    val existingTunnelChecks: Int,\n    val oreFieldEvaluations: Int,\n)\n'''
if insert.strip() not in s:
    s = s.replace(marker, marker + insert)
s = s.replace('''        oreBodies: List<OreBody>,\n    ): ExcavationMaterialBreakdown {\n        val length = distance(start, end)\n''', '''        oreBodies: List<OreBody>,\n        onDiagnostics: ((ExcavationClassificationDiagnostics) -> Unit)? = null,\n    ): ExcavationMaterialBreakdown {\n        val diagnosticStart = System.nanoTime()\n        val length = distance(start, end)\n''')
s = s.replace('''        var newSampleCount = 0\n        val oreSamplesByBodyId = mutableMapOf<String, Int>()\n''', '''        var newSampleCount = 0\n        var existingTunnelChecks = 0\n        var oreFieldEvaluations = 0\n        val oreSamplesByBodyId = mutableMapOf<String, Int>()\n''')
s = s.replace('''                if (\n                    nearbyExistingSegments.any { existing ->\n                        isInsideFiniteCylinder(sample, existing, tunnelRadiusMetres)\n                    }\n                ) {\n                    return@forEach\n                }\n''', '''                var alreadyExcavated = false\n                for (existing in nearbyExistingSegments) {\n                    existingTunnelChecks += 1\n                    if (isInsideFiniteCylinder(sample, existing, tunnelRadiusMetres)) {\n                        alreadyExcavated = true\n                        break\n                    }\n                }\n                if (alreadyExcavated) return@forEach\n''')
s = s.replace('''                oreBodies.forEach { body ->\n                    val margin = oreMargin(sample, body.nodes)\n''', '''                oreBodies.forEach { body ->\n                    oreFieldEvaluations += 1\n                    val margin = oreMargin(sample, body.nodes)\n''')
s = s.replace('''        return ExcavationMaterialBreakdown(\n            newExcavatedVolumeCubicMetres = newVolume,\n            wasteRockVolumeCubicMetres = wasteVolume,\n            oreVolumeCubicMetresByBodyId = oreVolumes,\n        )\n''', '''        onDiagnostics?.invoke(\n            ExcavationClassificationDiagnostics(\n                elapsedMs = (System.nanoTime() - diagnosticStart) / 1_000_000f,\n                candidateSamples = longitudinalSteps * crossOffsets.size,\n                newMaterialSamples = newSampleCount,\n                nearbyExistingSegments = nearbyExistingSegments.size,\n                existingTunnelChecks = existingTunnelChecks,\n                oreFieldEvaluations = oreFieldEvaluations,\n            ),\n        )\n        return ExcavationMaterialBreakdown(\n            newExcavatedVolumeCubicMetres = newVolume,\n            wasteRockVolumeCubicMetres = wasteVolume,\n            oreVolumeCubicMetresByBodyId = oreVolumes,\n        )\n''')
geom.write_text(s)

controller = Path('app/src/main/java/com/mineit/minegame/domain/MineWorldController.kt')
s = controller.read_text()
if 'data class MineTickDiagnostics(' not in s:
    s = s.replace('object MineWorldController {', '''data class MineTickDiagnostics(\n    val totalMs: Float,\n    val materialMs: Float,\n    val discoveryMs: Float,\n    val segmentLengthMetres: Float,\n    val newExcavatedVolumeCubicMetres: Float,\n    val oreVolumeCubicMetres: Float,\n    val wasteRockVolumeCubicMetres: Float,\n    val classification: ExcavationClassificationDiagnostics,\n)\n\nobject MineWorldController {''')
s = s.replace('''    fun tick(state: MineWorldState, deltaSeconds: Float): MineWorldState {\n        if (!state.isDigging || deltaSeconds <= 0f) return state\n''', '''    fun tick(\n        state: MineWorldState,\n        deltaSeconds: Float,\n        onDiagnostics: ((MineTickDiagnostics) -> Unit)? = null,\n    ): MineWorldState {\n        if (!state.isDigging || deltaSeconds <= 0f) return state\n        val tickStart = System.nanoTime()\n''')
s = s.replace('''        val material = MineWorldGeometry.classifyNewExcavation(\n            start = start,\n            end = target,\n            tunnelRadiusMetres = state.tunnel.radiusMetres,\n            existingTunnelSegments = state.tunnel.segments,\n            oreBodies = state.oreBodies,\n        )\n''', '''        var classificationDiagnostics = ExcavationClassificationDiagnostics(0f, 0, 0, 0, 0, 0)\n        val material = MineWorldGeometry.classifyNewExcavation(\n            start = start,\n            end = target,\n            tunnelRadiusMetres = state.tunnel.radiusMetres,\n            existingTunnelSegments = state.tunnel.segments,\n            oreBodies = state.oreBodies,\n            onDiagnostics = { classificationDiagnostics = it },\n        )\n''')
s = s.replace('''        val newlyDiscoveredIds = state.oreBodies.asSequence()\n''', '''        val discoveryStart = System.nanoTime()\n        val newlyDiscoveredIds = state.oreBodies.asSequence()\n''')
s = s.replace('''        val bodyTypesById = state.oreBodies.associate { it.id to it.type }\n''', '''        val discoveryMs = (System.nanoTime() - discoveryStart) / 1_000_000f\n        val bodyTypesById = state.oreBodies.associate { it.id to it.type }\n''')
s = s.replace('''        return state.copy(\n            tunnel = tunnel,\n            extent = extent,\n            headingDegrees = heading,\n            wasteRockVolumeCubicMetres =\n                state.wasteRockVolumeCubicMetres + material.wasteRockVolumeCubicMetres,\n            minedOreVolumeCubicMetresByType = minedOreByType,\n            discoveredOreBodyIds = state.discoveredOreBodyIds + newlyDiscoveredIds,\n            isDigging = state.isDigging && !exitsSurface,\n        )\n''', '''        val nextState = state.copy(\n            tunnel = tunnel,\n            extent = extent,\n            headingDegrees = heading,\n            wasteRockVolumeCubicMetres =\n                state.wasteRockVolumeCubicMetres + material.wasteRockVolumeCubicMetres,\n            minedOreVolumeCubicMetresByType = minedOreByType,\n            discoveredOreBodyIds = state.discoveredOreBodyIds + newlyDiscoveredIds,\n            isDigging = state.isDigging && !exitsSurface,\n        )\n        onDiagnostics?.invoke(\n            MineTickDiagnostics(\n                totalMs = (System.nanoTime() - tickStart) / 1_000_000f,\n                materialMs = classificationDiagnostics.elapsedMs,\n                discoveryMs = discoveryMs,\n                segmentLengthMetres = segmentLength,\n                newExcavatedVolumeCubicMetres = material.newExcavatedVolumeCubicMetres,\n                oreVolumeCubicMetres = material.oreVolumeCubicMetres,\n                wasteRockVolumeCubicMetres = material.wasteRockVolumeCubicMetres,\n                classification = classificationDiagnostics,\n            ),\n        )\n        return nextState\n''')
controller.write_text(s)

vm = Path('app/src/main/java/com/mineit/minegame/ui/MineWorldViewModel.kt')
s = vm.read_text()
s = s.replace('import com.mineit.minegame.domain.MineWorldController\n', 'import com.mineit.minegame.domain.MineTickDiagnostics\nimport com.mineit.minegame.domain.MineWorldController\n')
s = s.replace('class MineWorldViewModel : ViewModel() {\n', 'data class SequencedTickDiagnostics(val sequence: Long, val diagnostics: MineTickDiagnostics)\n\nclass MineWorldViewModel : ViewModel() {\n')
s = s.replace('''    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()\n\n    private var diggingJob: Job? = null\n''', '''    val state: StateFlow<MineWorldState> = mutableState.asStateFlow()\n    private val mutableTickDiagnostics = MutableStateFlow<SequencedTickDiagnostics?>(null)\n    val tickDiagnostics: StateFlow<SequencedTickDiagnostics?> = mutableTickDiagnostics.asStateFlow()\n\n    private var diggingJob: Job? = null\n    private var tickSequence = 0L\n''')
s = s.replace('''                mutableState.update { state ->\n                    MineWorldController.tick(state, DIG_TICK_SECONDS)\n                }\n''', '''                var diagnostics: MineTickDiagnostics? = null\n                mutableState.update { state ->\n                    MineWorldController.tick(state, DIG_TICK_SECONDS) { diagnostics = it }\n                }\n                diagnostics?.let { value ->\n                    tickSequence += 1\n                    mutableTickDiagnostics.value = SequencedTickDiagnostics(tickSequence, value)\n                }\n''')
s = s.replace('''        mutableState.value = MineWorldController.reset()\n''', '''        mutableState.value = MineWorldController.reset()\n        mutableTickDiagnostics.value = null\n''')
vm.write_text(s)

coord = Path('app/src/main/java/com/mineit/minegame/ui/render/AsyncMeshBuildCoordinator.kt')
s = coord.read_text()
s = s.replace('''internal data class OreChunkBuildResult(\n    val pipelineGeneration: Long,\n    val revision: Long,\n    val key: OreChunkKey,\n    val mesh: MineMesh,\n    val buildMs: Float,\n)\n''', '''internal data class OreChunkBuildResult(\n    val pipelineGeneration: Long,\n    val revision: Long,\n    val key: OreChunkKey,\n    val gridStepMetres: Float,\n    val mesh: MineMesh,\n    val buildMs: Float,\n)\n''')
s = s.replace('''                        key = request.key,\n                        mesh = mesh,\n''', '''                        key = request.key,\n                        gridStepMetres = request.gridStepMetres,\n                        mesh = mesh,\n''')
coord.write_text(s)

renderer = Path('app/src/main/java/com/mineit/minegame/ui/render/MineWorldGlRenderer.kt')
s = renderer.read_text()
s = s.replace('''    val lastOreChunkBuildMs: Float = 0f,\n''', '''    val lastOreChunkBuildMs: Float = 0f,\n    val lastOreGridStepMetres: Float = 0f,\n''')
s = s.replace('''    private val pendingOreChunkBuilds = linkedSetOf<OreChunkKey>()\n''', '''    private val pendingOreChunkBuilds = linkedMapOf<OreChunkKey, Float>()\n    private val activeOreDirtyChunks = linkedSetOf<OreChunkKey>()\n''')
s = s.replace('''    private var lastOreChunkBuildMs = 0f\n''', '''    private var lastOreChunkBuildMs = 0f\n    private var lastOreGridStepMetres = 0f\n''')
s = s.replace('''        pendingOreChunkBuilds.clear()\n        oreChunkRevisions.clear()\n''', '''        pendingOreChunkBuilds.clear()\n        activeOreDirtyChunks.clear()\n        oreChunkRevisions.clear()\n''', 1)
s = s.replace('''            oreChunksTouchedDuringDigging.clear()\n            activeSliceDirty = false\n''', '''            oreChunksTouchedDuringDigging.clear()\n            activeOreDirtyChunks.clear()\n            activeSliceDirty = false\n''')
s = s.replace('''                state.discoveredOreBodies.forEach { body ->\n                    oreChunksTouchedDuringDigging += OreChunkPlanner.affectedChunks(\n                        segment = segment,\n                        tunnelRadiusMetres = state.tunnel.radiusMetres,\n                        body = body,\n                        worldBounds = state.bounds,\n                        extraPaddingMetres = OreMeshBuilder.BODY_GRID_STEP_METRES,\n                    )\n                }\n''', '''                state.discoveredOreBodies.forEach { body ->\n                    val affectedOre = OreChunkPlanner.affectedChunks(\n                        segment = segment,\n                        tunnelRadiusMetres = state.tunnel.radiusMetres,\n                        body = body,\n                        worldBounds = state.bounds,\n                        extraPaddingMetres = LIVE_ORE_GRID_STEP_METRES,\n                    )\n                    oreChunksTouchedDuringDigging += affectedOre\n                    activeOreDirtyChunks += affectedOre\n                }\n''')
s = s.replace('''            oreChunksTouchedDuringDigging.forEach(::markOreChunkForBuild)\n''', '''            oreChunksTouchedDuringDigging.forEach { key ->\n                markOreChunkForBuild(key, OreMeshBuilder.BODY_GRID_STEP_METRES, replaceExisting = true)\n            }\n''')
s = s.replace('''        activeDirtyChunks.forEach { key ->\n            markChunkForBuild(key, ACTIVE_GRID_STEP_METRES, replaceExisting = true)\n        }\n        activeDirtyChunks.clear()\n''', '''        activeDirtyChunks.forEach { key ->\n            markChunkForBuild(key, ACTIVE_GRID_STEP_METRES, replaceExisting = true)\n        }\n        activeDirtyChunks.clear()\n        activeOreDirtyChunks.forEach { key ->\n            markOreChunkForBuild(key, LIVE_ORE_GRID_STEP_METRES, replaceExisting = true)\n        }\n        activeOreDirtyChunks.clear()\n''')
s = s.replace('''        pendingOreChunkBuilds.clear()\n        oreChunkRevisions.clear()\n''', '''        pendingOreChunkBuilds.clear()\n        activeOreDirtyChunks.clear()\n        oreChunkRevisions.clear()\n''', 1)
s = s.replace('''            OreChunkPlanner.chunksForBody(body, state.bounds).forEach(::markOreChunkForBuild)\n''', '''            OreChunkPlanner.chunksForBody(body, state.bounds).forEach { key ->\n                markOreChunkForBuild(key, OreMeshBuilder.BODY_GRID_STEP_METRES)\n            }\n''')
s = s.replace('''    private fun markOreChunkForBuild(key: OreChunkKey) {\n        pendingOreChunkBuilds += key\n        oreChunkRevisions[key] = (oreChunkRevisions[key] ?: 0L) + 1L\n    }\n''', '''    private fun markOreChunkForBuild(\n        key: OreChunkKey,\n        gridStepMetres: Float,\n        replaceExisting: Boolean = false,\n    ) {\n        val existing = pendingOreChunkBuilds[key]\n        pendingOreChunkBuilds[key] = when {\n            replaceExisting -> gridStepMetres\n            existing == null -> gridStepMetres\n            else -> minOf(existing, gridStepMetres)\n        }\n        oreChunkRevisions[key] = (oreChunkRevisions[key] ?: 0L) + 1L\n    }\n''')
s = s.replace('''            lastOreChunkBuildMs = result.buildMs\n''', '''            lastOreChunkBuildMs = result.buildMs\n            lastOreGridStepMetres = result.gridStepMetres\n''')
old_sched = '''    private fun scheduleNextOreChunkBuild(state: MineWorldState) {\n        if (state.isDigging || pendingOreChunkBuilds.isEmpty() || meshBuildCoordinator.availableOreSlots() <= 0) return\n        val key = pendingOreChunkBuilds.minByOrNull { candidate ->\n            MineWorldGeometry.distance(OreChunkPlanner.chunkCentre(candidate), state.tunnel.end)\n        } ?: return\n        val body = state.oreBodies.firstOrNull { it.id == key.bodyId }\n        if (body == null || key !in OreChunkPlanner.chunksForBody(body, state.bounds)) {\n            pendingOreChunkBuilds.remove(key)\n            return\n        }\n        val revision = oreChunkRevisions[key] ?: return\n        val accepted = meshBuildCoordinator.trySubmitOreChunk(\n            OreChunkBuildRequest(\n                pipelineGeneration = pipelineGeneration,\n                revision = revision,\n                key = key,\n                state = state,\n                body = body,\n                gridStepMetres = OreMeshBuilder.BODY_GRID_STEP_METRES,\n            ),\n        )\n        if (accepted) pendingOreChunkBuilds.remove(key)\n    }\n'''
new_sched = '''    private fun scheduleNextOreChunkBuild(state: MineWorldState) {\n        if (pendingOreChunkBuilds.isEmpty() || meshBuildCoordinator.availableOreSlots() <= 0) return\n        val next = pendingOreChunkBuilds.entries.minByOrNull { entry ->\n            MineWorldGeometry.distance(OreChunkPlanner.chunkCentre(entry.key), state.tunnel.end)\n        } ?: return\n        val key = next.key\n        val gridStep = next.value\n        val body = state.oreBodies.firstOrNull { it.id == key.bodyId }\n        if (body == null || key !in OreChunkPlanner.chunksForBody(body, state.bounds)) {\n            pendingOreChunkBuilds.remove(key)\n            return\n        }\n        val revision = oreChunkRevisions[key] ?: return\n        val accepted = meshBuildCoordinator.trySubmitOreChunk(\n            OreChunkBuildRequest(\n                pipelineGeneration = pipelineGeneration,\n                revision = revision,\n                key = key,\n                state = state,\n                body = body,\n                gridStepMetres = gridStep,\n            ),\n        )\n        if (accepted) pendingOreChunkBuilds.remove(key)\n    }\n'''
if old_sched not in s:
    raise SystemExit('ore scheduler pattern not found')
s = s.replace(old_sched, new_sched)
s = s.replace('''                lastOreChunkBuildMs = lastOreChunkBuildMs,\n''', '''                lastOreChunkBuildMs = lastOreChunkBuildMs,\n                lastOreGridStepMetres = lastOreGridStepMetres,\n''')
s = s.replace('''        const val ACTIVE_REMESH_DISTANCE_METRES = 1.2f\n''', '''        const val ACTIVE_REMESH_DISTANCE_METRES = 1.2f\n        const val LIVE_ORE_GRID_STEP_METRES = 1.2f\n''')
renderer.write_text(s)

Path('app/src/main/java/com/mineit/minegame/ui/MineDiagnosticsLog.kt').write_text(r'''package com.mineit.minegame.ui

import com.mineit.minegame.domain.MineTickDiagnostics
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.ui.render.RenderPerformanceStats
import java.util.Locale

internal class MineDiagnosticsLog(private val startedAtMillis: Long = System.currentTimeMillis()) {
    private val rows = mutableListOf<String>()
    val recordCount: Int get() = rows.size

    @Synchronized
    fun record(event: String, state: MineWorldState, render: RenderPerformanceStats, tick: MineTickDiagnostics?, nowMillis: Long = System.currentTimeMillis()) {
        rows += listOf(
            nowMillis - startedAtMillis, event, state.isDigging, f(state.depthMetres), f(state.excavatedVolumeCubicMetres),
            f(state.totalMinedOreVolumeCubicMetres), render.framesPerSecond, f(render.frameTimeMs), f(render.lastChunkBuildMs),
            f(render.lastOreChunkBuildMs), f(render.lastOreGridStepMetres), f(render.lastCapBuildMs), f(render.lastUploadMs),
            render.triangleCount, render.oreTriangleCount, render.cachedChunks, render.cachedOreChunks, render.queuedChunks,
            render.queuedOreChunks, render.meshWorkerBusy, render.oreWorkerBusy, f(tick?.totalMs), f(tick?.materialMs),
            f(tick?.discoveryMs), tick?.classification?.candidateSamples ?: 0, tick?.classification?.newMaterialSamples ?: 0,
            tick?.classification?.nearbyExistingSegments ?: 0, tick?.classification?.existingTunnelChecks ?: 0,
            tick?.classification?.oreFieldEvaluations ?: 0, f(tick?.segmentLengthMetres), f(tick?.newExcavatedVolumeCubicMetres),
            f(tick?.oreVolumeCubicMetres), f(tick?.wasteRockVolumeCubicMetres),
        ).joinToString(",")
    }

    @Synchronized fun clear() = rows.clear()
    @Synchronized fun toCsv(): String = buildString {
        appendLine("elapsed_ms,event,digging,depth_m,removed_m3,ore_m3,fps,frame_ms,rock_mesh_ms,ore_mesh_ms,ore_grid_m,cap_ms,upload_ms,triangles,ore_triangles,rock_chunks,ore_chunks,rock_queue,ore_queue,rock_busy,ore_busy,tick_ms,material_ms,discovery_ms,candidate_samples,new_samples,nearby_tunnel_segments,tunnel_checks,ore_field_evals,segment_m,new_volume_m3,tick_ore_m3,tick_waste_m3")
        rows.forEach(::appendLine)
    }
    private fun f(value: Float?): String = if (value == null) "" else String.format(Locale.US, "%.3f", value)
}
''')

screen = Path('app/src/main/java/com/mineit/minegame/ui/MineWorldScreen.kt')
s = screen.read_text()
s = s.replace('import androidx.compose.runtime.Composable\n', 'import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.compose.runtime.Composable\n')
s = s.replace('import androidx.compose.runtime.remember\n', 'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberUpdatedState\n')
s = s.replace('import androidx.compose.ui.viewinterop.AndroidView\n', 'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.viewinterop.AndroidView\n')
s = s.replace('import kotlin.math.abs\n', 'import kotlinx.coroutines.delay\nimport java.text.SimpleDateFormat\nimport java.util.Date\nimport java.util.Locale\nimport kotlin.math.abs\n')
s = s.replace('''    val state by viewModel.state.collectAsStateWithLifecycle()\n''', '''    val state by viewModel.state.collectAsStateWithLifecycle()\n    val tickDiagnostics by viewModel.tickDiagnostics.collectAsStateWithLifecycle()\n''')
s = s.replace('''    var selectedPanel by remember { mutableStateOf(ControlPanel.CONTROL) }\n    val lifecycleOwner = LocalLifecycleOwner.current\n''', '''    var selectedPanel by remember { mutableStateOf(ControlPanel.CONTROL) }\n    val diagnosticsLog = remember { MineDiagnosticsLog() }\n    var diagnosticRecordCount by remember { mutableStateOf(0) }\n    var diagnosticExportStatus by remember { mutableStateOf<String?>(null) }\n    val context = LocalContext.current\n    val latestState by rememberUpdatedState(state)\n    val latestRender by rememberUpdatedState(performanceStats)\n    val latestTick by rememberUpdatedState(tickDiagnostics?.diagnostics)\n    val lifecycleOwner = LocalLifecycleOwner.current\n    val exportDiagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->\n        if (uri != null) {\n            runCatching {\n                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(diagnosticsLog.toCsv()) }\n                    ?: error("Unable to open selected file")\n            }.onSuccess { diagnosticExportStatus = "Diagnostics saved" }\n                .onFailure { diagnosticExportStatus = "Export failed: ${it.message ?: "unknown error"}" }\n        }\n    }\n''')
s = s.replace('''    DisposableEffect(lifecycleOwner, surfaceView) {\n''', '''    LaunchedEffect(Unit) {\n        while (true) {\n            delay(1_000)\n            diagnosticsLog.record("sample", latestState, latestRender, latestTick)\n            diagnosticRecordCount = diagnosticsLog.recordCount\n        }\n    }\n\n    LaunchedEffect(tickDiagnostics?.sequence) {\n        val tick = tickDiagnostics?.diagnostics ?: return@LaunchedEffect\n        if (tick.totalMs >= 20f || tick.materialMs >= 20f || tick.discoveryMs >= 20f) {\n            diagnosticsLog.record("tick_spike", state, performanceStats, tick)\n            diagnosticRecordCount = diagnosticsLog.recordCount\n        }\n    }\n\n    LaunchedEffect(performanceStats.lastChunkBuildMs, performanceStats.lastOreChunkBuildMs, performanceStats.chunksRebuilt, performanceStats.oreChunksRebuilt) {\n        if (performanceStats.chunksRebuilt > 0 || performanceStats.oreChunksRebuilt > 0) {\n            diagnosticsLog.record("mesh_event", state, performanceStats, tickDiagnostics?.diagnostics)\n            diagnosticRecordCount = diagnosticsLog.recordCount\n        }\n    }\n\n    DisposableEffect(lifecycleOwner, surfaceView) {\n''')
s = s.replace('''            performanceStats = performanceStats,\n            onSliceAxisChange = { selectedSliceAxis = it },\n''', '''            performanceStats = performanceStats,\n            diagnosticRecordCount = diagnosticRecordCount,\n            diagnosticExportStatus = diagnosticExportStatus,\n            onExportDiagnostics = {\n                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.UK).format(Date())\n                exportDiagnostics.launch("mineit-diagnostics-$stamp.csv")\n            },\n            onClearDiagnostics = {\n                diagnosticsLog.clear()\n                diagnosticRecordCount = 0\n                diagnosticExportStatus = "Diagnostics cleared"\n            },\n            onSliceAxisChange = { selectedSliceAxis = it },\n''')
s = s.replace('''    performanceStats: RenderPerformanceStats,\n    onSliceAxisChange: (ClipAxis) -> Unit,\n''', '''    performanceStats: RenderPerformanceStats,\n    diagnosticRecordCount: Int,\n    diagnosticExportStatus: String?,\n    onExportDiagnostics: () -> Unit,\n    onClearDiagnostics: () -> Unit,\n    onSliceAxisChange: (ClipAxis) -> Unit,\n''')
s = s.replace('''                performanceStats = performanceStats,\n                onToggleDiagnostics = onToggleDiagnostics,\n                onResetMine = onResetMine,\n''', '''                performanceStats = performanceStats,\n                diagnosticRecordCount = diagnosticRecordCount,\n                diagnosticExportStatus = diagnosticExportStatus,\n                onExportDiagnostics = onExportDiagnostics,\n                onClearDiagnostics = onClearDiagnostics,\n                onToggleDiagnostics = onToggleDiagnostics,\n                onResetMine = onResetMine,\n''')
s = s.replace('''    performanceStats: RenderPerformanceStats,\n    onToggleDiagnostics: () -> Unit,\n    onResetMine: () -> Unit,\n) {\n''', '''    performanceStats: RenderPerformanceStats,\n    diagnosticRecordCount: Int,\n    diagnosticExportStatus: String?,\n    onExportDiagnostics: () -> Unit,\n    onClearDiagnostics: () -> Unit,\n    onToggleDiagnostics: () -> Unit,\n    onResetMine: () -> Unit,\n) {\n''', 1)
target = '''    }\n\n    Text(\n        text = "MINED: Gold ${state.minedOreVolumeCubicMetres(OreType.GOLD).roundToInt()}m³ • " +\n'''
repl = '''    }\n\n    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {\n        Button(onClick = onExportDiagnostics, modifier = Modifier.weight(1f)) { Text("EXPORT LOG") }\n        OutlinedButton(onClick = onClearDiagnostics, modifier = Modifier.weight(1f)) { Text("CLEAR LOG") }\n    }\n    Text(\n        text = "$diagnosticRecordCount diagnostic rows • 1s samples + spike/mesh events" +\n            (diagnosticExportStatus?.let { " • $it" } ?: ""),\n        color = Color(0xFF80CBC4), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),\n    )\n\n    Text(\n        text = "MINED: Gold ${state.minedOreVolumeCubicMetres(OreType.GOLD).roundToInt()}m³ • " +\n'''
if target not in s:
    raise SystemExit('Other panel insertion anchor not found')
s = s.replace(target, repl, 1)
s = s.replace('''            append("  ore ${format1(stats.lastOreChunkBuildMs)}ms")\n''', '''            append("  ore ${format1(stats.lastOreChunkBuildMs)}ms@${format1(stats.lastOreGridStepMetres)}m")\n''')
screen.write_text(s)

test = Path('app/src/test/java/com/mineit/minegame/domain/MineWorldControllerTest.kt')
s = test.read_text()
idx = s.rfind('\n}')
s = s[:idx] + r'''

    @Test
    fun `tick diagnostics report material work without changing accounting`() {
        var state = MineWorldState().copy(verticalAngleDegrees = 20f)
        state = MineWorldController.startDigging(state)
        var diagnostics: MineTickDiagnostics? = null
        val next = MineWorldController.tick(state, 0.1f) { diagnostics = it }
        val measured = requireNotNull(diagnostics)
        assertTrue(measured.totalMs >= measured.materialMs)
        assertTrue(measured.classification.candidateSamples > 0)
        assertEquals(next.excavatedVolumeCubicMetres, next.wasteRockVolumeCubicMetres + next.totalMinedOreVolumeCubicMetres, 0.05f)
    }
''' + s[idx:]
test.write_text(s)

Path('app/src/test/java/com/mineit/minegame/ui/MineDiagnosticsLogTest.kt').write_text(r'''package com.mineit.minegame.ui

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
''')

replace('README.md', 'Current prototype — 0.13.1', 'Current prototype — 0.13.2')
replace('README.md', '0.13 changes ore from radius-depleted tube geometry to true subtractive solid geometry.', '0.13 keeps true subtractive solid geometry; 0.13.2 adds coarse live ore remeshing plus exportable one-second/spike diagnostics.')
