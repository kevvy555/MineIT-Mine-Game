package com.mineit.minegame.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object MineWorldGeometry {
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

    private fun closestParameter(
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
        return (((wx * vx) + (wy * vy) + (wz * vz)) / lengthSquared).coerceIn(0f, 1f)
    }
}
