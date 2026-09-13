package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldBounds
import com.mineit.minegame.domain.MineWorldGeometry
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

internal object ScalarFieldMesher {
    private val tetrahedra = arrayOf(
        intArrayOf(0, 5, 1, 6), intArrayOf(0, 1, 2, 6), intArrayOf(0, 2, 3, 6),
        intArrayOf(0, 3, 7, 6), intArrayOf(0, 7, 4, 6), intArrayOf(0, 4, 5, 6),
    )

    fun buildVolume(
        bounds: MineWorldBounds,
        gridStepMetres: Float,
        colour: FloatArray,
        field: (MinePoint3D) -> Float,
    ): MineMesh {
        if (bounds.width <= 0f || bounds.height <= 0f || bounds.depth <= 0f) return emptyMesh()
        val xCells = cells(bounds.width, gridStepMetres)
        val yCells = cells(bounds.height, gridStepMetres)
        val zCells = cells(bounds.depth, gridStepMetres)
        val xCount = xCells + 1
        val yCount = yCells + 1
        val zCount = zCells + 1
        val dx = bounds.width / xCells
        val dy = bounds.height / yCells
        val dz = bounds.depth / zCells
        fun point(x: Int, y: Int, z: Int) = MinePoint3D(
            bounds.minX + x * dx, bounds.minY + y * dy, bounds.minZ + z * dz,
        )
        fun index(x: Int, y: Int, z: Int) = ((z * yCount) + y) * xCount + x
        val values = FloatArray(xCount * yCount * zCount)
        for (z in 0 until zCount) for (y in 0 until yCount) for (x in 0 until xCount) {
            values[index(x, y, z)] = field(point(x, y, z))
        }
        val output = MeshAccumulator()
        for (z in 0 until zCells) for (y in 0 until yCells) for (x in 0 until xCells) {
            val corners = arrayOf(
                point(x, y, z), point(x + 1, y, z), point(x + 1, y + 1, z), point(x, y + 1, z),
                point(x, y, z + 1), point(x + 1, y, z + 1), point(x + 1, y + 1, z + 1), point(x, y + 1, z + 1),
            )
            val cornerValues = floatArrayOf(
                values[index(x, y, z)], values[index(x + 1, y, z)],
                values[index(x + 1, y + 1, z)], values[index(x, y + 1, z)],
                values[index(x, y, z + 1)], values[index(x + 1, y, z + 1)],
                values[index(x + 1, y + 1, z + 1)], values[index(x, y + 1, z + 1)],
            )
            tetrahedra.forEach { tetra ->
                polygoniseTetrahedron(
                    tetra.map { corners[it] }, tetra.map { cornerValues[it] }, colour, output,
                )
            }
        }
        return output.toMesh()
    }

    fun buildSlice(
        bounds: MineWorldBounds,
        axis: ClipAxis,
        samplePlaneValue: Float,
        renderPlaneValue: Float,
        gridStepMetres: Float,
        normal: MinePoint3D,
        colour: FloatArray,
        field: (MinePoint3D) -> Float,
    ): MineMesh {
        val dimensions = when (axis) {
            ClipAxis.X -> floatArrayOf(bounds.minY, bounds.maxY, bounds.minZ, bounds.maxZ)
            ClipAxis.Y -> floatArrayOf(bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ)
            ClipAxis.Z -> floatArrayOf(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY)
        }
        val minU = dimensions[0]
        val maxU = dimensions[1]
        val minV = dimensions[2]
        val maxV = dimensions[3]
        val width = maxU - minU
        val height = maxV - minV
        if (width <= 0f || height <= 0f) return emptyMesh()
        val uCells = cells(width, gridStepMetres)
        val vCells = cells(height, gridStepMetres)
        val uCount = uCells + 1
        val vCount = vCells + 1
        val du = width / uCells
        val dv = height / vCells
        fun point(u: Float, v: Float, plane: Float) = when (axis) {
            ClipAxis.X -> MinePoint3D(plane, u, v)
            ClipAxis.Y -> MinePoint3D(u, plane, v)
            ClipAxis.Z -> MinePoint3D(u, v, plane)
        }
        fun index(u: Int, v: Int) = v * uCount + u
        val values = FloatArray(uCount * vCount)
        for (v in 0 until vCount) for (u in 0 until uCount) {
            values[index(u, v)] = field(point(minU + u * du, minV + v * dv, samplePlaneValue))
        }
        val output = MeshAccumulator(8192)
        for (v in 0 until vCells) for (u in 0 until uCells) {
            val u0 = minU + u * du
            val u1 = minU + (u + 1) * du
            val v0 = minV + v * dv
            val v1 = minV + (v + 1) * dv
            val p00 = SliceVertex(point(u0, v0, renderPlaneValue), values[index(u, v)])
            val p10 = SliceVertex(point(u1, v0, renderPlaneValue), values[index(u + 1, v)])
            val p11 = SliceVertex(point(u1, v1, renderPlaneValue), values[index(u + 1, v + 1)])
            val p01 = SliceVertex(point(u0, v1, renderPlaneValue), values[index(u, v + 1)])
            appendPositiveSliceTriangle(output, listOf(p00, p10, p11), normal, colour)
            appendPositiveSliceTriangle(output, listOf(p00, p11, p01), normal, colour)
        }
        return output.toMesh()
    }

    private fun polygoniseTetrahedron(
        points: List<MinePoint3D>, values: List<Float>, colour: FloatArray, output: MeshAccumulator,
    ) {
        val solid = points.indices.filter { values[it] >= 0f }
        val air = points.indices.filter { values[it] < 0f }
        if (solid.isEmpty() || air.isEmpty()) return
        val insideReference = average(solid.map { points[it] })
        fun edge(a: Int, b: Int) = interpolate(points[a], points[b], values[a], values[b])
        when (solid.size) {
            1 -> {
                val s = solid.single()
                emit(edge(s, air[0]), edge(s, air[1]), edge(s, air[2]), insideReference, colour, output)
            }
            3 -> {
                val a = air.single()
                emit(edge(a, solid[0]), edge(a, solid[2]), edge(a, solid[1]), insideReference, colour, output)
            }
            2 -> {
                val s0 = solid[0]
                val s1 = solid[1]
                val a0 = air[0]
                val a1 = air[1]
                val p0 = edge(s0, a0)
                val p1 = edge(s0, a1)
                val p2 = edge(s1, a0)
                val p3 = edge(s1, a1)
                emit(p0, p1, p2, insideReference, colour, output)
                emit(p1, p3, p2, insideReference, colour, output)
            }
        }
    }

    private fun emit(
        a: MinePoint3D, b: MinePoint3D, c: MinePoint3D,
        insideReference: MinePoint3D, colour: FloatArray, output: MeshAccumulator,
    ) {
        var normal = triangleNormal(a, b, c) ?: return
        val centre = average(listOf(a, b, c))
        val towardInside = MinePoint3D(
            insideReference.x - centre.x, insideReference.y - centre.y, insideReference.z - centre.z,
        )
        if (dot(normal, towardInside) > 0f) {
            normal = MinePoint3D(-normal.x, -normal.y, -normal.z)
            output.appendTriangle(a, c, b, normal, colour)
        } else {
            output.appendTriangle(a, b, c, normal, colour)
        }
    }

    private data class SliceVertex(val point: MinePoint3D, val value: Float)

    private fun appendPositiveSliceTriangle(
        output: MeshAccumulator, triangle: List<SliceVertex>, normal: MinePoint3D, colour: FloatArray,
    ) {
        var previous = triangle.last()
        var previousInside = previous.value >= 0f
        val clipped = ArrayList<MinePoint3D>(4)
        triangle.forEach { current ->
            val currentInside = current.value >= 0f
            when {
                currentInside && previousInside -> clipped += current.point
                currentInside && !previousInside -> {
                    clipped += interpolate(previous.point, current.point, previous.value, current.value)
                    clipped += current.point
                }
                !currentInside && previousInside -> clipped +=
                    interpolate(previous.point, current.point, previous.value, current.value)
            }
            previous = current
            previousInside = currentInside
        }
        if (clipped.size < 3) return
        val anchor = clipped[0]
        for (i in 1 until clipped.lastIndex) {
            val b = clipped[i]
            val c = clipped[i + 1]
            val geometric = triangleNormal(anchor, b, c) ?: continue
            if (dot(geometric, normal) < 0f) output.appendTriangle(anchor, c, b, normal, colour)
            else output.appendTriangle(anchor, b, c, normal, colour)
        }
    }

    private fun interpolate(a: MinePoint3D, b: MinePoint3D, valueA: Float, valueB: Float): MinePoint3D {
        val denominator = valueA - valueB
        val t = if (abs(denominator) < 0.00001f) 0.5f else (valueA / denominator).coerceIn(0f, 1f)
        return MineWorldGeometry.interpolate(a, b, t)
    }

    private fun cells(span: Float, step: Float) = ceil(span / step.coerceAtLeast(0.05f)).toInt().coerceAtLeast(1)

    private fun average(points: List<MinePoint3D>): MinePoint3D {
        val count = points.size.toFloat().coerceAtLeast(1f)
        return MinePoint3D(
            points.sumOf { it.x.toDouble() }.toFloat() / count,
            points.sumOf { it.y.toDouble() }.toFloat() / count,
            points.sumOf { it.z.toDouble() }.toFloat() / count,
        )
    }

    private fun dot(a: MinePoint3D, b: MinePoint3D) = a.x * b.x + a.y * b.y + a.z * b.z

    private fun triangleNormal(a: MinePoint3D, b: MinePoint3D, c: MinePoint3D): MinePoint3D? {
        val ux = b.x - a.x
        val uy = b.y - a.y
        val uz = b.z - a.z
        val vx = c.x - a.x
        val vy = c.y - a.y
        val vz = c.z - a.z
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz)
        if (length < 0.0001f) return null
        return MinePoint3D(nx / length, ny / length, nz / length)
    }

    private fun emptyMesh() = MineMesh(FloatArray(0), 0)

    private class MeshAccumulator(initialCapacity: Int = 32768) {
        private var data = FloatArray(initialCapacity.coerceAtLeast(64))
        private var size = 0

        fun appendTriangle(a: MinePoint3D, b: MinePoint3D, c: MinePoint3D, normal: MinePoint3D, colour: FloatArray) {
            appendVertex(a, normal, colour)
            appendVertex(b, normal, colour)
            appendVertex(c, normal, colour)
        }

        private fun appendVertex(point: MinePoint3D, normal: MinePoint3D, colour: FloatArray) {
            ensureCapacity(9)
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

        private fun ensureCapacity(extra: Int) {
            if (size + extra <= data.size) return
            var nextSize = data.size * 2
            while (nextSize < size + extra) nextSize *= 2
            data = data.copyOf(nextSize)
        }

        fun toMesh(): MineMesh = MineMesh(data.copyOf(size), size / 9)
    }
}
