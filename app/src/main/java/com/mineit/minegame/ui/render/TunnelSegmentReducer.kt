package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.sqrt

/**
 * The simulation records a point every 100 ms so controls remain smooth. Meshing does not need
 * that same density: a 1 m+ scalar grid gains nothing from testing dozens of almost-collinear
 * 30 cm tunnel segments. This reducer merges short, gently-curving neighbours while preserving
 * sharp bends and exact endpoints.
 */
internal object TunnelSegmentReducer {
    private const val CONTIGUOUS_EPSILON_METRES = 0.04f
    private const val MIN_DIRECTION_DOT = 0.97f

    fun reduce(
        segments: Collection<TunnelSegment>,
        targetLengthMetres: Float,
    ): List<TunnelSegment> {
        if (segments.size <= 1 || targetLengthMetres <= 0f) return segments.toList()

        val ordered = segments.toList()
        val result = ArrayList<TunnelSegment>(ordered.size)
        var aggregateStart = ordered.first().start
        var aggregateEnd = ordered.first().end

        for (index in 1 until ordered.size) {
            val next = ordered[index]
            val contiguous = MineWorldGeometry.distance(aggregateEnd, next.start) <= CONTIGUOUS_EPSILON_METRES
            val candidateLength = MineWorldGeometry.distance(aggregateStart, next.end)
            val currentDirection = direction(aggregateStart, aggregateEnd)
            val nextDirection = direction(next.start, next.end)
            val gentleTurn = dot(currentDirection, nextDirection) >= MIN_DIRECTION_DOT

            if (contiguous && candidateLength <= targetLengthMetres && gentleTurn) {
                aggregateEnd = next.end
            } else {
                result += TunnelSegment(aggregateStart, aggregateEnd)
                aggregateStart = next.start
                aggregateEnd = next.end
            }
        }

        result += TunnelSegment(aggregateStart, aggregateEnd)
        return result
    }

    private fun direction(start: MinePoint3D, end: MinePoint3D): MinePoint3D {
        val x = end.x - start.x
        val y = end.y - start.y
        val z = end.z - start.z
        val length = sqrt((x * x) + (y * y) + (z * z))
        if (length < 0.0001f) return MinePoint3D(1f, 0f, 0f)
        return MinePoint3D(x / length, y / length, z / length)
    }

    private fun dot(a: MinePoint3D, b: MinePoint3D): Float =
        (a.x * b.x) + (a.y * b.y) + (a.z * b.z)
}
