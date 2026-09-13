package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldGeometry
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

/** Disposable render-cache partitioning around the canonical domain deposit field. */
internal object OreChunkPlanner {
    const val CHUNK_SIZE_METRES = 12f

    fun chunksForBody(body: OreBody, worldBounds: MineWorldBounds): Set<OreChunkKey> {
        val bodyBounds = bodyBounds(body) ?: return emptySet()
        val clipped = intersect(bodyBounds, worldBounds) ?: return emptySet()
        return keysForBounds(body.id, clipped).filterTo(linkedSetOf()) { key ->
            bodyEnvelopeTouchesBounds(body, key.bounds())
        }
    }

    fun affectedChunks(
        segment: TunnelSegment,
        tunnelRadiusMetres: Float,
        body: OreBody,
        worldBounds: MineWorldBounds,
        extraPaddingMetres: Float,
    ): Set<OreChunkKey> {
        val bodyBounds = bodyBounds(body) ?: return emptySet()
        val padding = tunnelRadiusMetres + extraPaddingMetres
        val cutBounds = MineWorldBounds(
            minX = min(segment.start.x, segment.end.x) - padding,
            maxX = max(segment.start.x, segment.end.x) + padding,
            minY = min(segment.start.y, segment.end.y) - padding,
            maxY = max(segment.start.y, segment.end.y) + padding,
            minZ = min(segment.start.z, segment.end.z) - padding,
            maxZ = max(segment.start.z, segment.end.z) + padding,
        )
        val clippedToBody = intersect(cutBounds, bodyBounds) ?: return emptySet()
        val clippedToWorld = intersect(clippedToBody, worldBounds) ?: return emptySet()
        return keysForBounds(body.id, clippedToWorld).filterTo(linkedSetOf()) { key ->
            bodyEnvelopeTouchesBoth(body, cutBounds, key.bounds())
        }
    }

    fun bodyBounds(body: OreBody): MineWorldBounds? = MineWorldGeometry.oreBodyBounds(body)

    fun chunkCentre(key: OreChunkKey): MinePoint3D = key.bounds().centre

    private fun bodyEnvelopeTouchesBounds(body: OreBody, bounds: MineWorldBounds): Boolean {
        if (body.nodes.size == 1) return intersects(nodeBounds(body.nodes.single()), bounds)
        for (index in 0 until body.nodes.lastIndex) {
            if (intersects(segmentEnvelope(body.nodes[index], body.nodes[index + 1]), bounds)) {
                return true
            }
        }
        return false
    }

    private fun bodyEnvelopeTouchesBoth(
        body: OreBody,
        first: MineWorldBounds,
        second: MineWorldBounds,
    ): Boolean {
        if (body.nodes.size == 1) {
            val envelope = nodeBounds(body.nodes.single())
            return intersects(envelope, first) && intersects(envelope, second)
        }
        for (index in 0 until body.nodes.lastIndex) {
            val envelope = segmentEnvelope(body.nodes[index], body.nodes[index + 1])
            if (intersects(envelope, first) && intersects(envelope, second)) return true
        }
        return false
    }

    private fun segmentEnvelope(start: OreBodyNode, end: OreBodyNode): MineWorldBounds {
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

    private fun nodeBounds(node: OreBodyNode) = MineWorldBounds(
        minX = node.centre.x - node.radiusMetres,
        maxX = node.centre.x + node.radiusMetres,
        minY = node.centre.y - node.radiusMetres,
        maxY = node.centre.y + node.radiusMetres,
        minZ = node.centre.z - node.radiusMetres,
        maxZ = node.centre.z + node.radiusMetres,
    )

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

    private fun intersects(a: MineWorldBounds, b: MineWorldBounds): Boolean =
        a.maxX >= b.minX && a.minX <= b.maxX &&
            a.maxY >= b.minY && a.minY <= b.maxY &&
            a.maxZ >= b.minZ && a.minZ <= b.maxZ

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
