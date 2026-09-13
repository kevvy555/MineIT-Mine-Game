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
    /** 12m chunks divide cleanly into 0.6m cells, keeping adjacent chunk sampling aligned. */
    const val BODY_GRID_STEP_METRES = 0.6f
    private const val SLICE_GRID_STEP_METRES = 0.45f
    private const val ORE_SLICE_OFFSET_METRES = 0.065f
    private const val WORLD_EDGE_EPSILON_METRES = 0.001f

    /**
     * Compatibility/helper path used by tests. Runtime rendering keeps these meshes chunked and
     * uploads them independently so a local cutter pass never rebuilds an entire deposit.
     */
    fun buildDiscovered(state: MineWorldState): MineMesh = combine(
        state.discoveredOreBodies.flatMap { body ->
            OreChunkPlanner.chunksForBody(body, state.bounds)
                .sortedWith(compareBy<OreChunkKey>({ it.z }, { it.y }, { it.x }))
                .mapNotNull { key ->
                    buildChunk(state, body, key).takeIf { it.vertexCount > 0 }
                }
        },
    )

    /** Builds one disposable remaining-ore cache chunk from canonical deposit - excavation fields. */
    fun buildChunk(
        state: MineWorldState,
        body: OreBody,
        key: OreChunkKey,
        gridStepMetres: Float = BODY_GRID_STEP_METRES,
    ): MineMesh {
        if (key.bodyId != body.id) return emptyMesh()
        val originalBounds = OreChunkPlanner.bodyBounds(body) ?: return emptyMesh()
        val coreBounds = OreChunkPlanner.intersect(key.bounds(), state.bounds) ?: return emptyMesh()
        if (OreChunkPlanner.intersect(coreBounds, originalBounds) == null) return emptyMesh()

        // Internal ore-chunk boundaries stay exact and aligned. Only true generated-world edges get
        // a one-cell halo so the scalar field can close the ore surface against unexplored space.
        val samplingBounds = worldEdgeSamplingBounds(
            core = coreBounds,
            world = state.bounds,
            halo = gridStepMetres,
        )
        val segments = relevantTunnelSegments(
            state.tunnel.segments,
            samplingBounds,
            state.tunnel.radiusMetres + gridStepMetres,
        )
        return ScalarFieldMesher.buildVolume(
            bounds = samplingBounds,
            gridStepMetres = gridStepMetres,
            colour = colourFor(body.type),
        ) { point ->
            min(
                MineWorldGeometry.remainingOreMargin(
                    point = point,
                    oreBody = body,
                    tunnelRadiusMetres = state.tunnel.radiusMetres,
                    excavationSegments = segments,
                ),
                boundsMargin(point, state.bounds),
            )
        }
    }

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
            val originalBounds = OreChunkPlanner.bodyBounds(body) ?: return@mapNotNull null
            if (clip < axisMinimum(originalBounds, axis) || clip > axisMaximum(originalBounds, axis)) {
                return@mapNotNull null
            }
            val planeBounds = OreChunkPlanner.intersect(originalBounds, state.bounds)
                ?: return@mapNotNull null
            val segments = relevantTunnelSegments(
                state.tunnel.segments,
                planeBounds,
                state.tunnel.radiusMetres,
            )
            ScalarFieldMesher.buildSlice(
                bounds = planeBounds,
                axis = axis,
                samplePlaneValue = clip,
                renderPlaneValue = renderPlane,
                gridStepMetres = SLICE_GRID_STEP_METRES,
                normal = normal,
                colour = colourFor(body.type),
            ) { point ->
                MineWorldGeometry.remainingOreMargin(
                    point = point,
                    oreBody = body,
                    tunnelRadiusMetres = state.tunnel.radiusMetres,
                    excavationSegments = segments,
                )
            }.takeIf { it.vertexCount > 0 }
        })
    }

    private fun worldEdgeSamplingBounds(
        core: MineWorldBounds,
        world: MineWorldBounds,
        halo: Float,
    ): MineWorldBounds = MineWorldBounds(
        minX = if (core.minX <= world.minX + WORLD_EDGE_EPSILON_METRES) core.minX - halo else core.minX,
        maxX = if (core.maxX >= world.maxX - WORLD_EDGE_EPSILON_METRES) core.maxX + halo else core.maxX,
        minY = if (core.minY <= world.minY + WORLD_EDGE_EPSILON_METRES) core.minY - halo else core.minY,
        maxY = if (core.maxY >= world.maxY - WORLD_EDGE_EPSILON_METRES) core.maxY + halo else core.maxY,
        minZ = if (core.minZ <= world.minZ + WORLD_EDGE_EPSILON_METRES) core.minZ - halo else core.minZ,
        maxZ = if (core.maxZ >= world.maxZ - WORLD_EDGE_EPSILON_METRES) core.maxZ + halo else core.maxZ,
    )

    private fun relevantTunnelSegments(
        segments: Collection<TunnelSegment>,
        bounds: MineWorldBounds,
        padding: Float,
    ): List<TunnelSegment> = segments.filter { segment ->
        max(segment.start.x, segment.end.x) + padding >= bounds.minX &&
            min(segment.start.x, segment.end.x) - padding <= bounds.maxX &&
            max(segment.start.y, segment.end.y) + padding >= bounds.minY &&
            min(segment.start.y, segment.end.y) - padding <= bounds.maxY &&
            max(segment.start.z, segment.end.z) + padding >= bounds.minZ &&
            min(segment.start.z, segment.end.z) - padding <= bounds.maxZ
    }

    private fun boundsMargin(point: MinePoint3D, bounds: MineWorldBounds) = minOf(
        point.x - bounds.minX,
        bounds.maxX - point.x,
        point.y - bounds.minY,
        bounds.maxY - point.y,
        point.z - bounds.minZ,
        bounds.maxZ - point.z,
    )

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
