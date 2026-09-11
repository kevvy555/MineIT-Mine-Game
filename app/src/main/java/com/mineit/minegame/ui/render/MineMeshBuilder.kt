package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal data class MineMesh(
    val vertices: FloatArray,
    val vertexCount: Int,
)

internal object MineMeshBuilder {
    const val ACTIVE_GRID_STEP_METRES = 2f
    const val REFINED_GRID_STEP_METRES = 1.2f

    private const val CUT_CAP_STEP_METRES = 0.9f
    private const val NORMAL_SAMPLE_METRES = 0.45f
    private const val CUT_CAP_EPSILON_METRES = 0.03f

    private val tetrahedra = arrayOf(
        intArrayOf(0, 5, 1, 6),
        intArrayOf(0, 1, 2, 6),
        intArrayOf(0, 2, 3, 6),
        intArrayOf(0, 3, 7, 6),
        intArrayOf(0, 7, 4, 6),
        intArrayOf(0, 4, 5, 6),
    )

    fun buildChunk(
        state: MineWorldState,
        key: ChunkKey,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
    ): MineMesh {
        val worldBounds = state.bounds
        val chunkBounds = key.bounds()
        val minX = chunkBounds.minX - if (chunkBounds.minX == worldBounds.minX) gridStepMetres else 0f
        val maxX = chunkBounds.maxX + if (chunkBounds.maxX == worldBounds.maxX) gridStepMetres else 0f
        val minY = chunkBounds.minY - if (chunkBounds.minY == worldBounds.minY) gridStepMetres else 0f
        val maxY = chunkBounds.maxY + if (chunkBounds.maxY == worldBounds.maxY) gridStepMetres else 0f
        val minZ = chunkBounds.minZ - if (chunkBounds.minZ == worldBounds.minZ) gridStepMetres else 0f
        val maxZ = chunkBounds.maxZ + if (chunkBounds.maxZ == worldBounds.maxZ) gridStepMetres else 0f

        val xCount = (((maxX - minX) / gridStepMetres).roundToInt() + 1).coerceAtLeast(2)
        val yCount = (((maxY - minY) / gridStepMetres).roundToInt() + 1).coerceAtLeast(2)
        val zCount = (((maxZ - minZ) / gridStepMetres).roundToInt() + 1).coerceAtLeast(2)

        val values = FloatArray(xCount * yCount * zCount)
        fun index(x: Int, y: Int, z: Int): Int = ((z * yCount) + y) * xCount + x
        fun point(x: Int, y: Int, z: Int) = MinePoint3D(
            x = minX + (x * gridStepMetres),
            y = minY + (y * gridStepMetres),
            z = minZ + (z * gridStepMetres),
        )

        for (z in 0 until zCount) {
            for (y in 0 until yCount) {
                for (x in 0 until xCount) {
                    values[index(x, y, z)] = MineWorldGeometry.solidMargin(
                        point = point(x, y, z),
                        bounds = worldBounds,
                        tunnelRadiusMetres = state.tunnel.radiusMetres,
                        segments = tunnelSegments,
                    )
                }
            }
        }

        val output = FloatAccumulator()
        for (z in 0 until zCount - 1) {
            for (y in 0 until yCount - 1) {
                for (x in 0 until xCount - 1) {
                    val corners = arrayOf(
                        point(x, y, z),
                        point(x + 1, y, z),
                        point(x + 1, y + 1, z),
                        point(x, y + 1, z),
                        point(x, y, z + 1),
                        point(x + 1, y, z + 1),
                        point(x + 1, y + 1, z + 1),
                        point(x, y + 1, z + 1),
                    )
                    val cornerValues = floatArrayOf(
                        values[index(x, y, z)],
                        values[index(x + 1, y, z)],
                        values[index(x + 1, y + 1, z)],
                        values[index(x, y + 1, z)],
                        values[index(x, y, z + 1)],
                        values[index(x + 1, y, z + 1)],
                        values[index(x + 1, y + 1, z + 1)],
                        values[index(x, y + 1, z + 1)],
                    )
                    tetrahedra.forEach { tetra ->
                        polygoniseTetrahedron(
                            points = tetra.map { corners[it] },
                            values = tetra.map { cornerValues[it] },
                            state = state,
                            tunnelSegments = tunnelSegments,
                            gridStepMetres = gridStepMetres,
                            output = output,
                        )
                    }
                }
            }
        }

        return output.toMesh()
    }

    fun buildCutCap(
        state: MineWorldState,
        axis: ClipAxis,
        fraction: Float,
        flipped: Boolean,
        tunnelSegments: Collection<TunnelSegment>,
    ): MineMesh {
        val bounds = state.bounds
        val clipValue = clipValue(state, axis, fraction)
        val keptDirection = if (flipped) -1f else 1f
        val plane = clipValue + (keptDirection * CUT_CAP_EPSILON_METRES)
        val output = FloatAccumulator(16_384)

        val uMin: Float
        val uMax: Float
        val vMin: Float
        val vMax: Float
        when (axis) {
            ClipAxis.X -> {
                uMin = bounds.minY
                uMax = bounds.maxY
                vMin = bounds.minZ
                vMax = bounds.maxZ
            }
            ClipAxis.Y -> {
                uMin = bounds.minX
                uMax = bounds.maxX
                vMin = bounds.minZ
                vMax = bounds.maxZ
            }
            ClipAxis.Z -> {
                uMin = bounds.minX
                uMax = bounds.maxX
                vMin = bounds.minY
                vMax = bounds.maxY
            }
        }

        val uCells = ceil((uMax - uMin) / CUT_CAP_STEP_METRES).toInt().coerceAtLeast(1)
        val vCells = ceil((vMax - vMin) / CUT_CAP_STEP_METRES).toInt().coerceAtLeast(1)

        for (vIndex in 0 until vCells) {
            val v0 = vMin + (vIndex * CUT_CAP_STEP_METRES)
            val v1 = minOf(v0 + CUT_CAP_STEP_METRES, vMax)
            for (uIndex in 0 until uCells) {
                val u0 = uMin + (uIndex * CUT_CAP_STEP_METRES)
                val u1 = minOf(u0 + CUT_CAP_STEP_METRES, uMax)
                val centre = capPoint(axis, plane, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f)
                if (
                    MineWorldGeometry.solidMargin(
                        point = centre,
                        bounds = bounds,
                        tunnelRadiusMetres = state.tunnel.radiusMetres,
                        segments = tunnelSegments,
                    ) <= 0f
                ) {
                    continue
                }

                val colour = cutCapColour(centre, state)
                val normal = capNormal(axis, flipped)
                val p00 = capPoint(axis, plane, u0, v0)
                val p10 = capPoint(axis, plane, u1, v0)
                val p11 = capPoint(axis, plane, u1, v1)
                val p01 = capPoint(axis, plane, u0, v1)

                output.appendTriangle(p00, p10, p11, normal, colour)
                output.appendTriangle(p00, p11, p01, normal, colour)
            }
        }

        return output.toMesh()
    }

    fun buildMachine(state: MineWorldState): MineMesh {
        val heading = Math.toRadians(state.machineHeadingDegrees.toDouble())
        val angle = Math.toRadians(state.verticalAngleDegrees.toDouble())
        val cosAngle = cos(angle).toFloat()
        val forward = MinePoint3D(
            x = cos(heading).toFloat() * cosAngle,
            y = sin(heading).toFloat() * cosAngle,
            z = sin(angle).toFloat(),
        )
        val right = MinePoint3D(
            x = -sin(heading).toFloat(),
            y = cos(heading).toFloat(),
            z = 0f,
        )
        val up = MinePoint3D(
            x = cos(heading).toFloat() * sin(angle).toFloat(),
            y = sin(heading).toFloat() * sin(angle).toFloat(),
            z = -cosAngle,
        )

        val nose = state.tunnel.end
        val output = FloatAccumulator(512)
        val bodyCentre = combine(
            nose,
            forward to -2.45f,
            up to 0.15f,
        )
        appendBox(
            output = output,
            centre = bodyCentre,
            forward = forward,
            right = right,
            up = up,
            halfLength = 2.8f,
            halfWidth = 1.45f,
            halfHeight = 1.05f,
            colour = floatArrayOf(0.82f, 0.39f, 0.07f),
        )

        val cutterCentre = combine(nose, forward to -0.12f)
        appendBox(
            output = output,
            centre = cutterCentre,
            forward = forward,
            right = right,
            up = up,
            halfLength = 0.45f,
            halfWidth = 1.85f,
            halfHeight = 1.32f,
            colour = floatArrayOf(0.98f, 0.70f, 0.12f),
        )

        val spineCentre = combine(
            nose,
            forward to -1.35f,
            up to -1.12f,
        )
        appendBox(
            output = output,
            centre = spineCentre,
            forward = forward,
            right = right,
            up = up,
            halfLength = 1.35f,
            halfWidth = 0.18f,
            halfHeight = 0.10f,
            colour = floatArrayOf(1.0f, 0.88f, 0.24f),
        )

        val rearCentre = combine(nose, forward to -5.0f)
        appendBox(
            output = output,
            centre = rearCentre,
            forward = forward,
            right = right,
            up = up,
            halfLength = 0.55f,
            halfWidth = 1.20f,
            halfHeight = 0.92f,
            colour = floatArrayOf(0.16f, 0.19f, 0.22f),
        )
        return output.toMesh()
    }

    fun clipValue(state: MineWorldState, axis: ClipAxis, fraction: Float): Float = when (axis) {
        ClipAxis.X -> state.bounds.minX + (state.bounds.width * fraction)
        ClipAxis.Y -> state.bounds.minY + (state.bounds.height * fraction)
        ClipAxis.Z -> state.bounds.minZ + (state.bounds.depth * fraction)
    }

    private fun polygoniseTetrahedron(
        points: List<MinePoint3D>,
        values: List<Float>,
        state: MineWorldState,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
        output: FloatAccumulator,
    ) {
        val inside = points.indices.filter { values[it] >= 0f }
        val outside = points.indices.filter { values[it] < 0f }
        if (inside.isEmpty() || outside.isEmpty()) return

        when (inside.size) {
            1 -> {
                val i = inside.single()
                emitTriangle(
                    interpolate(points[i], points[outside[0]], values[i], values[outside[0]]),
                    interpolate(points[i], points[outside[1]], values[i], values[outside[1]]),
                    interpolate(points[i], points[outside[2]], values[i], values[outside[2]]),
                    state,
                    tunnelSegments,
                    gridStepMetres,
                    output,
                )
            }

            3 -> {
                val o = outside.single()
                emitTriangle(
                    interpolate(points[o], points[inside[0]], values[o], values[inside[0]]),
                    interpolate(points[o], points[inside[2]], values[o], values[inside[2]]),
                    interpolate(points[o], points[inside[1]], values[o], values[inside[1]]),
                    state,
                    tunnelSegments,
                    gridStepMetres,
                    output,
                )
            }

            2 -> {
                val i0 = inside[0]
                val i1 = inside[1]
                val o0 = outside[0]
                val o1 = outside[1]
                val p0 = interpolate(points[i0], points[o0], values[i0], values[o0])
                val p1 = interpolate(points[i0], points[o1], values[i0], values[o1])
                val p2 = interpolate(points[i1], points[o0], values[i1], values[o0])
                val p3 = interpolate(points[i1], points[o1], values[i1], values[o1])
                emitTriangle(p0, p1, p2, state, tunnelSegments, gridStepMetres, output)
                emitTriangle(p1, p3, p2, state, tunnelSegments, gridStepMetres, output)
            }
        }
    }

    private fun interpolate(
        a: MinePoint3D,
        b: MinePoint3D,
        valueA: Float,
        valueB: Float,
    ): MinePoint3D {
        val denominator = valueA - valueB
        val t = if (abs(denominator) < 0.00001f) 0.5f else (valueA / denominator).coerceIn(0f, 1f)
        return MineWorldGeometry.interpolate(a, b, t)
    }

    private fun emitTriangle(
        first: MinePoint3D,
        second: MinePoint3D,
        third: MinePoint3D,
        state: MineWorldState,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
        output: FloatAccumulator,
    ) {
        var a = first
        var b = second
        var c = third
        var normal = triangleNormal(a, b, c) ?: return
        val centre = MinePoint3D(
            x = (a.x + b.x + c.x) / 3f,
            y = (a.y + b.y + c.y) / 3f,
            z = (a.z + b.z + c.z) / 3f,
        )

        val plus = MineWorldGeometry.solidMargin(
            point = offset(centre, normal, NORMAL_SAMPLE_METRES),
            bounds = state.bounds,
            tunnelRadiusMetres = state.tunnel.radiusMetres,
            segments = tunnelSegments,
        )
        val minus = MineWorldGeometry.solidMargin(
            point = offset(centre, normal, -NORMAL_SAMPLE_METRES),
            bounds = state.bounds,
            tunnelRadiusMetres = state.tunnel.radiusMetres,
            segments = tunnelSegments,
        )
        if (plus > minus) {
            val swap = b
            b = c
            c = swap
            normal = MinePoint3D(-normal.x, -normal.y, -normal.z)
        }

        val colour = surfaceColour(centre, state, tunnelSegments, gridStepMetres)
        output.appendTriangle(a, b, c, normal, colour)
    }

    private fun surfaceColour(
        point: MinePoint3D,
        state: MineWorldState,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
    ): FloatArray {
        val tunnelDistance = MineWorldGeometry.distanceToTunnelSegments(point, tunnelSegments)
        val nearTunnelWall = tunnelDistance <= state.tunnel.radiusMetres + (gridStepMetres * 1.6f)
        val oreAtWall = nearTunnelWall &&
            MineWorldGeometry.oreMargin(point, state.oreBody) >= -(gridStepMetres * 0.75f)
        val activeFace = state.tunnel.points.size > 1 && nearTunnelWall &&
            MineWorldGeometry.distance(point, state.tunnel.end) <= state.tunnel.radiusMetres * 1.35f
        val surfaceGrass = point.z <= state.bounds.minZ + 0.7f && !nearTunnelWall

        return when {
            oreAtWall -> floatArrayOf(0.72f, 0.35f, 0.94f)
            activeFace -> floatArrayOf(0.88f, 0.62f, 0.19f)
            nearTunnelWall -> floatArrayOf(0.23f, 0.26f, 0.29f)
            surfaceGrass -> floatArrayOf(0.20f, 0.48f, 0.22f)
            else -> rockColour(point, state)
        }
    }

    private fun cutCapColour(point: MinePoint3D, state: MineWorldState): FloatArray {
        if (state.oreBodyDiscovered && MineWorldGeometry.oreMargin(point, state.oreBody) >= 0f) {
            return floatArrayOf(0.74f, 0.34f, 0.95f)
        }
        if (point.z <= state.bounds.minZ + 0.45f) {
            return floatArrayOf(0.22f, 0.50f, 0.23f)
        }
        return rockColour(point, state)
    }

    private fun rockColour(point: MinePoint3D, state: MineWorldState): FloatArray {
        val depthShade = (point.z / max(1f, state.bounds.maxZ)).coerceIn(0f, 1f)
        return floatArrayOf(
            0.48f - (depthShade * 0.10f),
            0.46f - (depthShade * 0.09f),
            0.43f - (depthShade * 0.07f),
        )
    }

    private fun capPoint(axis: ClipAxis, plane: Float, u: Float, v: Float): MinePoint3D = when (axis) {
        ClipAxis.X -> MinePoint3D(plane, u, v)
        ClipAxis.Y -> MinePoint3D(u, plane, v)
        ClipAxis.Z -> MinePoint3D(u, v, plane)
    }

    private fun capNormal(axis: ClipAxis, flipped: Boolean): MinePoint3D {
        val sign = if (flipped) 1f else -1f
        return when (axis) {
            ClipAxis.X -> MinePoint3D(sign, 0f, 0f)
            ClipAxis.Y -> MinePoint3D(0f, sign, 0f)
            ClipAxis.Z -> MinePoint3D(0f, 0f, sign)
        }
    }

    private fun appendBox(
        output: FloatAccumulator,
        centre: MinePoint3D,
        forward: MinePoint3D,
        right: MinePoint3D,
        up: MinePoint3D,
        halfLength: Float,
        halfWidth: Float,
        halfHeight: Float,
        colour: FloatArray,
    ) {
        fun corner(f: Float, r: Float, u: Float): MinePoint3D = combine(
            centre,
            forward to (f * halfLength),
            right to (r * halfWidth),
            up to (u * halfHeight),
        )

        val p000 = corner(-1f, -1f, -1f)
        val p001 = corner(-1f, -1f, 1f)
        val p010 = corner(-1f, 1f, -1f)
        val p011 = corner(-1f, 1f, 1f)
        val p100 = corner(1f, -1f, -1f)
        val p101 = corner(1f, -1f, 1f)
        val p110 = corner(1f, 1f, -1f)
        val p111 = corner(1f, 1f, 1f)

        appendQuad(output, p100, p110, p111, p101, forward, colour)
        appendQuad(output, p010, p000, p001, p011, negate(forward), colour)
        appendQuad(output, p110, p010, p011, p111, right, colour)
        appendQuad(output, p000, p100, p101, p001, negate(right), colour)
        appendQuad(output, p101, p111, p011, p001, up, colour)
        appendQuad(output, p000, p010, p110, p100, negate(up), colour)
    }

    private fun appendQuad(
        output: FloatAccumulator,
        a: MinePoint3D,
        b: MinePoint3D,
        c: MinePoint3D,
        d: MinePoint3D,
        normal: MinePoint3D,
        colour: FloatArray,
    ) {
        output.appendTriangle(a, b, c, normal, colour)
        output.appendTriangle(a, c, d, normal, colour)
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

    private fun negate(point: MinePoint3D) = MinePoint3D(-point.x, -point.y, -point.z)

    private fun triangleNormal(
        a: MinePoint3D,
        b: MinePoint3D,
        c: MinePoint3D,
    ): MinePoint3D? {
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

    private fun offset(point: MinePoint3D, direction: MinePoint3D, distance: Float) = MinePoint3D(
        x = point.x + (direction.x * distance),
        y = point.y + (direction.y * distance),
        z = point.z + (direction.z * distance),
    )

    private class FloatAccumulator(initialCapacity: Int = 32_768) {
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
            ensureCapacity(FLOATS_PER_VERTEX)
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

        fun toMesh(): MineMesh {
            val vertices = data.copyOf(size)
            return MineMesh(vertices = vertices, vertexCount = vertices.size / FLOATS_PER_VERTEX)
        }

        private fun ensureCapacity(additional: Int) {
            if (size + additional <= data.size) return
            data = data.copyOf(max(data.size * 2, size + additional))
        }
    }

    private const val FLOATS_PER_VERTEX = 9
}
