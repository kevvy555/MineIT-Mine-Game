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
    val headingDegrees: Float = 25f,
    val verticalAngleDegrees: Float = 55f,
    val steering: Float = 0f,
    val isDigging: Boolean = false,
    val excavatedVolumeCubicMetres: Float = 0f,
    val exposedOreSegments: Set<Int> = emptySet(),
    val oreBodyDiscovered: Boolean = false,
) {
    val bounds: MineWorldBounds
        get() = extent.bounds(MineWorldController.CHUNK_SIZE_METRES)

    val depthMetres: Float
        get() = tunnel.end.z.coerceAtLeast(0f)

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
        points = listOf(MinePoint3D(0f, 0f, 0f)),
        radiusMetres = 3.2f,
    )

    // One connected hard-rock vein. It is hidden until excavation first intersects it.
    // After discovery, slices may reveal the connected body throughout generated geology.
    val oreBody = listOf(
        OreBodyNode(MinePoint3D(11f, 5f, 17f), 4.6f),
        OreBodyNode(MinePoint3D(15f, 9f, 25f), 5.0f),
        OreBodyNode(MinePoint3D(20f, 12f, 34f), 5.6f),
        OreBodyNode(MinePoint3D(24f, 10f, 44f), 6.2f),
        OreBodyNode(MinePoint3D(21f, 3f, 54f), 5.4f),
        OreBodyNode(MinePoint3D(14f, -5f, 65f), 4.7f),
        OreBodyNode(MinePoint3D(6f, -12f, 76f), 4.0f),
    )
}

internal fun cylinderVolume(radiusMetres: Float, lengthMetres: Float): Float =
    (PI * radiusMetres * radiusMetres * lengthMetres).toFloat()
