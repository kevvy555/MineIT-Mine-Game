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
import java.util.concurrent.atomic.AtomicLong
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

internal class MineWorldGlRenderer : GLSurfaceView.Renderer {
    @Volatile
    private var worldState = MineWorldState()

    @Volatile
    private var clipAxis = ClipAxis.X

    @Volatile
    private var clipFraction = 0.18f

    @Volatile
    private var clipFlipped = false

    @Volatile
    private var clipEnabled = true

    @Volatile
    private var machineDirty = true

    @Volatile
    private var performanceListener: ((RenderPerformanceStats) -> Unit)? = null

    @Volatile
    private var yawDegrees = 38f

    @Volatile
    private var pitchDegrees = 24f

    @Volatile
    private var zoomScale = 1f

    @Volatile
    private var followDigger = false

    @Volatile
    private var cameraMode = CameraMode.ORBIT

    @Volatile
    private var capBuildPending = true

    private val capRevision = AtomicLong(1L)
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
    private var activeCapDirty = false
    private var lastActiveRemeshPoint: MinePoint3D? = null
    private var processedState: MineWorldState? = null
    private var pipelineGeneration = 1L
    private var appliedCapRevision = 0L

    private var capMesh: GlMesh? = null
    private var machineMesh: GlMesh? = null

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewModelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var mvpLocation = -1
    private var modelLocation = -1
    private var clipAxisLocation = -1
    private var clipValueLocation = -1
    private var clipDirectionLocation = -1
    private var clipEnabledLocation = -1
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
        if (state.oreBodyDiscovered != previous.oreBodyDiscovered) {
            markCapDirty()
        }
    }

    fun setClip(axis: ClipAxis, fraction: Float, flipped: Boolean, enabled: Boolean) {
        val boundedFraction = fraction.coerceIn(0f, 1f)
        if (
            axis != clipAxis ||
            boundedFraction != clipFraction ||
            flipped != clipFlipped ||
            enabled != clipEnabled
        ) {
            markCapDirty()
        }
        clipAxis = axis
        clipFraction = boundedFraction
        clipFlipped = flipped
        clipEnabled = enabled
    }

    fun setFollowDigger(enabled: Boolean) {
        followDigger = enabled
    }

    fun setCameraMode(mode: CameraMode) {
        cameraMode = mode
    }

    fun setPerformanceListener(listener: ((RenderPerformanceStats) -> Unit)?) {
        performanceListener = listener
    }

    fun rotate(deltaX: Float, deltaY: Float) {
        if (cameraMode != CameraMode.ORBIT) return
        yawDegrees = (yawDegrees + (deltaX * 0.35f)) % 360f
        pitchDegrees = (pitchDegrees + (deltaY * 0.30f)).coerceIn(-82f, 82f)
    }

    fun zoom(scaleFactor: Float) {
        if (scaleFactor <= 0f || cameraMode != CameraMode.ORBIT) return
        zoomScale = (zoomScale / scaleFactor).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
    }

    fun resetCamera() {
        yawDegrees = 38f
        pitchDegrees = 24f
        zoomScale = 1f
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
        activeCapDirty = false
        lastActiveRemeshPoint = null
        processedState = null
        capMesh = null
        machineMesh = null
        pipelineGeneration += 1L
        capBuildPending = true
        capRevision.incrementAndGet()
        appliedCapRevision = 0L
        machineDirty = true
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
        consumeCompletedCapBuilds()
        scheduleNextChunkBuild(state)
        scheduleCapBuild(state)
        rebuildMachineIfNeeded(state)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        updateMatrices(state)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelLocation, 1, false, modelMatrix, 0)
        GLES20.glUniform1f(alphaLocation, 1f)

        val showCutaway = clipEnabled && cameraMode == CameraMode.ORBIT
        applyClipUniforms(state, enabled = showCutaway)
        chunkMeshes.values.forEach { cached -> drawMesh(cached.mesh) }

        if (showCutaway) {
            // The cap mesh already lies at the cut plane. Keep the latest completed cap visible
            // while a newer asynchronous section is building instead of flashing a hollow volume.
            applyClipUniforms(state, enabled = false)
            drawMesh(capMesh)
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
            activeCapDirty = false
            lastActiveRemeshPoint = state.tunnel.end
        }

        val extentChanged = state.extent != previous.extent
        if (extentChanged) {
            val previousKeys = ChunkMeshPlanner.allChunks(previous.extent)
            val currentKeys = ChunkMeshPlanner.allChunks(state.extent)
            val newKeys = currentKeys - previousKeys

            newKeys.forEach { key ->
                populateSegmentIndexForChunk(state, key)
                if (ChunkMeshPlanner.requiresMesh(key, state.extent, segmentIndex[key]?.isNotEmpty() == true)) {
                    markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
                }
            }

            ChunkMeshPlanner.chunksWhoseBoundaryChanged(previous.extent, state.extent).forEach { key ->
                if (ChunkMeshPlanner.requiresMesh(key, state.extent, segmentIndex[key]?.isNotEmpty() == true)) {
                    markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
                } else {
                    removeCachedChunk(key)
                }
            }
            markCapDirty()
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

                val currentClipValue = MineMeshBuilder.clipValue(state, clipAxis, clipFraction)
                if (
                    ChunkMeshPlanner.segmentTouchesSlice(
                        segment = segment,
                        axis = clipAxis,
                        clipValue = currentClipValue,
                        tunnelRadiusMetres = state.tunnel.radiusMetres,
                        paddingMetres = 1f,
                    )
                ) {
                    activeCapDirty = true
                }
            }

            if (state.isDigging && shouldFlushActiveRemesh(state, extentChanged)) {
                flushActiveRemesh(state)
            }
        }

        if (previous.isDigging && !state.isDigging) {
            if (activeCapDirty) markCapDirty()
            (chunksTouchedDuringDigging + activeDirtyChunks).forEach { key ->
                markChunkForBuild(key, REFINED_GRID_STEP_METRES)
            }
            chunksTouchedDuringDigging.clear()
            activeDirtyChunks.clear()
            activeCapDirty = false
            lastActiveRemeshPoint = null
        }

        if (state.oreBodyDiscovered != previous.oreBodyDiscovered) {
            markCapDirty()
        }

        processedState = state
    }

    private fun shouldFlushActiveRemesh(state: MineWorldState, extentChanged: Boolean): Boolean {
        if (extentChanged) return true
        val previousPoint = lastActiveRemeshPoint ?: return true
        return MineWorldGeometry.distance(previousPoint, state.tunnel.end) >= ACTIVE_REMESH_DISTANCE_METRES
    }

    private fun flushActiveRemesh(state: MineWorldState) {
        activeDirtyChunks.forEach { key ->
            markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
        }
        activeDirtyChunks.clear()
        if (activeCapDirty) {
            markCapDirty()
            activeCapDirty = false
        }
        lastActiveRemeshPoint = state.tunnel.end
    }

    private fun resetChunkPipeline(state: MineWorldState) {
        pipelineGeneration += 1L
        chunkMeshes.values.forEach { deleteMesh(it.mesh) }
        chunkMeshes.clear()
        deleteMesh(capMesh)
        capMesh = null
        segmentIndex.clear()
        pendingChunkBuilds.clear()
        chunkRevisions.clear()
        appliedChunkRevisions.clear()
        chunksTouchedDuringDigging.clear()
        activeDirtyChunks.clear()
        activeCapDirty = false
        appliedCapRevision = 0L

        state.tunnel.segments.forEach { segment -> indexSegment(state, segment) }
        ChunkMeshPlanner.allChunks(state.extent).forEach { key ->
            if (ChunkMeshPlanner.requiresMesh(key, state.extent, segmentIndex[key]?.isNotEmpty() == true)) {
                markChunkForBuild(key, ACTIVE_GRID_STEP_METRES)
            }
        }
        lastActiveRemeshPoint = if (state.isDigging) state.tunnel.end else null
        markCapDirty()
        machineDirty = true
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

    private fun markChunkForBuild(key: ChunkKey, gridStepMetres: Float) {
        val existing = pendingChunkBuilds[key]
        pendingChunkBuilds[key] = if (existing == null) gridStepMetres else minOf(existing, gridStepMetres)
        chunkRevisions[key] = (chunkRevisions[key] ?: 0L) + 1L
    }

    private fun removeCachedChunk(key: ChunkKey) {
        pendingChunkBuilds.remove(key)
        chunkRevisions[key] = (chunkRevisions[key] ?: 0L) + 1L
        appliedChunkRevisions.remove(key)
        deleteMesh(chunkMeshes.remove(key)?.mesh)
    }

    private fun consumeCompletedChunkBuilds() {
        while (true) {
            val result = meshBuildCoordinator.pollChunk() ?: break
            if (result.pipelineGeneration != pipelineGeneration) continue

            // A newer request may already be queued for this same chunk. The completed result is
            // still useful: showing it progressively prevents excavation appearing frozen until the
            // digger stops. Never let an older result overwrite geometry already shown on-screen.
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

    private fun scheduleNextChunkBuild(state: MineWorldState) {
        if (meshBuildCoordinator.isChunkBusy() || pendingChunkBuilds.isEmpty()) return

        val next = pendingChunkBuilds.entries.minByOrNull { entry ->
            MineWorldGeometry.distance(entry.key.bounds().centre, state.tunnel.end)
        } ?: return
        val key = next.key
        val gridStep = next.value
        val revision = chunkRevisions[key] ?: return
        val accepted = meshBuildCoordinator.trySubmitChunk(
            ChunkBuildRequest(
                pipelineGeneration = pipelineGeneration,
                revision = revision,
                key = key,
                state = state,
                tunnelSegments = segmentIndex[key].orEmpty().toList(),
                gridStepMetres = gridStep,
            ),
        )
        if (accepted) pendingChunkBuilds.remove(key)
    }

    private fun consumeCompletedCapBuilds() {
        while (true) {
            val result = meshBuildCoordinator.pollCap() ?: break
            if (result.pipelineGeneration != pipelineGeneration) continue
            if (result.axis != clipAxis || result.flipped != clipFlipped) continue
            if (result.revision <= appliedCapRevision) continue

            // As with chunk meshes, an intermediate CT result is better than freezing the section
            // until finger movement stops. Later revisions keep replacing it as they complete.
            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(result.mesh)
            lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
            deleteMesh(capMesh)
            capMesh = uploaded
            appliedCapRevision = result.revision
            lastCapBuildMs = result.buildMs
        }
    }

    private fun scheduleCapBuild(state: MineWorldState) {
        if (!capBuildPending) return

        if (!clipEnabled) {
            deleteMesh(capMesh)
            capMesh = null
            capBuildPending = false
            return
        }
        if (meshBuildCoordinator.isCapBusy()) return

        val clipValue = MineMeshBuilder.clipValue(state, clipAxis, clipFraction)
        val nearbySegments = linkedSetOf<TunnelSegment>()
        ChunkMeshPlanner.chunksNearSlice(
            extent = state.extent,
            axis = clipAxis,
            clipValue = clipValue,
            paddingMetres = state.tunnel.radiusMetres + 2f,
        ).forEach { key ->
            segmentIndex[key]?.let(nearbySegments::addAll)
        }

        val revision = capRevision.get()
        val accepted = meshBuildCoordinator.trySubmitCap(
            CapBuildRequest(
                pipelineGeneration = pipelineGeneration,
                revision = revision,
                state = state,
                axis = clipAxis,
                fraction = clipFraction,
                flipped = clipFlipped,
                tunnelSegments = nearbySegments.toList(),
            ),
        )
        if (accepted) capBuildPending = false
    }

    private fun markCapDirty() {
        capRevision.incrementAndGet()
        capBuildPending = true
    }

    private fun rebuildMachineIfNeeded(state: MineWorldState) {
        if (!machineDirty) return
        val mesh = MineMeshBuilder.buildMachine(state)
        val uploadStart = System.nanoTime()
        val uploaded = uploadMesh(mesh)
        lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
        deleteMesh(machineMesh)
        machineMesh = uploaded
        machineDirty = false
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

    private fun applyClipUniforms(state: MineWorldState, enabled: Boolean) {
        val clipValue = MineMeshBuilder.clipValue(state, clipAxis, clipFraction)
        GLES20.glUniform1i(clipAxisLocation, clipAxis.ordinal)
        GLES20.glUniform1f(clipValueLocation, clipValue)
        GLES20.glUniform1f(clipDirectionLocation, if (clipFlipped) -1f else 1f)
        GLES20.glUniform1f(clipEnabledLocation, if (enabled) 1f else 0f)
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
        val targetX = target.x
        val targetY = -target.z
        val targetZ = target.y
        val largestSpan = max(bounds.width, max(bounds.height, bounds.depth))
        val framingSpan = if (followDigger) FOLLOW_DIGGER_FRAMING_SPAN_METRES else largestSpan
        val distance = framingSpan * 2.25f * zoomScale
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val horizontal = cos(pitch).toFloat() * distance

        val eyeX = targetX + (cos(yaw).toFloat() * horizontal)
        val eyeY = targetY + (sin(pitch).toFloat() * distance)
        val eyeZ = targetZ + (sin(yaw).toFloat() * horizontal)

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
        clipAxisLocation = GLES20.glGetUniformLocation(program, "uClipAxis")
        clipValueLocation = GLES20.glGetUniformLocation(program, "uClipValue")
        clipDirectionLocation = GLES20.glGetUniformLocation(program, "uClipDirection")
        clipEnabledLocation = GLES20.glGetUniformLocation(program, "uClipEnabled")
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
        val rockVertices = chunkMeshes.values.sumOf { it.mesh?.vertexCount ?: 0 }
        val machineCopies = if (cameraMode == CameraMode.ORBIT) 2 else 0
        val totalVertices = rockVertices +
            (if (clipEnabled && cameraMode == CameraMode.ORBIT) capMesh?.vertexCount ?: 0 else 0) +
            ((machineMesh?.vertexCount ?: 0) * machineCopies)
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
                capWorkerBusy = meshBuildCoordinator.isCapBusy(),
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
        const val STATS_WINDOW_NANOS = 500_000_000L
        const val ACTIVE_REMESH_DISTANCE_METRES = 1.2f
        const val ACTIVE_GRID_STEP_METRES = 1.6f
        const val REFINED_GRID_STEP_METRES = 1.0f
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
            uniform int uClipAxis;
            uniform float uClipValue;
            uniform float uClipDirection;
            uniform float uClipEnabled;
            uniform float uAlpha;
            varying vec3 vDomainPosition;
            varying vec3 vNormal;
            varying vec3 vColor;

            void main() {
                float coordinate = vDomainPosition.z;
                if (uClipAxis == 0) {
                    coordinate = vDomainPosition.x;
                } else if (uClipAxis == 1) {
                    coordinate = vDomainPosition.y;
                }

                if (uClipEnabled > 0.5 && ((coordinate - uClipValue) * uClipDirection) < 0.0) {
                    discard;
                }

                vec3 lightDirection = normalize(vec3(0.35, 0.78, 0.52));
                float diffuse = 0.40 + (0.60 * abs(dot(normalize(vNormal), lightDirection)));
                gl_FragColor = vec4(vColor * diffuse, uAlpha);
            }
        """
    }
}
