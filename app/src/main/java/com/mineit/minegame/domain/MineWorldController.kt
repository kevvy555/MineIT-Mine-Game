package com.mineit.minegame.domain

import kotlin.math.cos
import kotlin.math.sin

object MineWorldController {
    const val CHUNK_SIZE_METRES = 24f
    const val DIG_STEP_METRES = 8f
    const val ROCK_DENSITY_TONNES_PER_CUBIC_METRE = 2.7f
    const val MIN_DIP_DEGREES = -70f
    const val MAX_DIP_DEGREES = 70f

    private const val EXTENT_MARGIN_METRES = 6f

    fun setAzimuth(state: MineWorldState, degrees: Float): MineWorldState =
        state.copy(azimuthDegrees = degrees.coerceIn(0f, 360f))

    fun setDip(state: MineWorldState, degrees: Float): MineWorldState =
        state.copy(dipDegrees = degrees.coerceIn(MIN_DIP_DEGREES, MAX_DIP_DEGREES))

    fun dig(state: MineWorldState): MineWorldState {
        val start = state.tunnel.end
        val azimuth = Math.toRadians(state.azimuthDegrees.toDouble())
        val dip = Math.toRadians(state.dipDegrees.toDouble())
        val horizontal = cos(dip).toFloat() * DIG_STEP_METRES
        val minimumCentreDepth = state.tunnel.radiusMetres * 0.65f

        val target = MinePoint3D(
            x = start.x + (cos(azimuth).toFloat() * horizontal),
            y = start.y + (sin(azimuth).toFloat() * horizontal),
            z = (start.z + (sin(dip).toFloat() * DIG_STEP_METRES))
                .coerceAtLeast(minimumCentreDepth),
        )

        val segmentLength = MineWorldGeometry.distance(start, target)
        if (segmentLength < 0.25f) return state

        val tunnel = state.tunnel.copy(points = state.tunnel.points + target)
        val extent = expandExtent(state.extent, target)
        val volume = state.excavatedVolumeCubicMetres +
            cylinderVolume(state.tunnel.radiusMetres, segmentLength)

        return state.copy(
            tunnel = tunnel,
            extent = extent,
            excavatedVolumeCubicMetres = volume,
            exposedOreSegments = MineWorldGeometry.exposedOreSegments(
                tunnel = tunnel,
                oreBody = state.oreBody,
            ),
        )
    }

    fun reset(): MineWorldState = MineWorldState()

    private fun expandExtent(
        current: ChunkExtent,
        point: MinePoint3D,
    ): ChunkExtent {
        var minX = current.minChunkX
        var maxX = current.maxChunkX
        var minY = current.minChunkY
        var maxY = current.maxChunkY
        var maxZ = current.maxChunkZ

        while (point.x < (minX * CHUNK_SIZE_METRES) + EXTENT_MARGIN_METRES) minX -= 1
        while (point.x > ((maxX + 1) * CHUNK_SIZE_METRES) - EXTENT_MARGIN_METRES) maxX += 1
        while (point.y < (minY * CHUNK_SIZE_METRES) + EXTENT_MARGIN_METRES) minY -= 1
        while (point.y > ((maxY + 1) * CHUNK_SIZE_METRES) - EXTENT_MARGIN_METRES) maxY += 1
        while (point.z > ((maxZ + 1) * CHUNK_SIZE_METRES) - EXTENT_MARGIN_METRES) maxZ += 1

        return current.copy(
            minChunkX = minX,
            maxChunkX = maxX,
            minChunkY = minY,
            maxChunkY = maxY,
            maxChunkZ = maxZ,
        )
    }
}
