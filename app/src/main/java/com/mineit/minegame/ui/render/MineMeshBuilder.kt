package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MineMesh(
    val vertices: FloatArray,
    val vertexCount: Int,
)

object MineMeshBuilder {
    private const val GRID_STEP_METRES = 2f
    private const val NORMAL_SAMPLE_METRES = 0.45f

    private val tetrahedra = arrayOf(
        intArrayOf(0, 5, 1, 6),
        intArrayOf(0, 1, 2, 6),
        intArrayOf(0, 2, 3, 6),
        intArrayOf(0, 3, 7, 6),
        intArrayOf(0, 7, 4, 6),
        intArrayOf(0, 4, 5, 6),
    )

    fun build(state: MineWorldState): MineMesh {
        val bounds = state.bounds
        val minX = bounds.minX - GRID_STEP_METRES
        val maxX = bounds.maxX + GRID_STEP_METRES
        val minY = bounds.minY - GRID_STEP_METRES
        val maxY = bounds.maxY + GRID_STEP_METRES
        val minZ = bounds.minZ - GRID_STEP_METRES
        val maxZ = bounds.maxZ + GRID_STEP_METRES

        val xCount = (((maxX - minX) / GRID_STEP_METRES).roundToInt() + 1).coerceAtLeast(2)
        val yCount = (((maxY - minY) / GRID_STEP_METRES).roundToInt() + 1).coerceAtLeast(2)
        val zCount = (((maxZ - minZ) / GRID_STEP_METRES).roundToInt() + 1).coerceAtLeast(2)

        val values = FloatArray(xCount * yCount * zCount)
        fun index(x: Int, y: Int, z: Int): Int = ((z * yCount) + y) * xCount + x
        fun point(x: Int, y: Int, z: Int) = MinePoint3D(
            x = minX + (x * GRID_STEP_METRES),
            y = minY + (y * GRID_STEP_METRES),
            z = minZ + (z * GRID_STEP_METRES),
        )

        for (z in 0 until zCount) {
            for (y in 0 until yCount) {
                for (x in 0 until xCount) {
                    values[index(x, y, z)] = MineWorldGeometry.solidMargin(
                        point = point(x, y, z),
                        bounds = bounds,
                        tunnel = state.tunnel,
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
                            output = output,
                        )
                    }
                }
            }
        }

        val vertices = output.toArray()
        return MineMesh(
            vertices = vertices,
            vertexCount = vertices.size / FLOATS_PER_VERTEX,
        )
    }

    private fun polygoniseTetrahedron(
        points: List<MinePoint3D>,
        values: List<Float>,
        state: MineWorldState,
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
                emitTriangle(p0, p1, p2, state, output)
                emitTriangle(p1, p3, p2, state, output)
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
            tunnel = state.tunnel,
        )
        val minus = MineWorldGeometry.solidMargin(
            point = offset(centre, normal, -NORMAL_SAMPLE_METRES),
            bounds = state.bounds,
            tunnel = state.tunnel,
        )
        if (plus > minus) {
            val swap = b
            b = c
            c = swap
            normal = MinePoint3D(-normal.x, -normal.y, -normal.z)
        }

        val colour = surfaceColour(centre, state)
        output.appendVertex(a, normal, colour)
        output.appendVertex(b, normal, colour)
        output.appendVertex(c, normal, colour)
    }

    private fun surfaceColour(point: MinePoint3D, state: MineWorldState): FloatArray {
        val tunnelDistance = MineWorldGeometry.distanceToTunnel(point, state.tunnel)
        val nearTunnelWall = tunnelDistance <= state.tunnel.radiusMetres + (GRID_STEP_METRES * 1.6f)
        val oreAtWall = nearTunnelWall &&
            MineWorldGeometry.oreMargin(point, state.oreBody) >= -(GRID_STEP_METRES * 0.75f)
        val activeFace = nearTunnelWall &&
            MineWorldGeometry.distance(point, state.tunnel.end) <= state.tunnel.radiusMetres * 1.35f

        return when {
            oreAtWall -> floatArrayOf(0.72f, 0.35f, 0.94f)
            activeFace -> floatArrayOf(0.86f, 0.64f, 0.24f)
            nearTunnelWall -> floatArrayOf(0.25f, 0.28f, 0.31f)
            else -> {
                val depthShade = (point.z / max(1f, state.bounds.maxZ)).coerceIn(0f, 1f)
                floatArrayOf(
                    0.48f - (depthShade * 0.08f),
                    0.50f - (depthShade * 0.08f),
                    0.53f - (depthShade * 0.07f),
                )
            }
        }
    }

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

        fun appendVertex(point: MinePoint3D, normal: MinePoint3D, colour: FloatArray) {
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

        fun toArray(): FloatArray = data.copyOf(size)

        private fun ensureCapacity(additional: Int) {
            if (size + additional <= data.size) return
            data = data.copyOf(max(data.size * 2, size + additional))
        }
    }

    private const val FLOATS_PER_VERTEX = 9
}
