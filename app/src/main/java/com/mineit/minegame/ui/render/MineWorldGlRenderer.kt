package com.mineit.minegame.ui.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
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
    private var capDirty = true

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
    private val chunksTouchedDuringDigging = linkedSetOf<ChunkKey>()
    private var processedState: MineWorldState? = null

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
    private var lastChunksRebuilt = 0

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
            capDirty = true
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
            capDirty = true
        }
        clipAxis = axis
        clipFraction = boundedFraction
        clipFlipped = flipped
        clipEnabled = enabled
    }

    fun setPerformanceListener(listener: ((RenderPerformanceStats) -> Unit)?) {
        performanceListener = listener
    }

    fun rotate(deltaX: Float, deltaY: Float) {
        yawDegrees = (yawDegrees + (deltaX * 0.35f)) % 360f
        pitchDegrees = (pitchDegrees + (deltaY * 0.30f)).coerceIn(-82f, 82f)
    }

    fun zoom(scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        zoomScale = (zoomScale / scaleFactor).coerceIn(0.42f, 2.8f)
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
        chunksTouchedDuringDigging.clear()
        processedState = null
        capMesh = null
        machineMesh = null
        capDirty = true
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
        processChunkBuildQueue(state)
        rebuildCapIfNeeded(state)
        rebuildMachineIfNeeded(state)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        updateMatrices(state)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelLocation, 1, false, modelMatrix, 0)
        applyClipUniforms(state, enabled = clipEnabled)
        GLES20.glUniform1f(alphaLocation, 1f)

        chunkMeshes.values.forEach { cached -> drawMesh(cached.mesh) }
        drawMesh(capMesh)
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
        }

        if (state.extent != previous.extent) {
            val previousKeys = ChunkMeshPlanner.allChunks(previous.extent)
            val currentKeys = ChunkMeshPlanner.allChunks(state.extent)
            val newKeys = currentKeys - previousKeys

            newKeys.forEach { key ->
                populateSegmentIndexForChunk(state, key)
                markChunkForBuild(key, MineMeshBuilder.ACTIVE_GRID_STEP_METRES)
            }
            ChunkMeshPlanner.chunksWhoseBoundaryChanged(previous.extent, state.extent).forEach { key ->
                markChunkForBuild(key, MineMeshBuilder.ACTIVE_GRID_STEP_METRES)
            }
            capDirty = true
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
                    extraPaddingMetres = MineMeshBuilder.ACTIVE_GRID_STEP_METRES * 1.5f,
                )
                affected.forEach { key ->
                    segmentIndex.getOrPut(key) { linkedSetOf() }.add(segment)
                    markChunkForBuild(key, MineMeshBuilder.ACTIVE_GRID_STEP_METRES)
                }
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
                    capDirty = true
                }
            }
        }

        if (previous.isDigging && !state.isDigging) {
            chunksTouchedDuringDigging.forEach { key ->
                markChunkForBuild(key, MineMeshBuilder.REFINED_GRID_STEP_METRES)
            }
            chunksTouchedDuringDigging.clear()
        }

        if (state.oreBodyDiscovered != previous.oreBodyDiscovered) {
            capDirty = true
        }

        processedState = state
    }

    private fun resetChunkPipeline(state: MineWorldState) {
        chunkMeshes.values.forEach { deleteMesh(it.mesh) }
        chunkMeshes.clear()
        deleteMesh(capMesh)
        capMesh = null
        segmentIndex.clear()
        pendingChunkBuilds.clear()
        chunksTouchedDuringDigging.clear()

        state.tunnel.segments.forEach { segment -> indexSegment(state, segment) }
        ChunkMeshPlanner.allChunks(state.extent).forEach { key ->
            markChunkForBuild(key, MineMeshBuilder.ACTIVE_GRID_STEP_METRES)
        }
        capDirty = true
        machineDirty = true
    }

    private fun indexSegment(state: MineWorldState, segment: TunnelSegment) {
        ChunkMeshPlanner.affectedChunks(
            segment = segment,
            tunnelRadiusMetres = state.tunnel.radiusMetres,
            extent = state.extent,
            extraPaddingMetres = MineMeshBuilder.ACTIVE_GRID_STEP_METRES * 1.5f,
        ).forEach { key ->
            segmentIndex.getOrPut(key) { linkedSetOf() }.add(segment)
        }
    }

    private fun populateSegmentIndexForChunk(state: MineWorldState, key: ChunkKey) {
        val threshold = state.tunnel.radiusMetres + (MineMeshBuilder.ACTIVE_GRID_STEP_METRES * 1.5f)
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
    }

    private fun processChunkBuildQueue(state: MineWorldState) {
        val maxBuilds = if (state.isDigging) MAX_ACTIVE_CHUNK_BUILDS_PER_FRAME else MAX_IDLE_CHUNK_BUILDS_PER_FRAME
        var rebuilt = 0
        var buildNanos = 0L
        var uploadNanos = 0L

        while (rebuilt < maxBuilds && pendingChunkBuilds.isNotEmpty()) {
            val entry = pendingChunkBuilds.entries.first()
            val key = entry.key
            val gridStep = entry.value
            pendingChunkBuilds.remove(key)

            val buildStart = System.nanoTime()
            val mesh = MineMeshBuilder.buildChunk(
                state = state,
                key = key,
                tunnelSegments = segmentIndex[key].orEmpty(),
                gridStepMetres = gridStep,
            )
            buildNanos += System.nanoTime() - buildStart

            val uploadStart = System.nanoTime()
            val uploaded = uploadMesh(mesh)
            uploadNanos += System.nanoTime() - uploadStart

            val previous = chunkMeshes.put(key, CachedChunk(uploaded, gridStep))
            deleteMesh(previous?.mesh)
            rebuilt += 1
        }

        if (rebuilt > 0) {
            lastChunkBuildMs = nanosToMs(buildNanos)
            lastUploadMs = nanosToMs(uploadNanos)
            lastChunksRebuilt = rebuilt
        }
    }

    private fun rebuildCapIfNeeded(state: MineWorldState) {
        if (!capDirty) return

        val start = System.nanoTime()
        val newMesh = if (clipEnabled) {
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
            MineMeshBuilder.buildCutCap(
                state = state,
                axis = clipAxis,
                fraction = clipFraction,
                flipped = clipFlipped,
                tunnelSegments = nearbySegments,
            )
        } else {
            MineMesh(FloatArray(0), 0)
        }
        lastCapBuildMs = nanosToMs(System.nanoTime() - start)

        val uploadStart = System.nanoTime()
        val uploaded = uploadMesh(newMesh)
        lastUploadMs = nanosToMs(System.nanoTime() - uploadStart)
        deleteMesh(capMesh)
        capMesh = uploaded
        capDirty = false
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
        val bounds = state.bounds
        val centre = bounds.centre
        val targetX = centre.x
        val targetY = -centre.z
        val targetZ = centre.y
        val largestSpan = max(bounds.width, max(bounds.height, bounds.depth))
        val distance = largestSpan * 2.25f * zoomScale
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
            0.5f,
            largestSpan * 10f,
        )
        Matrix.multiplyMM(viewModelMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewModelMatrix, 0)
    }

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
        val totalVertices = rockVertices +
            (capMesh?.vertexCount ?: 0) +
            ((machineMesh?.vertexCount ?: 0) * 2)
        performanceListener?.invoke(
            RenderPerformanceStats(
                framesPerSecond = fps,
                frameTimeMs = if (fps > 0) 1000f / fps.toFloat() else 0f,
                lastChunkBuildMs = lastChunkBuildMs,
                lastCapBuildMs = lastCapBuildMs,
                lastUploadMs = lastUploadMs,
                triangleCount = totalVertices / 3,
                cachedChunks = chunkMeshes.size,
                chunksRebuilt = lastChunksRebuilt,
            ),
        )
        framesInStatsWindow = 0
        statsWindowStartNanos = now
        lastChunksRebuilt = 0
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
        const val XRAY_MACHINE_ALPHA = 0.32f
        const val MAX_ACTIVE_CHUNK_BUILDS_PER_FRAME = 1
        const val MAX_IDLE_CHUNK_BUILDS_PER_FRAME = 2
        const val STATS_WINDOW_NANOS = 750_000_000L

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
