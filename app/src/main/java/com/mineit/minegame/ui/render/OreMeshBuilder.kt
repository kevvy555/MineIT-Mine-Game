package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Lightweight tube meshes for discovered ore and the SEE ORE inspection overlay. */
internal object OreMeshBuilder {
    private const val RING_SEGMENTS = 12
    private const val FLOATS_PER_VERTEX = 9

    fun build(state: MineWorldState, showAll: Boolean): MineMesh {
        val bodies = if (showAll) state.oreBodies else state.discoveredOreBodies
        if (bodies.isEmpty()) return MineMesh(FloatArray(0), 0)

        val output = FloatAccumulator()
        bodies.forEach { body -> appendBody(output, body, state.bounds) }
        return output.toMesh()
    }

    private fun appendBody(output: FloatAccumulator, body: OreBody, bounds: MineWorldBounds) {
        if (body.nodes.size < 2) return
        val colour = colourFor(body.type)

        body.nodes.zipWithNext().forEach { (start, end) ->
            val padding = maxOf(start.radiusMetres, end.radiusMetres)
            if (!segmentOverlapsBounds(start.centre, end.centre, bounds, padding)) return@forEach

            val tangent = normalized(
                MinePoint3D(
                    end.centre.x - start.centre.x,
                    end.centre.y - start.centre.y,
                    end.centre.z - start.centre.z,
                ),
            )
            val reference = if (abs(tangent.z) < 0.85f) {
                MinePoint3D(0f, 0f, 1f)
            } else {
                MinePoint3D(1f, 0f, 0f)
            }
            val right = normalized(cross(reference, tangent))
            val up = normalized(cross(tangent, right))

            val startRing = ring(start.centre, start.radiusMetres, right, up)
            val endRing = ring(end.centre, end.radiusMetres, right, up)
            for (index in 0 until RING_SEGMENTS) {
                val next = (index + 1) % RING_SEGMENTS
                appendQuad(
                    output = output,
                    a = startRing[index],
                    b = startRing[next],
                    c = endRing[next],
                    d = endRing[index],
                    colour = colour,
                )
            }
        }
    }

    private fun ring(
        centre: MinePoint3D,
        radius: Float,
        right: MinePoint3D,
        up: MinePoint3D,
    ): Array<MinePoint3D> = Array(RING_SEGMENTS) { index ->
        val angle = 2.0 * PI * index.toDouble() / RING_SEGMENTS.toDouble()
        val cosAngle = cos(angle).toFloat()
        val sinAngle = sin(angle).toFloat()
        MinePoint3D(
            x = centre.x + (right.x * cosAngle * radius) + (up.x * sinAngle * radius),
            y = centre.y + (right.y * cosAngle * radius) + (up.y * sinAngle * radius),
            z = centre.z + (right.z * cosAngle * radius) + (up.z * sinAngle * radius),
        )
    }

    private fun appendQuad(
        output: FloatAccumulator,
        a: MinePoint3D,
        b: MinePoint3D,
        c: MinePoint3D,
        d: MinePoint3D,
        colour: FloatArray,
    ) {
        triangleNormal(a, b, c)?.let { output.appendTriangle(a, b, c, it, colour) }
        triangleNormal(a, c, d)?.let { output.appendTriangle(a, c, d, it, colour) }
    }

    private fun segmentOverlapsBounds(
        start: MinePoint3D,
        end: MinePoint3D,
        bounds: MineWorldBounds,
        padding: Float,
    ): Boolean =
        maxOf(start.x, end.x) + padding >= bounds.minX &&
            minOf(start.x, end.x) - padding <= bounds.maxX &&
            maxOf(start.y, end.y) + padding >= bounds.minY &&
            minOf(start.y, end.y) - padding <= bounds.maxY &&
            maxOf(start.z, end.z) + padding >= bounds.minZ &&
            minOf(start.z, end.z) - padding <= bounds.maxZ

    private fun colourFor(type: OreType): FloatArray = when (type) {
        OreType.GOLD -> floatArrayOf(0.96f, 0.72f, 0.10f)
        OreType.SILVER -> floatArrayOf(0.72f, 0.79f, 0.86f)
        OreType.COPPER -> floatArrayOf(0.82f, 0.36f, 0.13f)
    }

    private fun cross(a: MinePoint3D, b: MinePoint3D) = MinePoint3D(
        x = (a.y * b.z) - (a.z * b.y),
        y = (a.z * b.x) - (a.x * b.z),
        z = (a.x * b.y) - (a.y * b.x),
    )

    private fun normalized(point: MinePoint3D): MinePoint3D {
        val length = sqrt((point.x * point.x) + (point.y * point.y) + (point.z * point.z))
        if (length < 0.0001f) return MinePoint3D(1f, 0f, 0f)
        return MinePoint3D(point.x / length, point.y / length, point.z / length)
    }

    private fun triangleNormal(a: MinePoint3D, b: MinePoint3D, c: MinePoint3D): MinePoint3D? {
        val ux = b.x - a.x
        val uy = b.y - a.y
        val uz = b.z - a.z
        val vx = c.x - a.x
        val vy = c.y - a.y
        val vz = c.z - a.z
        val nx = (uy * vz) - (uz * vy)
        val ny = (uz * vx) - (ux * vz)
        val nz = (ux * vy) - (uy * vx)
        val length = sqrt((nx * nx) + (ny * ny) + (nz * nz))
        if (length < 0.0001f) return null
        return MinePoint3D(nx / length, ny / length, nz / length)
    }

    private class FloatAccumulator(initialCapacity: Int = 16_384) {
        private var data = FloatArray(initialCapacity)
        private var size = 0

        fun appendTriangle(
            a: MinePoint3D,
            b: MinePoint3D,
            c: MinePoint3D,
            normal: MinePoint3D,
            colour: FloatArray,
        ) {
            appendVertex(a, normal, colour)
            appendVertex(b, normal, colour)
            appendVertex(c, normal, colour)
        }

        private fun appendVertex(point: MinePoint3D, normal: MinePoint3D, colour: FloatArray) {
            ensureCapacity(size + FLOATS_PER_VERTEX)
            data[size++] = point.x
            data[size++] = point.y
            data[size++] = point.z
            data[size++] = normal.x
            data[size++] = normal.y
            data[size++] = normal.z
            data[size++] = colour[0]
            data[size++] = colour[1]
            data[size++] = colour[2]
        }

        private fun ensureCapacity(required: Int) {
            if (required <= data.size) return
            data = data.copyOf(maxOf(required, data.size * 2))
        }

        fun toMesh(): MineMesh {
            val vertices = data.copyOf(size)
            return MineMesh(vertices = vertices, vertexCount = vertices.size / FLOATS_PER_VERTEX)
        }
    }
}
