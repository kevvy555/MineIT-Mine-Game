package com.mineit.minegame.domain

data class MinePoint2D(
    val x: Float,
    val y: Float,
)

data class MinePoint3D(
    val x: Float,
    val y: Float,
    val depthMetres: Float,
)

data class MineLevel(
    val id: String,
    val name: String,
    val depthMetres: Float,
    val drifts: List<List<MinePoint2D>>,
    val oreTrace: List<MinePoint2D>,
)

data class OreBodySample(
    val centre: MinePoint3D,
    val radiusMetres: Float,
)

data class MinePrismState(
    val cutDepthMetres: Float = 210f,
    val exploded: Boolean = false,
    val selectedLevelId: String? = null,
    val levels: List<MineLevel> = MinePrismContent.levels,
    val oreBody: List<OreBodySample> = MinePrismContent.oreBody,
) {
    val selectedLevel: MineLevel?
        get() = levels.firstOrNull { it.id == selectedLevelId }
}

object MinePrismContent {
    val levels = listOf(
        MineLevel(
            id = "level-90",
            name = "Level 1",
            depthMetres = 90f,
            drifts = listOf(
                listOf(MinePoint2D(-62f, 0f), MinePoint2D(62f, 0f)),
                listOf(MinePoint2D(-22f, 0f), MinePoint2D(-22f, -34f)),
                listOf(MinePoint2D(28f, 0f), MinePoint2D(28f, 30f)),
            ),
            oreTrace = listOf(
                MinePoint2D(18f, -28f),
                MinePoint2D(30f, -14f),
                MinePoint2D(42f, 4f),
                MinePoint2D(53f, 18f),
            ),
        ),
        MineLevel(
            id = "level-180",
            name = "Level 2",
            depthMetres = 180f,
            drifts = listOf(
                listOf(MinePoint2D(-72f, 0f), MinePoint2D(70f, 0f)),
                listOf(MinePoint2D(-34f, 0f), MinePoint2D(-34f, 38f)),
                listOf(MinePoint2D(12f, 0f), MinePoint2D(12f, -42f)),
                listOf(MinePoint2D(46f, 0f), MinePoint2D(46f, 28f)),
            ),
            oreTrace = listOf(
                MinePoint2D(-12f, -34f),
                MinePoint2D(4f, -20f),
                MinePoint2D(20f, -8f),
                MinePoint2D(38f, 8f),
                MinePoint2D(51f, 25f),
            ),
        ),
        MineLevel(
            id = "level-285",
            name = "Level 3",
            depthMetres = 285f,
            drifts = listOf(
                listOf(MinePoint2D(-64f, 0f), MinePoint2D(66f, 0f)),
                listOf(MinePoint2D(-18f, 0f), MinePoint2D(-18f, -40f)),
                listOf(MinePoint2D(30f, 0f), MinePoint2D(30f, 42f)),
            ),
            oreTrace = listOf(
                MinePoint2D(-48f, -20f),
                MinePoint2D(-28f, -8f),
                MinePoint2D(-10f, 3f),
                MinePoint2D(8f, 18f),
                MinePoint2D(20f, 32f),
            ),
        ),
    )

    val oreBody = listOf(
        OreBodySample(MinePoint3D(42f, -24f, 35f), 12f),
        OreBodySample(MinePoint3D(35f, -18f, 80f), 14f),
        OreBodySample(MinePoint3D(25f, -12f, 125f), 17f),
        OreBodySample(MinePoint3D(14f, -5f, 175f), 19f),
        OreBodySample(MinePoint3D(2f, 5f, 225f), 17f),
        OreBodySample(MinePoint3D(-14f, 12f, 280f), 15f),
        OreBodySample(MinePoint3D(-30f, 20f, 335f), 11f),
    )
}
