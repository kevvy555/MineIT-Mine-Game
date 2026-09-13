package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.OreBody
import com.mineit.minegame.domain.OreType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Presentation geometry for typed ore.
 *
 * Discovered deposits keep a closed, opaque body surface so normal gameplay can inspect a known
 * connected vein. SEE ORE never uses that body surface for undiscovered deposits: it only adds
 * solid typed cross-sections to the active CT plane.
 */
internal object OreMeshBuilder {
    private const val RING_SEGMENTS = 12
    private const val SLICE_DISC_SEGMENTS = 20
    private const val ORE_SLICE_SAMPLE_METRES = 1.2f
    private const val ORE_SLICE_OFFSET_METRES = 0.065f
    private const val FLOATS_PER_VERTEX = 9

    fun buildDiscovered(state: MineWorldState): MineMesh {
        val bodies = state.discoveredOreBodies
        if (bodies.isEmpty()) return emptyMesh()

        val output = FloatAccumulator()
        bodies.forEach { body -> appendBodySurface(output, body, state.bounds) }
        return output.toMesh()
    }

    /**
     * Builds only filled CT cross-sections. With [showAll] enabled, undiscovered deposits become
     * visible solely where the current X/Y/Z slice intersects them; intact unsliced rock remains
     * opaque and contains no x-ray ore overlay.
     */
    fun buildSlice(
        state: MineWorldState,
        axis: ClipAxis,
        fraction: Float,
        flipped: Boolean,
        showAll: Boolean,
    ): MineMesh {
        val bodies = if (showAll) state.oreBodies else state.discoveredOreBodies
        if (bodies.isEmpty()) return emptyMesh()

        val clip = MineMeshBuilder.clipValue(state, axis, fraction)
        val normal = capNormal(axis, flipped)
        val planeSign = axisCoordinate(normal, axis)
        val plane = clip + (planeSign * ORE_SLICE_OFFSET_METRES)
        val output = FloatAccumulator(8_192)

        bodies.forEach { body ->
            val colour = colourFor(body.type)
            body.nodes.zipWithNext().forEach { (start, end) ->
                val length = MineWorldGeometry.distance(start.centre, end.centre)
                val steps = max(1, ceil(length / ORE_SLICE_SAMPLE_METRES).toInt())
                for (step in 0..steps) {
                    val t = step.toFloat() / steps.toFloat()
                    val centre = MineWorldGeometry.interpolate(start.centre, end.centre, t)
                    val radius = start.radiusMetres + ((end.radiusMetres - start.radiusMetres) * t)
                    appendSliceDiscIfIntersecting(
                        output = output,
                        bounds = state.bounds,
                        axis = axis,
                        clipValue = clip,
                        planeValue = plane,
                        centre = centre,
                        radius = radius,
                        normal = normal,
                        colour = colour,
                    )
                }
            }
        }

        return output.toMesh()
    }

    private fun appendBodySurface(
        output: FloatAccumulator,
        body: OreBody,
        bounds: MineWorldBounds,
    ) {
        if (body.nodes.size < 2) return
        val colour = colourFor(body.type)
        val lastSegmentIndex = body.nodes.lastIndex - 1

        body.nodes.zipWithNext().forEachIndexed { segmentIndex, (start, end) ->
            val padding = maxOf(start.radiusMetres, end.radiusMetres)
            if (!segmentOverlapsBounds(start.centre, end.centre, bounds, padding)) return@forEachIndexed

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

            if (segmentIndex == 0) {
                appendCap(
                    output = output,
                    centre = start.centre,
                    ring = startRing,
                    normal = negate(tangent),
                    colour = colour,
                    reverse = true,
                )
            }
            if (segmentIndex == lastSegmentIndex) {
                appendCap(
                    output = output,
                    centre = end.centre,
                    ring = endRing,
                    normal = tangent,
                    colour = colour,
                    reverse = false,
                )
            }
        }
    }

    private fun appendSliceDiscIfIntersecting(
        output: FloatAccumulator,
        bounds: MineWorldBounds,
        axis: ClipAxis,
        clipValue: Float,
        planeValue: Float,
        centre: MinePoint3D,
        radius: Float,
        normal: MinePoint3D,
        colour: FloatArray,
    ) {
        val distanceToPlane = abs(axisCoordinate(centre, axis) - clipValue)
        if (distanceToPlane >= radius) return

        val crossRadius = sqrt(max(0f, (radius * radius) - (distanceToPlane * distanceToPlane)))
        if (crossRadius < 0.06f) return

        val projected = withAxis(centre, axis, planeValue)
        if (!discOverlapsBounds(projected, crossRadius, axis, bounds)) return

        val u = when (axis) {
            ClipAxis.X -> MinePoint3D(0f, 1f, 0f)
            ClipAxis.Y -> MinePoint3D(1f, 0f, 0f)
            ClipAxis.Z -> MinePoint3D(1f, 0f, 0f)
        }
        val v = when (axis) {
            ClipAxis.X -> MinePoint3D(0f, 0f, 1f)
            ClipAxis.Y -> MinePoint3D(0f, 0f, 1f)
            ClipAxis.Z -> MinePoint3D(0f, 1f, 0f)
        }

        for (index in 0 until SLICE_DISC_SEGMENTS) {
            val angleA = 2.0 * PI * index.toDouble() / SLICE_DISC_SEGMENTS.toDouble()
            val angleB = 2.0 * PI * (index + 1).toDouble() / SLICE_DISC_SEGMENTS.toDouble()
            val a = combine(
                projected,
                u to (cos(angleA).toFloat() * crossRadius),
                v to (sin(angleA).toFloat() * crossRadius),
            )
            val b = combine(
                projected,
                u to (cos(angleB).toFloat() * crossRadius),
                v to (sin(angleB).toFloat() * crossRadius),
            )
            output.appendTriangle(projected, a, b, normal, colour)
        }
    }

    private fun discOverlapsBounds(
        centre: MinePoint3D,
        radius: Float,
        axis: ClipAxis,
        bounds: MineWorldBounds,
    ): Boolean = when (axis) {
        ClipAxis.X ->
            centre.y + radius >= bounds.minY && centre.y - radius <= bounds.maxY &&
                centre.z + radius >= bounds.minZ && centre.z - radius <= bounds.maxZ
        ClipAxis.Y ->
            centre.x + radius >= bounds.minX && centre.x - radius <= bounds.maxX &&
                centre.z + radius >= bounds.minZ && centre.z - radius <= bounds.maxZ
        ClipAxis.Z ->
            centre.x + radius >= bounds.minX && centre.x - radius <= bounds.maxX &&
                centre.y + radius >= bounds.minY && centre.y - radius <= bounds.maxY
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

    private fun appendCap(
        output: FloatAccumulator,
        centre: MinePoint3D,
        ring: Array<MinePoint3D>,
        normal: MinePoint3D,
        colour: FloatArray,
        reverse: Boolean,
    ) {
        for (index in ring.indices) {
            val next = (index + 1) % ring.size
            if (reverse) {
                output.appendTriangle(centre, ring[next], ring[index], normal, colour)
            } else {
                output.appendTriangle(centre, ring[index], ring[next], normal, colour)
            }
        }
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

    private fun capNormal(axis: ClipAxis, flipped: Boolean): MinePoint3D {
        val sign = if (flipped) 1f else -1f
        return when (axis) {
            ClipAxis.X -> MinePoint3D(sign, 0f, 0f)
            ClipAxis.Y -> MinePoint3D(0f, sign, 0f)
            ClipAxis.Z -> MinePoint3D(0f, 0f, sign)
        }
    }

    private fun axisCoordinate(point: MinePoint3D, axis: ClipAxis): Float = when (axis) {
        ClipAxis.X -> point.x
        ClipAxis.Y -> point.y
        ClipAxis.Z -> point.z
    }

    private fun withAxis(point: MinePoint3D, axis: ClipAxis, value: Float): MinePoint3D = when (axis) {
        ClipAxis.X -> point.copy(x = value)
        ClipAxis.Y -> point.copy(y = value)
        ClipAxis.Z -> point.copy(z = value)
    }

    private fun combine(
        origin: MinePoint3D,
        vararg terms: Pair<MinePoint3D, Float>,
    ): MinePoint3D {
        var x = origin.x
        var y = origin.y
        var z = origin.z
        terms.forEach { (direction, scale) ->
            x += direction.x * scale
            y += direction.y * scale
            z += direction.z * scale
        }
        return MinePoint3D(x, y, z)
    }

    private fun cross(a: MinePoint3D, b: MinePoint3D) = MinePoint3D(
        x = (a.y * b.z) - (a.z * b.y),
        y = (a.z * b.x) - (a.x * b.z),
        z = (a.x * b.y) - (a.y * b.x),
    )

    private fun negate(point: MinePoint3D) = MinePoint3D(-point.x, -point.y, -point.z)

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

    private fun emptyMesh() = MineMesh(FloatArray(0), 0)

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
