package com.mineit.minegame.domain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ExcavationMaterialBreakdown(
    val newExcavatedVolumeCubicMetres: Float,
    val wasteRockVolumeCubicMetres: Float,
    val oreVolumeCubicMetresByBodyId: Map<String, Float>,
) {
    val oreVolumeCubicMetres: Float
        get() = oreVolumeCubicMetresByBodyId.values.sum()
}

object MineWorldGeometry {
    private const val MATERIAL_SAMPLE_SPACING_METRES = 0.45f
    private const val DEPLETION_NODE_SPACING_METRES = 0.9f
    private const val MIN_REMAINING_ORE_RADIUS_METRES = 0.03f

    fun distance(a: MinePoint3D, b: MinePoint3D): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt((dx * dx) + (dy * dy) + (dz * dz))
    }

    fun distanceToTunnel(point: MinePoint3D, tunnel: TunnelGeometry): Float =
        distanceToTunnelSegments(point, tunnel.segments)

    fun distanceToTunnelSegments(point: MinePoint3D, segments: Collection<TunnelSegment>): Float {
        if (segments.isEmpty()) return Float.POSITIVE_INFINITY
        var best = Float.POSITIVE_INFINITY
        segments.forEach { segment ->
            best = min(best, distanceToSegment(point, segment.start, segment.end))
        }
        return best
    }

    fun oreMargin(point: MinePoint3D, oreBody: List<OreBodyNode>): Float {
        if (oreBody.size < 2) return Float.NEGATIVE_INFINITY
        var best = Float.NEGATIVE_INFINITY
        oreBody.zipWithNext().forEach { (start, end) ->
            val t = closestParameter(point, start.centre, end.centre)
            val closest = interpolate(start.centre, end.centre, t)
            val radius = start.radiusMetres + ((end.radiusMetres - start.radiusMetres) * t)
            best = max(best, radius - distance(point, closest))
        }
        return best
    }

    fun solidMargin(
        point: MinePoint3D,
        bounds: MineWorldBounds,
        tunnel: TunnelGeometry,
    ): Float = solidMargin(
        point = point,
        bounds = bounds,
        tunnelRadiusMetres = tunnel.radiusMetres,
        segments = tunnel.segments,
    )

    fun solidMargin(
        point: MinePoint3D,
        bounds: MineWorldBounds,
        tunnelRadiusMetres: Float,
        segments: Collection<TunnelSegment>,
    ): Float {
        val insideBounds = minOf(
            point.x - bounds.minX,
            bounds.maxX - point.x,
            point.y - bounds.minY,
            bounds.maxY - point.y,
            point.z - bounds.minZ,
            bounds.maxZ - point.z,
        )
        val outsideTunnel = distanceToTunnelSegments(point, segments) - tunnelRadiusMetres
        return min(insideBounds, outsideTunnel)
    }

    fun sweptVolume(tunnel: TunnelGeometry): Float = tunnel.segments
        .sumOf { segment ->
            cylinderVolume(tunnel.radiusMetres, distance(segment.start, segment.end)).toDouble()
        }
        .toFloat()

    /**
     * Partitions only the material that is newly removed by [start]-[end].
     *
     * Every accepted sample is classified exactly once: either as one ore body or as waste rock.
     * Samples inside an existing finite tunnel cylinder are ignored, so crossing an old working
     * cannot create material for a second time. The returned components therefore always add back
     * to [ExcavationMaterialBreakdown.newExcavatedVolumeCubicMetres].
     */
    fun classifyNewExcavation(
        start: MinePoint3D,
        end: MinePoint3D,
        tunnelRadiusMetres: Float,
        existingTunnelSegments: Collection<TunnelSegment>,
        oreBodies: List<OreBody>,
    ): ExcavationMaterialBreakdown {
        val length = distance(start, end)
        if (length < 0.001f || tunnelRadiusMetres <= 0f) {
            return ExcavationMaterialBreakdown(0f, 0f, emptyMap())
        }

        val tangent = normalized(
            MinePoint3D(
                end.x - start.x,
                end.y - start.y,
                end.z - start.z,
            ),
        )
        val reference = if (kotlin.math.abs(tangent.z) < 0.85f) {
            MinePoint3D(0f, 0f, 1f)
        } else {
            MinePoint3D(1f, 0f, 0f)
        }
        val right = normalized(cross(reference, tangent))
        val up = normalized(cross(tangent, right))

        val longitudinalSteps = max(1, ceil(length / MATERIAL_SAMPLE_SPACING_METRES).toInt())
        val crossSteps = max(
            4,
            ceil((tunnelRadiusMetres * 2f) / MATERIAL_SAMPLE_SPACING_METRES).toInt(),
        )
        val crossCell = (tunnelRadiusMetres * 2f) / crossSteps.toFloat()
        val crossOffsets = buildList {
            for (uIndex in 0 until crossSteps) {
                val u = -tunnelRadiusMetres + ((uIndex + 0.5f) * crossCell)
                for (vIndex in 0 until crossSteps) {
                    val v = -tunnelRadiusMetres + ((vIndex + 0.5f) * crossCell)
                    if ((u * u) + (v * v) <= tunnelRadiusMetres * tunnelRadiusMetres) {
                        add(u to v)
                    }
                }
            }
        }
        if (crossOffsets.isEmpty()) {
            return ExcavationMaterialBreakdown(0f, 0f, emptyMap())
        }

        val nearbyExistingSegments = existingTunnelSegments.filter { existing ->
            segmentBoundsOverlap(
                firstStart = start,
                firstEnd = end,
                secondStart = existing.start,
                secondEnd = existing.end,
                padding = tunnelRadiusMetres * 2f,
            )
        }

        val sampleVolume = cylinderVolume(tunnelRadiusMetres, length) /
            (longitudinalSteps * crossOffsets.size).toFloat()
        var newSampleCount = 0
        val oreSamplesByBodyId = mutableMapOf<String, Int>()

        for (step in 0 until longitudinalSteps) {
            val t = (step + 0.5f) / longitudinalSteps.toFloat()
            val centre = interpolate(start, end, t)

            crossOffsets.forEach { (u, v) ->
                val sample = MinePoint3D(
                    x = centre.x + (right.x * u) + (up.x * v),
                    y = centre.y + (right.y * u) + (up.y * v),
                    z = centre.z + (right.z * u) + (up.z * v),
                )

                // Air above the grass surface is not mined material.
                if (sample.z < 0f) return@forEach

                if (
                    nearbyExistingSegments.any { existing ->
                        isInsideFiniteCylinder(sample, existing, tunnelRadiusMetres)
                    }
                ) {
                    return@forEach
                }

                newSampleCount += 1

                var bestBodyId: String? = null
                var bestMargin = 0f
                oreBodies.forEach { body ->
                    val margin = oreMargin(sample, body.nodes)
                    if (margin > bestMargin) {
                        bestMargin = margin
                        bestBodyId = body.id
                    }
                }
                if (bestBodyId != null) {
                    oreSamplesByBodyId[bestBodyId!!] =
                        (oreSamplesByBodyId[bestBodyId!!] ?: 0) + 1
                }
            }
        }

        val newVolume = newSampleCount * sampleVolume
        val oreVolumes = oreSamplesByBodyId.mapValues { (_, count) -> count * sampleVolume }
        val oreVolume = oreVolumes.values.sum()
        val wasteVolume = (newVolume - oreVolume).coerceAtLeast(0f)

        return ExcavationMaterialBreakdown(
            newExcavatedVolumeCubicMetres = newVolume,
            wasteRockVolumeCubicMetres = wasteVolume,
            oreVolumeCubicMetresByBodyId = oreVolumes,
        )
    }

    /**
     * Applies excavation to the canonical remaining ore geometry.
     *
     * A touched body is densified locally enough for a short cutter segment to affect the correct
     * part of the vein. At each affected station the overlapping circular area is removed and the
     * remainder is represented by an equivalent smaller radius. This is deliberately lightweight:
     * narrow veins can disappear completely, while broad bodies survive with a reduced section.
     */
    fun depleteOreBodies(
        oreBodies: List<OreBody>,
        start: MinePoint3D,
        end: MinePoint3D,
        tunnelRadiusMetres: Float,
        minedBodyIds: Set<String>,
    ): List<OreBody> {
        if (minedBodyIds.isEmpty()) return oreBodies

        return oreBodies.map { body ->
            if (body.id !in minedBodyIds || body.nodes.size < 2) {
                body
            } else {
                val densified = densifyOreNodes(body.nodes, DEPLETION_NODE_SPACING_METRES)
                val depleted = densified.map { node ->
                    val projection = projectionParameter(node.centre, start, end)
                    if (projection < 0f || projection > 1f || node.radiusMetres <= 0f) {
                        node
                    } else {
                        val axisPoint = interpolate(start, end, projection)
                        val axisDistance = distance(node.centre, axisPoint)
                        val removedArea = circleIntersectionArea(
                            firstRadius = node.radiusMetres,
                            secondRadius = tunnelRadiusMetres,
                            centreDistance = axisDistance,
                        )
                        if (removedArea <= 0f) {
                            node
                        } else {
                            val originalArea = (PI * node.radiusMetres * node.radiusMetres).toFloat()
                            val remainingArea = (originalArea - removedArea).coerceAtLeast(0f)
                            val remainingRadius = if (remainingArea <= 0f) {
                                0f
                            } else {
                                sqrt((remainingArea / PI).toFloat())
                            }
                            node.copy(
                                radiusMetres = if (remainingRadius < MIN_REMAINING_ORE_RADIUS_METRES) {
                                    0f
                                } else {
                                    remainingRadius
                                },
                            )
                        }
                    }
                }
                body.copy(nodes = depleted)
            }
        }
    }

    fun exposedOreSegments(
        tunnel: TunnelGeometry,
        oreBody: List<OreBodyNode>,
        sampleSpacingMetres: Float = 0.75f,
    ): Set<Int> {
        if (tunnel.points.size < 2 || oreBody.size < 2) return emptySet()
        val exposed = mutableSetOf<Int>()
        tunnel.segments.forEach { segment ->
            exposed += exposedOreSegmentsForSegment(
                start = segment.start,
                end = segment.end,
                tunnelRadiusMetres = tunnel.radiusMetres,
                oreBody = oreBody,
                sampleSpacingMetres = sampleSpacingMetres,
            )
        }
        return exposed
    }

    fun exposedOreSegmentsForSegment(
        start: MinePoint3D,
        end: MinePoint3D,
        tunnelRadiusMetres: Float,
        oreBody: List<OreBodyNode>,
        sampleSpacingMetres: Float = 0.75f,
    ): Set<Int> {
        if (oreBody.size < 2) return emptySet()
        val exposed = mutableSetOf<Int>()
        val length = distance(start, end)
        val steps = max(1, ceil(length / sampleSpacingMetres).toInt())

        for (step in 0..steps) {
            val t = step.toFloat() / steps.toFloat()
            val sample = interpolate(start, end, t)
            oreBody.zipWithNext().forEachIndexed { index, (oreStart, oreEnd) ->
                val oreT = closestParameter(sample, oreStart.centre, oreEnd.centre)
                val closest = interpolate(oreStart.centre, oreEnd.centre, oreT)
                val oreRadius = oreStart.radiusMetres +
                    ((oreEnd.radiusMetres - oreStart.radiusMetres) * oreT)
                if (distance(sample, closest) <= oreRadius + tunnelRadiusMetres) {
                    exposed += index
                }
            }
        }

        return exposed
    }

    fun distanceToSegment(
        point: MinePoint3D,
        start: MinePoint3D,
        end: MinePoint3D,
    ): Float {
        val t = closestParameter(point, start, end)
        return distance(point, interpolate(start, end, t))
    }

    fun interpolate(start: MinePoint3D, end: MinePoint3D, t: Float): MinePoint3D = MinePoint3D(
        x = start.x + ((end.x - start.x) * t),
        y = start.y + ((end.y - start.y) * t),
        z = start.z + ((end.z - start.z) * t),
    )

    private fun densifyOreNodes(
        nodes: List<OreBodyNode>,
        spacingMetres: Float,
    ): List<OreBodyNode> {
        if (nodes.size < 2) return nodes
        val output = ArrayList<OreBodyNode>()
        output += nodes.first()

        nodes.zipWithNext().forEach { (start, end) ->
            val length = distance(start.centre, end.centre)
            val steps = max(1, ceil(length / spacingMetres).toInt())
            for (step in 1..steps) {
                val t = step.toFloat() / steps.toFloat()
                output += OreBodyNode(
                    centre = interpolate(start.centre, end.centre, t),
                    radiusMetres = start.radiusMetres +
                        ((end.radiusMetres - start.radiusMetres) * t),
                )
            }
        }
        return output
    }

    private fun isInsideFiniteCylinder(
        point: MinePoint3D,
        segment: TunnelSegment,
        radiusMetres: Float,
    ): Boolean {
        val t = projectionParameter(point, segment.start, segment.end)
        if (t < 0f || t > 1f) return false
        val closest = interpolate(segment.start, segment.end, t)
        return distance(point, closest) <= radiusMetres
    }

    private fun segmentBoundsOverlap(
        firstStart: MinePoint3D,
        firstEnd: MinePoint3D,
        secondStart: MinePoint3D,
        secondEnd: MinePoint3D,
        padding: Float,
    ): Boolean =
        max(firstStart.x, firstEnd.x) + padding >= min(secondStart.x, secondEnd.x) &&
            min(firstStart.x, firstEnd.x) - padding <= max(secondStart.x, secondEnd.x) &&
            max(firstStart.y, firstEnd.y) + padding >= min(secondStart.y, secondEnd.y) &&
            min(firstStart.y, firstEnd.y) - padding <= max(secondStart.y, secondEnd.y) &&
            max(firstStart.z, firstEnd.z) + padding >= min(secondStart.z, secondEnd.z) &&
            min(firstStart.z, firstEnd.z) - padding <= max(secondStart.z, secondEnd.z)

    private fun circleIntersectionArea(
        firstRadius: Float,
        secondRadius: Float,
        centreDistance: Float,
    ): Float {
        if (firstRadius <= 0f || secondRadius <= 0f) return 0f
        if (centreDistance >= firstRadius + secondRadius) return 0f

        val smaller = min(firstRadius, secondRadius)
        if (centreDistance <= kotlin.math.abs(firstRadius - secondRadius)) {
            return (PI * smaller * smaller).toFloat()
        }

        val r1 = firstRadius.toDouble()
        val r2 = secondRadius.toDouble()
        val d = centreDistance.toDouble().coerceAtLeast(0.000001)
        val firstAngle = acos(
            (((d * d) + (r1 * r1) - (r2 * r2)) / (2.0 * d * r1))
                .coerceIn(-1.0, 1.0),
        )
        val secondAngle = acos(
            (((d * d) + (r2 * r2) - (r1 * r1)) / (2.0 * d * r2))
                .coerceIn(-1.0, 1.0),
        )
        val product = (
            (-d + r1 + r2) *
                (d + r1 - r2) *
                (d - r1 + r2) *
                (d + r1 + r2)
            ).coerceAtLeast(0.0)
        val triangleAreaTwice = sqrt(product)

        return (
            (r1 * r1 * firstAngle) +
                (r2 * r2 * secondAngle) -
                (0.5 * triangleAreaTwice)
            ).toFloat()
    }

    private fun closestParameter(
        point: MinePoint3D,
        start: MinePoint3D,
        end: MinePoint3D,
    ): Float = projectionParameter(point, start, end).coerceIn(0f, 1f)

    private fun projectionParameter(
        point: MinePoint3D,
        start: MinePoint3D,
        end: MinePoint3D,
    ): Float {
        val vx = end.x - start.x
        val vy = end.y - start.y
        val vz = end.z - start.z
        val lengthSquared = (vx * vx) + (vy * vy) + (vz * vz)
        if (lengthSquared <= 0.0001f) return 0f

        val wx = point.x - start.x
        val wy = point.y - start.y
        val wz = point.z - start.z
        return ((wx * vx) + (wy * vy) + (wz * vz)) / lengthSquared
    }

    private fun normalized(point: MinePoint3D): MinePoint3D {
        val length = sqrt(
            (point.x * point.x) +
                (point.y * point.y) +
                (point.z * point.z),
        )
        if (length < 0.0001f) return MinePoint3D(1f, 0f, 0f)
        return MinePoint3D(point.x / length, point.y / length, point.z / length)
    }

    private fun cross(a: MinePoint3D, b: MinePoint3D): MinePoint3D = MinePoint3D(
        x = (a.y * b.z) - (a.z * b.y),
        y = (a.z * b.x) - (a.x * b.z),
        z = (a.x * b.y) - (a.y * b.x),
    )
}
