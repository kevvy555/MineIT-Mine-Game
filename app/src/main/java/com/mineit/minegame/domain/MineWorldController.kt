package com.mineit.minegame.domain

import kotlin.math.cos
import kotlin.math.sin

object MineWorldController {
    const val CHUNK_SIZE_METRES = 24f
    const val ROCK_DENSITY_TONNES_PER_CUBIC_METRE = 2.7f
    const val DIG_SPEED_METRES_PER_SECOND = 3f
    const val TURN_RATE_DEGREES_PER_SECOND = 42f
    const val STEERING_PREVIEW_DEGREES = 90f
    const val MIN_VERTICAL_ANGLE_DEGREES = -90f
    const val MAX_VERTICAL_ANGLE_DEGREES = 90f
    const val MIN_STEERING = -1f
    const val MAX_STEERING = 1f

    private const val EXTENT_MARGIN_METRES = 6f
    private const val MAX_TICK_SECONDS = 0.35f
    private const val SURFACE_EPSILON_METRES = 0.03f
    private const val MIN_SURFACE_ENTRY_ANGLE_DEGREES = 2f

    fun setSteering(state: MineWorldState, steering: Float): MineWorldState =
        state.copy(steering = steering.coerceIn(MIN_STEERING, MAX_STEERING))

    fun setVerticalAngle(state: MineWorldState, degrees: Float): MineWorldState =
        state.copy(
            verticalAngleDegrees = degrees.coerceIn(
                MIN_VERTICAL_ANGLE_DEGREES,
                MAX_VERTICAL_ANGLE_DEGREES,
            ),
        )

    /**
     * Applies a fixed horizontal turn relative to the direction currently shown by the machine.
     * The steering control is recentered so the machine visibly settles on the new heading.
     */
    fun turnHeadingBy(state: MineWorldState, degrees: Float): MineWorldState = state.copy(
        headingDegrees = normalizeHeading(state.machineHeadingDegrees + degrees),
        steering = 0f,
    )

    fun startDigging(state: MineWorldState): MineWorldState {
        if (
            state.tunnel.end.z <= SURFACE_EPSILON_METRES &&
            state.verticalAngleDegrees <= MIN_SURFACE_ENTRY_ANGLE_DEGREES
        ) {
            return state.copy(isDigging = false)
        }

        // Steering while stopped is a direction preview. Commit that preview when excavation starts,
        // then return the steering control to centre so it becomes a turn-rate input while moving.
        return state.copy(
            headingDegrees = state.machineHeadingDegrees,
            steering = 0f,
            isDigging = true,
        )
    }

    fun stopDigging(state: MineWorldState): MineWorldState = state.copy(isDigging = false)

    fun tick(state: MineWorldState, deltaSeconds: Float): MineWorldState {
        if (!state.isDigging || deltaSeconds <= 0f) return state

        val boundedDelta = deltaSeconds.coerceAtMost(MAX_TICK_SECONDS)
        val heading = normalizeHeading(
            state.headingDegrees +
                (state.steering * TURN_RATE_DEGREES_PER_SECOND * boundedDelta),
        )
        val headingRadians = Math.toRadians(heading.toDouble())
        val verticalRadians = Math.toRadians(state.verticalAngleDegrees.toDouble())
        val travel = DIG_SPEED_METRES_PER_SECOND * boundedDelta
        val horizontalTravel = cos(verticalRadians).toFloat() * travel
        val start = state.tunnel.end
        val verticalTravel = sin(verticalRadians).toFloat() * travel

        if (start.z <= SURFACE_EPSILON_METRES && verticalTravel <= 0f) {
            return state.copy(
                headingDegrees = heading,
                isDigging = false,
            )
        }

        val proposed = MinePoint3D(
            x = start.x + (cos(headingRadians).toFloat() * horizontalTravel),
            y = start.y + (sin(headingRadians).toFloat() * horizontalTravel),
            z = start.z + verticalTravel,
        )

        val exitsSurface = proposed.z < 0f
        val target = if (exitsSurface) {
            val fraction = if (start.z <= SURFACE_EPSILON_METRES) {
                0f
            } else {
                (start.z / (start.z - proposed.z)).coerceIn(0f, 1f)
            }
            MineWorldGeometry.interpolate(start, proposed, fraction).copy(z = 0f)
        } else {
            proposed
        }

        val segmentLength = MineWorldGeometry.distance(start, target)
        if (segmentLength < 0.01f) {
            return state.copy(
                headingDegrees = heading,
                isDigging = !exitsSurface,
            )
        }

        val tunnel = state.tunnel.copy(points = state.tunnel.points + target)
        val extent = expandExtent(state.extent, target)
        val volume = state.excavatedVolumeCubicMetres +
            cylinderVolume(state.tunnel.radiusMetres, segmentLength)
        val newlyExposed = MineWorldGeometry.exposedOreSegmentsForSegment(
            start = start,
            end = target,
            tunnelRadiusMetres = state.tunnel.radiusMetres,
            oreBody = state.oreBody,
        )
        val exposures = state.exposedOreSegments + newlyExposed

        return state.copy(
            tunnel = tunnel,
            extent = extent,
            headingDegrees = heading,
            excavatedVolumeCubicMetres = volume,
            exposedOreSegments = exposures,
            oreBodyDiscovered = state.oreBodyDiscovered || newlyExposed.isNotEmpty(),
            isDigging = state.isDigging && !exitsSurface,
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
