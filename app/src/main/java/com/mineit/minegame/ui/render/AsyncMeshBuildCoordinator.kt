package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.TunnelSegment
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * Only excavation meshes need asynchronous CPU polygonisation now. The world shell and CT slice
 * are deliberately cheap, immediate meshes so they can track world growth and slider movement on
 * the render thread without queueing behind historical geology work.
 */
internal class AsyncMeshBuildCoordinator {
    private val chunkExecutor = Executors.newFixedThreadPool(CHUNK_WORKER_COUNT) { runnable ->
        Thread(runnable, "mineit-chunk-mesher").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val chunkInFlight = AtomicInteger(0)
    private val completedChunks = ConcurrentLinkedQueue<ChunkBuildResult>()

    fun trySubmitChunk(request: ChunkBuildRequest): Boolean {
        while (true) {
            val current = chunkInFlight.get()
            if (current >= CHUNK_WORKER_COUNT) return false
            if (chunkInFlight.compareAndSet(current, current + 1)) break
        }

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
                chunkInFlight.decrementAndGet()
            }
        }
        return true
    }

    fun pollChunk(): ChunkBuildResult? = completedChunks.poll()

    fun isChunkBusy(): Boolean = chunkInFlight.get() > 0

    fun availableChunkSlots(): Int = (CHUNK_WORKER_COUNT - chunkInFlight.get()).coerceAtLeast(0)

    private fun nanosToMs(nanos: Long): Float = nanos / 1_000_000f

    private companion object {
        const val CHUNK_WORKER_COUNT = 2
    }
}
