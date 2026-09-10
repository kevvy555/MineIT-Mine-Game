package com.mineit.minegame.domain

import kotlin.math.cos
import kotlin.math.sin

object DigSimulation {
    const val MIN_HEADING_DEGREES = 20f
    const val MAX_HEADING_DEGREES = 160f

    private const val DIG_SPEED_METRES_PER_SECOND = 4f
    private const val TURN_SPEED_DEGREES_PER_SECOND = 55f
    private const val PATH_POINT_SPACING_METRES = 0.20f

    fun start(state: DiggerState): DiggerState = state.copy(isDigging = true)

    fun stop(state: DiggerState): DiggerState = state.copy(isDigging = false)

    fun reset(): DiggerState = DiggerState()

    fun setTargetHeading(state: DiggerState, degrees: Float): DiggerState {
        val boundedHeading = degrees.coerceIn(MIN_HEADING_DEGREES, MAX_HEADING_DEGREES)
        return if (state.isDigging) {
            state.copy(targetHeadingDegrees = boundedHeading)
        } else {
            state.copy(
                headingDegrees = boundedHeading,
                targetHeadingDegrees = boundedHeading,
            )
        }
    }

    fun tick(state: DiggerState, deltaSeconds: Float): DiggerState {
        if (!state.isDigging || deltaSeconds <= 0f) {
            return state
        }

        val boundedDelta = deltaSeconds.coerceAtMost(0.1f)
        val nextHeading = moveTowards(
            current = state.headingDegrees,
            target = state.targetHeadingDegrees,
            maximumChange = TURN_SPEED_DEGREES_PER_SECOND * boundedDelta,
        )
        val radians = Math.toRadians(nextHeading.toDouble())
        val travel = DIG_SPEED_METRES_PER_SECOND * boundedDelta
        val nextPosition = WorldPoint(
            xMetres = state.position.xMetres + (cos(radians) * travel).toFloat(),
            yMetres = (state.position.yMetres + (sin(radians) * travel).toFloat()).coerceAtLeast(0f),
        )

        val path = if (state.excavatedPath.last().distanceTo(nextPosition) >= PATH_POINT_SPACING_METRES) {
            state.excavatedPath + nextPosition
        } else {
            state.excavatedPath
        }

        return state.copy(
            position = nextPosition,
            headingDegrees = nextHeading,
            excavatedPath = path,
        )
    }

    private fun moveTowards(current: Float, target: Float, maximumChange: Float): Float {
        val difference = target - current
        if (kotlin.math.abs(difference) <= maximumChange) {
            return target
        }
        return current + difference.coerceIn(-maximumChange, maximumChange)
    }
}
