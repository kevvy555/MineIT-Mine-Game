package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreBody
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

internal data class OreChunkBuildRequest(
    val pipelineGeneration: Long,
    val revision: Long,
    val key: OreChunkKey,
    val state: MineWorldState,
    val body: OreBody,
    val gridStepMetres: Float,
)

internal data class OreChunkBuildResult(
    val pipelineGeneration: Long,
    val revision: Long,
    val key: OreChunkKey,
    val mesh: MineMesh,
    val buildMs: Float,
)

/**
 * Background polygonisation for excavation and remaining-ore cache chunks.
 *
 * Ore gets its own single worker so a newly discovered/depleted deposit cannot block the two rock
 * workers that keep tunnel walls responsive. CT faces remain immediate because they are 2D fields.
 */
internal class AsyncMeshBuildCoordinator {
    private val chunkExecutor = Executors.newFixedThreadPool(CHUNK_WORKER_COUNT) { runnable ->
        Thread(runnable, "mineit-chunk-mesher").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val oreExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mineit-ore-mesher").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val chunkInFlight = AtomicInteger(0)
    private val oreInFlight = AtomicInteger(0)
    private val completedChunks = ConcurrentLinkedQueue<ChunkBuildResult>()
    private val completedOreChunks = ConcurrentLinkedQueue<OreChunkBuildResult>()

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

    fun trySubmitOreChunk(request: OreChunkBuildRequest): Boolean {
        if (!oreInFlight.compareAndSet(0, 1)) return false
        oreExecutor.execute {
            try {
                val start = System.nanoTime()
                val mesh = OreMeshBuilder.buildChunk(
                    state = request.state,
                    body = request.body,
                    key = request.key,
                    gridStepMetres = request.gridStepMetres,
                )
                completedOreChunks.add(
                    OreChunkBuildResult(
                        pipelineGeneration = request.pipelineGeneration,
                        revision = request.revision,
                        key = request.key,
                        mesh = mesh,
                        buildMs = nanosToMs(System.nanoTime() - start),
                    ),
                )
            } finally {
                oreInFlight.set(0)
            }
        }
        return true
    }

    fun pollChunk(): ChunkBuildResult? = completedChunks.poll()

    fun pollOreChunk(): OreChunkBuildResult? = completedOreChunks.poll()

    fun isChunkBusy(): Boolean = chunkInFlight.get() > 0

    fun isOreBusy(): Boolean = oreInFlight.get() > 0

    fun availableChunkSlots(): Int = (CHUNK_WORKER_COUNT - chunkInFlight.get()).coerceAtLeast(0)

    fun availableOreSlots(): Int = if (oreInFlight.get() == 0) 1 else 0

    private fun nanosToMs(nanos: Long): Float = nanos / 1_000_000f

    private companion object {
        const val CHUNK_WORKER_COUNT = 2
    }
}
