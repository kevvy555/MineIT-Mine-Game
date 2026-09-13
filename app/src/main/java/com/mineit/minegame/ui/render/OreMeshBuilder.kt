package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreType
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.max
import kotlin.math.min

internal object OreMeshBuilder {
    private const val BODY_GRID_STEP_METRES = 0.75f
    private const val SLICE_GRID_STEP_METRES = 0.45f
    private const val ORE_SLICE_OFFSET_METRES = 0.065f

    fun buildDiscovered(state: MineWorldState): MineMesh = combine(
        state.discoveredOreBodies.mapNotNull { body ->
            buildRemainingBodyMesh(state, body).takeIf { it.vertexCount > 0 }
        },
    )

    fun buildSlice(
        state: MineWorldState,
        axis: ClipAxis,
        fraction: Float,
        flipped: Boolean,
        showAll: Boolean,
    ): MineMesh {
        val bodies = if (showAll) state.oreBodies else state.discoveredOreBodies
        val clip = MineMeshBuilder.clipValue(state, axis, fraction)
        val normal = capNormal(axis, flipped)
        val renderPlane = clip + axisCoordinate(normal, axis) * ORE_SLICE_OFFSET_METRES
        return combine(bodies.mapNotNull { body ->
            val originalBounds = bodyBounds(body) ?: return@mapNotNull null
            if (clip < axisMinimum(originalBounds, axis) || clip > axisMaximum(originalBounds, axis)) return@mapNotNull null
            val planeBounds = intersectBounds(originalBounds, state.bounds) ?: return@mapNotNull null
            val segments = relevantTunnelSegments(state.tunnel.segments, originalBounds, state.tunnel.radiusMetres)
            ScalarFieldMesher.buildSlice(
                bounds = planeBounds,
                axis = axis,
                samplePlaneValue = clip,
                renderPlaneValue = renderPlane,
                gridStepMetres = SLICE_GRID_STEP_METRES,
                normal = normal,
                colour = colourFor(body.type),
            ) { point ->
                MineWorldGeometry.remainingOreMargin(point, body, state.tunnel.radiusMetres, segments)
            }.takeIf { it.vertexCount > 0 }
        })
    }

    private fun buildRemainingBodyMesh(state: MineWorldState, body: OreBody): MineMesh {
        val originalBounds = bodyBounds(body) ?: return emptyMesh()
        val samplingBounds = intersectBounds(
            expandBounds(originalBounds, BODY_GRID_STEP_METRES),
            expandBounds(state.bounds, BODY_GRID_STEP_METRES),
        ) ?: return emptyMesh()
        val segments = relevantTunnelSegments(
            state.tunnel.segments,
            originalBounds,
            state.tunnel.radiusMetres + BODY_GRID_STEP_METRES,
        )
        return ScalarFieldMesher.buildVolume(
            bounds = samplingBounds,
            gridStepMetres = BODY_GRID_STEP_METRES,
            colour = colourFor(body.type),
        ) { point ->
            min(
                MineWorldGeometry.remainingOreMargin(point, body, state.tunnel.radiusMetres, segments),
                boundsMargin(point, state.bounds),
            )
        }
    }

    private fun bodyBounds(body: OreBody): MineWorldBounds? {
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

    private fun relevantTunnelSegments(
        segments: Collection<TunnelSegment>, bounds: MineWorldBounds, padding: Float,
    ): List<TunnelSegment> = segments.filter { segment ->
        max(segment.start.x, segment.end.x) + padding >= bounds.minX &&
            min(segment.start.x, segment.end.x) - padding <= bounds.maxX &&
            max(segment.start.y, segment.end.y) + padding >= bounds.minY &&
            min(segment.start.y, segment.end.y) - padding <= bounds.maxY &&
            max(segment.start.z, segment.end.z) + padding >= bounds.minZ &&
            min(segment.start.z, segment.end.z) - padding <= bounds.maxZ
    }

    private fun boundsMargin(point: MinePoint3D, bounds: MineWorldBounds) = minOf(
        point.x - bounds.minX, bounds.maxX - point.x,
        point.y - bounds.minY, bounds.maxY - point.y,
        point.z - bounds.minZ, bounds.maxZ - point.z,
    )

    private fun expandBounds(bounds: MineWorldBounds, amount: Float) = MineWorldBounds(
        bounds.minX - amount, bounds.maxX + amount,
        bounds.minY - amount, bounds.maxY + amount,
        bounds.minZ - amount, bounds.maxZ + amount,
    )

    private fun intersectBounds(a: MineWorldBounds, b: MineWorldBounds): MineWorldBounds? {
        val minX = max(a.minX, b.minX)
        val maxX = min(a.maxX, b.maxX)
        val minY = max(a.minY, b.minY)
        val maxY = min(a.maxY, b.maxY)
        val minZ = max(a.minZ, b.minZ)
        val maxZ = min(a.maxZ, b.maxZ)
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) return null
        return MineWorldBounds(minX, maxX, minY, maxY, minZ, maxZ)
    }

    private fun axisMinimum(bounds: MineWorldBounds, axis: ClipAxis) = when (axis) {
        ClipAxis.X -> bounds.minX
        ClipAxis.Y -> bounds.minY
        ClipAxis.Z -> bounds.minZ
    }

    private fun axisMaximum(bounds: MineWorldBounds, axis: ClipAxis) = when (axis) {
        ClipAxis.X -> bounds.maxX
        ClipAxis.Y -> bounds.maxY
        ClipAxis.Z -> bounds.maxZ
    }

    private fun capNormal(axis: ClipAxis, flipped: Boolean): MinePoint3D {
        val sign = if (flipped) 1f else -1f
        return when (axis) {
            ClipAxis.X -> MinePoint3D(sign, 0f, 0f)
            ClipAxis.Y -> MinePoint3D(0f, sign, 0f)
            ClipAxis.Z -> MinePoint3D(0f, 0f, sign)
        }
    }

    private fun axisCoordinate(point: MinePoint3D, axis: ClipAxis) = when (axis) {
        ClipAxis.X -> point.x
        ClipAxis.Y -> point.y
        ClipAxis.Z -> point.z
    }

    private fun colourFor(type: OreType) = when (type) {
        OreType.GOLD -> floatArrayOf(0.96f, 0.72f, 0.10f)
        OreType.SILVER -> floatArrayOf(0.72f, 0.79f, 0.86f)
        OreType.COPPER -> floatArrayOf(0.82f, 0.36f, 0.13f)
    }

    private fun combine(meshes: List<MineMesh>): MineMesh {
        if (meshes.isEmpty()) return emptyMesh()
        val vertices = FloatArray(meshes.sumOf { it.vertices.size })
        var offset = 0
        meshes.forEach { mesh ->
            mesh.vertices.copyInto(vertices, destinationOffset = offset)
            offset += mesh.vertices.size
        }
        return MineMesh(vertices, vertices.size / 9)
    }

    private fun emptyMesh() = MineMesh(FloatArray(0), 0)
}
