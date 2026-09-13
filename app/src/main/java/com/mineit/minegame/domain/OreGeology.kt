package com.mineit.minegame.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class OreType {
    GOLD,
    SILVER,
    COPPER,
}

enum class OreDepositArchetype {
    TABULAR_VEIN,
    LENS_MASSIVE,
    LAYERED_STRATIFORM,
    DISSEMINATED_STOCKWORK,
}

/**
 * Immutable original ore geometry.
 *
 * [margin] is the canonical material field: positive means solid ore, zero is the original ore
 * surface and negative means outside the deposit. [bounds] and [planningBounds] are conservative
 * broad-phase helpers only; gameplay truth always comes from [margin].
 */
sealed interface OreDepositGeometry {
    val archetype: OreDepositArchetype
    val bounds: MineWorldBounds

    fun margin(point: MinePoint3D): Float

    fun planningBounds(): List<MineWorldBounds>
}

data class OreBody(
    val id: String,
    val type: OreType,
    val geometry: OreDepositGeometry,
)

data class TabularVeinGeometry(
    val centre: MinePoint3D,
    val strikeDegrees: Float,
    val dipDegrees: Float,
    val lengthMetres: Float,
    val widthMetres: Float,
    val thicknessMetres: Float,
    val pinchAmplitude: Float,
    val waveAmplitudeMetres: Float,
    val waveCycles: Float,
    val phaseRadians: Float,
) : OreDepositGeometry {
    override val archetype: OreDepositArchetype = OreDepositArchetype.TABULAR_VEIN
    private val basis = oreBasis(strikeDegrees, dipDegrees)
    private val halfLength = lengthMetres * 0.5f
    private val halfWidth = widthMetres * 0.5f
    private val halfThickness = thicknessMetres * 0.5f
    private val halfNormalExtent = halfThickness + waveAmplitudeMetres

    override val bounds: MineWorldBounds = orientedBounds(
        centre = centre,
        basis = basis,
        halfU = halfLength,
        halfV = halfWidth,
        halfN = halfNormalExtent,
    )

    private val localPlanningBounds = segmentedPlanningBounds(
        centre = centre,
        basis = basis,
        halfU = halfLength,
        halfV = halfWidth,
        halfN = halfNormalExtent,
        targetSegmentLengthMetres = 18f,
    )

    override fun margin(point: MinePoint3D): Float {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        val dz = point.z - centre.z
        val localU = (dx * basis.u.x) + (dy * basis.u.y) + (dz * basis.u.z)
        val localV = (dx * basis.v.x) + (dy * basis.v.y) + (dz * basis.v.z)
        val localN = (dx * basis.n.x) + (dy * basis.n.y) + (dz * basis.n.z)
        val uRatio = if (halfLength > 0f) localU / halfLength else 0f
        val vRatio = if (halfWidth > 0f) localV / halfWidth else 0f
        val footprintMargin = min(halfLength - abs(localU), halfWidth - abs(localV))
        val uTaper = sqrt(max(0f, 1f - (uRatio * uRatio)))
        val vTaper = sqrt(max(0f, 1f - (vRatio * vRatio)))
        val edgeTaper = 0.55f + (0.45f * uTaper * vTaper)
        val pinch = (
            1f + pinchAmplitude * sin((uRatio * 2f * PI.toFloat()) + phaseRadians)
            ).coerceIn(0.45f, 1.55f)
        val wave = waveAmplitudeMetres *
            sin((uRatio * PI.toFloat() * waveCycles) + phaseRadians) *
            cos(vRatio * PI.toFloat() * 0.5f)
        val thicknessMargin = (halfThickness * pinch * edgeTaper) - abs(localN - wave)
        return min(footprintMargin, thicknessMargin)
    }

    override fun planningBounds(): List<MineWorldBounds> = localPlanningBounds
}

data class LensMassiveGeometry(
    val centre: MinePoint3D,
    val strikeDegrees: Float,
    val dipDegrees: Float,
    val halfLengthMetres: Float,
    val halfWidthMetres: Float,
    val halfThicknessMetres: Float,
    val irregularityMetres: Float,
    val phaseRadians: Float,
) : OreDepositGeometry {
    override val archetype: OreDepositArchetype = OreDepositArchetype.LENS_MASSIVE
    private val basis = oreBasis(strikeDegrees, dipDegrees)
    private val baseScale = minOf(halfLengthMetres, halfWidthMetres, halfThicknessMetres)
    private val irregularityPaddingMetres = conservativeIrregularityPadding(
        irregularityMetres,
        baseScale,
        maxOf(halfLengthMetres, halfWidthMetres, halfThicknessMetres),
    )

    override val bounds: MineWorldBounds = orientedBounds(
        centre,
        basis,
        halfLengthMetres,
        halfWidthMetres,
        halfThicknessMetres,
        irregularityPaddingMetres,
    )

    private val localPlanningBounds = segmentedPlanningBounds(
        centre = centre,
        basis = basis,
        halfU = halfLengthMetres,
        halfV = halfWidthMetres,
        halfN = halfThicknessMetres,
        targetSegmentLengthMetres = 16f,
        paddingMetres = irregularityPaddingMetres,
    )

    override fun margin(point: MinePoint3D): Float {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        val dz = point.z - centre.z
        val localU = (dx * basis.u.x) + (dy * basis.u.y) + (dz * basis.u.z)
        val localV = (dx * basis.v.x) + (dy * basis.v.y) + (dz * basis.v.z)
        val localN = (dx * basis.n.x) + (dy * basis.n.y) + (dz * basis.n.z)
        val normalized = sqrt(
            square(localU / halfLengthMetres) +
                square(localV / halfWidthMetres) +
                square(localN / halfThicknessMetres),
        )
        val noise = irregularityMetres * (
            sin((localU * 0.17f) + phaseRadians) +
                sin((localV * 0.21f) - (phaseRadians * 0.7f)) +
                sin((localN * 0.31f) + (phaseRadians * 1.4f))
            ) / 3f
        return (baseScale * (1f - normalized)) + noise
    }

    override fun planningBounds(): List<MineWorldBounds> = localPlanningBounds
}

data class LayeredStratiformGeometry(
    val centre: MinePoint3D,
    val strikeDegrees: Float,
    val dipDegrees: Float,
    val lengthMetres: Float,
    val widthMetres: Float,
    val thicknessMetres: Float,
    val waveAmplitudeMetres: Float,
    val phaseRadians: Float,
) : OreDepositGeometry {
    override val archetype: OreDepositArchetype = OreDepositArchetype.LAYERED_STRATIFORM
    private val basis = oreBasis(strikeDegrees, dipDegrees)
    private val halfLength = lengthMetres * 0.5f
    private val halfWidth = widthMetres * 0.5f
    private val halfThickness = thicknessMetres * 0.5f
    private val halfNormalExtent = halfThickness * 1.12f + waveAmplitudeMetres

    override val bounds: MineWorldBounds = orientedBounds(
        centre = centre,
        basis = basis,
        halfU = halfLength,
        halfV = halfWidth,
        halfN = halfNormalExtent,
    )

    private val localPlanningBounds = segmentedPlanningBounds(
        centre = centre,
        basis = basis,
        halfU = halfLength,
        halfV = halfWidth,
        halfN = halfNormalExtent,
        targetSegmentLengthMetres = 18f,
    )

    override fun margin(point: MinePoint3D): Float {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        val dz = point.z - centre.z
        val localU = (dx * basis.u.x) + (dy * basis.u.y) + (dz * basis.u.z)
        val localV = (dx * basis.v.x) + (dy * basis.v.y) + (dz * basis.v.z)
        val localN = (dx * basis.n.x) + (dy * basis.n.y) + (dz * basis.n.z)
        val footprintMargin = min(halfLength - abs(localU), halfWidth - abs(localV))
        val wave = waveAmplitudeMetres * 0.5f * (
            sin((localU / max(1f, lengthMetres)) * 4f * PI.toFloat() + phaseRadians) +
                sin((localV / max(1f, widthMetres)) * 3f * PI.toFloat() - phaseRadians)
            )
        val thicknessVariation = 1f + 0.12f * sin(
            ((localU + localV) * 0.07f) + (phaseRadians * 0.5f),
        )
        val thicknessMargin = (halfThickness * thicknessVariation) - abs(localN - wave)
        return min(footprintMargin, thicknessMargin)
    }

    override fun planningBounds(): List<MineWorldBounds> = localPlanningBounds
}

data class DisseminatedStockworkGeometry(
    val centre: MinePoint3D,
    val strikeDegrees: Float,
    val dipDegrees: Float,
    val halfLengthMetres: Float,
    val halfWidthMetres: Float,
    val halfHeightMetres: Float,
    val irregularityMetres: Float,
    val phaseRadians: Float,
) : OreDepositGeometry {
    override val archetype: OreDepositArchetype = OreDepositArchetype.DISSEMINATED_STOCKWORK
    private val basis = oreBasis(strikeDegrees, dipDegrees)
    private val baseScale = minOf(halfLengthMetres, halfWidthMetres, halfHeightMetres)
    private val irregularityPaddingMetres = conservativeIrregularityPadding(
        irregularityMetres,
        baseScale,
        maxOf(halfLengthMetres, halfWidthMetres, halfHeightMetres),
    )

    override val bounds: MineWorldBounds = orientedBounds(
        centre,
        basis,
        halfLengthMetres,
        halfWidthMetres,
        halfHeightMetres,
        irregularityPaddingMetres,
    )

    private val localPlanningBounds = segmentedPlanningBounds(
        centre = centre,
        basis = basis,
        halfU = halfLengthMetres,
        halfV = halfWidthMetres,
        halfN = halfHeightMetres,
        targetSegmentLengthMetres = 18f,
        paddingMetres = irregularityPaddingMetres,
    )

    override fun margin(point: MinePoint3D): Float {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        val dz = point.z - centre.z
        val localU = (dx * basis.u.x) + (dy * basis.u.y) + (dz * basis.u.z)
        val localV = (dx * basis.v.x) + (dy * basis.v.y) + (dz * basis.v.z)
        val localN = (dx * basis.n.x) + (dy * basis.n.y) + (dz * basis.n.z)
        val normalized = sqrt(
            square(localU / halfLengthMetres) +
                square(localV / halfWidthMetres) +
                square(localN / halfHeightMetres),
        )
        val noise = irregularityMetres * (
            sin((localU * 0.13f) + phaseRadians) +
                sin((localV * 0.16f) - phaseRadians) +
                sin((localN * 0.19f) + (phaseRadians * 0.6f)) +
                sin(((localU + localV - localN) * 0.09f) + (phaseRadians * 1.3f))
            ) / 4f
        return (baseScale * (1f - normalized)) + noise
    }

    override fun planningBounds(): List<MineWorldBounds> = localPlanningBounds
}

/** Deterministic, gameplay-oriented Stage 2 geology generator. */
object OreGeologyGenerator {
    fun generate(seed: Int): List<OreBody> {
        val random = Random(seed)
        return listOf(
            OreBody(
                id = "gold-1",
                type = OreType.GOLD,
                geometry = TabularVeinGeometry(
                    centre = MinePoint3D(11.5f, 5.4f, 18.5f),
                    strikeDegrees = 295f,
                    dipDegrees = 52f,
                    lengthMetres = random.nextFloat(52f, 70f),
                    widthMetres = random.nextFloat(16f, 22f),
                    thicknessMetres = random.nextFloat(2.2f, 3.4f),
                    pinchAmplitude = random.nextFloat(0.18f, 0.34f),
                    waveAmplitudeMetres = random.nextFloat(0.45f, 0.95f),
                    waveCycles = random.nextFloat(1.4f, 2.4f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
            OreBody(
                id = "gold-2",
                type = OreType.GOLD,
                geometry = TabularVeinGeometry(
                    centre = randomCentre(random, minDepth = 42f, maxDepth = 105f),
                    strikeDegrees = random.nextFloat(0f, 360f),
                    dipDegrees = random.nextFloat(32f, 72f),
                    lengthMetres = random.nextFloat(58f, 85f),
                    widthMetres = random.nextFloat(12f, 20f),
                    thicknessMetres = random.nextFloat(1.8f, 3.2f),
                    pinchAmplitude = random.nextFloat(0.20f, 0.38f),
                    waveAmplitudeMetres = random.nextFloat(0.35f, 0.9f),
                    waveCycles = random.nextFloat(1.4f, 2.8f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
            OreBody(
                id = "silver-1",
                type = OreType.SILVER,
                geometry = TabularVeinGeometry(
                    centre = MinePoint3D(-12f, 9f, 30f),
                    strikeDegrees = 28f,
                    dipDegrees = 24f,
                    lengthMetres = random.nextFloat(60f, 88f),
                    widthMetres = random.nextFloat(20f, 29f),
                    thicknessMetres = random.nextFloat(3f, 5f),
                    pinchAmplitude = random.nextFloat(0.10f, 0.22f),
                    waveAmplitudeMetres = random.nextFloat(0.3f, 0.7f),
                    waveCycles = random.nextFloat(1.1f, 2f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
            OreBody(
                id = "silver-2",
                type = OreType.SILVER,
                geometry = LensMassiveGeometry(
                    centre = randomCentre(random, minDepth = 42f, maxDepth = 110f),
                    strikeDegrees = random.nextFloat(0f, 360f),
                    dipDegrees = random.nextFloat(8f, 55f),
                    halfLengthMetres = random.nextFloat(14f, 24f),
                    halfWidthMetres = random.nextFloat(9f, 16f),
                    halfThicknessMetres = random.nextFloat(4f, 8f),
                    irregularityMetres = random.nextFloat(0.35f, 0.9f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
            OreBody(
                id = "copper-1",
                type = OreType.COPPER,
                geometry = DisseminatedStockworkGeometry(
                    centre = MinePoint3D(7f, -13f, 38f),
                    strikeDegrees = 305f,
                    dipDegrees = 14f,
                    halfLengthMetres = random.nextFloat(22.5f, 34f),
                    halfWidthMetres = random.nextFloat(18f, 29f),
                    halfHeightMetres = random.nextFloat(10f, 17f),
                    irregularityMetres = random.nextFloat(1.1f, 2.6f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
            OreBody(
                id = "copper-2",
                type = OreType.COPPER,
                geometry = LayeredStratiformGeometry(
                    centre = randomCentre(random, minDepth = 52f, maxDepth = 120f),
                    strikeDegrees = random.nextFloat(0f, 360f),
                    dipDegrees = random.nextFloat(4f, 24f),
                    lengthMetres = random.nextFloat(78f, 126f),
                    widthMetres = random.nextFloat(58f, 96f),
                    thicknessMetres = random.nextFloat(6f, 11f),
                    waveAmplitudeMetres = random.nextFloat(0.6f, 1.5f),
                    phaseRadians = random.nextFloat(0f, (2f * PI).toFloat()),
                ),
            ),
        )
    }

    private fun randomCentre(random: Random, minDepth: Float, maxDepth: Float): MinePoint3D = MinePoint3D(
        x = random.nextFloat(-78f, 78f),
        y = random.nextFloat(-78f, 78f),
        z = random.nextFloat(minDepth, maxDepth),
    )

    private fun Random.nextFloat(minimum: Float, maximum: Float): Float =
        minimum + (nextFloat() * (maximum - minimum))
}

private data class OreBasis(
    val u: MinePoint3D,
    val v: MinePoint3D,
    val n: MinePoint3D,
)

private fun oreBasis(strikeDegrees: Float, dipDegrees: Float): OreBasis {
    val strike = Math.toRadians(strikeDegrees.toDouble())
    val dip = Math.toRadians(dipDegrees.toDouble())
    val u = MinePoint3D(
        cos(strike).toFloat(),
        sin(strike).toFloat(),
        0f,
    )
    val v = MinePoint3D(
        -sin(strike).toFloat() * cos(dip).toFloat(),
        cos(strike).toFloat() * cos(dip).toFloat(),
        sin(dip).toFloat(),
    )
    val n = normalized(cross(u, v))
    return OreBasis(u = u, v = v, n = n)
}

private fun orientedBounds(
    centre: MinePoint3D,
    basis: OreBasis,
    halfU: Float,
    halfV: Float,
    halfN: Float,
    paddingMetres: Float = 0f,
): MineWorldBounds {
    val extentX = abs(basis.u.x) * halfU + abs(basis.v.x) * halfV + abs(basis.n.x) * halfN + paddingMetres
    val extentY = abs(basis.u.y) * halfU + abs(basis.v.y) * halfV + abs(basis.n.y) * halfN + paddingMetres
    val extentZ = abs(basis.u.z) * halfU + abs(basis.v.z) * halfV + abs(basis.n.z) * halfN + paddingMetres
    return MineWorldBounds(
        minX = centre.x - extentX,
        maxX = centre.x + extentX,
        minY = centre.y - extentY,
        maxY = centre.y + extentY,
        minZ = centre.z - extentZ,
        maxZ = centre.z + extentZ,
    )
}

private fun segmentedPlanningBounds(
    centre: MinePoint3D,
    basis: OreBasis,
    halfU: Float,
    halfV: Float,
    halfN: Float,
    targetSegmentLengthMetres: Float,
    paddingMetres: Float = 0f,
): List<MineWorldBounds> {
    val totalLength = halfU * 2f
    val count = max(1, ceil(totalLength / targetSegmentLengthMetres).toInt())
    val segmentLength = totalLength / count.toFloat()
    return List(count) { index ->
        val localU = -halfU + ((index + 0.5f) * segmentLength)
        val segmentCentre = MinePoint3D(
            x = centre.x + (basis.u.x * localU),
            y = centre.y + (basis.u.y * localU),
            z = centre.z + (basis.u.z * localU),
        )
        orientedBounds(
            centre = segmentCentre,
            basis = basis,
            halfU = (segmentLength * 0.5f) + paddingMetres,
            halfV = halfV + paddingMetres,
            halfN = halfN + paddingMetres,
        )
    }
}

private fun conservativeIrregularityPadding(
    irregularityMetres: Float,
    baseScale: Float,
    maximumHalfExtent: Float,
): Float {
    if (irregularityMetres <= 0f || baseScale <= 0f) return 0f
    return irregularityMetres * (maximumHalfExtent / baseScale)
}

private fun cross(a: MinePoint3D, b: MinePoint3D): MinePoint3D = MinePoint3D(
    x = (a.y * b.z) - (a.z * b.y),
    y = (a.z * b.x) - (a.x * b.z),
    z = (a.x * b.y) - (a.y * b.x),
)

private fun normalized(point: MinePoint3D): MinePoint3D {
    val length = sqrt(square(point.x) + square(point.y) + square(point.z))
    if (length < 0.0001f) return MinePoint3D(0f, 0f, 1f)
    return MinePoint3D(point.x / length, point.y / length, point.z / length)
}

private fun square(value: Float): Float = value * value
