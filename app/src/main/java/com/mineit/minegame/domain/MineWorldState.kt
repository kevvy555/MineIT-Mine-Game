package com.mineit.minegame.domain

import kotlin.math.PI

data class MinePoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class TunnelSegment(
    val start: MinePoint3D,
    val end: MinePoint3D,
)

data class ChunkExtent(
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkY: Int,
    val maxChunkY: Int,
    val minChunkZ: Int,
    val maxChunkZ: Int,
) {
    val chunkCount: Int
        get() = (maxChunkX - minChunkX + 1) *
            (maxChunkY - minChunkY + 1) *
            (maxChunkZ - minChunkZ + 1)

    fun bounds(chunkSizeMetres: Float): MineWorldBounds = MineWorldBounds(
        minX = minChunkX * chunkSizeMetres,
        maxX = (maxChunkX + 1) * chunkSizeMetres,
        minY = minChunkY * chunkSizeMetres,
        maxY = (maxChunkY + 1) * chunkSizeMetres,
        minZ = minChunkZ * chunkSizeMetres,
        maxZ = (maxChunkZ + 1) * chunkSizeMetres,
    )
}

data class MineWorldBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val depth: Float get() = maxZ - minZ
    val centre: MinePoint3D
        get() = MinePoint3D(
            x = (minX + maxX) * 0.5f,
            y = (minY + maxY) * 0.5f,
            z = (minZ + maxZ) * 0.5f,
        )
}

data class TunnelGeometry(
    val points: List<MinePoint3D>,
    val radiusMetres: Float,
) {
    val end: MinePoint3D get() = points.last()

    val segments: List<TunnelSegment>
        get() = points.zipWithNext { start, end -> TunnelSegment(start, end) }
}

data class OreBodyNode(
    val centre: MinePoint3D,
    val radiusMetres: Float,
)

data class MineWorldState(
    val tunnel: TunnelGeometry = MineWorldContent.initialTunnel,
    val extent: ChunkExtent = MineWorldContent.initialExtent,
    val oreBodies: List<OreBody> = MineWorldContent.oreBodies,
    val discoveredOreBodyIds: Set<String> = emptySet(),
    val headingDegrees: Float = 25f,
    val verticalAngleDegrees: Float = 55f,
    val steering: Float = 0f,
    val digSpeedMultiplier: Float = 1f,
    val isDigging: Boolean = false,
    val excavatedVolumeCubicMetres: Float = 0f,
) {
    val bounds: MineWorldBounds
        get() = extent.bounds(MineWorldController.CHUNK_SIZE_METRES)

    val depthMetres: Float
        get() = tunnel.end.z.coerceAtLeast(0f)

    val wasteRockTonnes: Float
        get() = excavatedVolumeCubicMetres * MineWorldController.ROCK_DENSITY_TONNES_PER_CUBIC_METRE

    val oreBodyDiscovered: Boolean
        get() = discoveredOreBodyIds.isNotEmpty()

    val discoveredOreBodies: List<OreBody>
        get() = oreBodies.filter { it.id in discoveredOreBodyIds }

    /**
     * The original single-body POC renderer still asks for one body when colouring detailed
     * tunnel walls. This derived view is not geology state: typed [oreBodies] remain canonical.
     * Full typed bodies are rendered separately and this simply preserves wall colouring for the
     * first discovered deposit until that small renderer path is removed.
     */
    val oreBody: List<OreBodyNode>
        get() = discoveredOreBodies.firstOrNull()?.nodes.orEmpty()

    /**
     * While stopped, the steering slider previews the direction that will be committed when
     * digging starts. While digging, headingDegrees is the physical direction and steering is
     * a turn-rate input.
     */
    val machineHeadingDegrees: Float
        get() = normalizeHeading(
            if (isDigging) {
                headingDegrees
            } else {
                headingDegrees + (steering * MineWorldController.STEERING_PREVIEW_DEGREES)
            },
        )
}

object MineWorldContent {
    const val ORE_SEED = 53_260_010

    val initialExtent = ChunkExtent(
        minChunkX = -1,
        maxChunkX = 0,
        minChunkY = -1,
        maxChunkY = 0,
        minChunkZ = 0,
        maxChunkZ = 1,
    )

    val initialTunnel = TunnelGeometry(
        points = listOf(MinePoint3D(0f, 0f, 0f)),
        radiusMetres = 3.2f,
    )

    val oreBodies: List<OreBody> = OreGeologyGenerator.generate(ORE_SEED)
}

internal fun cylinderVolume(radiusMetres: Float, lengthMetres: Float): Float =
    (PI * radiusMetres * radiusMetres * lengthMetres).toFloat()

internal fun normalizeHeading(degrees: Float): Float {
    val normalized = degrees % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}
