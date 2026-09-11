package com.mineit.minegame.domain

import kotlin.math.PI

data class MinePoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
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
}

data class OreBodyNode(
    val centre: MinePoint3D,
    val radiusMetres: Float,
)

data class MineWorldState(
    val tunnel: TunnelGeometry = MineWorldContent.initialTunnel,
    val extent: ChunkExtent = MineWorldContent.initialExtent,
    val oreBody: List<OreBodyNode> = MineWorldContent.oreBody,
    val azimuthDegrees: Float = 32f,
    val dipDegrees: Float = 12f,
    val excavatedVolumeCubicMetres: Float = MineWorldContent.initialExcavatedVolume,
    val exposedOreSegments: Set<Int> = MineWorldContent.initialExposedOreSegments,
) {
    val bounds: MineWorldBounds
        get() = extent.bounds(MineWorldController.CHUNK_SIZE_METRES)

    val wasteRockTonnes: Float
        get() = excavatedVolumeCubicMetres * MineWorldController.ROCK_DENSITY_TONNES_PER_CUBIC_METRE
}

object MineWorldContent {
    val initialExtent = ChunkExtent(
        minChunkX = -1,
        maxChunkX = 0,
        minChunkY = -1,
        maxChunkY = 0,
        minChunkZ = 0,
        maxChunkZ = 1,
    )

    val initialTunnel = TunnelGeometry(
        points = listOf(
            MinePoint3D(0f, 0f, 0f),
            MinePoint3D(0f, 0f, 24f),
        ),
        radiusMetres = 3.2f,
    )

    val oreBody = listOf(
        OreBodyNode(MinePoint3D(3.5f, -1.5f, 18f), 5.0f),
        OreBodyNode(MinePoint3D(5f, 1f, 26f), 5.5f),
        OreBodyNode(MinePoint3D(10f, 6f, 34f), 6.0f),
        OreBodyNode(MinePoint3D(16f, 12f, 42f), 6.5f),
        OreBodyNode(MinePoint3D(21f, 18f, 50f), 5.5f),
        OreBodyNode(MinePoint3D(18f, 25f, 60f), 4.5f),
        OreBodyNode(MinePoint3D(10f, 32f, 69f), 3.8f),
    )

    val initialExcavatedVolume: Float = MineWorldGeometry.sweptVolume(initialTunnel)

    val initialExposedOreSegments: Set<Int> = MineWorldGeometry.exposedOreSegments(
        tunnel = initialTunnel,
        oreBody = oreBody,
    )
}

internal fun cylinderVolume(radiusMetres: Float, lengthMetres: Float): Float =
    (PI * radiusMetres * radiusMetres * lengthMetres).toFloat()
