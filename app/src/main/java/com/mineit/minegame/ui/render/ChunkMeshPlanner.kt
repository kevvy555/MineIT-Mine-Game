package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.ChunkExtent
import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal data class ChunkKey(
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun bounds(): MineWorldBounds {
        val size = MineWorldController.CHUNK_SIZE_METRES
        return MineWorldBounds(
            minX = x * size,
            maxX = (x + 1) * size,
            minY = y * size,
            maxY = (y + 1) * size,
            minZ = z * size,
            maxZ = (z + 1) * size,
        )
    }
}

internal object ChunkMeshPlanner {
    fun allChunks(extent: ChunkExtent): Set<ChunkKey> = buildSet {
        for (z in extent.minChunkZ..extent.maxChunkZ) {
            for (y in extent.minChunkY..extent.maxChunkY) {
                for (x in extent.minChunkX..extent.maxChunkX) {
                    add(ChunkKey(x, y, z))
                }
            }
        }
    }

    fun affectedChunks(
        segment: TunnelSegment,
        tunnelRadiusMetres: Float,
        extent: ChunkExtent,
        extraPaddingMetres: Float,
    ): Set<ChunkKey> {
        val padding = tunnelRadiusMetres + extraPaddingMetres
        val minX = min(segment.start.x, segment.end.x) - padding
        val maxX = max(segment.start.x, segment.end.x) + padding
        val minY = min(segment.start.y, segment.end.y) - padding
        val maxY = max(segment.start.y, segment.end.y) + padding
        val minZ = min(segment.start.z, segment.end.z) - padding
        val maxZ = max(segment.start.z, segment.end.z) + padding
        val size = MineWorldController.CHUNK_SIZE_METRES

        val fromX = floor(minX / size).toInt().coerceAtLeast(extent.minChunkX)
        val toX = floor(maxX / size).toInt().coerceAtMost(extent.maxChunkX)
        val fromY = floor(minY / size).toInt().coerceAtLeast(extent.minChunkY)
        val toY = floor(maxY / size).toInt().coerceAtMost(extent.maxChunkY)
        val fromZ = floor(minZ / size).toInt().coerceAtLeast(extent.minChunkZ)
        val toZ = floor(maxZ / size).toInt().coerceAtMost(extent.maxChunkZ)

        if (fromX > toX || fromY > toY || fromZ > toZ) return emptySet()

        return buildSet {
            for (z in fromZ..toZ) {
                for (y in fromY..toY) {
                    for (x in fromX..toX) {
                        add(ChunkKey(x, y, z))
                    }
                }
            }
        }
    }

    fun chunksWhoseBoundaryChanged(
        previous: ChunkExtent,
        current: ChunkExtent,
    ): Set<ChunkKey> = buildSet {
        if (current.minChunkX < previous.minChunkX) {
            for (z in previous.minChunkZ..previous.maxChunkZ) {
                for (y in previous.minChunkY..previous.maxChunkY) add(ChunkKey(previous.minChunkX, y, z))
            }
        }
        if (current.maxChunkX > previous.maxChunkX) {
            for (z in previous.minChunkZ..previous.maxChunkZ) {
                for (y in previous.minChunkY..previous.maxChunkY) add(ChunkKey(previous.maxChunkX, y, z))
            }
        }
        if (current.minChunkY < previous.minChunkY) {
            for (z in previous.minChunkZ..previous.maxChunkZ) {
                for (x in previous.minChunkX..previous.maxChunkX) add(ChunkKey(x, previous.minChunkY, z))
            }
        }
        if (current.maxChunkY > previous.maxChunkY) {
            for (z in previous.minChunkZ..previous.maxChunkZ) {
                for (x in previous.minChunkX..previous.maxChunkX) add(ChunkKey(x, previous.maxChunkY, z))
            }
        }
        if (current.minChunkZ < previous.minChunkZ) {
            for (y in previous.minChunkY..previous.maxChunkY) {
                for (x in previous.minChunkX..previous.maxChunkX) add(ChunkKey(x, y, previous.minChunkZ))
            }
        }
        if (current.maxChunkZ > previous.maxChunkZ) {
            for (y in previous.minChunkY..previous.maxChunkY) {
                for (x in previous.minChunkX..previous.maxChunkX) add(ChunkKey(x, y, previous.maxChunkZ))
            }
        }
    }

    fun segmentTouchesSlice(
        segment: TunnelSegment,
        axis: ClipAxis,
        clipValue: Float,
        tunnelRadiusMetres: Float,
        paddingMetres: Float,
    ): Boolean {
        val padding = tunnelRadiusMetres + paddingMetres
        val (a, b) = when (axis) {
            ClipAxis.X -> segment.start.x to segment.end.x
            ClipAxis.Y -> segment.start.y to segment.end.y
            ClipAxis.Z -> segment.start.z to segment.end.z
        }
        return clipValue >= min(a, b) - padding && clipValue <= max(a, b) + padding
    }

    fun chunksNearSlice(
        extent: ChunkExtent,
        axis: ClipAxis,
        clipValue: Float,
        paddingMetres: Float,
    ): Set<ChunkKey> {
        val size = MineWorldController.CHUNK_SIZE_METRES
        fun intersects(index: Int): Boolean {
            val min = index * size
            val max = (index + 1) * size
            return clipValue >= min - paddingMetres && clipValue <= max + paddingMetres
        }

        return allChunks(extent).filterTo(mutableSetOf()) { key ->
            when (axis) {
                ClipAxis.X -> intersects(key.x)
                ClipAxis.Y -> intersects(key.y)
                ClipAxis.Z -> intersects(key.z)
            }
        }
    }

    fun segmentDistanceToBounds(segment: TunnelSegment, bounds: MineWorldBounds): Float {
        fun axisDistance(a: Float, b: Float, min: Float, max: Float): Float {
            val low = kotlin.math.min(a, b)
            val high = kotlin.math.max(a, b)
            return when {
                high < min -> min - high
                low > max -> low - max
                else -> 0f
            }
        }

        val dx = axisDistance(segment.start.x, segment.end.x, bounds.minX, bounds.maxX)
        val dy = axisDistance(segment.start.y, segment.end.y, bounds.minY, bounds.maxY)
        val dz = axisDistance(segment.start.z, segment.end.z, bounds.minZ, bounds.maxZ)
        return kotlin.math.sqrt((dx * dx) + (dy * dy) + (dz * dz))
    }

    fun pointInsideExtent(point: MinePoint3D, extent: ChunkExtent): Boolean {
        val bounds = extent.bounds(MineWorldController.CHUNK_SIZE_METRES)
        return point.x in bounds.minX..bounds.maxX &&
            point.y in bounds.minY..bounds.maxY &&
            point.z in bounds.minZ..bounds.maxZ
    }
}
