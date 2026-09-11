package com.mineit.minegame.ui.render

import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MineWorldGeometry
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.domain.TunnelSegment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class MineMesh(
    val vertices: FloatArray,
    val vertexCount: Int,
)

/**
 * Generates presentation geometry from the canonical 3D mine state.
 *
 * The expensive scalar-field polygoniser is deliberately restricted to tunnel-bearing chunks.
 * The enclosing geological block is one global shell, while the CT face is an analytic plane with
 * analytic tunnel/ore intersections. This keeps world growth and slice movement independent from
 * the amount of untouched rock in the enclosing volume.
 */
internal object MineMeshBuilder {
    const val ACTIVE_GRID_STEP_METRES = 1.6f
    const val REFINED_GRID_STEP_METRES = 1.0f

    private const val TUNNEL_OVERVIEW_RING_SEGMENTS = 14
    private const val SLICE_DISC_SEGMENTS = 16
    private const val SLICE_BASE_OFFSET_METRES = 0.035f
    private const val SLICE_ORE_OFFSET_METRES = 0.055f
    private const val SLICE_TUNNEL_OFFSET_METRES = 0.075f
    private const val SLICE_DEPTH_BAND_METRES = 12f
    private const val ORE_SLICE_SAMPLE_METRES = 2.2f
    private const val GRASS_GRID_STEP_METRES = 2f

    private val tetrahedra = arrayOf(
        intArrayOf(0, 5, 1, 6),
        intArrayOf(0, 1, 2, 6),
        intArrayOf(0, 2, 3, 6),
        intArrayOf(0, 3, 7, 6),
        intArrayOf(0, 7, 4, 6),
        intArrayOf(0, 4, 5, 6),
    )

    /**
     * Detailed chunks now contain tunnel-wall geometry only. World-boundary faces are never
     * polygonised here; the global shell owns those surfaces.
     */
    fun buildChunk(
        state: MineWorldState,
        key: ChunkKey,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
    ): MineMesh {
        if (tunnelSegments.isEmpty()) return emptyMesh()

        val chunk = key.bounds()
        val minX = chunk.minX
        val maxX = chunk.maxX
        val minY = chunk.minY
        val maxY = chunk.maxY
        val minZ = chunk.minZ
        val maxZ = chunk.maxZ

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
                    values[index(x, y, z)] =
                        MineWorldGeometry.distanceToTunnelSegments(point(x, y, z), tunnelSegments) -
                            state.tunnel.radiusMetres
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

    /**
     * The geological block shell is constant-cost regardless of world size. The top face is owned
     * separately by [buildGrassSurface] so surface tunnel openings remain visible.
     */
    fun buildWorldShell(state: MineWorldState): MineMesh {
        val bounds = state.bounds
        val output = FloatAccumulator(128)

        fun colourAt(point: MinePoint3D) = rockColour(point, state)

        val leftCentre = MinePoint3D(bounds.minX, bounds.centre.y, bounds.centre.z)
        appendQuad(
            output,
            MinePoint3D(bounds.minX, bounds.minY, bounds.minZ),
            MinePoint3D(bounds.minX, bounds.minY, bounds.maxZ),
            MinePoint3D(bounds.minX, bounds.maxY, bounds.maxZ),
            MinePoint3D(bounds.minX, bounds.maxY, bounds.minZ),
            MinePoint3D(-1f, 0f, 0f),
            colourAt(leftCentre),
        )

        val rightCentre = MinePoint3D(bounds.maxX, bounds.centre.y, bounds.centre.z)
        appendQuad(
            output,
            MinePoint3D(bounds.maxX, bounds.minY, bounds.minZ),
            MinePoint3D(bounds.maxX, bounds.maxY, bounds.minZ),
            MinePoint3D(bounds.maxX, bounds.maxY, bounds.maxZ),
            MinePoint3D(bounds.maxX, bounds.minY, bounds.maxZ),
            MinePoint3D(1f, 0f, 0f),
            colourAt(rightCentre),
        )

        val frontCentre = MinePoint3D(bounds.centre.x, bounds.minY, bounds.centre.z)
        appendQuad(
            output,
            MinePoint3D(bounds.minX, bounds.minY, bounds.minZ),
            MinePoint3D(bounds.maxX, bounds.minY, bounds.minZ),
            MinePoint3D(bounds.maxX, bounds.minY, bounds.maxZ),
            MinePoint3D(bounds.minX, bounds.minY, bounds.maxZ),
            MinePoint3D(0f, -1f, 0f),
            colourAt(frontCentre),
        )

        val backCentre = MinePoint3D(bounds.centre.x, bounds.maxY, bounds.centre.z)
        appendQuad(
            output,
            MinePoint3D(bounds.minX, bounds.maxY, bounds.minZ),
            MinePoint3D(bounds.minX, bounds.maxY, bounds.maxZ),
            MinePoint3D(bounds.maxX, bounds.maxY, bounds.maxZ),
            MinePoint3D(bounds.maxX, bounds.maxY, bounds.minZ),
            MinePoint3D(0f, 1f, 0f),
            colourAt(backCentre),
        )

        val bottomCentre = MinePoint3D(bounds.centre.x, bounds.centre.y, bounds.maxZ)
        appendQuad(
            output,
            MinePoint3D(bounds.minX, bounds.minY, bounds.maxZ),
            MinePoint3D(bounds.maxX, bounds.minY, bounds.maxZ),
            MinePoint3D(bounds.maxX, bounds.maxY, bounds.maxZ),
            MinePoint3D(bounds.minX, bounds.maxY, bounds.maxZ),
            MinePoint3D(0f, 0f, 1f),
            colourAt(bottomCentre),
        )

        return output.toMesh()
    }

    /**
     * Constant-area CT construction: one rock plane plus only the tunnel and ore intersections that
     * actually touch that plane. It no longer scans a 2D grid across the whole generated world.
     */
    fun buildCutCap(
        state: MineWorldState,
        axis: ClipAxis,
        fraction: Float,
        flipped: Boolean,
        tunnelSegments: Collection<TunnelSegment>,
    ): MineMesh {
        val bounds = state.bounds
        val clip = clipValue(state, axis, fraction)
        val normal = capNormal(axis, flipped)
        val normalSign = axisCoordinate(normal, axis)
        val basePlane = clip + (normalSign * SLICE_BASE_OFFSET_METRES)
        val orePlane = clip + (normalSign * SLICE_ORE_OFFSET_METRES)
        val tunnelPlane = clip + (normalSign * SLICE_TUNNEL_OFFSET_METRES)
        val output = FloatAccumulator(8_192)

        appendRockSliceBase(output, state, axis, basePlane, normal)

        if (state.oreBodyDiscovered) {
            state.oreBody.zipWithNext().forEach { (start, end) ->
                val length = MineWorldGeometry.distance(start.centre, end.centre)
                val steps = max(1, ceil(length / ORE_SLICE_SAMPLE_METRES).toInt())
                for (step in 0..steps) {
                    val t = step.toFloat() / steps.toFloat()
                    val centre = MineWorldGeometry.interpolate(start.centre, end.centre, t)
                    val radius = start.radiusMetres + ((end.radiusMetres - start.radiusMetres) * t)
                    appendSliceDiscIfIntersecting(
                        output = output,
                        axis = axis,
                        clipValue = clip,
                        planeValue = orePlane,
                        centre = centre,
                        radius = radius,
                        normal = normal,
                        colour = ORE_COLOUR,
                    )
                }
            }
        }

        val tunnelRadius = state.tunnel.radiusMetres
        val sampleSpacing = max(0.8f, tunnelRadius * 0.55f)
        tunnelSegments.forEach { segment ->
            val length = MineWorldGeometry.distance(segment.start, segment.end)
            val steps = max(1, ceil(length / sampleSpacing).toInt())
            for (step in 0..steps) {
                val t = step.toFloat() / steps.toFloat()
                appendSliceDiscIfIntersecting(
                    output = output,
                    axis = axis,
                    clipValue = clip,
                    planeValue = tunnelPlane,
                    centre = MineWorldGeometry.interpolate(segment.start, segment.end, t),
                    radius = tunnelRadius,
                    normal = normal,
                    colour = TUNNEL_COLOUR,
                )
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
        appendBox(
            output = output,
            centre = combine(nose, forward to -2.45f, up to 0.15f),
            forward = forward,
            right = right,
            up = up,
            halfLength = 2.8f,
            halfWidth = 1.45f,
            halfHeight = 1.05f,
            colour = floatArrayOf(0.82f, 0.39f, 0.07f),
        )
        appendBox(
            output = output,
            centre = combine(nose, forward to -0.12f),
            forward = forward,
            right = right,
            up = up,
            halfLength = 0.45f,
            halfWidth = 1.85f,
            halfHeight = 1.32f,
            colour = floatArrayOf(0.98f, 0.70f, 0.12f),
        )
        appendBox(
            output = output,
            centre = combine(nose, forward to -1.35f, up to -1.12f),
            forward = forward,
            right = right,
            up = up,
            halfLength = 1.35f,
            halfWidth = 0.18f,
            halfHeight = 0.10f,
            colour = floatArrayOf(1.0f, 0.88f, 0.24f),
        )
        appendBox(
            output = output,
            centre = combine(nose, forward to -5.0f),
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

    /** Lightweight direct tunnel skin used by ROCK OFF. */
    fun buildTunnelOverview(state: MineWorldState): MineMesh {
        val points = state.tunnel.points
        if (points.size < 2) return emptyMesh()

        val radius = state.tunnel.radiusMetres
        val rings = Array(points.size) { index ->
            val previous = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(points.lastIndex)]
            val tangent = normalized(
                MinePoint3D(
                    x = next.x - previous.x,
                    y = next.y - previous.y,
                    z = next.z - previous.z,
                ),
            )
            val reference = if (abs(tangent.z) < 0.85f) {
                MinePoint3D(0f, 0f, 1f)
            } else {
                MinePoint3D(1f, 0f, 0f)
            }
            val right = normalized(cross(reference, tangent))
            val up = normalized(cross(tangent, right))
            Array(TUNNEL_OVERVIEW_RING_SEGMENTS) { ringIndex ->
                val radians = 2.0 * PI * ringIndex.toDouble() / TUNNEL_OVERVIEW_RING_SEGMENTS.toDouble()
                combine(
                    points[index],
                    right to (cos(radians).toFloat() * radius),
                    up to (sin(radians).toFloat() * radius),
                )
            }
        }

        val output = FloatAccumulator(points.size * TUNNEL_OVERVIEW_RING_SEGMENTS * 18)
        for (index in 0 until points.lastIndex) {
            val midpoint = MineWorldGeometry.interpolate(points[index], points[index + 1], 0.5f)
            val colour = if (
                state.oreBodyDiscovered &&
                MineWorldGeometry.oreMargin(midpoint, state.oreBody) >= -(radius * 0.65f)
            ) {
                ORE_COLOUR
            } else {
                TUNNEL_COLOUR
            }
            for (ringIndex in 0 until TUNNEL_OVERVIEW_RING_SEGMENTS) {
                val nextRing = (ringIndex + 1) % TUNNEL_OVERVIEW_RING_SEGMENTS
                val a = rings[index][ringIndex]
                val b = rings[index + 1][ringIndex]
                val c = rings[index + 1][nextRing]
                val d = rings[index][nextRing]
                triangleNormal(a, b, c)?.let { output.appendTriangle(a, b, c, it, colour) }
                triangleNormal(a, c, d)?.let { output.appendTriangle(a, c, d, it, colour) }
            }
        }
        return output.toMesh()
    }

    /** Grass is a separate surface so the global rock shell can remain constant-cost. */
    fun buildGrassSurface(state: MineWorldState): MineMesh {
        val bounds = state.bounds
        val radius = state.tunnel.radiusMetres
        val surfaceSegments = state.tunnel.segments.filter { segment ->
            min(segment.start.z, segment.end.z) <= radius + GRASS_GRID_STEP_METRES
        }
        val xCells = ceil(bounds.width / GRASS_GRID_STEP_METRES).toInt().coerceAtLeast(1)
        val yCells = ceil(bounds.height / GRASS_GRID_STEP_METRES).toInt().coerceAtLeast(1)
        val output = FloatAccumulator(xCells * yCells * 18)
        val normal = MinePoint3D(0f, 0f, -1f)

        for (yIndex in 0 until yCells) {
            val y0 = bounds.minY + (yIndex * GRASS_GRID_STEP_METRES)
            val y1 = min(y0 + GRASS_GRID_STEP_METRES, bounds.maxY)
            for (xIndex in 0 until xCells) {
                val x0 = bounds.minX + (xIndex * GRASS_GRID_STEP_METRES)
                val x1 = min(x0 + GRASS_GRID_STEP_METRES, bounds.maxX)
                val centre = MinePoint3D((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, bounds.minZ)
                if (
                    surfaceSegments.isNotEmpty() &&
                    MineWorldGeometry.distanceToTunnelSegments(centre, surfaceSegments) <=
                    radius + (GRASS_GRID_STEP_METRES * 0.55f)
                ) {
                    continue
                }

                val p00 = MinePoint3D(x0, y0, bounds.minZ)
                val p10 = MinePoint3D(x1, y0, bounds.minZ)
                val p11 = MinePoint3D(x1, y1, bounds.minZ)
                val p01 = MinePoint3D(x0, y1, bounds.minZ)
                output.appendTriangle(p00, p11, p10, normal, GRASS_COLOUR)
                output.appendTriangle(p00, p01, p11, normal, GRASS_COLOUR)
            }
        }
        return output.toMesh()
    }

    fun clipValue(state: MineWorldState, axis: ClipAxis, fraction: Float): Float = when (axis) {
        ClipAxis.X -> state.bounds.minX + (state.bounds.width * fraction)
        ClipAxis.Y -> state.bounds.minY + (state.bounds.height * fraction)
        ClipAxis.Z -> state.bounds.minZ + (state.bounds.depth * fraction)
    }

    private fun appendRockSliceBase(
        output: FloatAccumulator,
        state: MineWorldState,
        axis: ClipAxis,
        plane: Float,
        normal: MinePoint3D,
    ) {
        val bounds = state.bounds
        when (axis) {
            ClipAxis.Z -> {
                val centre = MinePoint3D(bounds.centre.x, bounds.centre.y, plane)
                val colour = if (plane <= bounds.minZ + 0.45f) GRASS_COLOUR else rockColour(centre, state)
                appendQuad(
                    output,
                    MinePoint3D(bounds.minX, bounds.minY, plane),
                    MinePoint3D(bounds.maxX, bounds.minY, plane),
                    MinePoint3D(bounds.maxX, bounds.maxY, plane),
                    MinePoint3D(bounds.minX, bounds.maxY, plane),
                    normal,
                    colour,
                )
            }

            ClipAxis.X, ClipAxis.Y -> {
                var z0 = bounds.minZ
                while (z0 < bounds.maxZ - 0.001f) {
                    val z1 = min(z0 + SLICE_DEPTH_BAND_METRES, bounds.maxZ)
                    if (axis == ClipAxis.X) {
                        val centre = MinePoint3D(plane, bounds.centre.y, (z0 + z1) * 0.5f)
                        appendQuad(
                            output,
                            MinePoint3D(plane, bounds.minY, z0),
                            MinePoint3D(plane, bounds.maxY, z0),
                            MinePoint3D(plane, bounds.maxY, z1),
                            MinePoint3D(plane, bounds.minY, z1),
                            normal,
                            rockColour(centre, state),
                        )
                    } else {
                        val centre = MinePoint3D(bounds.centre.x, plane, (z0 + z1) * 0.5f)
                        appendQuad(
                            output,
                            MinePoint3D(bounds.minX, plane, z0),
                            MinePoint3D(bounds.minX, plane, z1),
                            MinePoint3D(bounds.maxX, plane, z1),
                            MinePoint3D(bounds.maxX, plane, z0),
                            normal,
                            rockColour(centre, state),
                        )
                    }
                    z0 = z1
                }
            }
        }
    }

    private fun appendSliceDiscIfIntersecting(
        output: FloatAccumulator,
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
        if (crossRadius < 0.08f) return

        val projected = withAxis(centre, axis, planeValue)
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

    private fun capNormal(axis: ClipAxis, flipped: Boolean): MinePoint3D {
        val sign = if (flipped) 1f else -1f
        return when (axis) {
            ClipAxis.X -> MinePoint3D(sign, 0f, 0f)
            ClipAxis.Y -> MinePoint3D(0f, sign, 0f)
            ClipAxis.Z -> MinePoint3D(0f, 0f, sign)
        }
    }

    private fun polygoniseTetrahedron(
        points: List<MinePoint3D>,
        values: List<Float>,
        state: MineWorldState,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
        output: FloatAccumulator,
    ) {
        val solid = points.indices.filter { values[it] >= 0f }
        val air = points.indices.filter { values[it] < 0f }
        if (solid.isEmpty() || air.isEmpty()) return

        when (solid.size) {
            1 -> {
                val inside = solid.single()
                emitTriangle(
                    interpolate(points[inside], points[air[0]], values[inside], values[air[0]]),
                    interpolate(points[inside], points[air[1]], values[inside], values[air[1]]),
                    interpolate(points[inside], points[air[2]], values[inside], values[air[2]]),
                    state,
                    tunnelSegments,
                    gridStepMetres,
                    output,
                )
            }

            3 -> {
                val outside = air.single()
                emitTriangle(
                    interpolate(points[outside], points[solid[0]], values[outside], values[solid[0]]),
                    interpolate(points[outside], points[solid[2]], values[outside], values[solid[2]]),
                    interpolate(points[outside], points[solid[1]], values[outside], values[solid[1]]),
                    state,
                    tunnelSegments,
                    gridStepMetres,
                    output,
                )
            }

            2 -> {
                val s0 = solid[0]
                val s1 = solid[1]
                val a0 = air[0]
                val a1 = air[1]
                val p0 = interpolate(points[s0], points[a0], values[s0], values[a0])
                val p1 = interpolate(points[s0], points[a1], values[s0], values[a1])
                val p2 = interpolate(points[s1], points[a0], values[s1], values[a0])
                val p3 = interpolate(points[s1], points[a1], values[s1], values[a1])
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
        val normal = triangleNormal(first, second, third) ?: return
        val centre = MinePoint3D(
            x = (first.x + second.x + third.x) / 3f,
            y = (first.y + second.y + third.y) / 3f,
            z = (first.z + second.z + third.z) / 3f,
        )
        output.appendTriangle(
            first,
            second,
            third,
            normal,
            surfaceColour(centre, state, tunnelSegments, gridStepMetres),
        )
    }

    private fun surfaceColour(
        point: MinePoint3D,
        state: MineWorldState,
        tunnelSegments: Collection<TunnelSegment>,
        gridStepMetres: Float,
    ): FloatArray {
        val oreAtWall = state.oreBodyDiscovered &&
            MineWorldGeometry.oreMargin(point, state.oreBody) >= -(gridStepMetres * 0.75f)
        val activeFace = MineWorldGeometry.distance(point, state.tunnel.end) <=
            state.tunnel.radiusMetres * 1.35f
        return when {
            oreAtWall -> ORE_COLOUR
            activeFace -> floatArrayOf(0.88f, 0.62f, 0.19f)
            else -> TUNNEL_COLOUR
        }
    }

    private fun rockColour(point: MinePoint3D, state: MineWorldState): FloatArray {
        val depthShade = (point.z / max(1f, state.bounds.maxZ)).coerceIn(0f, 1f)
        return floatArrayOf(
            0.48f - (depthShade * 0.10f),
            0.46f - (depthShade * 0.09f),
            0.43f - (depthShade * 0.07f),
        )
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

    private fun normalized(point: MinePoint3D): MinePoint3D {
        val length = sqrt((point.x * point.x) + (point.y * point.y) + (point.z * point.z))
        if (length < 0.0001f) return MinePoint3D(1f, 0f, 0f)
        return MinePoint3D(point.x / length, point.y / length, point.z / length)
    }

    private fun cross(a: MinePoint3D, b: MinePoint3D) = MinePoint3D(
        x = (a.y * b.z) - (a.z * b.y),
        y = (a.z * b.x) - (a.x * b.z),
        z = (a.x * b.y) - (a.y * b.x),
    )

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

    private class FloatAccumulator(initialCapacity: Int = 32_768) {
        private var data = FloatArray(initialCapacity.coerceAtLeast(64))
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

        fun toMesh(): MineMesh {
            val vertices = data.copyOf(size)
            return MineMesh(vertices = vertices, vertexCount = size / 9)
        }
    }

    private val ORE_COLOUR = floatArrayOf(0.72f, 0.35f, 0.94f)
    private val TUNNEL_COLOUR = floatArrayOf(0.23f, 0.26f, 0.29f)
    private val GRASS_COLOUR = floatArrayOf(0.20f, 0.48f, 0.22f)
}
