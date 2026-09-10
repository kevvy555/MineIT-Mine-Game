package com.mineit.minegame.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object DigSimulation {
    const val MIN_HEADING_DEGREES = 0f
    const val MAX_HEADING_DEGREES = 360f

    private const val DIG_SPEED_METRES_PER_SECOND = 4f
    private const val TURN_SPEED_DEGREES_PER_SECOND = 55f
    private const val PATH_POINT_SPACING_METRES = 0.20f
    private const val SURFACE_ENTRY_EPSILON = 0.0001f

    fun start(state: DiggerState): DiggerState {
        if (state.position.yMetres <= 0f && downwardComponent(state.headingDegrees) <= SURFACE_ENTRY_EPSILON) {
            return state.copy(isDigging = false)
        }
        return state.copy(isDigging = true)
    }

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
        val nextHeading = moveTowardsAngle(
            current = state.headingDegrees,
            target = state.targetHeadingDegrees,
            maximumChange = TURN_SPEED_DEGREES_PER_SECOND * boundedDelta,
        )
        val radians = Math.toRadians(nextHeading.toDouble())
        val travel = DIG_SPEED_METRES_PER_SECOND * boundedDelta
        val xTravel = (cos(radians) * travel).toFloat()
        val yTravel = (sin(radians) * travel).toFloat()

        if (state.position.yMetres <= 0f && yTravel <= SURFACE_ENTRY_EPSILON) {
            return state.copy(
                headingDegrees = nextHeading,
                isDigging = false,
            )
        }

        val proposedPosition = WorldPoint(
            xMetres = state.position.xMetres + xTravel,
            yMetres = state.position.yMetres + yTravel,
        )

        if (proposedPosition.yMetres < 0f) {
            val travelFractionToSurface = state.position.yMetres /
                (state.position.yMetres - proposedPosition.yMetres)
            val surfacePosition = WorldPoint(
                xMetres = state.position.xMetres +
                    ((proposedPosition.xMetres - state.position.xMetres) * travelFractionToSurface),
                yMetres = 0f,
            )
            return state.copy(
                position = surfacePosition,
                headingDegrees = nextHeading,
                excavatedPath = appendPathPoint(
                    path = state.excavatedPath,
                    point = surfacePosition,
                    force = true,
                ),
                isDigging = false,
            )
        }

        return state.copy(
            position = proposedPosition,
            headingDegrees = nextHeading,
            excavatedPath = appendPathPoint(
                path = state.excavatedPath,
                point = proposedPosition,
            ),
        )
    }

    private fun downwardComponent(headingDegrees: Float): Float {
        val radians = Math.toRadians(headingDegrees.toDouble())
        return sin(radians).toFloat()
    }

    private fun appendPathPoint(
        path: List<WorldPoint>,
        point: WorldPoint,
        force: Boolean = false,
    ): List<WorldPoint> {
        if (force || path.last().distanceTo(point) >= PATH_POINT_SPACING_METRES) {
            return path + point
        }
        return path
    }

    private fun moveTowardsAngle(current: Float, target: Float, maximumChange: Float): Float {
        val difference = shortestAngularDifference(current, target)
        if (abs(difference) <= maximumChange) {
            return target
        }
        return normalizeHeading(current + difference.coerceIn(-maximumChange, maximumChange))
    }

    private fun shortestAngularDifference(current: Float, target: Float): Float {
        return ((target - current + 540f) % 360f) - 180f
    }

    private fun normalizeHeading(degrees: Float): Float {
        val normalized = degrees % 360f
        return if (normalized < 0f) normalized + 360f else normalized
    }
}
