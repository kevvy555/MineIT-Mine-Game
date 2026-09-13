package com.mineit.minegame.ui.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.TunnelSegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal enum class ClipAxis { X, Y, Z }

internal data class RenderPerformanceStats(
    val framesPerSecond: Int = 0,
    val frameTimeMs: Float = 0f,
    val lastChunkBuildMs: Float = 0f,
    val lastCapBuildMs: Float = 0f,
    val lastUploadMs: Float = 0f,
    val triangleCount: Int = 0,
    val cachedChunks: Int = 0,
    val chunksRebuilt: Int = 0,
    val queuedChunks: Int = 0,
    val meshWorkerBusy: Boolean = false,
    val capWorkerBusy: Boolean = false,
)

/**
 * OpenGL presentation for the 3D mine.
 *
 * Expensive scalar-field work remains limited to tunnel-bearing chunks. The geological world shell
 * and CT faces are immediate meshes. X/Y/Z clipping is performed simultaneously in the fragment
 * shader, allowing persistent tri-planar inspection without duplicating world geometry.
 */
internal class MineWorldGlRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var worldState = MineWorldState()

    @Volatile
    private var slices = SliceConfiguration()

    @Volatile
    private var rockVisible = true

    @Volatile
    private var tunnelVisible = true

    @Volatile
    private var seeOre = false

    @Volatile
    private var machineDirty = true

    @Volatile
    private var tunnelOverviewDirty = true

    @Volatile
    private var grassDirty = true

    @Volatile
    private var worldShellDirty = true

    @Volatile
    private var sliceDirty = true

    @Volatile
    private var oreOverlayDirty = true

    @Volatile
    private var oreSliceDirty = true

    @Volatile
    private var performanceListener: ((RenderPerformanceStats) -> Unit)? = null

    @Volatile
    private var yawDegrees = 38f

    @Volatile
    private var pitchDegrees = 24f

    @Volatile
    private var zoomScale = 1f

    @Volatile
    private var panRightMetres = 0f

    @Volatile
    private var panUpMetres = 0f

    @Volatile
    private var followDigger = false

    @Volatile
    private var cameraMode = CameraMode.ORBIT

    private val meshBuildCoordinator = AsyncMeshBuildCoordinator()

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var program = 0

    private data class GlMesh(
        val bufferId: Int,
        val vertexCount: Int,
    )

    private data class CachedChunk(
        val mesh: GlMesh?,
        val gridStepMetres: Float,
    )

    private val chunkMeshes = linkedMapOf<ChunkKey, CachedChunk>()
    private val segmentIndex = mutableMapOf<ChunkKey, LinkedHashSet<TunnelSegment>>()
    private val pendingChunkBuilds = linkedMapOf<ChunkKey, Float>()
    private val chunkRevisions = mutableMapOf<ChunkKey, Long>()
    private val appliedChunkRevisions = mutableMapOf<ChunkKey, Long>()
    private val chunksTouchedDuringDigging = linkedSetOf<ChunkKey>()
    private val activeDirtyChunks = linkedSetOf<ChunkKey>()
    private var activeSliceDirty = false
    private var lastActiveRemeshPoint: MinePoint3D? = null
    private var processedState: MineWorldState? = null
    private var pipelineGeneration = 1L

    private val sliceMeshes = linkedMapOf<ClipAxis, GlMesh?>()
    private val oreSliceMeshes = linkedMapOf<ClipAxis, GlMesh?>()
    private var machineMesh: GlMesh? = null
    private var tunnelOverviewMesh: GlMesh? = null
    private var grassMesh: GlMesh? = null
    private var worldShellMesh: GlMesh? = null
    private var oreOverlayMesh: GlMesh? = null

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewModelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var mvpLocation = -1
    private var modelLocation = -1
    private var clipValuesLocation = -1
    private var clipDirectionsLocation = -1
    private var clipEnabledLocation = -1
    private var boundsMinLocation = -1
    private var boundsMaxLocation = -1
    private var boundsClipEnabledLocation = -1
    private var alphaLocation = -1
    private var positionLocation = -1
    private var normalLocation = -1
    private var colourLocation = -1

    private var statsWindowStartNanos = System.nanoTime()
    private var framesInStatsWindow = 0
    private var lastChunkBuildMs = 0f
    private var lastCapBuildMs = 0f
    private var lastUploadMs = 0f
    private var chunksRebuiltSinceStats = 0

    fun setWorldState(state: MineWorldState) {
        val previous = worldState
        worldState = state

        if (
            state.tunnel.end != previous.tunnel.end ||
            state.machineHeadingDegrees != previous.machineHeadingDegrees ||
            state.verticalAngleDegrees != previous.verticalAngleDegrees ||
            state.steering != previous.steering ||
            state.isDigging != previous.isDigging
        ) {
            machineDirty = true
        }
        if (state.tunnel.points.size != previous.tunnel.points.size) {
            tunnelOverviewDirty = true
            val surfaceRange = state.tunnel.radiusMetres + 3f
            if (state.tunnel.end.z <= surfaceRange || previous.tunnel.end.z <= surfaceRange) {
                grassDirty = true
            }
        }
        if (state.extent != previous.extent) {
            worldShellDirty = true
            grassDirty = true
            sliceDirty = true
            oreOverlayDirty = true
            oreSliceDirty = true
        }
        if (state.discoveredOreBodyIds != previous.discoveredOreBodyIds) {
            sliceDirty = true
            tunnelOverviewDirty = true
            oreOverlayDirty = true
            oreSliceDirty = true
        }
        if (state.minedOreVolumeCubicMetresByType != previous.minedOreVolumeCubicMetresByType) {
            // Original deposits stay immutable; mining changes only the subtraction field.
            tunnelOverviewDirty = true
            oreOverlayDirty = true
            oreSliceDirty = true
        }
    }

    fun setSlices(configuration: SliceConfiguration) {
        val normalized = configuration.copy(
            enabledAxes = configuration.enabledAxes.toSet(),
            flippedAxes = configuration.flippedAxes.toSet(),
        )
        if (normalized == slices) return
        slices = normalized
        sliceDirty = true
        oreSliceDirty = true
    }

    fun setFollowDigger(enabled: Boolean) {
        if (enabled && !followDigger) {
            panRightMetres = 0f
            panUpMetres = 0f
        }
        followDigger = enabled
    }

    fun setCameraMode(mode: CameraMode) {
        if (mode != cameraMode) {
            if (mode == CameraMode.ORBIT) sliceDirty = true
            oreSliceDirty = true
        }
        cameraMode = mode
    }

    fun setRockVisible(visible: Boolean) {
        if (visible == rockVisible) return
        rockVisible = visible
        oreSliceDirty = true
        if (visible) {
            sliceDirty = true
            worldShellDirty = true
            grassDirty = true
        } else {
            tunnelOverviewDirty = true
            grassDirty = true
        }
    }

    fun setTunnelVisible(visible: Boolean) {
        tunnelVisible = visible
    }

    fun setSeeOre(enabled: Boolean) {
        if (enabled == seeOre) return
        seeOre = enabled
        oreSliceDirty = true
    }

    fun setPerformanceListener(listener: ((RenderPerformanceStats) -> Unit)?) {
        performanceListener = listener
    }

    fun rotate(deltaX: Float, deltaY: Float) {
        if (cameraMode != CameraMode.ORBIT) return
        yawDegrees = (yawDegrees + (deltaX * 0.35f)) % 360f
        pitchDegrees = (pitchDegrees + (deltaY * 0.30f)).coerceIn(-82f, 82f)
    }

    fun pan(deltaX: Float, deltaY: Float) {
        if (cameraMode != CameraMode.ORBIT) return
        val bounds = worldState.bounds
        val largestSpan = max(bounds.width, max(bounds.height, bounds.depth))
        val framingSpan = if (followDigger) FOLLOW_DIGGER_FRAMING_SPAN_METRES else largestSpan
        val metresPerPixel = (framingSpan * zoomScale * PAN_SCREEN_SCALE) / max(1, surfaceHeight).toFloat()
        panRightMetres -= deltaX * metresPerPixel
        panUpMetres += deltaY * metresPerPixel
    }

    fun zoom(scaleFactor: Float) {
        if (scaleFactor <= 0f || cameraMode != CameraMode.ORBIT) return
        zoomScale = (zoomScale / scaleFactor).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
    }

    fun resetCamera() {
        yawDegrees = 38f
        pitchDegrees = 24f
        zoomScale = 1f
        panRightMetres = 0f
        panUpMetres = 0f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.075f, 0.095f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        cacheShaderLocations()
        configureDomainToOpenGlMatrix()

        chunkMeshes.clear()
        segmentIndex.clear()
        pendingChunkBuilds.clear()
        chunkRevisions.clear()
        appliedChunkRevisions.clear()
        chunksTouchedDuringDigging.clear()
        activeDirtyChunks.clear()
        activeSliceDirty = false
        lastActiveRemeshPoint = null
        processedState = null
        sliceMeshes.clear()
        oreSliceMeshes.clear()
        machineMesh = null
        tunnelOverviewMesh = null
        grassMesh = null
        worldShellMesh = null
        oreOverlayMesh = null
        pipelineGeneration += 1L
        machineDirty = true
        tunnelOverviewDirty = true
        grassDirty = true
        worldShellDirty = true
        sliceDirty = true
        oreOverlayDirty = true
        oreSliceDirty = true
        statsWindowStartNanos = System.nanoTime()
        framesInStatsWindow = 0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val state = worldState
        processWorldChanges(state)
        consumeCompletedChunkBuilds()
        scheduleNextChunkBuilds(state)
        rebuildImmediateMeshesIfNeeded(state)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        updateMatrices(state)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelLocation, 1, false, modelMatrix, 0)
        GLES20.glUniform1f(alphaLocation, 1f)
        applyBoundsClip(state, enabled = false)

        val showCutaway = rockVisible && slices.enabledAxes.isNotEmpty() && cameraMode == CameraMode.ORBIT
        if (rockVisible) {
            applyClipUniforms(state, enabled = showCutaway)
            drawMesh(worldShellMesh)
            drawMesh(grassMesh)
            chunkMeshes.values.forEach { cached -> drawMesh(cached.mesh) }

            if (showCutaway) {
                slices.enabledAxes.forEach { axis ->
                    applyClipUniforms(state, enabled = true, excludedAxis = axis)
                    drawMesh(sliceMeshes[axis])
                }
            }
        } else {
            applyClipUniforms(state, enabled = false)
            drawMesh(grassMesh)
            if (TunnelRenderPlanner.shouldRenderOverview(rockVisible, tunnelVisible)) {
                drawMesh(tunnelOverviewMesh)
            }
        }

        // Discovered connected bodies stay opaque and obey both geological bounds and every active
        // cut plane. SEE ORE never turns these into an x-ray overlay.
        if (oreOverlayMesh != null) {
            applyBoundsClip(state, enabled = true)
            applyClipUniforms(state, enabled = showCutaway)
            drawMesh(oreOverlayMesh)
            applyBoundsClip(state, enabled = false)
        }

        // CT ore is drawn per axis. Its own clip plane is excluded so the face itself survives;
        // other enabled axes trim that face to the same retained octant as the rock.
        if (showCutaway) {
            applyBoundsClip(state, enabled = true)
            slices.enabledAxes.forEach { axis ->
                applyClipUniforms(state, enabled = true, excludedAxis = axis)
                drawMesh(oreSliceMeshes[axis])
            }
            applyBoundsClip(state, enabled = false)
        }

        if (cameraMode == CameraMode.ORBIT) {
            applyClipUniforms(state, enabled = showCutaway)
            drawMesh(machineMesh)

            machineMesh?.let { mesh ->
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glDisable(GLES20.GL_DEPTH_TEST)
                applyClipUniforms(state, enabled = false)
                GLES20.glUniform1f(alphaLocation, XRAY_MACHINE_ALPHA)
                drawMesh(mesh)
                GLES20.glUniform1f(alphaLocation, 1f)
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }

        publishPerformanceStats()
    }

    private fun processWorldChanges(state: MineWorldState) {
        val previous = processedState
        if (
            previous == null ||
            state.tunnel.points.size < previous.tunnel.points.size ||
            state.extent.chunkCount < previous.extent.chunkCount
        ) {
            resetChunkPipeline(state)
            processedState = state
            return
        }

        if (!previous.isDigging && state.isDigging) {
            chunksTouchedDuringDigging.clear()
            activeDirtyChunks.clear()
            activeSliceDirty = false
            lastActiveRemeshPoint = state.tunnel.end
        }

        val extentChanged = state.extent != previous.extent
        if (extentChanged) {
            val newKeys = ChunkMeshPlanner.allChunks(state.extent) - ChunkMeshPlanner.allChunks(previous.extent)
            newKeys.forEach { key ->
                populateSegmentIndexForChunk(state, key)
                if (ChunkMeshPlanner.requiresMesh(segmentIndex[key]?.isNotEmpty() == true)) {
                    markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
                }
            }
            worldShellDirty = true
            grassDirty = true
            sliceDirty = true
            oreOverlayDirty = true
            oreSliceDirty = true
        }

        if (state.tunnel.points.size > previous.tunnel.points.size) {
            val points = state.tunnel.points
            val startIndex = (previous.tunnel.points.size - 1).coerceAtLeast(0)
            for (index in startIndex until points.lastIndex) {
                val segment = TunnelSegment(points[index], points[index + 1])
                val affected = ChunkMeshPlanner.affectedChunks(
                    segment = segment,
                    tunnelRadiusMetres = state.tunnel.radiusMetres,
                    extent = state.extent,
                    extraPaddingMetres = ACTIVE_GRID_STEP_METRES * 1.5f,
                )
                affected.forEach { key ->
                    segmentIndex.getOrPut(key) { linkedSetOf() }.add(segment)
                }
                activeDirtyChunks += affected
                if (state.isDigging || previous.isDigging) {
                    chunksTouchedDuringDigging += affected
                }

                if (segmentTouchesAnyActiveSlice(state, segment)) {
                    activeSliceDirty = true
                }
            }

            if (state.isDigging && shouldFlushActiveRemesh(state, extentChanged)) {
                flushActiveRemesh(state)
            }
        }

        if (previous.isDigging && !state.isDigging) {
            if (activeSliceDirty) sliceDirty = true
            (chunksTouchedDuringDigging + activeDirtyChunks).forEach { key ->
                markChunkForBuild(key, REFINED_GRID_STEP_METRES)
            }
            chunksTouchedDuringDigging.clear()
            activeDirtyChunks.clear()
            activeSliceDirty = false
            lastActiveRemeshPoint = null
        }

        if (state.discoveredOreBodyIds != previous.discoveredOreBodyIds) {
            sliceDirty = true
            oreOverlayDirty = true
            oreSliceDirty = true
        }

        processedState = state
    }

    private fun segmentTouchesAnyActiveSlice(state: MineWorldState, segment: TunnelSegment): Boolean =
        slices.enabledAxes.any { axis ->
            ChunkMeshPlanner.segmentTouchesSlice(
                segment = segment,
                axis = axis,
                clipValue = MineMeshBuilder.clipValue(state, axis, slices.fraction(axis)),
                tunnelRadiusMetres = state.tunnel.radiusMetres,
                paddingMetres = 0.5f,
            )
        }

    private fun shouldFlushActiveRemesh(state: MineWorldState, extentChanged: Boolean): Boolean {
        if (extentChanged) return true
        val previousPoint = lastActiveRemeshPoint ?: return true
        return MineWorldGeometry.distance(previousPoint, state.tunnel.end) >= ACTIVE_REMESH_DISTANCE_METRES
    }

    private fun flushActiveRemesh(state: MineWorldState) {
        activeDirtyChunks.forEach { key ->
            markChunkForBuild(key, ACTIVE_GRID_STEP_METRES, replaceExisting = true)
        }
        activeDirtyChunks.clear()
        if (activeSliceDirty) {
            sliceDirty = true
            activeSliceDirty = false
        }
        lastActiveRemeshPoint = state.tunnel.end
    }

    private fun resetChunkPipeline(state: MineWorldState) {
        pipelineGeneration += 1L
        chunkMeshes.values.forEach { deleteMesh(it.mesh) }
        chunkMeshes.clear()
        segmentIndex.clear()
        pendingChunkBuilds.clear()
        chunkRevisions.clear()
        appliedChunkRevisions.clear()
        chunksTouchedDuringDigging.clear()
        activeDirtyChunks.clear()
        activeSliceDirty = false

        state.tunnel.segments.forEach { segment -> indexSegment(state, segment) }
        segmentIndex.keys.forEach { key ->
            if (ChunkMeshPlanner.requiresMesh(segmentIndex[key]?.isNotEmpty() == true)) {
                markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
            }
        }

        lastActiveRemeshPoint = if (state.isDigging) state.tunnel.end else null
        machineDirty = true
        tunnelOverviewDirty = true
        grassDirty = true
        worldShellDirty = true
        sliceDirty = true
        oreOverlayDirty = true
        oreSliceDirty = true
    }

    private fun indexSegment(state: MineWorldState, segment: TunnelSegment) {
        ChunkMeshPlanner.affectedChunks(
            segment = segment,
            tunnelRadiusMetres = state.tunnel.radiusMetres,
            extent = state.extent,
            extraPaddingMetres = ACTIVE_GRID_STEP_METRES * 1.5f,
        ).forEach { key ->
            segmentIndex.getOrPut(key) { linkedSetOf() }.add(segment)
        }
    }

    private fun populateSegmentIndexForChunk(state: MineWorldState, key: ChunkKey) {
        val threshold = state.tunnel.radiusMetres + (ACTIVE_GRID_STEP_METRES * 1.5f)
        val bounds = key.bounds()
        val matching = state.tunnel.segments.filter { segment ->
            ChunkMeshPlanner.segmentDistanceToBounds(segment, bounds) <= threshold
        }
        if (matching.isNotEmpty()) {
            segmentIndex.getOrPut(key) { linkedSetOf() }.addAll(matching)
        }
    }

    private fun markChunkForBuild(
        key: ChunkKey,
        gridStepMetres: Float,
        replaceExisting: Boolean = false,
    ) {
        if (!ChunkMeshPlanner.requiresMesh(segmentIndex[key]?.isNotEmpty() == true)) return
        val existing = pendingChunkBuilds[key]
        pendingChunkBuilds[key] = when {
            replaceExisting -> gridStepMetres
            existing == null -> gridStepMetres
            else -> minOf(existing, gridStepMetres)
        }
        chunkRevisions[key] = (chunkRevisions[key] ?: 0L) + 1L
    }

    private fun consumeCompletedChunkBuilds() {
        while (true) {
            val result = meshBuildCoordinator.pollChunk() ?: break
            if (result.pipelineGeneration != pipelineGeneration) continue

            val appliedRevision = appliedChunkRevisions[result.key] ?: 0L
            if (result.revision <= appliedRevision) continue

            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(result.mesh)
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            val previous = chunkMeshes.put(result.key, CachedChunk(uploaded, result.gridStepMetres))
            deleteMesh(previous?.mesh)
            appliedChunkRevisions[result.key] = result.revision
            lastChunkBuildMs = result.buildMs
            chunksRebuiltSinceStats += 1
        }
    }

    private fun scheduleNextChunkBuilds(state: MineWorldState) {
        var slots = meshBuildCoordinator.availableChunkSlots()
        while (slots > 0 && pendingChunkBuilds.isNotEmpty()) {
            val next = pendingChunkBuilds.entries.minByOrNull { entry ->
                val distance = MineWorldGeometry.distance(entry.key.bounds().centre, state.tunnel.end)
                val refinementPenalty = if (
                    state.isDigging && entry.value < ACTIVE_GRID_STEP_METRES - 0.01f
                ) {
                    BACKGROUND_REFINEMENT_DISTANCE_PENALTY
                } else {
                    0f
                }
                distance + refinementPenalty
            } ?: return

            val key = next.key
            val gridStep = next.value
            val revision = chunkRevisions[key] ?: return
            val reducedSegments = TunnelSegmentReducer.reduce(
                segmentIndex[key].orEmpty(),
                targetLengthMetres = max(0.8f, gridStep * 0.9f),
            )
            val accepted = meshBuildCoordinator.trySubmitChunk(
                ChunkBuildRequest(
                    pipelineGeneration = pipelineGeneration,
                    revision = revision,
                    key = key,
                    state = state,
                    tunnelSegments = reducedSegments,
                    gridStepMetres = gridStep,
                ),
            )
            if (!accepted) return
            pendingChunkBuilds.remove(key)
            slots -= 1
        }
    }

    private fun rebuildImmediateMeshesIfNeeded(state: MineWorldState) {
        if (worldShellDirty) {
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(MineMeshBuilder.buildWorldShell(state))
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(worldShellMesh)
            worldShellMesh = uploaded
            worldShellDirty = false
        }

        if (grassDirty) {
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(MineMeshBuilder.buildGrassSurface(state))
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(grassMesh)
            grassMesh = uploaded
            grassDirty = false
        }

        if (machineDirty) {
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(MineMeshBuilder.buildMachine(state))
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(machineMesh)
            machineMesh = uploaded
            machineDirty = false
        }

        if (!rockVisible && tunnelVisible && tunnelOverviewDirty) {
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(MineMeshBuilder.buildTunnelOverview(state))
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(tunnelOverviewMesh)
            tunnelOverviewMesh = uploaded
            tunnelOverviewDirty = false
        }

        if (oreOverlayDirty && !state.isDigging) {
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(OreMeshBuilder.buildDiscovered(state))
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(oreOverlayMesh)
            oreOverlayMesh = uploaded
            oreOverlayDirty = false
        }

        if (oreSliceDirty && !state.isDigging) {
            oreSliceMeshes.values.forEach(::deleteMesh)
            oreSliceMeshes.clear()
            if (rockVisible && slices.enabledAxes.isNotEmpty() && cameraMode == CameraMode.ORBIT) {
                val uploadStart = System.nanoTime()
                slices.enabledAxes.forEach { axis ->
                    oreSliceMeshes[axis] = uploadMesh(
                        OreMeshBuilder.buildSlice(
                            state = state,
                            axis = axis,
                            fraction = slices.fraction(axis),
                            flipped = slices.isFlipped(axis),
                            showAll = seeOre,
                        ),
                    )
                }
                lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            }
            oreSliceDirty = false
        }

        if (sliceDirty) {
            sliceMeshes.values.forEach(::deleteMesh)
            sliceMeshes.clear()
            if (rockVisible && slices.enabledAxes.isNotEmpty() && cameraMode == CameraMode.ORBIT) {
                val buildStart = System.nanoTime()
                slices.enabledAxes.forEach { axis ->
                    val clipValue = MineMeshBuilder.clipValue(state, axis, slices.fraction(axis))
                    val nearbySegments = linkedSetOf<TunnelSegment>()
                    ChunkMeshPlanner.chunksNearSlice(
                        extent = state.extent,
                        axis = axis,
                        clipValue = clipValue,
                        paddingMetres = state.tunnel.radiusMetres + 2f,
                    ).forEach { key ->
                        segmentIndex[key]?.let(nearbySegments::addAll)
                    }
                    val reducedSegments = TunnelSegmentReducer.reduce(
                        nearbySegments,
                        targetLengthMetres = SLICE_SEGMENT_TARGET_METRES,
                    )
                    sliceMeshes[axis] = uploadMesh(
                        MineMeshBuilder.buildCutCap(
                            // Typed ore is rendered independently; suppress the legacy generic ore
                            // colour from the rock cap without altering canonical domain state.
                            state = state.copy(discoveredOreBodyIds = emptySet()),
                            axis = axis,
                            fraction = slices.fraction(axis),
                            flipped = slices.isFlipped(axis),
                            tunnelSegments = reducedSegments,
                        ),
                    )
                }
                lastCapBuildMs = nanosToMs(System.nanoTime() - buildStart)
            }
            sliceDirty = false
        }
    }

    private fun uploadMesh(mesh: MineMesh): GlMesh? {
        if (mesh.vertices.isEmpty()) return null

        val floatBuffer = ByteBuffer
            .allocateDirect(mesh.vertices.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(mesh.vertices)
                position(0)
            }
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            mesh.vertices.size * Float.SIZE_BYTES,
            floatBuffer,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return GlMesh(bufferId = ids[0], vertexCount = mesh.vertexCount)
    }

    private fun deleteMesh(mesh: GlMesh?) {
        if (mesh == null || mesh.bufferId == 0) return
        GLES20.glDeleteBuffers(1, intArrayOf(mesh.bufferId), 0)
    }

    private fun drawMesh(mesh: GlMesh?) {
        if (mesh == null || mesh.vertexCount <= 0) return
        val strideBytes = FLOATS_PER_VERTEX * Float.SIZE_BYTES

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.bufferId)
        GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(normalLocation, 3, GLES20.GL_FLOAT, false, strideBytes, 3 * Float.SIZE_BYTES)
        GLES20.glEnableVertexAttribArray(normalLocation)
        GLES20.glVertexAttribPointer(colourLocation, 3, GLES20.GL_FLOAT, false, strideBytes, 6 * Float.SIZE_BYTES)
        GLES20.glEnableVertexAttribArray(colourLocation)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun applyClipUniforms(
        state: MineWorldState,
        enabled: Boolean,
        excludedAxis: ClipAxis? = null,
    ) {
        GLES20.glUniform3f(
            clipValuesLocation,
            MineMeshBuilder.clipValue(state, ClipAxis.X, slices.fraction(ClipAxis.X)),
            MineMeshBuilder.clipValue(state, ClipAxis.Y, slices.fraction(ClipAxis.Y)),
            MineMeshBuilder.clipValue(state, ClipAxis.Z, slices.fraction(ClipAxis.Z)),
        )
        GLES20.glUniform3f(
            clipDirectionsLocation,
            clipDirection(ClipAxis.X),
            clipDirection(ClipAxis.Y),
            clipDirection(ClipAxis.Z),
        )
        GLES20.glUniform3f(
            clipEnabledLocation,
            clipFlag(ClipAxis.X, enabled, excludedAxis),
            clipFlag(ClipAxis.Y, enabled, excludedAxis),
            clipFlag(ClipAxis.Z, enabled, excludedAxis),
        )
    }

    private fun clipDirection(axis: ClipAxis): Float = if (slices.isFlipped(axis)) -1f else 1f

    private fun clipFlag(axis: ClipAxis, enabled: Boolean, excludedAxis: ClipAxis?): Float =
        if (enabled && axis != excludedAxis && slices.isEnabled(axis)) 1f else 0f

    private fun applyBoundsClip(state: MineWorldState, enabled: Boolean) {
        val bounds = state.bounds
        GLES20.glUniform3f(boundsMinLocation, bounds.minX, bounds.minY, bounds.minZ)
        GLES20.glUniform3f(boundsMaxLocation, bounds.maxX, bounds.maxY, bounds.maxZ)
        GLES20.glUniform1f(boundsClipEnabledLocation, if (enabled) 1f else 0f)
    }

    private fun updateMatrices(state: MineWorldState) {
        when (cameraMode) {
            CameraMode.ORBIT -> updateOrbitMatrices(state)
            CameraMode.DIGGER_POV -> updateDiggerPovMatrices(state)
        }
        Matrix.multiplyMM(viewModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewModelMatrix, 0)
    }

    private fun updateOrbitMatrices(state: MineWorldState) {
        val bounds = state.bounds
        val target = if (followDigger) state.tunnel.end else bounds.centre
        val largestSpan = max(bounds.width, max(bounds.height, bounds.depth))
        val framingSpan = if (followDigger) FOLLOW_DIGGER_FRAMING_SPAN_METRES else largestSpan
        val distance = framingSpan * 2.25f * zoomScale
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cosYaw = cos(yaw).toFloat()
        val sinYaw = sin(yaw).toFloat()
        val cosPitch = cos(pitch).toFloat()
        val sinPitch = sin(pitch).toFloat()
        val horizontal = cosPitch * distance

        val baseTargetX = target.x
        val baseTargetY = -target.z
        val baseTargetZ = target.y
        val rightX = sinYaw
        val rightY = 0f
        val rightZ = -cosYaw
        val upX = -cosYaw * sinPitch
        val upY = cosPitch
        val upZ = -sinYaw * sinPitch
        val targetX = baseTargetX + (rightX * panRightMetres) + (upX * panUpMetres)
        val targetY = baseTargetY + (rightY * panRightMetres) + (upY * panUpMetres)
        val targetZ = baseTargetZ + (rightZ * panRightMetres) + (upZ * panUpMetres)

        val eyeX = targetX + (cosYaw * horizontal)
        val eyeY = targetY + (sinPitch * distance)
        val eyeZ = targetZ + (sinYaw * horizontal)

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            1f,
            0f,
        )
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            42f,
            surfaceWidth.toFloat() / surfaceHeight.toFloat(),
            0.15f,
            max(largestSpan * 12f, distance * 12f),
        )
    }

    private fun updateDiggerPovMatrices(state: MineWorldState) {
        val heading = Math.toRadians(state.machineHeadingDegrees.toDouble())
        val angle = Math.toRadians(state.verticalAngleDegrees.toDouble())
        val cosAngle = cos(angle).toFloat()
        val forward = MinePoint3D(
            x = cos(heading).toFloat() * cosAngle,
            y = sin(heading).toFloat() * cosAngle,
            z = sin(angle).toFloat(),
        )
        val machineUp = MinePoint3D(
            x = cos(heading).toFloat() * sin(angle).toFloat(),
            y = sin(heading).toFloat() * sin(angle).toFloat(),
            z = -cosAngle,
        )
        val nose = state.tunnel.end
        val eyeDomain = MinePoint3D(
            x = nose.x - (forward.x * POV_EYE_BEHIND_CUTTER_METRES) + (machineUp.x * POV_EYE_LIFT_METRES),
            y = nose.y - (forward.y * POV_EYE_BEHIND_CUTTER_METRES) + (machineUp.y * POV_EYE_LIFT_METRES),
            z = nose.z - (forward.z * POV_EYE_BEHIND_CUTTER_METRES) + (machineUp.z * POV_EYE_LIFT_METRES),
        )
        val targetDomain = MinePoint3D(
            x = nose.x + (forward.x * POV_LOOK_AHEAD_METRES),
            y = nose.y + (forward.y * POV_LOOK_AHEAD_METRES),
            z = nose.z + (forward.z * POV_LOOK_AHEAD_METRES),
        )
        val eye = domainPointToGl(eyeDomain)
        val target = domainPointToGl(targetDomain)
        val up = domainDirectionToGl(machineUp)
        val largestSpan = max(state.bounds.width, max(state.bounds.height, state.bounds.depth))

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eye.x,
            eye.y,
            eye.z,
            target.x,
            target.y,
            target.z,
            up.x,
            up.y,
            up.z,
        )
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            POV_FIELD_OF_VIEW_DEGREES,
            surfaceWidth.toFloat() / surfaceHeight.toFloat(),
            0.05f,
            max(POV_MIN_FAR_PLANE_METRES, largestSpan * 12f),
        )
    }

    private fun domainPointToGl(point: MinePoint3D): MinePoint3D = MinePoint3D(
        x = point.x,
        y = -point.z,
        z = point.y,
    )

    private fun domainDirectionToGl(direction: MinePoint3D): MinePoint3D = MinePoint3D(
        x = direction.x,
        y = -direction.z,
        z = direction.y,
    )

    private fun configureDomainToOpenGlMatrix() {
        Matrix.setIdentityM(modelMatrix, 0)
        modelMatrix[0] = 1f
        modelMatrix[1] = 0f
        modelMatrix[2] = 0f
        modelMatrix[4] = 0f
        modelMatrix[5] = 0f
        modelMatrix[6] = 1f
        modelMatrix[8] = 0f
        modelMatrix[9] = -1f
        modelMatrix[10] = 0f
    }

    private fun cacheShaderLocations() {
        mvpLocation = GLES20.glGetUniformLocation(program, "uMvp")
        modelLocation = GLES20.glGetUniformLocation(program, "uModel")
        clipValuesLocation = GLES20.glGetUniformLocation(program, "uClipValues")
        clipDirectionsLocation = GLES20.glGetUniformLocation(program, "uClipDirections")
        clipEnabledLocation = GLES20.glGetUniformLocation(program, "uClipEnabled")
        boundsMinLocation = GLES20.glGetUniformLocation(program, "uBoundsMin")
        boundsMaxLocation = GLES20.glGetUniformLocation(program, "uBoundsMax")
        boundsClipEnabledLocation = GLES20.glGetUniformLocation(program, "uBoundsClipEnabled")
        alphaLocation = GLES20.glGetUniformLocation(program, "uAlpha")
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        normalLocation = GLES20.glGetAttribLocation(program, "aNormal")
        colourLocation = GLES20.glGetAttribLocation(program, "aColor")
    }

    private fun publishPerformanceStats() {
        framesInStatsWindow += 1
        val now = System.nanoTime()
        val elapsed = now - statsWindowStartNanos
        if (elapsed < STATS_WINDOW_NANOS) return

        val fps = ((framesInStatsWindow.toDouble() * 1_000_000_000.0) / elapsed.toDouble()).toInt()
        val showCutaway = rockVisible && slices.enabledAxes.isNotEmpty() && cameraMode == CameraMode.ORBIT
        val worldVertices = if (rockVisible) {
            (worldShellMesh?.vertexCount ?: 0) +
                (grassMesh?.vertexCount ?: 0) +
                chunkMeshes.values.sumOf { it.mesh?.vertexCount ?: 0 } +
                (if (showCutaway) sliceMeshes.values.sumOf { it?.vertexCount ?: 0 } else 0)
        } else {
            (if (TunnelRenderPlanner.shouldRenderOverview(rockVisible, tunnelVisible)) {
                tunnelOverviewMesh?.vertexCount ?: 0
            } else {
                0
            }) + (grassMesh?.vertexCount ?: 0)
        }
        val oreVertices = (oreOverlayMesh?.vertexCount ?: 0) +
            (if (showCutaway) oreSliceMeshes.values.sumOf { it?.vertexCount ?: 0 } else 0)
        val machineCopies = if (cameraMode == CameraMode.ORBIT) 2 else 0
        val totalVertices = worldVertices + oreVertices + ((machineMesh?.vertexCount ?: 0) * machineCopies)
        performanceListener?.invoke(
            RenderPerformanceStats(
                framesPerSecond = fps,
                frameTimeMs = if (fps > 0) 1000f / fps.toFloat() else 0f,
                lastChunkBuildMs = lastChunkBuildMs,
                lastCapBuildMs = lastCapBuildMs,
                lastUploadMs = lastUploadMs,
                triangleCount = totalVertices / 3,
                cachedChunks = chunkMeshes.size,
                chunksRebuilt = chunksRebuiltSinceStats,
                queuedChunks = pendingChunkBuilds.size,
                meshWorkerBusy = meshBuildCoordinator.isChunkBusy(),
                capWorkerBusy = false,
            ),
        )
        framesInStatsWindow = 0
        statsWindowStartNanos = now
        chunksRebuiltSinceStats = 0
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { result ->
            GLES20.glAttachShader(result, vertexShader)
            GLES20.glAttachShader(result, fragmentShader)
            GLES20.glLinkProgram(result)
            val status = IntArray(1)
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val message = GLES20.glGetProgramInfoLog(result)
                GLES20.glDeleteProgram(result)
                throw IllegalStateException("OpenGL program link failed: $message")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("OpenGL shader compile failed: $message")
        }
    }

    private fun nanosToMs(nanos: Long): Float = nanos / 1_000_000f

    private companion object {
        const val FLOATS_PER_VERTEX = 9
        const val XRAY_MACHINE_ALPHA = 0.38f
        const val PAN_SCREEN_SCALE = 1.35f
        const val STATS_WINDOW_NANOS = 500_000_000L
        const val ACTIVE_REMESH_DISTANCE_METRES = 1.2f
        const val ACTIVE_GRID_STEP_METRES = 1.6f
        const val REFINED_GRID_STEP_METRES = 1.0f
        const val SLICE_SEGMENT_TARGET_METRES = 1.2f
        const val BACKGROUND_REFINEMENT_DISTANCE_PENALTY = 100_000f
        const val FOLLOW_DIGGER_FRAMING_SPAN_METRES = 36f
        const val MIN_ZOOM_SCALE = 0.08f
        const val MAX_ZOOM_SCALE = 5f
        const val POV_EYE_BEHIND_CUTTER_METRES = 1.45f
        const val POV_EYE_LIFT_METRES = 0.18f
        const val POV_LOOK_AHEAD_METRES = 14f
        const val POV_FIELD_OF_VIEW_DEGREES = 68f
        const val POV_MIN_FAR_PLANE_METRES = 180f

        const val VERTEX_SHADER = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            varying vec3 vDomainPosition;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                vDomainPosition = aPosition;
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vColor = aColor;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uClipValues;
            uniform vec3 uClipDirections;
            uniform vec3 uClipEnabled;
            uniform vec3 uBoundsMin;
            uniform vec3 uBoundsMax;
            uniform float uBoundsClipEnabled;
            uniform float uAlpha;
            varying vec3 vDomainPosition;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                if (uClipEnabled.x > 0.5 && ((vDomainPosition.x - uClipValues.x) * uClipDirections.x) < 0.0) {
                    discard;
                }
                if (uClipEnabled.y > 0.5 && ((vDomainPosition.y - uClipValues.y) * uClipDirections.y) < 0.0) {
                    discard;
                }
                if (uClipEnabled.z > 0.5 && ((vDomainPosition.z - uClipValues.z) * uClipDirections.z) < 0.0) {
                    discard;
                }

                if (uBoundsClipEnabled > 0.5) {
                    if (
                        vDomainPosition.x < uBoundsMin.x || vDomainPosition.x > uBoundsMax.x ||
                        vDomainPosition.y < uBoundsMin.y || vDomainPosition.y > uBoundsMax.y ||
                        vDomainPosition.z < uBoundsMin.z || vDomainPosition.z > uBoundsMax.z
                    ) {
                        discard;
                    }
                }

                vec3 lightDirection = normalize(vec3(0.35, 0.78, 0.52));
                float diffuse = 0.40 + (0.60 * abs(dot(normalize(vNormal), lightDirection)));
                gl_FragColor = vec4(vColor * diffuse, uAlpha);
            }
        """
    }
}
