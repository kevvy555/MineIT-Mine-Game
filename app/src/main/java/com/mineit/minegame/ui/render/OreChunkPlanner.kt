package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreBodyNode
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Fixed-size render chunks for remaining-ore meshes. */
internal data class OreChunkKey(
    val bodyId: String,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun bounds(): MineWorldBounds {
        val size = OreChunkPlanner.CHUNK_SIZE_METRES
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

/**
 * Maps immutable deposits and cutter movement onto small render-only ore chunks.
 *
 * The domain still owns the deposit and excavation fields. These keys are only disposable cache
 * partitions so one cutter pass does not force the whole deposit through marching tetrahedra.
 * Chunk selection follows the individual deposit segments rather than the deposit's enclosing box,
 * preventing empty corners of a long/curved body from entering the ore mesh queue.
 */
internal object OreChunkPlanner {
    const val CHUNK_SIZE_METRES = 12f

    fun chunksForBody(body: OreBody, worldBounds: MineWorldBounds): Set<OreChunkKey> {
        if (body.nodes.size < 2) return emptySet()
        return buildSet {
            var index = 0
            while (index < body.nodes.lastIndex) {
                val localBounds = oreSegmentBounds(body.nodes[index], body.nodes[index + 1])
                intersect(localBounds, worldBounds)?.let { clipped ->
                    addAll(keysForBounds(body.id, clipped))
                }
                index += 1
            }
        }
    }

    fun affectedChunks(
        segment: TunnelSegment,
        tunnelRadiusMetres: Float,
        body: OreBody,
        worldBounds: MineWorldBounds,
        extraPaddingMetres: Float,
    ): Set<OreChunkKey> {
        if (body.nodes.size < 2) return emptySet()
        val padding = tunnelRadiusMetres + extraPaddingMetres
        val cutBounds = MineWorldBounds(
            minX = min(segment.start.x, segment.end.x) - padding,
            maxX = max(segment.start.x, segment.end.x) + padding,
            minY = min(segment.start.y, segment.end.y) - padding,
            maxY = max(segment.start.y, segment.end.y) + padding,
            minZ = min(segment.start.z, segment.end.z) - padding,
            maxZ = max(segment.start.z, segment.end.z) + padding,
        )

        return buildSet {
            var index = 0
            while (index < body.nodes.lastIndex) {
                val localOreBounds = oreSegmentBounds(body.nodes[index], body.nodes[index + 1])
                val cutAgainstOre = intersect(cutBounds, localOreBounds)
                val clippedToWorld = cutAgainstOre?.let { intersect(it, worldBounds) }
                if (clippedToWorld != null) {
                    addAll(keysForBounds(body.id, clippedToWorld))
                }
                index += 1
            }
        }
    }

    fun bodyBounds(body: OreBody): MineWorldBounds? {
        if (body.nodes.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        body.nodes.forEach { node ->
            minX = min(minX, node.centre.x - node.radiusMetres)
            maxX = max(maxX, node.centre.x + node.radiusMetres)
            minY = min(minY, node.centre.y - node.radiusMetres)
            maxY = max(maxY, node.centre.y + node.radiusMetres)
            minZ = min(minZ, node.centre.z - node.radiusMetres)
            maxZ = max(maxZ, node.centre.z + node.radiusMetres)
        }
        return MineWorldBounds(minX, maxX, minY, maxY, minZ, maxZ)
    }

    fun chunkCentre(key: OreChunkKey): MinePoint3D = key.bounds().centre

    private fun oreSegmentBounds(start: OreBodyNode, end: OreBodyNode): MineWorldBounds {
        val radius = max(start.radiusMetres, end.radiusMetres)
        return MineWorldBounds(
            minX = min(start.centre.x, end.centre.x) - radius,
            maxX = max(start.centre.x, end.centre.x) + radius,
            minY = min(start.centre.y, end.centre.y) - radius,
            maxY = max(start.centre.y, end.centre.y) + radius,
            minZ = min(start.centre.z, end.centre.z) - radius,
            maxZ = max(start.centre.z, end.centre.z) + radius,
        )
    }

    private fun keysForBounds(bodyId: String, bounds: MineWorldBounds): Set<OreChunkKey> {
        val size = CHUNK_SIZE_METRES
        val epsilon = 0.0001f
        val fromX = floor(bounds.minX / size).toInt()
        val toX = floor((bounds.maxX - epsilon) / size).toInt()
        val fromY = floor(bounds.minY / size).toInt()
        val toY = floor((bounds.maxY - epsilon) / size).toInt()
        val fromZ = floor(bounds.minZ / size).toInt()
        val toZ = floor((bounds.maxZ - epsilon) / size).toInt()
        if (fromX > toX || fromY > toY || fromZ > toZ) return emptySet()

        return buildSet {
            for (z in fromZ..toZ) {
                for (y in fromY..toY) {
                    for (x in fromX..toX) {
                        add(OreChunkKey(bodyId = bodyId, x = x, y = y, z = z))
                    }
                }
            }
        }
    }

    internal fun intersect(a: MineWorldBounds, b: MineWorldBounds): MineWorldBounds? {
        val minX = max(a.minX, b.minX)
        val maxX = min(a.maxX, b.maxX)
        val minY = max(a.minY, b.minY)
        val maxY = min(a.maxY, b.maxY)
        val minZ = max(a.minZ, b.minZ)
        val maxZ = min(a.maxZ, b.maxZ)
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return null
        return MineWorldBounds(minX, maxX, minY, maxY, minZ, maxZ)
    }
}
