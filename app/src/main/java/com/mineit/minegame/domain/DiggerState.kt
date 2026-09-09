package com.mineit.minegame.domain

import kotlin.math.hypot

data class WorldPoint(
    val xMetres: Float,
    val yMetres: Float,
) {
    fun distanceTo(other: WorldPoint): Float = hypot(
        xMetres - other.xMetres,
        yMetres - other.yMetres,
    )
}

data class DiggerState(
    val position: WorldPoint = WorldPoint(0f, 0f),
    val headingDegrees: Float = 90f,
    val targetHeadingDegrees: Float = 90f,
    val isDigging: Boolean = false,
    val excavatedPath: List<WorldPoint> = listOf(WorldPoint(0f, 0f)),
) {
    val depthMetres: Float
        get() = position.yMetres.coerceAtLeast(0f)
}
