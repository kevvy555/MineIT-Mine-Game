package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.TunnelSegment
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class ChunkBuildRequest(
    val pipelineGeneration: Long,
    val revision: Long,
    val key: ChunkKey,
    val state: MineWorldState,
    val tunnelSegments: List<TunnelSegment>,
    val gridStepMetres: Float,
)

internal data class ChunkBuildResult(
    val pipelineGeneration: Long,
    val revision: Long,
    val key: ChunkKey,
    val gridStepMetres: Float,
    val mesh: MineMesh,
    val buildMs: Float,
)

internal data class CapBuildRequest(
    val pipelineGeneration: Long,
    val revision: Long,
    val state: MineWorldState,
    val axis: ClipAxis,
    val fraction: Float,
    val flipped: Boolean,
    val tunnelSegments: List<TunnelSegment>,
)

internal data class CapBuildResult(
    val pipelineGeneration: Long,
    val revision: Long,
    val mesh: MineMesh,
    val buildMs: Float,
)

/**
 * CPU-only mesh work is deliberately kept off the GLSurfaceView render thread. OpenGL uploads
 * remain on the GL thread, but expensive scalar-field sampling/polygonisation can no longer stall
 * camera movement or the x-ray machine every time excavation advances.
 */
internal class AsyncMeshBuildCoordinator {
    private val chunkExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mineit-chunk-mesher").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val capExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mineit-cap-mesher").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val chunkInFlight = AtomicBoolean(false)
    private val capInFlight = AtomicBoolean(false)
    private val completedChunks = ConcurrentLinkedQueue<ChunkBuildResult>()
    private val completedCaps = ConcurrentLinkedQueue<CapBuildResult>()

    fun trySubmitChunk(request: ChunkBuildRequest): Boolean {
        if (!chunkInFlight.compareAndSet(false, true)) return false
        chunkExecutor.execute {
            try {
                val start = System.nanoTime()
                val mesh = MineMeshBuilder.buildChunk(
                    state = request.state,
                    key = request.key,
                    tunnelSegments = request.tunnelSegments,
                    gridStepMetres = request.gridStepMetres,
                )
                completedChunks.add(
                    ChunkBuildResult(
                        pipelineGeneration = request.pipelineGeneration,
                        revision = request.revision,
                        key = request.key,
                        gridStepMetres = request.gridStepMetres,
                        mesh = mesh,
                        buildMs = nanosToMs(System.nanoTime() - start),
                    ),
                )
            } finally {
                chunkInFlight.set(false)
            }
        }
        return true
    }

    fun trySubmitCap(request: CapBuildRequest): Boolean {
        if (!capInFlight.compareAndSet(false, true)) return false
        capExecutor.execute {
            try {
                val start = System.nanoTime()
                val mesh = MineMeshBuilder.buildCutCap(
                    state = request.state,
                    axis = request.axis,
                    fraction = request.fraction,
                    flipped = request.flipped,
                    tunnelSegments = request.tunnelSegments,
                )
                completedCaps.add(
                    CapBuildResult(
                        pipelineGeneration = request.pipelineGeneration,
                        revision = request.revision,
                        mesh = mesh,
                        buildMs = nanosToMs(System.nanoTime() - start),
                    ),
                )
            } finally {
                capInFlight.set(false)
            }
        }
        return true
    }

    fun pollChunk(): ChunkBuildResult? = completedChunks.poll()

    fun pollCap(): CapBuildResult? = completedCaps.poll()

    fun isChunkBusy(): Boolean = chunkInFlight.get()

    fun isCapBusy(): Boolean = capInFlight.get()

    private fun nanosToMs(nanos: Long): Float = nanos / 1_000_000f
}
